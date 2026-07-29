import { useEffect, useRef, useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from './auth'
import { useWallet } from './WalletContext'
import { useStomp } from './StompContext'
import { type Auction } from './api'
import { updateCache } from './useCachedFetch'

type NotificationKind = 'BID_PLACED' | 'OUTBID' | 'AUCTION_WON' | 'AUCTION_LOST' | 'WALLET_TOPUP'

interface PushNotification {
  id: string
  type: NotificationKind
  title: string
  message: string
  auctionId: string | null
  timestamp: string
}

interface Toast extends PushNotification {
  toastId: number
}

const KIND_META: Record<NotificationKind, { icon: string; className: string }> = {
  BID_PLACED: { icon: '✓', className: 'neutral' },
  OUTBID: { icon: '⚠', className: 'warn' },
  AUCTION_WON: { icon: '🏆', className: 'success' },
  AUCTION_LOST: { icon: 'ℹ', className: 'muted' },
  WALLET_TOPUP: { icon: '₹', className: 'success' },
}

const AUTO_DISMISS_MS = 6000

export function NotificationProvider({ children }: { children: ReactNode }) {
  const { isAuthenticated } = useAuth()
  const { refreshWallet } = useWallet()
  const { subscribe } = useStomp()
  const navigate = useNavigate()
  const [toasts, setToasts] = useState<Toast[]>([])
  const toastSeq = useRef(0)

  const dismiss = (toastId: number) => setToasts((t) => t.filter((x) => x.toastId !== toastId))

  // These three kinds always mean the wallet just moved server-side (EMD released/captured on
  // close, top-up landed) — none of that came from an action this tab took itself, so nothing
  // else would otherwise trigger a re-fetch and the header's "Available Credit" would sit stale
  // until the user happened to navigate somewhere that remounts it.
  const WALLET_AFFECTING: Partial<Record<NotificationKind, true>> = {
    AUCTION_WON: true, AUCTION_LOST: true, WALLET_TOPUP: true,
  }

  useEffect(() => {
    if (!isAuthenticated) return

    // Rides the one shared connection (StompContext) instead of opening its own socket — this
    // subscription itself still only exists while signed in, and gets torn down/re-added whenever
    // the underlying connection reconnects with a new Principal (login/logout), same as before.
    const unsubscribe = subscribe('/user/queue/notifications', (body) => {
      const n = JSON.parse(body) as PushNotification
      const toastId = ++toastSeq.current
      setToasts((t) => [...t, { ...n, toastId }])
      setTimeout(() => dismiss(toastId), AUTO_DISMISS_MS)
      if (WALLET_AFFECTING[n.type]) refreshWallet()
      // Being outbid doesn't change the wallet, but it does flip this auction's "Leading" badge
      // to "Outbid" everywhere it's on screen right now — catalogue grid, the events browse view
      // (same 'auctions' cache key), and this auction's own detail page if open — instead of
      // leaving it stale until the user navigates away and back (the 'auctions' list otherwise
      // only refetches on remount / 20s cache expiry).
      if (n.type === 'OUTBID' && n.auctionId) {
        const auctionId = n.auctionId
        updateCache<Auction[]>('auctions', (list) =>
          list.map((a) => (a.id === auctionId ? { ...a, isWinner: false } : a)))
        updateCache<Auction>(`auction:${auctionId}`, (a) => ({ ...a, isWinner: false }))
      }
    })
    return unsubscribe
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isAuthenticated])

  return (
    <>
      {children}
      <div className="toast-stack">
        {toasts.map((t) => {
          const meta = KIND_META[t.type] ?? KIND_META.BID_PLACED
          return (
            <div key={t.toastId} className={`toast toast-${meta.className}`}>
              <span className="toast-icon">{meta.icon}</span>
              <div className="toast-body" onClick={() => { if (t.auctionId) navigate(`/auctions/${t.auctionId}`); dismiss(t.toastId) }}>
                <div className="toast-title">{t.title}</div>
                <div className="toast-message">{t.message}</div>
              </div>
              <button type="button" className="toast-close" onClick={() => dismiss(t.toastId)} aria-label="Dismiss">✕</button>
            </div>
          )
        })}
      </div>
    </>
  )
}
