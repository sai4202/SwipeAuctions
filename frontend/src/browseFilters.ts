import type { Auction } from './api'

/** A category filter as rendered in a filter bar — either the native `brand` field or a live-fetched attribute def. */
export type BrowseFilter = { key: string; label: string; type: 'brand' | 'ENUM' | 'NUMBER'; options: string[] }

/** NUMBER filters default to "at least" (e.g. Year (from)); these instead mean "at most". */
const AT_MOST_NUMBER_KEYS = new Set(['KM driven'])

/** URL param key for a category filter (namespaced to avoid clashing with global filters). */
export function filterParam(key: string): string {
  return 'a_' + key.toLowerCase().replace(/[^a-z0-9]+/g, '_')
}

/** Digits from a value like "45,000 km" → 45000; NaN if none. */
function toNumber(v: string | undefined): number {
  if (!v) return NaN
  return parseInt(v.replace(/[^0-9]/g, ''), 10)
}

/** Does an auction pass a single category filter set to `value`? */
export function matchesFilter(a: Auction, f: BrowseFilter, value: string): boolean {
  if (!value) return true
  if (f.type === 'brand') return (a.brand ?? '').toLowerCase() === value.toLowerCase()
  if (f.type === 'NUMBER') {
    const n = toNumber(a.attributes?.[f.key])
    if (isNaN(n)) return false
    return AT_MOST_NUMBER_KEYS.has(f.key) ? n <= Number(value) : n >= Number(value)
  }
  return (a.attributes?.[f.key] ?? '').toLowerCase() === value.toLowerCase()
}
