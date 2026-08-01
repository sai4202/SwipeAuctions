import type { CSSProperties } from 'react'
import type { SortDir } from '../useSort'

interface Props {
  label: string
  sortKey: string
  activeKey: string | null
  dir: SortDir
  onSort: (key: string) => void
  style?: CSSProperties
}

/** A clickable <th> that toggles asc/desc sort on its column — pair with useSortableData. */
export default function SortableTh({ label, sortKey, activeKey, dir, onSort, style }: Props) {
  const active = activeKey === sortKey
  return (
    <th
      onClick={() => onSort(sortKey)}
      style={{ cursor: 'pointer', userSelect: 'none', whiteSpace: 'nowrap', ...style }}
      aria-sort={active ? (dir === 'asc' ? 'ascending' : 'descending') : 'none'}
    >
      {label}
      <span style={{ marginLeft: 4, fontSize: 10, opacity: active ? 1 : .35 }}>
        {active ? (dir === 'asc' ? '▲' : '▼') : '↕'}
      </span>
    </th>
  )
}
