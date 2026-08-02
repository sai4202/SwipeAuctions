import { useEffect, useRef, useState, type FormEvent } from 'react'
import { getMyChatMessages, sendChatMessage, errorMessage, type ChatMsg } from '../api'
import { formatDateTimeShort } from '../util'

const POLL_MS = 4000

/** Floating support-chat widget for signed-in customers/dealers — polls the thread while open
 *  rather than a WebSocket push (see ChatController javadoc for why), which is simple, reliable,
 *  and plenty responsive at a few-second interval for a support conversation. */
export default function ChatWidget() {
  const [open, setOpen] = useState(false)
  const [messages, setMessages] = useState<ChatMsg[]>([])
  const [draft, setDraft] = useState('')
  const [sending, setSending] = useState(false)
  const [error, setError] = useState('')
  const listRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const load = () => getMyChatMessages().then(setMessages).catch((e) => setError(errorMessage(e)))
    load()
    const interval = setInterval(load, POLL_MS)
    return () => clearInterval(interval)
  }, [open])

  useEffect(() => {
    listRef.current?.scrollTo({ top: listRef.current.scrollHeight })
  }, [messages])

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (!draft.trim()) return
    setSending(true); setError('')
    try {
      const sent = await sendChatMessage(draft.trim())
      setMessages((prev) => [...prev, sent])
      setDraft('')
    } catch (e2) { setError(errorMessage(e2)) } finally { setSending(false) }
  }

  return (
    <div style={{ position: 'fixed', right: 20, bottom: 20, zIndex: 50 }}>
      {open && (
        <div className="card" style={{
          width: 320, height: 420, display: 'flex', flexDirection: 'column', padding: 0,
          marginBottom: 12, boxShadow: 'var(--shadow-lg)', overflow: 'hidden',
        }}>
          <div style={{ padding: '12px 14px', borderBottom: '1px solid var(--border)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <b style={{ fontSize: 13.5 }}>Live Support</b>
            <button type="button" className="modal-close" onClick={() => setOpen(false)} aria-label="Close chat">✕</button>
          </div>
          <div ref={listRef} style={{ flex: 1, overflowY: 'auto', padding: 12, display: 'flex', flexDirection: 'column', gap: 8 }}>
            {messages.length === 0 && <p className="muted" style={{ fontSize: 12.5 }}>Send a message and our team will get back to you.</p>}
            {messages.map((m) => (
              <div key={m.id} style={{ alignSelf: m.sender === 'USER' ? 'flex-end' : 'flex-start', maxWidth: '85%' }}>
                <div style={{
                  background: m.sender === 'USER' ? 'var(--red)' : 'var(--panel-2)',
                  color: m.sender === 'USER' ? '#fff' : 'var(--text)',
                  borderRadius: 10, padding: '7px 10px', fontSize: 13, whiteSpace: 'pre-wrap', wordBreak: 'break-word',
                }}>
                  {m.body}
                </div>
                <div className="muted" style={{ fontSize: 10, marginTop: 2, textAlign: m.sender === 'USER' ? 'right' : 'left' }}>
                  {formatDateTimeShort(m.createdAt)}
                </div>
              </div>
            ))}
          </div>
          {error && <div className="error" style={{ margin: '0 12px 8px' }}>{error}</div>}
          <form onSubmit={submit} style={{ display: 'flex', gap: 6, padding: 10, borderTop: '1px solid var(--border)' }}>
            <input value={draft} onChange={(e) => setDraft(e.target.value)} placeholder="Type a message…"
                   style={{ flex: 1, fontSize: 13 }} disabled={sending} />
            <button type="submit" className="btn sm" disabled={sending || !draft.trim()}>Send</button>
          </form>
        </div>
      )}
      <button type="button" className="btn" style={{ borderRadius: 999, width: 52, height: 52, padding: 0, fontSize: 20, float: 'right' }}
              onClick={() => setOpen((o) => !o)} aria-label="Toggle support chat">
        {open ? '✕' : '💬'}
      </button>
    </div>
  )
}
