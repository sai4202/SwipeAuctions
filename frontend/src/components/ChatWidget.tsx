import { useEffect, useRef, useState, type FormEvent } from 'react'
import { getMyChatMessages, sendChatMessage, sendChatAttachment, errorMessage, type ChatMsg } from '../api'
import { formatDateTimeShort } from '../util'

const POLL_MS = 4000
const MAX_ATTACHMENT_BYTES = 15 * 1024 * 1024

/** Floating support-chat widget for signed-in customers/dealers — polls the thread continuously
 *  (even while closed, so the FAB can show an unread badge) rather than a WebSocket push (see
 *  ChatController javadoc for why), which is simple, reliable, and plenty responsive at a
 *  few-second interval for a support conversation. */
export default function ChatWidget() {
  const [open, setOpen] = useState(false)
  const [messages, setMessages] = useState<ChatMsg[]>([])
  const [lastSeenAdminCount, setLastSeenAdminCount] = useState(0)
  const [draft, setDraft] = useState('')
  const [stagedFile, setStagedFile] = useState<File | null>(null)
  const [stagedPreview, setStagedPreview] = useState<string | null>(null)
  const [sending, setSending] = useState(false)
  const [error, setError] = useState('')
  const listRef = useRef<HTMLDivElement>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    const load = () => getMyChatMessages().then(setMessages).catch((e) => setError(errorMessage(e)))
    load()
    const interval = setInterval(load, POLL_MS)
    return () => clearInterval(interval)
  }, [])

  useEffect(() => {
    if (open) setLastSeenAdminCount(messages.filter((m) => m.sender === 'ADMIN').length)
  }, [open, messages])

  useEffect(() => {
    if (open) listRef.current?.scrollTo({ top: listRef.current.scrollHeight })
  }, [messages, open])

  useEffect(() => () => { if (stagedPreview) URL.revokeObjectURL(stagedPreview) }, [stagedPreview])

  const unread = open ? 0 : Math.max(0, messages.filter((m) => m.sender === 'ADMIN').length - lastSeenAdminCount)

  const pickFile = () => fileInputRef.current?.click()

  const onFileChosen = (file: File | undefined) => {
    if (!file) return
    setError('')
    if (!file.type.startsWith('image/') && !file.type.startsWith('video/')) {
      setError('Only images and videos can be attached.')
      return
    }
    if (file.size > MAX_ATTACHMENT_BYTES) {
      setError('That file is too large — attachments are limited to 15MB.')
      return
    }
    if (stagedPreview) URL.revokeObjectURL(stagedPreview)
    setStagedFile(file)
    setStagedPreview(file.type.startsWith('image/') ? URL.createObjectURL(file) : null)
  }

  const clearStaged = () => {
    if (stagedPreview) URL.revokeObjectURL(stagedPreview)
    setStagedFile(null); setStagedPreview(null)
    if (fileInputRef.current) fileInputRef.current.value = ''
  }

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (!stagedFile && !draft.trim()) return
    setSending(true); setError('')
    try {
      const sent = stagedFile ? await sendChatAttachment(stagedFile, draft.trim() || undefined) : await sendChatMessage(draft.trim())
      setMessages((prev) => [...prev, sent])
      setDraft('')
      clearStaged()
    } catch (e2) { setError(errorMessage(e2)) } finally { setSending(false) }
  }

  return (
    <div style={{ position: 'fixed', right: 20, bottom: 20, zIndex: 50 }}>
      {open && (
        <div className="card chat-panel">
          <div className="chat-header">
            <div className="chat-header-avatar">🎧</div>
            <div className="chat-header-text">
              <b>Live Support</b>
              <div className="chat-header-sub"><span className="chat-online-dot" />We usually reply in a few minutes</div>
            </div>
            <button type="button" className="modal-close" onClick={() => setOpen(false)} aria-label="Close chat">✕</button>
          </div>
          <div ref={listRef} className="chat-messages">
            {messages.length === 0 && (
              <div className="chat-empty">
                <div className="chat-empty-icon">💬</div>
                <p>Send a message (or attach a photo/video) and our team will get back to you.</p>
              </div>
            )}
            {messages.map((m) => (
              <div key={m.id} className={`chat-row ${m.sender === 'USER' ? 'user' : 'support'}`}>
                <div className={`chat-bubble ${m.attachmentUrl && !m.body ? 'attachment-only' : ''}`}>
                  {m.attachmentUrl && m.attachmentType === 'IMAGE' && (
                    <a href={m.attachmentUrl} target="_blank" rel="noopener noreferrer">
                      <img src={m.attachmentUrl} alt={m.attachmentName ?? 'attachment'} className="chat-attachment-img" />
                    </a>
                  )}
                  {m.attachmentUrl && m.attachmentType === 'VIDEO' && (
                    <video src={m.attachmentUrl} controls className="chat-attachment-video" />
                  )}
                  {m.body && <div className={m.attachmentUrl ? 'chat-attachment-caption' : ''}>{m.body}</div>}
                </div>
                <div className="chat-time">{formatDateTimeShort(m.createdAt)}</div>
              </div>
            ))}
          </div>
          {error && <div className="error chat-error">{error}</div>}
          {stagedFile && (
            <div className="chat-staged">
              {stagedPreview ? <img src={stagedPreview} alt="" /> : <span>🎬</span>}
              <span className="chat-staged-name">{stagedFile.name}</span>
              <button type="button" className="chat-staged-remove" onClick={clearStaged} aria-label="Remove attachment">✕</button>
            </div>
          )}
          <form onSubmit={submit} className="chat-input-row">
            <input ref={fileInputRef} type="file" accept="image/*,video/*" style={{ display: 'none' }}
                   onChange={(e) => onFileChosen(e.target.files?.[0])} />
            <button type="button" className="chat-attach-btn" onClick={pickFile} disabled={sending} title="Attach a photo or video" aria-label="Attach a photo or video">+</button>
            <input type="text" value={draft} onChange={(e) => setDraft(e.target.value)}
                   placeholder={stagedFile ? 'Add a caption (optional)…' : 'Type a message…'} disabled={sending} />
            <button type="submit" className="btn sm" disabled={sending || (!draft.trim() && !stagedFile)}>
              {sending ? <span className="chat-spinner" /> : 'Send'}
            </button>
          </form>
        </div>
      )}
      <button type="button" className="chat-fab" onClick={() => setOpen((o) => !o)} aria-label="Toggle support chat">
        <span className="chat-fab-icon">{open ? '✕' : '💬'}</span>
        {unread > 0 && <span className="chat-fab-badge">{unread > 9 ? '9+' : unread}</span>}
      </button>
    </div>
  )
}
