import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  getSubscriptionPrices, getMembershipBenefits, errorMessage,
  type SubscriptionPrice, type SubscriptionTier, type BillingCycle, type MembershipBenefit,
} from '../../api'
import { money } from '../../util'
import { AdminPageHeader } from './shared'

const TIERS: ('SILVER' | 'GOLD' | 'DIAMOND')[] = ['SILVER', 'GOLD', 'DIAMOND']
const TIER_ACCENT: Record<'SILVER' | 'GOLD' | 'DIAMOND', string> = { SILVER: '#9ca3af', GOLD: '#d4a017', DIAMOND: '#7c3aed' }
const CYCLES: { key: BillingCycle; label: string }[] = [
  { key: 'MONTHLY', label: 'Monthly' },
  { key: 'QUARTERLY', label: 'Quarterly' },
  { key: 'HALF_YEARLY', label: 'Half-yearly' },
  { key: 'YEARLY', label: 'Yearly' },
]

/** Reference site's "Plans" section shows fixed-return investment products (₹ range → guaranteed
 *  %, lock-in). SwipeAuctions has no such product — the real equivalent is the subscription tier
 *  system, so this reframes that data as the same card-grid layout: one card per tier, pricing per
 *  billing cycle, and which membership benefits it includes. Read-only; actual edits stay on the
 *  Settings page (already has the full form) to avoid two places that can drift out of sync. */
export default function AdminPlansPage() {
  const [prices, setPrices] = useState<SubscriptionPrice[]>([])
  const [benefits, setBenefits] = useState<MembershipBenefit[]>([])
  const [error, setError] = useState('')

  useEffect(() => {
    Promise.all([getSubscriptionPrices(), getMembershipBenefits()])
      .then(([p, b]) => { setPrices(p); setBenefits(b) })
      .catch((e) => setError(errorMessage(e)))
  }, [])

  const priceFor = (tier: SubscriptionTier, cycle: BillingCycle) =>
    prices.find((p) => p.tier === tier && p.billingCycle === cycle)?.price ?? 0

  return (
    <div>
      <AdminPageHeader section="Plans" title="Membership Tiers"
                        subtitle="Subscription pricing and included benefits, per tier."
                        actions={<Link to="/admin/settings" className="btn ghost sm">Edit in Settings</Link>} />
      {error && <div className="error">{error}</div>}

      <div className="chart-grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))' }}>
        {TIERS.map((tier) => {
          const included = benefits.filter((b) => b.enabledTiers.includes(tier))
          return (
            <div key={tier} className="card" style={{ borderTop: `3px solid ${TIER_ACCENT[tier]}`, padding: 18 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
                <h3 style={{ margin: 0, fontSize: 16 }}>{tier[0] + tier.slice(1).toLowerCase()}</h3>
                <span className="badge" style={{ background: `${TIER_ACCENT[tier]}22`, color: TIER_ACCENT[tier] }}>{tier}</span>
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 4, marginBottom: 14 }}>
                {CYCLES.map((c) => (
                  <div key={c.key} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13 }}>
                    <span className="muted">{c.label}</span>
                    <b>{money(priceFor(tier, c.key))}</b>
                  </div>
                ))}
              </div>
              <div className="muted" style={{ fontSize: 11.5, textTransform: 'uppercase', letterSpacing: '.05em', marginBottom: 6 }}>
                Included benefits
              </div>
              {included.length === 0 ? (
                <p className="muted" style={{ fontSize: 13 }}>None configured.</p>
              ) : (
                <ul style={{ margin: 0, paddingLeft: 18, fontSize: 13, display: 'flex', flexDirection: 'column', gap: 4 }}>
                  {included.map((b) => <li key={b.id}>{b.name}</li>)}
                </ul>
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}
