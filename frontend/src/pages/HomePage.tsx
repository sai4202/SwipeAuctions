import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { getCategories, getEvents, type Category, type AuctionEvent, type SubscriptionTier } from '../api'
import { useAuth } from '../auth'
import { formatCountdown, msUntil, eventStatus } from '../util'
import TierCards from '../components/TierCards'
import HeroVehicle from '../components/HeroVehicle'
import CarSilhouette from '../components/CarSilhouette'
import { EventTileGridSkeleton } from '../components/Skeleton'
import { useReveal } from '../useReveal'

const EVENT_STATUS_RANK: Record<string, number> = { LIVE: 0, UPCOMING: 1, CLOSED: 2 }
const FEATURED_EVENT_COUNT = 5

// Picking straight off the front of `sorted` tends to surface 5 events from the same category
// (Bank Vehicles seeds first and has the most Live-zone events) — round-robin across categorySlug
// instead so the featured row reads as "a bit of everything" rather than one category's back-catalog.
function pickDiverseEvents(sorted: AuctionEvent[], count: number): AuctionEvent[] {
  const byCategory = new Map<string, AuctionEvent[]>()
  for (const e of sorted) {
    const list = byCategory.get(e.categorySlug)
    if (list) list.push(e); else byCategory.set(e.categorySlug, [e])
  }
  const categorySlugs = [...byCategory.keys()]
  const picked: AuctionEvent[] = []
  for (let round = 0; picked.length < count && round < sorted.length; round++) {
    let addedThisRound = false
    for (const slug of categorySlugs) {
      const event = byCategory.get(slug)![round]
      if (!event) continue
      picked.push(event)
      addedThisRound = true
      if (picked.length === count) break
    }
    if (!addedThisRound) break
  }
  return picked
}

const CATEGORY_ICONS: Record<string, string> = {
  vehicles: '🚗', properties: '🏠', 'bank-vehicles': '🏛️', insurance: '🛡️',
  electronics: '📱', premium: '💎', 'swipe-stock': '🏷️', jewellery: '💍', furniture: '🛋️',
}

const FALLBACK_CATEGORIES = [
  { slug: 'vehicles', name: 'Vehicles' },
  { slug: 'bank-vehicles', name: 'Bank Vehicles' },
  { slug: 'insurance', name: 'Insurance Salvage' },
  { slug: 'properties', name: 'Properties' },
  { slug: 'electronics', name: 'Electronics' },
]

const STEPS = [
  { n: '1', title: 'Register & verify KYC', body: 'Create an account, pay a one-time registration fee, and complete a quick, RBI-regulated identity check.' },
  { n: '2', title: 'Deposit & unlock credit', body: 'Every ₹5,000 you deposit unlocks ₹2.5 crore of bidding credit on your wallet.' },
  { n: '3', title: 'Bid live', body: 'Place bids in real time as the clock counts down — no per-item deposits once you’re registered.' },
  { n: '4', title: 'Win & settle', body: 'Winners settle instantly from their wallet; everyone else keeps their full credit limit intact.' },
]

