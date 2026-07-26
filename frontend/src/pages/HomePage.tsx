import { useState } from 'react'
import { useNavigate } from 'react-router-dom'

export default function HomePage() {
  const [q, setQ] = useState('')
  const navigate = useNavigate()

  const search = () => navigate(`/auctions${q ? `?q=${encodeURIComponent(q)}` : ''}`)
  const chip = (slug: string, label: string, icon: string) => (
    <button className="chip" onClick={() => navigate(`/auctions?category=${slug}`)}>{icon}&nbsp; {label}</button>
  )
  const pageChip = (path: string, label: string, icon: string) => (
    <button className="chip" onClick={() => navigate(path)}>{icon}&nbsp; {label}</button>
  )

  return (
    <div className="container">
      <section className="hero">
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
          <span className="trust-label">48-Hour EMD Refund</span>
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
  )
}
