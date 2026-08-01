import { useMemo, useState } from 'react'

export type SortDir = 'asc' | 'desc'

/**
 * Client-side (current-page — tables here are server-paginated, so this only reorders what's
 * already loaded) sort-by-column-header state. `getValue` extracts the comparable value for a
 * given sort key from a row; ISO date strings sort correctly as plain strings.
 */
export function useSortableData<T>(
  data: T[],
  getValue: (item: T, key: string) => string | number | boolean | null | undefined,
) {
  const [sortKey, setSortKey] = useState<string | null>(null)
  const [sortDir, setSortDir] = useState<SortDir>('asc')

  const toggleSort = (key: string) => {
    if (sortKey === key) setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'))
    else { setSortKey(key); setSortDir('asc') }
  }

  const sorted = useMemo(() => {
    if (!sortKey) return data
    const dir = sortDir === 'asc' ? 1 : -1
    return [...data].sort((a, b) => {
      const av = getValue(a, sortKey)
      const bv = getValue(b, sortKey)
      if (av == null && bv == null) return 0
      if (av == null) return 1
      if (bv == null) return -1
      if (typeof av === 'string' || typeof bv === 'string') return String(av).localeCompare(String(bv)) * dir
      return (Number(av) - Number(bv)) * dir
    })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [data, sortKey, sortDir])

  return { sorted, sortKey, sortDir, toggleSort }
}
