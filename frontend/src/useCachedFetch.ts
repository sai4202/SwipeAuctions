import { useEffect, useRef, useState } from 'react'

interface CacheEntry<T> { data: T; time: number }

// Module-level (survives across component mounts/navigations, cleared on a full page reload) —
// exactly what makes revisiting a page feel instant instead of re-paying the full fetch every time.
const cache = new Map<string, CacheEntry<unknown>>()
// One real network request per key at a time — if a second useCachedFetch(key) mounts (or
// StrictMode's dev-only double-effect fires) while the first is still in flight, it awaits this
// same promise instead of firing its own duplicate request. Without this, two components sharing
// a cache key (or a single one under StrictMode) both hit the network before either has cached a
// result, doubling load on every cold page view.
const inFlight = new Map<string, Promise<unknown>>()
// Every currently-mounted useCachedFetch(key) hook registers a callback here so updateCache() can
// push a change straight into it, instead of the change only showing up on that key's next mount.
const subscribers = new Map<string, Set<(data: unknown) => void>>()

/** Forces the next read of `key` to refetch instead of serving cached data — call after a mutation
 *  (placing a bid, registering, etc.) that makes the cached list/detail stale. */
export function invalidateCache(key: string): void {
  cache.delete(key)
}

export function primeCache<T>(key: string, data: T): void {
  cache.set(key, { data, time: Date.now() })
}

/**
 * Patches an already-cached value in place and immediately re-renders every currently-mounted
 * useCachedFetch(key) consumer with the result — for a live push (e.g. a websocket notification)
 * that needs to show up on screen right now rather than waiting for that key's next mount or TTL
 * expiry. No-ops if nothing is cached for `key` yet, since there's no base value to patch and
 * nothing mounted needs it.
 */
export function updateCache<T>(key: string, updater: (data: T) => T): void {
  const current = cache.get(key) as CacheEntry<T> | undefined
  if (!current) return
  const next = updater(current.data)
  cache.set(key, { data: next, time: current.time })
  subscribers.get(key)?.forEach((fn) => fn(next))
}

const DEFAULT_MAX_AGE_MS = 20_000

/**
 * Stale-while-revalidate data fetching: if a cached value exists for `key` (from an earlier mount,
 * even on a different page instance), it's shown immediately with no loading state, while a fresh
 * fetch happens in the background and silently swaps in once it resolves. Only shows `loading` when
 * there's truly nothing cached yet — that's the "loading…" text callers can replace with a skeleton.
 */
export function useCachedFetch<T>(
  key: string | null,
  fetcher: () => Promise<T>,
  opts?: { maxAgeMs?: number; onError?: (e: unknown) => void },
) {
  const maxAgeMs = opts?.maxAgeMs ?? DEFAULT_MAX_AGE_MS
  const existing = key ? (cache.get(key) as CacheEntry<T> | undefined) : undefined
  const [data, setData] = useState<T | undefined>(existing?.data)
  const [loading, setLoading] = useState(!existing)

  // Keep the latest fetcher/onError without making them effect dependencies (they're new closures
  // every render) — only `key`/`maxAgeMs` should actually trigger a re-fetch.
  const fetcherRef = useRef(fetcher)
  fetcherRef.current = fetcher
  const onErrorRef = useRef(opts?.onError)
  onErrorRef.current = opts?.onError

  useEffect(() => {
    if (!key) return
    let cancelled = false
    const current = cache.get(key) as CacheEntry<T> | undefined
    if (current) {
      setData(current.data)
      setLoading(false)
      if (Date.now() - current.time < maxAgeMs) return // fresh enough — skip the network entirely
    } else {
      setLoading(true)
    }
    let promise = inFlight.get(key) as Promise<T> | undefined
    if (!promise) {
      promise = fetcherRef.current()
      inFlight.set(key, promise)
      promise.finally(() => { if (inFlight.get(key) === promise) inFlight.delete(key) })
    }
    promise
      .then((result) => {
        if (cancelled) return
        cache.set(key, { data: result, time: Date.now() })
        setData(result)
      })
      .catch((e) => { if (!cancelled) onErrorRef.current?.(e) })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [key, maxAgeMs])

  // Separate from the fetch effect above: this only needs to track live pushes via updateCache(),
  // not re-run on maxAgeMs changes.
  useEffect(() => {
    if (!key) return
    const fn = (data: unknown) => setData(data as T)
    const set = subscribers.get(key) ?? new Set()
    set.add(fn)
    subscribers.set(key, set)
    return () => {
      set.delete(fn)
      if (set.size === 0) subscribers.delete(key)
    }
  }, [key])

  const refresh = () => {
    if (!key) return Promise.resolve(undefined)
    return fetcherRef.current().then((result) => {
      cache.set(key, { data: result, time: Date.now() })
      setData(result)
      return result
    }).catch((e) => { onErrorRef.current?.(e); return undefined })
  }

  return { data, loading, setData, refresh }
}
