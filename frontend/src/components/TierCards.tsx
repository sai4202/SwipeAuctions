import { useEffect, useState, type ReactNode } from 'react'
import { getSubscriptionPrices, type SubscriptionPrice, type SubscriptionTier } from '../api'
import { money } from '../util'

/** Every tier gets these — bidding mechanics don't change with subscription level. */
const BASE_FEATURES = [
  'Browse & bid on standard listings',
  'Real-time live bidding',
  'No per-item deposits — bid freely within your wallet credit limit',
  'Leveraged wallet credit limit on every deposit',
]

/** Cumulative — each tier's card also shows every unlock from the tiers before it, since
 *  `SubscriptionTier.meetsRequirement` in the backend is a strict level comparison (higher tiers
 *  automatically satisfy lower-tier gates). This mirrors that truthfully instead of inventing
 *  perks the app doesn't actually implement. */
const TIER_ORDER: { tier: SubscriptionTier; label: string; unlock?: string }[] = [
  { tier: 'NONE', label: 'Guest' },
  { tier: 'SILVER', label: 'Silver', unlock: 'Access to Silver-tier listings' },
  { tier: 'GOLD', label: 'Gold', unlock: 'Access to Gold-tier listings' },
  { tier: 'DIAMOND', label: 'Diamond', unlock: 'Access to Diamond-tier listings' },
]

export default function TierCards({ currentTier, renderCta }: {
  currentTier?: SubscriptionTier
  renderCta?: (tier: SubscriptionTier) => ReactNode
}) {
  const [prices, setPrices] = useState<SubscriptionPrice[]>([])

  useEffect(() => {
    getSubscriptionPrices().then(setPrices).catch(() => {})
  }, [])

  const monthlyPrice = (tier: SubscriptionTier) =>
    prices.find((p) => p.tier === tier && p.billingCycle === 'MONTHLY')?.price ?? 0

  return (
    <div className="tier-card-grid">
      {TIER_ORDER.map((t, i) => {
        const unlocks = TIER_ORDER.slice(1, i + 1).map((x) => x.unlock as string)
        const isCurrent = currentTier === t.tier
        return (
          <div key={t.tier} className={`tier-card${isCurrent ? ' current' : ''}${t.tier === 'DIAMOND' ? ' featured' : ''}`}>
            {isCurrent && <span className="tier-card-badge">Current plan</span>}
            <h3>{t.label}</h3>
            <div className="tier-card-price">
              {t.tier === 'NONE' ? 'Free' : <>{money(monthlyPrice(t.tier))}<span className="tier-card-price-period">/mo</span></>}
            </div>
            <ul className="tier-card-features">
              {BASE_FEATURES.map((f) => <li key={f}>✓ {f}</li>)}
              {unlocks.map((f) => <li key={f} className="tier-card-unlock">✓ {f}</li>)}
            </ul>
            {renderCta?.(t.tier)}
          </div>
        )
      })}
    </div>
  )
}
