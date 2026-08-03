import { useEffect, useRef, useState, type FormEvent } from 'react'
import {
  getAdminChatConversations, getAdminChatMessages, sendAdminChatReply, sendAdminChatAttachment, errorMessage,
  type AdminChatConversation, type ChatMsg,
} from '../../api'
import { formatDateTimeShort } from '../../util'
import { AdminPageHeader } from './shared'

const POLL_MS = 4000
const MAX_ATTACHMENT_BYTES = 15 * 1024 * 1024

export default function AdminChatPage() {
  const [conversations, setConversations] = useState<AdminChatConversation[]>([])
  const [selected, setSelected] = useState<AdminChatConversation | null>(null)
  const [messages, setMessages] = useState<ChatMsg[]>([])
  const [draft, setDraft] = useState('')
  const [stagedFile, setStagedFile] = useState<File | null>(null)
  const [stagedPreview, setStagedPreview] = useState<string | null>(null)
  const [sending, setSending] = useState(false)
  const [error, setError] = useState('')
  const listRef = useRef<HTMLDivElement>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

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

  useEffect(() => () => { if (stagedPreview) URL.revokeObjectURL(stagedPreview) }, [stagedPreview])

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
    if (!selected || (!draft.trim() && !stagedFile)) return
    setSending(true); setError('')
    try {
      const sent = stagedFile
        ? await sendAdminChatAttachment(selected.userId, stagedFile, draft.trim() || undefined)
        : await sendAdminChatReply(selected.userId, draft.trim())
      setMessages((prev) => [...prev, sent])
      setDraft('')
      clearStaged()
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
                {c.lastSender === 'ADMIN' ? 'You: ' : ''}{c.lastMessage || '📎 Attachment'}
              </div>
            </button>
          ))}
          {conversations.length === 0 && <p className="muted" style={{ padding: 14, fontSize: 12.5 }}>No conversations yet.</p>}
        </div>
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', minWidth: 0 }}>
          {!selected ? (
            <div className="chat-empty" style={{ flex: 1 }}>
              <div className="chat-empty-icon">💬</div>
              <p>Select a conversation to start replying.</p>
            </div>
          ) : (
            <>
              <div className="chat-header" style={{ borderRadius: 0 }}>
                <div className="chat-header-avatar">{selected.userEmail[0]?.toUpperCase()}</div>
                <div className="chat-header-text"><b>{selected.userEmail}</b></div>
              </div>
              <div ref={listRef} className="chat-messages" style={{ padding: 16 }}>
                {messages.map((m) => (
                  <div key={m.id} className={`chat-row ${m.sender === 'ADMIN' ? 'user' : 'support'}`} style={{ maxWidth: '70%' }}>
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
              {stagedFile && (
                <div className="chat-staged" style={{ margin: '0 12px' }}>
                  {stagedPreview ? <img src={stagedPreview} alt="" /> : <span>🎬</span>}
                  <span className="chat-staged-name">{stagedFile.name}</span>
                  <button type="button" className="chat-staged-remove" onClick={clearStaged} aria-label="Remove attachment">✕</button>
                </div>
              )}
              <form onSubmit={submit} className="chat-input-row" style={{ padding: 12 }}>
                <input ref={fileInputRef} type="file" accept="image/*,video/*" style={{ display: 'none' }}
                       onChange={(e) => onFileChosen(e.target.files?.[0])} />
                <button type="button" className="chat-attach-btn" onClick={pickFile} disabled={sending} title="Attach a photo or video" aria-label="Attach a photo or video">+</button>
                <input type="text" value={draft} onChange={(e) => setDraft(e.target.value)}
                       placeholder={stagedFile ? 'Add a caption (optional)…' : 'Type a reply…'} disabled={sending} />
                <button type="submit" className="btn sm" disabled={sending || (!draft.trim() && !stagedFile)}>
                  {sending ? <span className="chat-spinner" /> : 'Send'}
                </button>
              </form>
            </>
          )}
        </div>
      </div>
    </div>
  )
}
