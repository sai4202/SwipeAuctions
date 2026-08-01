import { useEffect, useState, type ReactNode } from 'react'
import { getMembershipBenefits, getSubscriptionPrices, type MembershipBenefit, type SubscriptionPrice, type SubscriptionTier } from '../api'
import { money } from '../util'

const TIER_ORDER: { tier: SubscriptionTier; label: string }[] = [
  { tier: 'SILVER', label: 'Silver' },
  { tier: 'GOLD', label: 'Gold' },
  { tier: 'DIAMOND', label: 'Diamond' },
]

export default function TierCards({ currentTier, renderCta }: {
  currentTier?: SubscriptionTier
  renderCta?: (tier: SubscriptionTier) => ReactNode
}) {
  const [prices, setPrices] = useState<SubscriptionPrice[]>([])
  const [benefits, setBenefits] = useState<MembershipBenefit[]>([])

  useEffect(() => {
    getSubscriptionPrices().then(setPrices).catch(() => {})
    getMembershipBenefits().then(setBenefits).catch(() => {})
  }, [])

  const monthlyPrice = (tier: SubscriptionTier) =>
    prices.find((p) => p.tier === tier && p.billingCycle === 'MONTHLY')?.price ?? 0

  return (
    <div className="tier-card-grid">
      {TIER_ORDER.map((t) => {
        const isCurrent = currentTier === t.tier
        return (
          <div key={t.tier} className={`tier-card${isCurrent ? ' current' : ''}${t.tier === 'DIAMOND' ? ' featured' : ''}`}>
            {isCurrent && <span className="tier-card-badge">Current plan</span>}
            <h3>{t.label}</h3>
            <div className="tier-card-price">
              {money(monthlyPrice(t.tier))}<span className="tier-card-price-period">/mo</span>
            </div>
            <ul className="tier-card-features">
              {benefits.map((b) => {
                const enabled = b.enabledTiers.includes(t.tier)
                return (
                  <li key={b.id} className={enabled ? 'tier-benefit-enabled' : 'tier-benefit-disabled'}>
                    {enabled ? '✓' : '✗'} {b.name}
                  </li>
                )
              })}
            </ul>
            {renderCta?.(t.tier)}
          </div>
        )
      })}
    </div>
  )
}
