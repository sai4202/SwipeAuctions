import { useEffect, useRef, useState, type ReactNode } from 'react'
import { WhatsAppIcon, TelegramIcon, EmailIcon, InstagramIcon, CopyLinkIcon, CheckIcon, ShareIcon } from './ShareIcons'

interface ShareOption {
  key: string
  label: string
  icon: ReactNode
  href?: string
  onClick?: () => void
}

interface ShareMenuProps {
  /** Relative or absolute URL to share — resolved against window.location.origin if relative. */
  url: string
  /** The message shown alongside the link (WhatsApp/Telegram/Email body, Instagram clipboard text). */
  text: string
  /** Subject line for the Email option only. */
  emailSubject?: string
  /**
   * "inline" renders the option row directly, always visible (used on the Profile referral card,
   * per the user's request that the apps be shown up front, not hidden behind a copy-then-paste
   * step). "popover" renders a single share-icon trigger that reveals the same row on click — used
   * on listing cards everywhere (browse grid, list rows, item detail) where space is tight.
   */
  variant?: 'inline' | 'popover'
  /** Overrides the trigger button's class — use "btn ghost sm" for a plain (non-overlay) spot like
   *  a list row, instead of the dark circular badge meant to sit on top of a photo. */
  triggerClassName?: string
}

/**
 * WhatsApp/Telegram/Email all support a real prefilled-message share link, so those go straight to
 * the target app/site with the text+link already in place. Instagram has no such web intent — there
 * is no URL that opens Instagram with a caption/DM pre-typed — so that option instead copies the
 * message+link to the clipboard and opens instagram.com, with a label that says exactly that rather
 * than implying it does something it can't. Where the device supports the native OS share sheet
 * (mostly mobile), that's offered first since it can hand off to literally any installed app,
 * Instagram included, with the content actually pre-filled.
 */
export default function ShareMenu({ url, text, emailSubject, variant = 'popover', triggerClassName }: ShareMenuProps) {
  const [open, setOpen] = useState(variant === 'inline')
  const [copied, setCopied] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  const absoluteUrl = url.startsWith('http') ? url : `${window.location.origin}${url}`
  // The link on its own line (not run into the sentence) is what makes WhatsApp/Telegram/SMS/email
  // clients reliably auto-detect and linkify it — several will still linkify a trailing inline URL,
  // but not all, and a standalone line never fails to.
  const message = `${text}\n\n${absoluteUrl}`

  useEffect(() => {
    if (variant === 'inline') return
    const onDocClick = (e: MouseEvent) => { if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false) }
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setOpen(false) }
    document.addEventListener('mousedown', onDocClick)
    document.addEventListener('keydown', onKey)
    return () => { document.removeEventListener('mousedown', onDocClick); document.removeEventListener('keydown', onKey) }
  }, [variant])

  const flashCopied = () => { setCopied(true); setTimeout(() => setCopied(false), 2000) }

  /** wa.me/t.me/mailto: only ever carry plain text — no share URL scheme lets a browser inject a
   *  real HTML anchor into WhatsApp/Telegram/Email, so a shared link there only turns into a
   *  clickable, underlined link once that app's own linkifier renders the actually-sent message
   *  (true of every "share" button on the web, not specific to this one). Copy Link is the one
   *  place we control the destination format directly, so it writes a real `text/html` clipboard
   *  entry with an `<a href>` alongside the plain-text fallback — paste into Gmail, Docs, Slack,
   *  Notion, etc. and it lands as an actual clickable hyperlink, not just visible text. */
  const copyLink = () => {
    const html = `<a href="${absoluteUrl}">${absoluteUrl}</a>`
    if (typeof ClipboardItem !== 'undefined') {
      const item = new ClipboardItem({
        'text/plain': new Blob([absoluteUrl], { type: 'text/plain' }),
        'text/html': new Blob([html], { type: 'text/html' }),
      })
      navigator.clipboard.write([item]).then(flashCopied).catch(() => navigator.clipboard.writeText(absoluteUrl).then(flashCopied))
    } else {
      navigator.clipboard.writeText(absoluteUrl).then(flashCopied)
    }
  }

  const shareViaDevice = () => {
    navigator.share?.({ title: emailSubject ?? 'SwipeAuctions', text, url: absoluteUrl }).catch(() => {})
  }

  const shareViaInstagram = () => {
    navigator.clipboard.writeText(message).then(() => {
      flashCopied()
      window.open('https://www.instagram.com/', '_blank', 'noopener,noreferrer')
    })
  }

  const options: ShareOption[] = [
    { key: 'whatsapp', label: 'WhatsApp', icon: <WhatsAppIcon />, href: `https://wa.me/?text=${encodeURIComponent(message)}` },
    { key: 'telegram', label: 'Telegram', icon: <TelegramIcon />, href: `https://t.me/share/url?url=${encodeURIComponent(absoluteUrl)}&text=${encodeURIComponent(text)}` },
    { key: 'email', label: 'Email', icon: <EmailIcon />, href: `mailto:?subject=${encodeURIComponent(emailSubject ?? 'Check this out on SwipeAuctions')}&body=${encodeURIComponent(message)}` },
    { key: 'instagram', label: 'Instagram (copies link)', icon: <InstagramIcon />, onClick: shareViaInstagram },
    { key: 'copy', label: copied ? 'Copied!' : 'Copy link', icon: copied ? <CheckIcon /> : <CopyLinkIcon />, onClick: copyLink },
  ]

  const optionRow = (
    <div className={variant === 'popover' ? 'share-menu-popover' : 'share-menu-inline'}>
      {typeof navigator.share === 'function' && (
        <button type="button" className="share-menu-option" onClick={shareViaDevice}>
          <span className="share-menu-icon"><ShareIcon /></span> Share via device
        </button>
      )}
      {options.map((o) => o.href ? (
        <a key={o.key} className="share-menu-option" href={o.href} target="_blank" rel="noopener noreferrer">
          <span className="share-menu-icon">{o.icon}</span> {o.label}
        </a>
      ) : (
        <button key={o.key} type="button" className="share-menu-option" onClick={o.onClick}>
          <span className="share-menu-icon">{o.icon}</span> {o.label}
        </button>
      ))}
    </div>
  )

  if (variant === 'inline') {
    return <div className="share-menu-wrap">{optionRow}</div>
  }

  return (
    <div className="share-menu-wrap" ref={ref}>
      <button
        type="button"
        className={triggerClassName ?? 'share-menu-trigger'}
        onClick={(e) => { e.preventDefault(); e.stopPropagation(); setOpen((o) => !o) }}
        title="Share"
        aria-label="Share"
        aria-expanded={open}
      >
        <ShareIcon />
      </button>
      {open && (
        <div style={{ position: 'absolute', top: '110%', right: 0, zIndex: 30 }} onClick={(e) => e.stopPropagation()}>
          {optionRow}
        </div>
      )}
    </div>
  )
}
