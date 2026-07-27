import { API_BASE } from './api'

/**
 * Uploaded listing media comes back from the API as a root-relative path (e.g. "/uploads/...")
 * since it's served by the same backend — resolve it against the backend's own origin so it still
 * loads once the frontend is deployed on a different domain (Vercel) than the API (Railway).
 * Already-absolute URLs (the loremflickr.com fallback in cardImage(), external links) pass through.
 */
export function resolveMediaUrl(url: string): string {
  if (/^https?:\/\//i.test(url)) return url
  return `${API_BASE}${url}`
}

/** Milliseconds until an ISO (local, no-zone) datetime string. */
export function msUntil(iso: string): number {
  return new Date(iso).getTime() - Date.now()
}

const TIER_LEVEL: Record<string, number> = { NONE: 0, SILVER: 1, GOLD: 2, DIAMOND: 3 }

/** True if a viewer's subscription tier grants access to content gated at `required`. */
export function tierMeets(viewerTier: string | null | undefined, required: string): boolean {
  return (TIER_LEVEL[viewerTier || 'NONE'] ?? 0) >= (TIER_LEVEL[required] ?? 0)
}

/** Human countdown like "2d 03:14:05" or "00:42" from a millisecond delta. */
export function formatCountdown(ms: number): string {
  if (ms <= 0) return 'ended'
  const s = Math.floor(ms / 1000)
  const days = Math.floor(s / 86400)
  const h = Math.floor((s % 86400) / 3600)
  const m = Math.floor((s % 3600) / 60)
  const sec = s % 60
  const pad = (n: number) => String(n).padStart(2, '0')
  if (days > 0) return `${days}d ${pad(h)}:${pad(m)}:${pad(sec)}`
  if (h > 0) return `${pad(h)}:${pad(m)}:${pad(sec)}`
  return `${pad(m)}:${pad(sec)}`
}

export function money(n: number | null | undefined): string {
  if (n == null) return '—'
  const num = Number(n)
  const formatted = Math.abs(num).toLocaleString('en-IN', { minimumFractionDigits: 0, maximumFractionDigits: 2 })
  return (num < 0 ? '-₹' : '₹') + formatted
}

const ONE_LAKH = 100_000
const ONE_CRORE = 10_000_000

/** Same as {@link money}, but collapses lakh/crore-scale amounts (e.g. a leveraged credit limit)
 *  into "₹1.25 Cr" / "₹4.50 L" instead of a wall of digits — full precision below ₹1 lakh. */
export function moneyCompact(n: number | null | undefined): string {
  if (n == null) return '—'
  const num = Number(n)
  const abs = Math.abs(num)
  const sign = num < 0 ? '-₹' : '₹'
  if (abs >= ONE_CRORE) {
    return sign + (abs / ONE_CRORE).toLocaleString('en-IN', { maximumFractionDigits: 2 }) + ' Cr'
  }
  if (abs >= ONE_LAKH) {
    return sign + (abs / ONE_LAKH).toLocaleString('en-IN', { maximumFractionDigits: 2 }) + ' L'
  }
  return money(n)
}

// ---- Gallery media ----
const VIDEO_EXTENSIONS = ['.mp4', '.webm', '.mov', '.m4v', '.ogv']

/** An item's "images" list can include videos too — same array, just listed together. */
export function isVideoUrl(url: string): boolean {
  const path = url.split(/[?#]/)[0].toLowerCase()
  return VIDEO_EXTENSIONS.some((ext) => path.endsWith(ext))
}

// ---- Card imagery ----
// Stable positive int from a string, so the same auction always gets the same fallback photo.
function hashCode(s: string): number {
  let h = 0
  for (let i = 0; i < s.length; i++) h = (Math.imul(31, h) + s.charCodeAt(i)) | 0
  return Math.abs(h)
}

/**
 * Image to show on an auction card: the seller's uploaded cover if present, otherwise a
 * relevant keyword photo (from category/brand) so cards are never just text. Deterministic.
 */
export function cardImage(a: {
  id: string; coverImageUrl?: string | null; categoryName?: string; brand?: string | null; title?: string
}): string {
  if (a.coverImageUrl) return resolveMediaUrl(a.coverImageUrl)
  const keywords = [a.categoryName, a.brand, a.title?.split(' ')[0]]
    .filter(Boolean)
    .join(',')
    .toLowerCase()
    .replace(/[^a-z0-9, ]/g, '')
    .replace(/\s+/g, '') || 'auction'
  return `https://loremflickr.com/600/400/${encodeURIComponent(keywords)}?lock=${hashCode(a.id)}`
}

/** Shared "19 Jul 2026, 07:34 pm" formatting for auction/event start & end times across list tables. */
export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString('en-IN', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' })
}

// ---- Individual auction status ----
// Status only flips OPEN -> CLOSED/UNSOLD when the backend scheduler ticks (every few seconds
// normally, but indefinitely if that particular auction's close keeps failing) — so a raw `status`
// read can lag behind the real end time. Compute the status the auction will have (or already
// should have) the same way AuctionService.closeAuction() does, so the UI never shows a "Live" card
// with an expired countdown, and browse/tab filtering treats it as closed immediately rather than
// waiting on the backend.
export function effectiveStatus(a: {
  status: string; currentEndTime: string; currentHighestBid: number | null; basePrice: number
}): string {
  if (a.status === 'OPEN' && msUntil(a.currentEndTime) <= 0) {
    const sold = a.currentHighestBid != null && a.currentHighestBid >= a.basePrice
    return sold ? 'CLOSED' : 'UNSOLD'
  }
  return a.status
}

// ---- Auction event status ----
// Auction events have no explicit status field — it's derived from start/closing time vs now,
// same live/upcoming/closed vocabulary as individual auctions.
export type EventStatus = 'LIVE' | 'UPCOMING' | 'CLOSED'

export function eventStatus(e: { startTime: string; closingTime: string }): EventStatus {
  const now = Date.now()
  if (now < new Date(e.startTime).getTime()) return 'UPCOMING'
  if (now > new Date(e.closingTime).getTime()) return 'CLOSED'
  return 'LIVE'
}

// ---- Valuation download ----
// Auto-generates and downloads a plain-text "validated details" summary for an item — there's no
// uploaded inspection-report document in this system, so the file is built from the listing's own
// data (title, category, specs, location, price) rather than linking to a stored document.
export function downloadValuation(a: {
  id: string; title: string; categoryName: string; brand: string | null; condition: string
  city: string | null; state: string | null; zip: string | null; basePrice: number
  currentHighestBid: number | null; status: string; attributes: Record<string, string>
}): void {
  const lines = [
    'SWIPEAUCTIONS — ITEM VALUATION SUMMARY',
    '='.repeat(42),
    `Item: ${a.title}`,
    `Category: ${a.categoryName}`,
    a.brand ? `Brand: ${a.brand}` : null,
    `Condition: ${a.condition.replace('_', ' ')}`,
    `Location: ${[a.city, a.state, a.zip].filter(Boolean).join(', ') || '—'}`,
    '',
    'Specifications:',
    ...Object.entries(a.attributes).map(([k, v]) => `  ${k}: ${v}`),
    '',
    `Base / starting price: ${money(a.basePrice)}`,
    `Current highest bid: ${a.currentHighestBid != null ? money(a.currentHighestBid) : '—'}`,
    `Auction status: ${a.status}`,
    '',
    `Generated: ${new Date().toLocaleString('en-IN')}`,
    'This is an auto-generated summary of the seller-provided listing details, not a certified inspection report.',
  ].filter((l): l is string => l != null)

  const blob = new Blob([lines.join('\n')], { type: 'text/plain' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `valuation-${a.title.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '')}.txt`
  link.click()
  URL.revokeObjectURL(url)
}

// ---- Multi-bidding watchlist (persisted in localStorage) ----
// Exported so callers can recognize this key in a `storage` event and re-sync — otherwise a change
// made in another tab/iframe (see FloatingTabsContext) never reaches an already-open page here.
export const MULTI_KEY = 'multiBids'

export function getMultiBidIds(): string[] {
  try { return JSON.parse(localStorage.getItem(MULTI_KEY) || '[]') } catch { return [] }
}
export function addMultiBid(id: string): string[] {
  const ids = getMultiBidIds()
  if (!ids.includes(id)) ids.push(id)
  localStorage.setItem(MULTI_KEY, JSON.stringify(ids))
  return ids
}
export function removeMultiBid(id: string): string[] {
  const ids = getMultiBidIds().filter((x) => x !== id)
  localStorage.setItem(MULTI_KEY, JSON.stringify(ids))
  return ids
}
