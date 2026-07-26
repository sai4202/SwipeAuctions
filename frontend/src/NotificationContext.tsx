import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { useNavigate } from 'react-router-dom'
import { useAuth } from './auth'
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
  const navigate = useNavigate()
  const [toasts, setToasts] = useState<Toast[]>([])
  const toastSeq = useRef(0)

  const dismiss = (toastId: number) => setToasts((t) => t.filter((x) => x.toastId !== toastId))

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
        })
      },
    })
    client.activate()
    return () => { void client.deactivate() }
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
