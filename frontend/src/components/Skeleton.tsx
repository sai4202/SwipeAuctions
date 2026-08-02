import type { CSSProperties } from 'react'

/** A single shimmering placeholder block — the building block every skeleton below is made of. */
export function Skeleton({ width, height, radius = 6, style }: {
  width?: string | number; height?: string | number; radius?: number; style?: CSSProperties
}) {
  return <div className="skeleton" style={{ width, height, borderRadius: radius, ...style }} />
}

/** A handful of text-line placeholders, last one shorter so it doesn't look like a solid bar. */
export function SkeletonLines({ count = 3, lineHeight = 12, gap = 8 }: { count?: number; lineHeight?: number; gap?: number }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap }}>
      {Array.from({ length: count }).map((_, i) => (
        <Skeleton key={i} height={lineHeight} width={i === count - 1 ? '60%' : '100%'} />
      ))}
    </div>
  )
}

/** Mirrors AuctionCard's layout so the grid doesn't visibly reflow once real cards swap in. */
export function AuctionCardSkeleton() {
  return (
    <div className="card acard">
      <Skeleton height={180} radius={12} style={{ marginBottom: 14 }} />
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 10 }}>
        <Skeleton width={70} height={11} />
        <Skeleton width={50} height={11} />
      </div>
      <Skeleton height={16} width="85%" style={{ marginBottom: 10 }} />
      <Skeleton height={11} width="50%" style={{ marginBottom: 16 }} />
      <Skeleton height={11} width={90} style={{ marginBottom: 8 }} />
      <Skeleton height={22} width="70%" style={{ marginBottom: 10 }} />
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <Skeleton width={60} height={11} />
        <Skeleton width={80} height={11} />
      </div>
      <div style={{ display: 'flex', gap: 10 }}>
        <Skeleton height={34} style={{ flex: 1 }} radius={8} />
        <Skeleton height={34} style={{ flex: 1 }} radius={8} />
      </div>
    </div>
  )
}

/** A grid of card skeletons — drop-in replacement for the "Loading…" line above an auction grid. */
export function AuctionGridSkeleton({ count = 8 }: { count?: number }) {
  return (
    <div className="grid">
      {Array.from({ length: count }).map((_, i) => <AuctionCardSkeleton key={i} />)}
    </div>
  )
}

/** Generic table-row skeleton for admin/transaction/list tables — `cols` plain cells per row. */
export function SkeletonTableRows({ rows = 5, cols = 4 }: { rows?: number; cols?: number }) {
  return (
    <>
      {Array.from({ length: rows }).map((_, r) => (
        <tr key={r}>
          {Array.from({ length: cols }).map((_, c) => (
            <td key={c}><Skeleton height={13} width={c === 0 ? '80%' : '60%'} /></td>
          ))}
        </tr>
      ))}
    </>
  )
}

/** A row of stat-tile skeletons — matches .stat-tiles/.stat-tile (admin dashboard summary cards). */
export function StatTilesSkeleton({ count = 4 }: { count?: number }) {
  return (
    <div className="stat-tiles">
      {Array.from({ length: count }).map((_, i) => (
        <div className="stat-tile" key={i}>
          <Skeleton height={11} width="50%" style={{ marginBottom: 10 }} />
          <Skeleton height={22} width="70%" />
        </div>
      ))}
    </div>
  )
}

/** Mirrors .event-tile's layout (HomePage's "Featured auction events") while GET /api/events loads. */
export function EventTileGridSkeleton({ count = 5 }: { count?: number }) {
  return (
    <div className="event-tile-grid">
      {Array.from({ length: count }).map((_, i) => (
        <div className="event-tile" key={i}>
          <Skeleton height={16} width="80%" style={{ marginBottom: 6 }} />
          <Skeleton height={12} width="55%" style={{ marginBottom: 10 }} />
          <Skeleton height={12} width="65%" style={{ marginBottom: 14 }} />
          <Skeleton height={12} width={90} />
        </div>
      ))}
    </div>
  )
}

/** Mirrors AdminAnalytics's 2x2 chart grid while GET /api/admin/analytics loads. */
export function ChartGridSkeleton({ count = 4 }: { count?: number }) {
  return (
    <div className="chart-grid">
      {Array.from({ length: count }).map((_, i) => (
        <div className="card chart-card" key={i}>
          <Skeleton height={11} width="40%" style={{ marginBottom: 14 }} />
          <Skeleton height={160} radius={8} />
        </div>
      ))}
    </div>
  )
}

/** Mirrors AuctionDetailPage's two-column layout (gallery + buy box) while the auction loads. */
export function AuctionDetailSkeleton() {
  return (
    <div className="pdp-layout">
      <div className="card pdp-gallery">
        <Skeleton height={420} radius={12} />
      </div>
      <div className="card pdp-buybox">
        <Skeleton height={20} width={70} radius={20} style={{ marginBottom: 14 }} />
        <Skeleton height={26} width="90%" style={{ marginBottom: 10 }} />
        <Skeleton height={13} width="50%" style={{ marginBottom: 20 }} />
        <Skeleton height={36} width="60%" style={{ marginBottom: 20 }} />
        <Skeleton height={44} radius={8} />
      </div>
    </div>
  )
}
