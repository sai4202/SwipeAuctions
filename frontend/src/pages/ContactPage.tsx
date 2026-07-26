import { useState, type FormEvent } from 'react'

const SUPPORT_EMAIL = 'support@swipeauctions.test'

export default function ContactPage() {
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [message, setMessage] = useState('')

  const submit = (e: FormEvent) => {
    e.preventDefault()
    const subject = encodeURIComponent(`Message from ${name || 'a SwipeAuctions visitor'}`)
    const body = encodeURIComponent(`${message}\n\n— ${name} (${email})`)
    window.location.href = `mailto:${SUPPORT_EMAIL}?subject=${subject}&body=${body}`
  }

  return (
    <div className="container">
      <section className="hero" style={{ paddingBottom: 10 }}>
        <div className="eyebrow">We're listening</div>
        <h1>Get in <span className="accent">touch</span></h1>
        <p className="sub">
          Question about a live auction, a wallet hold, or just want to say hello? Drop us a line —
          a real person reads every message.
        </p>
      </section>

      <div className="stat-tiles" style={{ maxWidth: 760, margin: '0 auto 30px' }}>
        <div className="stat-tile">
          <div className="k">Email</div>
          <div className="v" style={{ fontSize: 15 }}>{SUPPORT_EMAIL}</div>
        </div>
        <div className="stat-tile">
          <div className="k">Phone</div>
          <div className="v" style={{ fontSize: 15 }}>+91 99999 99915</div>
        </div>
        <div className="stat-tile">
          <div className="k">Hours</div>
          <div className="v" style={{ fontSize: 15 }}>Mon–Sat, 9am–7pm IST</div>
        </div>
      </div>

      <form className="card" style={{ maxWidth: 520, margin: '0 auto 60px' }} onSubmit={submit}>
        <h2 style={{ fontSize: 17, margin: '0 0 4px' }}>Send us a message</h2>
        <p className="muted" style={{ margin: '0 0 4px', fontSize: 13 }}>This opens your email app with the message pre-filled — nothing is sent from here directly.</p>
        <label>Your name</label>
        <input style={{ width: '100%' }} value={name} onChange={(e) => setName(e.target.value)} placeholder="Jane Doe" />
        <label>Your email</label>
        <input style={{ width: '100%' }} type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="you@example.com" />
        <label>Message</label>
        <textarea style={{ width: '100%', minHeight: 110, fontFamily: 'inherit' }} value={message}
                  onChange={(e) => setMessage(e.target.value)} placeholder="How can we help?" />
        <div style={{ marginTop: 18 }}>
          <button className="btn block" disabled={!message.trim()}>Send message</button>
        </div>
      </form>
    </div>
  )
}
