import { Link } from 'react-router-dom'

const PILLARS = [
  { icon: '⚡', title: 'Live & Transparent', text: 'Every bid lands in real time over a live feed — no sealed envelopes, no back-room deals. What you see is the auction, happening.' },
  { icon: '🔐', title: 'Wallet-Backed Bidding', text: 'Deposit once and your wallet unlocks real bidding credit — no per-item deposits, no waiting between auctions. Just bid within your limit.' },
  { icon: '🗂️', title: 'Any Category, One Marketplace', text: 'Vehicles, properties, bank-seized assets, insurance salvage, electronics — one platform, one login, one wallet.' },
  { icon: '🛡️', title: 'Built for Trust', text: 'SARFAESI-aware, RBI-mindful, bank-grade security by default. Auctions people can actually rely on.' },
]

const STEPS = [
  { n: '01', title: 'Swipe through listings', text: 'Browse by category, filter by what actually matters — mileage, floor area, RAM, whatever the item calls for.' },
  { n: '02', title: 'Verify KYC & unlock credit', text: 'Complete KYC once, then every ₹5,000 you deposit unlocks ₹2.5 crore of bidding credit on your wallet — no per-auction deposit required.' },
  { n: '03', title: 'Bid, win, done', text: 'Anti-snipe protection means the last five seconds can’t rob you of a fair auction. Highest honest bid wins.' },
]

export default function AboutPage() {
  return (
    <div className="container">
      <section className="hero" style={{ paddingBottom: 10 }}>
        <div className="eyebrow">Who we are</div>
        <h1>Auctions, <span className="accent">reimagined</span></h1>
        <p className="sub">
          SwipeAuctions started from a simple frustration: real auctions — for real cars, real homes,
          real machinery — still felt like a closed room with a gavel and a guest list. We rebuilt the
          gavel as a swipe. Same stakes, same trust, none of the gatekeeping.
        </p>
      </section>

      <div className="card" style={{ maxWidth: 760, margin: '10px auto 34px' }}>
        <p style={{ lineHeight: 1.7, margin: 0 }}>
          Today SwipeAuctions is a live, real-time marketplace where anyone — a bank offloading a
          repossessed vehicle, a family selling an apartment, a dealer moving inventory — can put an
          asset in front of serious, wallet-verified bidders and watch the price find itself, honestly,
          in public. No hidden reserve games, no phantom bidders. Just a countdown, a crowd, and the
          highest real offer.
        </p>
      </div>

      <div className="section-head" style={{ maxWidth: 900, margin: '0 auto' }}>
        <h2 style={{ fontSize: 20, margin: 0 }}>What makes it different</h2>
      </div>
      <div className="stat-tiles" style={{ maxWidth: 900, margin: '14px auto 40px' }}>
        {PILLARS.map((p) => (
          <div className="stat-tile" key={p.title}>
            <div style={{ fontSize: 22 }}>{p.icon}</div>
            <div className="v" style={{ fontSize: 15, marginTop: 8 }}>{p.title}</div>
            <p className="muted" style={{ fontSize: 13, lineHeight: 1.55, marginTop: 6 }}>{p.text}</p>
          </div>
        ))}
      </div>

      <div className="section-head" style={{ maxWidth: 900, margin: '0 auto' }}>
        <h2 style={{ fontSize: 20, margin: 0 }}>How a swipe becomes a sale</h2>
      </div>
      <div className="stat-tiles" style={{ maxWidth: 900, margin: '14px auto 40px' }}>
        {STEPS.map((s) => (
          <div className="stat-tile" key={s.n}>
            <div className="k" style={{ color: 'var(--orange)', fontWeight: 800 }}>{s.n}</div>
            <div className="v" style={{ fontSize: 15, marginTop: 6 }}>{s.title}</div>
            <p className="muted" style={{ fontSize: 13, lineHeight: 1.55, marginTop: 6 }}>{s.text}</p>
          </div>
        ))}
      </div>

      <div className="card" style={{ maxWidth: 760, margin: '0 auto 60px', textAlign: 'center' }}>
        <h2 style={{ fontSize: 18, margin: '0 0 8px' }}>Ready to see what's live right now?</h2>
        <p className="muted" style={{ margin: '0 0 18px' }}>Every listing on SwipeAuctions is real, right now, with a real deadline.</p>
        <Link to="/auctions" className="btn">Browse live auctions →</Link>
      </div>
    </div>
  )
}
