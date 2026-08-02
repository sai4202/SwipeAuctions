import { useEffect, useState, type ReactNode } from 'react'
import type {
  AdminUser, AuditLogEntry, AdminListing, AdminAuction, Dispute, AdminKyc, AdminUserBid,
} from '../../api'

export function getUserSortValue(u: AdminUser, key: string) {
  switch (key) {
    case 'email': return u.email?.toLowerCase()
    case 'mobile': return u.mobileNumber
    case 'role': return u.role
    case 'tier': return u.subscriptionTier
    case 'kyc': return u.kycStatus
    case 'deposit': return u.walletAvailableBalance
    case 'credit': return u.walletCreditLimit
    case 'activeBids': return u.activeBidCount
    case 'status': return u.active ? 1 : 0
    default: return null
  }
}

export const AUDIT_ACTIONS = [
  'USER_SUSPENDED', 'USER_REACTIVATED', 'HOLD_RELEASED', 'AUCTION_FORCE_CLOSED', 'AUCTION_MODIFIED',
  'LISTING_REQUIRED_TIER_CHANGED', 'LISTING_CATEGORY_CHANGED', 'DISPUTE_RESOLVED', 'CATEGORY_CREATED',
  'CATEGORY_ATTRIBUTE_ADDED', 'KYC_APPROVED', 'KYC_REJECTED', 'STOCK_LISTING_CREATED', 'STOCK_BULK_IMPORTED',
  'REGISTRATION_FEE_UPDATED', 'SUBSCRIPTION_PRICES_UPDATED', 'MEMBERSHIP_BENEFIT_ADDED',
  'MEMBERSHIP_BENEFIT_TIERS_UPDATED', 'MEMBERSHIP_BENEFIT_REMOVED', 'ADMIN_CREATED',
]
export const auditActionLabel = (a: string) => a.split('_').map((w) => w[0] + w.slice(1).toLowerCase()).join(' ')

export function getAuditLogSortValue(e: AuditLogEntry, key: string) {
  switch (key) {
    case 'time': return e.createdAt
    case 'admin': return e.adminEmail?.toLowerCase()
    case 'action': return e.action
    case 'target': return e.targetType
    default: return null
  }
}

export function getListingSortValue(l: AdminListing, key: string) {
  switch (key) {
    case 'title': return l.title?.toLowerCase()
    case 'seller': return l.sellerEmail?.toLowerCase()
    case 'category': return l.categoryName?.toLowerCase()
    case 'price': return l.reservePrice
    case 'status': return l.status
    case 'tier': return l.requiredTier
    default: return null
  }
}

export function getAuctionSortValue(a: AdminAuction, key: string) {
  switch (key) {
    case 'title': return a.title?.toLowerCase()
    case 'seller': return a.sellerEmail?.toLowerCase()
    case 'base': return a.basePrice
    case 'highest': return a.currentHighestBid
    case 'bids': return a.bidCount
    case 'status': return a.status
    default: return null
  }
}

export function getDisputeSortValue(d: Dispute, key: string) {
  switch (key) {
    case 'auction': return d.auctionTitle?.toLowerCase()
    case 'raisedBy': return d.raisedByEmail?.toLowerCase()
    case 'reason': return d.reason?.toLowerCase()
    case 'status': return d.status
    default: return null
  }
}

export function getKycSortValue(k: AdminKyc, key: string) {
  switch (key) {
    case 'email': return k.email?.toLowerCase()
    case 'fullName': return k.fullName?.toLowerCase() ?? ''
    case 'aadhaar': return k.aadhaarMasked ?? ''
    case 'pan': return k.panNumberMasked ?? ''
    case 'provider': return k.provider ?? ''
    case 'status': return k.status
    case 'submitted': return k.submittedAt ?? ''
    default: return null
  }
}

/** What to call a bid row given the auction's actual state — mirrors the same OPEN-vs-closed,
 *  isWinner-driven logic the bidder-facing card uses (AuctionCard.tsx), so admin and bidder never
 *  disagree about who's actually winning. */
export function bidRowStatus(b: AdminUserBid): { label: string; cls: 'ok' | 'error' } {
  const closed = b.auctionStatus === 'CLOSED' || b.auctionStatus === 'UNSOLD'
  if (closed) return b.leading ? { label: 'Won', cls: 'ok' } : { label: 'Lost', cls: 'error' }
  return b.leading ? { label: 'Leading', cls: 'ok' } : { label: 'Outbid', cls: 'error' }
}

/** yyyy-MM-ddTHH:mm for a <input type="datetime-local">, from an ISO LocalDateTime string. */
export function toLocalInput(iso: string) {
  return iso.slice(0, 16)
}

/** Counts up from 0 to `value` on mount/change, honoring prefers-reduced-motion. */
export function AnimatedNumber({ value, format }: { value: number; format: (n: number) => string }) {
  const [display, setDisplay] = useState(0)

  useEffect(() => {
    if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) { setDisplay(value); return }
    const duration = 650
    const start = performance.now()
    let raf = 0
    const tick = (now: number) => {
      const t = Math.min(1, (now - start) / duration)
      setDisplay(value * (1 - Math.pow(1 - t, 3)))
      if (t < 1) raf = requestAnimationFrame(tick)
    }
    raf = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(raf)
  }, [value])

  return <>{format(Math.round(display))}</>
}

/** Shared Prev/Next pager for the paginated admin dashboard tabs. 0-indexed `page`, 1-indexed display. */
export function Pager({ page, totalPages, onChange }: { page: number; totalPages: number; onChange: (page: number) => void }) {
  if (totalPages <= 1) return null
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginTop: 14 }}>
      <button type="button" className="btn ghost sm" disabled={page <= 0} onClick={() => onChange(page - 1)}>Prev</button>
      <span className="muted" style={{ fontSize: 13 }}>Page {page + 1} of {totalPages}</span>
      <button type="button" className="btn ghost sm" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>Next</button>
    </div>
  )
}

/** A reference-site-style stat tile: colored icon badge top-right, caps label, big value, optional hint line. */
export function StatTile({ label, value, icon, color, hint }: {
  label: string; value: string; icon: string; color: 'blue' | 'green' | 'purple' | 'amber' | 'red'; hint?: string
}) {
  return (
    <div className="stat-tile">
      <div className="stat-tile-head">
        <div>
          <div className="k">{label}</div>
          <div className="v">{value}</div>
        </div>
        <div className={`stat-tile-icon ${color}`}>{icon}</div>
      </div>
      {hint && <div className="hint">{hint}</div>}
    </div>
  )
}

/** Breadcrumb + title + subtitle header used at the top of every admin page, matching the
 *  reference site's "ADMIN / SECTION" eyebrow + big heading pattern. */
export function AdminPageHeader({ section, title, subtitle, actions }: {
  section: string; title: string; subtitle?: string; actions?: ReactNode
}) {
  return (
    <div className="section-head" style={{ marginBottom: 18 }}>
      <div>
        <div className="admin-breadcrumb">Admin / {section}</div>
        <h1 className="page" style={{ marginTop: 0 }}>{title}</h1>
        {subtitle && <p className="muted">{subtitle}</p>}
      </div>
      {actions && <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>{actions}</div>}
    </div>
  )
}