export default function HomePage() {
  const [q, setQ] = useState('')
  const navigate = useNavigate()
  const { isAuthenticated, subscriptionTier } = useAuth()

  const [categories, setCategories] = useState<Category[]>([])
  const [events, setEvents] = useState<AuctionEvent[]>([])
  // Without this, the whole "Featured auction events" section stays entirely absent — not even a
  // placeholder — for however long GET /api/events takes, then pops in and shifts the layout. On a
  // slow connection or a cold backend that gap is long enough that a user glancing at the page reads
  // it as "the section is missing" rather than "still loading".
  const [eventsLoading, setEventsLoading] = useState(true)

  useEffect(() => {
    if (!isAuthenticated) return
    getCategories().then((cats) => setCategories(cats.filter((c) => !c.parentId))).catch(() => {})
    // Featured events on the homepage should only ever tease something a visitor can still act on —
    // never a closed one. Live events surface first, then upcoming ones fill any remaining slots.
    getEvents().then((evs) => {
      const active = evs
        .filter((e) => eventStatus(e) !== 'CLOSED')
        .sort((a, b) => EVENT_STATUS_RANK[eventStatus(a)] - EVENT_STATUS_RANK[eventStatus(b)] || msUntil(a.closingTime) - msUntil(b.closingTime))
      setEvents(pickDiverseEvents(active, FEATURED_EVENT_COUNT))
    }).catch(() => {}).finally(() => setEventsLoading(false))
  }, [isAuthenticated])

  useReveal([categories, events])

  const search = () => navigate(`/auctions${q ? `?q=${encodeURIComponent(q)}` : ''}`)
  const chip = (slug: string, label: string, icon: string) => (
    <button className="chip" onClick={() => navigate(`/auctions?category=${slug}`)}>{icon}&nbsp; {label}</button>
  )
  const pageChip = (path: string, label: string, icon: string) => (
    <button className="chip" onClick={() => navigate(path)}>{icon}&nbsp; {label}</button>
  )

  const catTiles = categories.length > 0
    ? categories.map((c) => ({ slug: c.slug, name: c.name }))
    : FALLBACK_CATEGORIES

  return (
    <>
      <div className="container">
        <section className="hero" id="hero">
          <HeroVehicle side="left" />
          <HeroVehicle side="right" />
          <div className="eyebrow">India's Premium Auction Marketplace</div>
          <h1>Premium Assets <span className="accent">for</span> Superstars</h1>
          <p className="sub">
            Transparent, compliant and real-time auctions for vehicles, properties, bank vehicles
            and insurance salvage across India.
          </p>

          <div className="searchbar">
            <span className="mag">🔍</span>
            <input
              value={q}
              onChange={(e) => setQ(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && search()}
              placeholder="Search Vehicles, Properties, Bank Vehicles…"
            />
            <button className="btn light search-btn" onClick={search}>Search →</button>
          </div>

          <div className="chips">
            {chip('bank-vehicles', 'Bank Vehicles', '🏛️')}
            {chip('insurance', 'Insurance', '🛡️')}
            {pageChip('/swipe-stock', 'Swipe Stock', '🏷️')}
          </div>
        </section>

        <div className="trustbar">
          <div className="trust-item">
            <span className="trust-icon">🔒</span>
            <span className="trust-label">SARFAESI Compliant</span>
          </div>
          <div className="trust-item">
            <span className="trust-icon">🛡️</span>
            <span className="trust-label">Bank-Grade Security</span>
          </div>
          <div className="trust-item">
            <span className="trust-icon">⚡</span>
            <span className="trust-label">Instant Credit Unlock</span>
          </div>
          <div className="trust-item">
            <span className="trust-icon">✅</span>
            <span className="trust-label">RBI Regulated</span>
          </div>
          <div className="trust-item">
            <span className="trust-icon">🎖️</span>
            <span className="trust-label">ISO 27001 Certified</span>
          </div>
        </div>
      </div>

      <nav className="home-subnav">
        <div className="home-subnav-inner">
          <a href="#categories">Categories</a>
          {isAuthenticated && events.length > 0 && <a href="#events">Auction Events</a>}
          <a href="#membership">Membership</a>
          <a href="#how-it-works">How it works</a>
          <a href="#contact">Contact</a>
        </div>
      </nav>

      <section className="home-band band-categories" id="categories">
        <div className="container">
          <div className="home-section">
            <div className="section-head">
              <h2>Browse by category</h2>
            </div>
            <div className="cat-tile-grid">
              {catTiles.map((c) => (
                <button key={c.slug} className="cat-tile" data-reveal onClick={() => navigate(`/auctions?category=${c.slug}`)}>
                  <span className="cat-tile-icon">{CATEGORY_ICONS[c.slug] ?? '📦'}</span>
                  <span className="cat-tile-label">{c.name}</span>
                </button>
              ))}
            </div>
          </div>
        </div>
      </section>

      {isAuthenticated && (eventsLoading || events.length > 0) && (
        <section className="home-band band-events" id="events">
          <div className="road-strip" aria-hidden="true">
            <CarSilhouette className="road-car road-car-1" />
            <CarSilhouette className="road-car road-car-2" />
          </div>
          <div className="container">
            <div className="home-section">
              <div className="section-head">
                <h2>Featured auction events</h2>
              </div>
              {eventsLoading ? <EventTileGridSkeleton count={FEATURED_EVENT_COUNT} /> : (
              <div className="event-tile-grid">
                {events.map((e) => (
                  <Link key={e.id} to={`/auctions?category=${e.categorySlug}&event=${e.id}`} className="event-tile" data-reveal>
                    <span className="event-tile-name">{e.name}</span>
                    <span className="event-tile-meta">
                      {e.itemCount} item{e.itemCount === 1 ? '' : 's'}{e.location ? ` · ${e.location}` : ''}
                    </span>
                    <span className="event-tile-countdown">
                      {msUntil(e.closingTime) <= 0 ? 'Closed' : `Closes in ${formatCountdown(msUntil(e.closingTime))}`}
                    </span>
                    <span className="event-tile-cta">Explore event →</span>
                  </Link>
                ))}
              </div>
              )}
            </div>
          </div>
        </section>
      )}

      <section className="home-band band-membership" id="membership">
        <div className="ghost-car ghost-car-1" aria-hidden="true"><CarSilhouette /></div>
        <div className="ghost-car ghost-car-2" aria-hidden="true"><CarSilhouette /></div>
        <div className="container">
          <div className="home-section">
            <div className="section-head">
              <h2>Membership plans</h2>
            </div>
            <p className="muted" style={{ margin: '-10px 0 20px' }}>
              Every plan bids the same way — higher tiers just unlock more listings. Each tier includes everything the one before it does.
            </p>
            <TierCards
              currentTier={isAuthenticated ? (subscriptionTier as SubscriptionTier) : undefined}
              renderCta={() => (
                <Link to="/subscription" className="btn ghost sm block">View plans</Link>
              )}
            />
          </div>
        </div>
      </section>

      <section className="home-band band-how" id="how-it-works">
        <div className="lane-line" aria-hidden="true" />
        <div className="container">
          <div className="home-section">
            <div className="section-head">
              <h2>How SwipeAuctions works</h2>
            </div>
            <div className="how-track" aria-hidden="true">
              <div className="how-track-line" />
              <div className="how-track-fill" />
              <div className="how-track-car"><CarSilhouette /></div>
              {STEPS.map((s, i) => (
                <div
                  className="how-track-point"
                  style={{ left: `${(i / (STEPS.length - 1)) * 100}%`, '--delay': `${(i / (STEPS.length - 1)) * 6}s` } as React.CSSProperties}
                  key={s.n}
                >
                  {s.n}
                </div>
              ))}
            </div>
            <div className="step-grid">
              {STEPS.map((s, i) => (
                <div
                  className="step-tile"
                  data-reveal
                  style={{ '--delay': `${(i / (STEPS.length - 1)) * 6}s` } as React.CSSProperties}
                  key={s.n}
                >
                  <span className="step-num">{s.n}</span>
                  <h3>{s.title}</h3>
                  <p>{s.body}</p>
                </div>
              ))}
            </div>
            <div className="how-cta">
              <Link to={isAuthenticated ? '/auctions' : '/register'} className="btn cta-pill">
                Register to Start Bidding <span className="arrow">›</span>
              </Link>
            </div>
          </div>
        </div>
      </section>

      <section className="home-band band-contact" id="contact">
        <div className="container">
          <div className="home-section">
            <div className="cta-banner" data-reveal>
              <div>
                <h2>We're here to help</h2>
                <p>Question about a live auction, a wallet hold, or your subscription? Reach out any time.</p>
              </div>
              <div className="cta-banner-actions">
                <Link to="/contact" className="btn light">Contact Us</Link>
                <Link to="/about" className="btn ghost">About Us</Link>
              </div>
            </div>
          </div>
        </div>
      </section>
    </>
  )
}
