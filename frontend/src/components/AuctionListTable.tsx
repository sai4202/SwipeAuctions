import { type Auction } from '../api'
import AuctionListRow from './AuctionListRow'
import SortableTh from './SortableTh'
import { useSortableData } from '../useSort'

interface Props {
  auctions: Auction[]
  multiIds: string[]
  onToggleTray: (id: string) => void
}

function getSortValue(a: Auction, key: string) {
  switch (key) {
    case 'title': return a.title?.toLowerCase()
    case 'startTime': return a.startTime
    case 'endTime': return a.currentEndTime
    case 'basePrice': return a.basePrice
    case 'currentBid': return a.currentHighestBid ?? -1
    case 'bids': return a.bidCount
    case 'bidsLeft': return a.bidsRemaining ?? -1
    default: return null
  }
}

/**
 * Flat "list view" rendering of an auction set — the table-style alternative to AuctionCard's
 * grid ("catalogue view"), toggled by the user on the browse/swipe-stock pages. Each row
 * (AuctionListRow) carries the exact same inline bid flow as the grid card.
 */
export default function AuctionListTable({ auctions, multiIds, onToggleTray }: Props) {
  const { sorted, sortKey, sortDir, toggleSort } = useSortableData(auctions, getSortValue)
  return (
    <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
      <div style={{ overflowX: 'auto', padding: 16 }}>
        <table className="admin-table list-view-table">
          <colgroup>
            <col style={{ width: 90 }} /><col />
            <col style={{ width: 135 }} /><col style={{ width: 135 }} />
            <col style={{ width: 105 }} /><col style={{ width: 105 }} />
            <col style={{ width: 60 }} /><col style={{ width: 70 }} /><col style={{ width: 200 }} />
          </colgroup>
          <thead>
            <tr>
              <th>Image</th>
              <SortableTh label="Details" sortKey="title" activeKey={sortKey} dir={sortDir} onSort={toggleSort} />
              <SortableTh label="Start Time" sortKey="startTime" activeKey={sortKey} dir={sortDir} onSort={toggleSort} />
              <SortableTh label="End Time" sortKey="endTime" activeKey={sortKey} dir={sortDir} onSort={toggleSort} />
              <SortableTh label="Base Price" sortKey="basePrice" activeKey={sortKey} dir={sortDir} onSort={toggleSort} />
              <SortableTh label="Current Bid" sortKey="currentBid" activeKey={sortKey} dir={sortDir} onSort={toggleSort} />
              <SortableTh label="Bids" sortKey="bids" activeKey={sortKey} dir={sortDir} onSort={toggleSort} />
              <SortableTh label="Bids Left" sortKey="bidsLeft" activeKey={sortKey} dir={sortDir} onSort={toggleSort} />
              <th>Action</th>
            </tr>
          </thead>
          <tbody>
            {sorted.map((a) => (
              <AuctionListRow key={a.id} auction={a} inTray={multiIds.includes(a.id)} onToggleTray={onToggleTray} />
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
