import { useEffect, useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import {
  getMembershipBenefits, getSubscriptionPrices,
  type BillingCycle, type MembershipBenefit, type SubscriptionPrice, type SubscriptionTier,
} from '../api'
import { money } from '../util'
import { useAuth } from '../auth'
import { useWallet } from '../WalletContext'

const TIER_ORDER: { tier: SubscriptionTier; label: string }[] = [
  { tier: 'SILVER', label: 'Silver' },
  { tier: 'GOLD', label: 'Gold' },
  { tier: 'DIAMOND', label: 'Diamond' },
]

/** The card's own top-of-card total and its "Subscribe Monthly" CTA are always monthly — the
 *  per-cycle price grid on SubscriptionPage prices the same selection using each addon's full
 *  `prices` map (see SelectedAddon) instead of being locked to this cycle. */
const ADDON_CYCLE = 'MONTHLY'

/** {@code prices} carries every billing cycle (not just the card's own monthly one) so a consumer
 *  like SubscriptionPage's "Prices by billing cycle" grid can price the same selection at whatever
 *  cycle the buyer actually picks there. */
export interface SelectedAddon { id: string; name: string; prices: Partial<Record<BillingCycle, number>> }

export default function TierCards({ currentTier, renderCta, onSelectionChange }: {
  currentTier?: SubscriptionTier
  renderCta?: (tier: SubscriptionTier, selectedAddons: SelectedAddon[]) => ReactNode
  /** Fired whenever the checked add-ons change, keyed by tier — lets a parent (e.g. a price grid
   *  for other cycles) stay in sync with selections made in these cards. */
  onSelectionChange?: (selection: Partial<Record<SubscriptionTier, SelectedAddon[]>>) => void
}) {
  const { isAuthenticated } = useAuth()
  const { wallet } = useWallet()
  const [prices, setPrices] = useState<SubscriptionPrice[]>([])
  const [benefits, setBenefits] = useState<MembershipBenefit[]>([])
  const [selectedAddons, setSelectedAddons] = useState<Partial<Record<SubscriptionTier, Set<string>>>>({})
  const [selectedDeposits, setSelectedDeposits] = useState<Partial<Record<SubscriptionTier, Set<string>>>>({})

  useEffect(() => {
    getSubscriptionPrices().then(setPrices).catch(() => {})
    getMembershipBenefits().then(setBenefits).catch(() => {})
  }, [])

  useEffect(() => {
    if (!onSelectionChange) return
    const selection: Partial<Record<SubscriptionTier, SelectedAddon[]>> = {}
    for (const t of TIER_ORDER) {
      const ids = selectedAddons[t.tier]
      if (ids && ids.size > 0) {
        selection[t.tier] = benefits.filter((b) => ids.has(b.id)).map((b) => ({ id: b.id, name: b.name, prices: b.prices }))
      }
    }
    onSelectionChange(selection)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedAddons, benefits])

  const monthlyPrice = (tier: SubscriptionTier) =>
    prices.find((p) => p.tier === tier && p.billingCycle === 'MONTHLY')?.price ?? 0

  const toggleAddon = (tier: SubscriptionTier, benefitId: string) => {
    setSelectedAddons((prev) => {
      const next = new Set(prev[tier])
      if (next.has(benefitId)) next.delete(benefitId)
      else next.add(benefitId)
      return { ...prev, [tier]: next }
    })
  }

  const toggleDeposit = (tier: SubscriptionTier, benefitId: string) => {
    setSelectedDeposits((prev) => {
      const next = new Set(prev[tier])
      if (next.has(benefitId)) next.delete(benefitId)
      else next.add(benefitId)
      return { ...prev, [tier]: next }
    })
  }

  const addonTotal = (tier: SubscriptionTier) => {
    const ids = selectedAddons[tier]
    if (!ids || ids.size === 0) return 0
    return benefits
      .filter((b) => ids.has(b.id))
      .reduce((sum, b) => sum + (b.prices[ADDON_CYCLE] ?? 0), 0)
  }

  return (
    <div className="tier-card-grid">
      {TIER_ORDER.map((t) => {
        const isCurrent = currentTier === t.tier
        const addons = addonTotal(t.tier)
        return (
          <div key={t.tier} className={`tier-card${isCurrent ? ' current' : ''}${t.tier === 'DIAMOND' ? ' featured' : ''}`}>
            {isCurrent && <span className="tier-card-badge">Current plan</span>}
            <h3>{t.label}</h3>
            <div className="tier-card-price">
              {money(monthlyPrice(t.tier) + addons)}<span className="tier-card-price-period">/mo</span>
            </div>
            {addons > 0 && <div className="tier-card-addon-note">Includes {money(addons)}/mo of add-ons</div>}
            <ul className="tier-card-features">
              {benefits.map((b) => {
                const enabled = b.enabledTiers.includes(t.tier)
                if (!enabled) {
                  return <li key={b.id} className="tier-benefit-disabled">✗ {b.name}</li>
                }
                if (b.minDeposit != null) {
                  const checked = selectedDeposits[t.tier]?.has(b.id) ?? false
                  const shortfall = wallet ? Math.max(0, b.minDeposit - wallet.availableBalance) : null
                  return (
                    <li key={b.id} className="tier-benefit-paid">
                      <label>
                        <input type="checkbox" checked={checked} onChange={() => toggleDeposit(t.tier, b.id)} />
                        {b.name} <span className="muted">(min. deposit {money(b.minDeposit)})</span>
                      </label>
                      {checked && !isAuthenticated && (
                        <div className="tier-benefit-deposit-note warn">
                          <Link to="/login">Sign in</Link> to check your wallet balance.
                        </div>
                      )}
                      {checked && isAuthenticated && wallet && shortfall !== null && shortfall > 0 && (
                        <div className="tier-benefit-deposit-note warn">
                          Your wallet balance is {money(wallet.availableBalance)} — top up {money(shortfall)} more to unlock this.{' '}
                          <Link to="/wallet">Top up wallet</Link>
                        </div>
                      )}
                      {checked && isAuthenticated && wallet && shortfall === 0 && (
                        <div className="tier-benefit-deposit-note ok">✓ Your balance meets this requirement.</div>
                      )}
                    </li>
                  )
                }
                if (!b.paid) {
                  return <li key={b.id} className="tier-benefit-enabled">✓ {b.name}</li>
                }
                const checked = selectedAddons[t.tier]?.has(b.id) ?? false
                const price = b.prices[ADDON_CYCLE] ?? 0
                return (
                  <li key={b.id} className="tier-benefit-paid">
                    <label>
                      <input type="checkbox" checked={checked} onChange={() => toggleAddon(t.tier, b.id)} />
                      {b.name}
                      {checked && <span className="tier-benefit-paid-price">+{money(price)}/mo</span>}
                    </label>
                  </li>
                )
              })}
            </ul>
            {renderCta?.(t.tier, benefits
              .filter((b) => selectedAddons[t.tier]?.has(b.id))
              .map((b) => ({ id: b.id, name: b.name, prices: b.prices })))}
          </div>
        )
      })}
    </div>
  )
}
