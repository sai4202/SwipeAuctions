import { createContext, useContext, useEffect, useRef, type ReactNode } from 'react'
import { Client, type StompSubscription } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { useAuth } from './auth'
import { API_BASE } from './api'

interface Subscriber { callback: (body: string) => void; sub?: StompSubscription }

interface StompState {
  /** Subscribe to a STOMP destination on the one shared connection. Returns an unsubscribe
   *  function. Safe to call before the socket has connected — it's queued and applied on connect
   *  (and on every reconnect). */
  subscribe: (topic: string, callback: (body: string) => void) => () => void
}

const StompContext = createContext<StompState | undefined>(undefined)

/**
 * One shared SockJS/STOMP connection for the whole app, instead of each page/context opening its
 * own (NotificationContext's personal queue, AuctionDetailPage's and MultiBiddingPage's per-auction
 * topics used to each run a separate socket — up to 3 at once in one tab). Connects unconditionally
 * (not gated on auth) since public auction topics work for anonymous browsing too; reconnects
 * whenever the auth token changes so `/user/queue/...` subscriptions bind to the right Principal
 * after login/logout.
 */
export function StompProvider({ children }: { children: ReactNode }) {
  const { token } = useAuth()
  const clientRef = useRef<Client | null>(null)
  // topic -> subscribers currently registered, whether or not the socket is connected yet.
  const subsRef = useRef<Map<string, Subscriber[]>>(new Map())

  useEffect(() => {
    const client = new Client({
      webSocketFactory: () => new SockJS(`${API_BASE}/ws`),
      connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
      reconnectDelay: 4000,
      onConnect: () => {
        subsRef.current.forEach((subscribers, topic) => {
          subscribers.forEach((s) => {
            s.sub = client.subscribe(topic, (frame) => s.callback(frame.body))
          })
        })
      },
    })
    client.activate()
    clientRef.current = client
    return () => {
      void client.deactivate()
      clientRef.current = null
    }
  }, [token])

  const subscribe = (topic: string, callback: (body: string) => void) => {
    const subscriber: Subscriber = { callback }
    const list = subsRef.current.get(topic) ?? []
    list.push(subscriber)
    subsRef.current.set(topic, list)
    const client = clientRef.current
    if (client?.connected) {
      subscriber.sub = client.subscribe(topic, (frame) => callback(frame.body))
    }
    return () => {
      subscriber.sub?.unsubscribe()
      const list2 = subsRef.current.get(topic)
      if (!list2) return
      const idx = list2.indexOf(subscriber)
      if (idx >= 0) list2.splice(idx, 1)
      if (list2.length === 0) subsRef.current.delete(topic)
    }
  }

  return <StompContext.Provider value={{ subscribe }}>{children}</StompContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useStomp(): StompState {
  const ctx = useContext(StompContext)
  if (!ctx) throw new Error('useStomp must be used within StompProvider')
  return ctx
}
