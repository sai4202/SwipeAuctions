import { useEffect, useRef, useState } from 'react'

interface CacheEntry<T> { data: T; time: number }

// Module-level (survives across component mounts/navigations, cleared on a full page reload) —
// exactly what makes revisiting a page feel instant instead of re-paying the full fetch every time.
const cache = new Map<string, CacheEntry<unknown>>()

/** Forces the next read of `key` to refetch instead of serving cached data — call after a mutation
 *  (placing a bid, registering, etc.) that makes the cached list/detail stale. */
export function invalidateCache(key: string): void {
  cache.delete(key)
}

export function primeCache<T>(key: string, data: T): void {
  cache.set(key, { data, time: Date.now() })
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
    fetcherRef.current()
      .then((result) => {
        if (cancelled) return
        cache.set(key, { data: result, time: Date.now() })
        setData(result)
      })
      .catch((e) => { if (!cancelled) onErrorRef.current?.(e) })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [key, maxAgeMs])

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
