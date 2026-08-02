import { useEffect, useRef, useState, type FormEvent } from 'react'
import {
  getAdminChatConversations, getAdminChatMessages, sendAdminChatReply, errorMessage,
  type AdminChatConversation, type ChatMsg,
} from '../../api'
import { formatDateTimeShort } from '../../util'
import { AdminPageHeader } from './shared'

const POLL_MS = 4000

export default function AdminChatPage() {
  const [conversations, setConversations] = useState<AdminChatConversation[]>([])
  const [selected, setSelected] = useState<AdminChatConversation | null>(null)
  const [messages, setMessages] = useState<ChatMsg[]>([])
  const [draft, setDraft] = useState('')
  const [sending, setSending] = useState(false)
  const [error, setError] = useState('')
  const listRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const load = () => getAdminChatConversations().then(setConversations).catch((e) => setError(errorMessage(e)))
    load()
    const interval = setInterval(load, POLL_MS)
    return () => clearInterval(interval)
  }, [])

  useEffect(() => {
    if (!selected) return
    const load = () => getAdminChatMessages(selected.userId).then(setMessages).catch((e) => setError(errorMessage(e)))
    load()
    const interval = setInterval(load, POLL_MS)
    return () => clearInterval(interval)
  }, [selected])

  useEffect(() => {
    listRef.current?.scrollTo({ top: listRef.current.scrollHeight })
  }, [messages])

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (!selected || !draft.trim()) return
    setSending(true); setError('')
    try {
      const sent = await sendAdminChatReply(selected.userId, draft.trim())
      setMessages((prev) => [...prev, sent])
      setDraft('')
    } catch (e2) { setError(errorMessage(e2)) } finally { setSending(false) }
  }

  return (
    <div>
      <AdminPageHeader section="Chat" title="Live Support Chat" subtitle="Every open conversation with a signed-in user." />
      {error && <div className="error">{error}</div>}
      <div className="card" style={{ padding: 0, display: 'flex', height: 560, overflow: 'hidden' }}>
        <div style={{ width: 260, borderRight: '1px solid var(--border)', overflowY: 'auto', flexShrink: 0 }}>
          <div className="admin-sidebar-label" style={{ padding: '14px 14px 8px' }}>Open chats ({conversations.length})</div>
          {conversations.map((c) => (
            <button key={c.userId} type="button" onClick={() => setSelected(c)}
                    className={'admin-nav-link' + (selected?.userId === c.userId ? ' active' : '')}
                    style={{ width: '100%', textAlign: 'left', display: 'block', borderRadius: 0 }}>
              <div style={{ fontWeight: 700, fontSize: 13 }}>{c.userEmail}</div>
              <div className="muted" style={{ fontSize: 11.5, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                {c.lastSender === 'ADMIN' ? 'You: ' : ''}{c.lastMessage}
              </div>
            </button>
          ))}
          {conversations.length === 0 && <p className="muted" style={{ padding: 14, fontSize: 12.5 }}>No conversations yet.</p>}
        </div>
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', minWidth: 0 }}>
          {!selected ? (
            <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <p className="muted">Select a conversation to start replying.</p>
            </div>
          ) : (
            <>
              <div style={{ padding: '12px 16px', borderBottom: '1px solid var(--border)' }}>
                <b>{selected.userEmail}</b>
              </div>
              <div ref={listRef} style={{ flex: 1, overflowY: 'auto', padding: 16, display: 'flex', flexDirection: 'column', gap: 10 }}>
                {messages.map((m) => (
                  <div key={m.id} style={{ alignSelf: m.sender === 'ADMIN' ? 'flex-end' : 'flex-start', maxWidth: '70%' }}>
                    <div style={{
                      background: m.sender === 'ADMIN' ? 'var(--red)' : 'var(--panel-2)',
                      color: m.sender === 'ADMIN' ? '#fff' : 'var(--text)',
                      borderRadius: 10, padding: '8px 12px', fontSize: 13.5, whiteSpace: 'pre-wrap', wordBreak: 'break-word',
                    }}>
                      {m.body}
                    </div>
                    <div className="muted" style={{ fontSize: 10.5, marginTop: 2, textAlign: m.sender === 'ADMIN' ? 'right' : 'left' }}>
                      {formatDateTimeShort(m.createdAt)}
                    </div>
                  </div>
                ))}
              </div>
              <form onSubmit={submit} style={{ display: 'flex', gap: 8, padding: 12, borderTop: '1px solid var(--border)' }}>
                <input value={draft} onChange={(e) => setDraft(e.target.value)} placeholder="Type a reply…"
                       style={{ flex: 1 }} disabled={sending} />
                <button type="submit" className="btn sm" disabled={sending || !draft.trim()}>Send</button>
              </form>
            </>
          )}
        </div>
      </div>
    </div>
  )
}
