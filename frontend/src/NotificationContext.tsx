import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { useNavigate } from 'react-router-dom'
import { useAuth } from './auth'
import { useWallet } from './WalletContext'
import { API_BASE } from './api'

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
  const { isAuthenticated, token } = useAuth()
  const { refreshWallet } = useWallet()
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
    if (!isAuthenticated || !token) return

    const client = new Client({
      webSocketFactory: () => new SockJS(`${API_BASE}/ws`),
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 4000,
      onConnect: () => {
        client.subscribe('/user/queue/notifications', (frame) => {
          const n = JSON.parse(frame.body) as PushNotification
          const toastId = ++toastSeq.current
          setToasts((t) => [...t, { ...n, toastId }])
          setTimeout(() => dismiss(toastId), AUTO_DISMISS_MS)
          if (WALLET_AFFECTING[n.type]) refreshWallet()
        })
      },
    })
    client.activate()
    return () => { void client.deactivate() }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isAuthenticated, token])

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
