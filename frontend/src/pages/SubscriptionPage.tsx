import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth'
import {
  getSubscriptionPrices, subscribeToPlan, errorMessage,
  type SubscriptionPrice, type SubscriptionTier, type BillingCycle,
} from '../api'
import { money } from '../util'

const TIERS: SubscriptionTier[] = ['SILVER', 'GOLD', 'DIAMOND']
const CYCLES: { key: BillingCycle; label: string }[] = [
  { key: 'MONTHLY', label: 'Monthly' },
  { key: 'QUARTERLY', label: 'Quarterly' },
  { key: 'HALF_YEARLY', label: 'Half-yearly' },
  { key: 'YEARLY', label: 'Yearly' },
]

export default function SubscriptionPage() {
  const { isAuthenticated, subscriptionTier, subscriptionExpiresAt, setSubscription } = useAuth()
  const [prices, setPrices] = useState<SubscriptionPrice[]>([])
  const [error, setError] = useState('')
  const [busyKey, setBusyKey] = useState<string | null>(null)
  const [ok, setOk] = useState('')

  useEffect(() => {
    getSubscriptionPrices().then(setPrices).catch((e) => setError(errorMessage(e)))
  }, [])

  const priceFor = (tier: SubscriptionTier, cycle: BillingCycle) =>
    prices.find((p) => p.tier === tier && p.billingCycle === cycle)?.price ?? 0

  const subscribe = async (tier: SubscriptionTier, cycle: BillingCycle) => {
    const key = `${tier}-${cycle}`
    setBusyKey(key); setError(''); setOk('')
    try {
      const result = await subscribeToPlan(tier, cycle)
      setSubscription(result.tier, result.expiresAt)
      setOk(`You're now on the ${result.tier} plan.`)
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusyKey(null)
    }
  }

  return (
    <div className="container">
      <div className="section-head">
        <h1 className="page">Subscription</h1>
        <p className="muted">
          Some listings require a subscription tier to view full details and bid. Higher tiers include
          everything lower tiers unlock.
        </p>
      </div>

      {!isAuthenticated && (
        <div className="card" style={{ maxWidth: 460, marginBottom: 20 }}>
          Please <Link to="/login">sign in</Link> to subscribe.
        </div>
      )}

      {isAuthenticated && (
        <p className="muted" style={{ marginBottom: 16 }}>
          Current plan: <b>{subscriptionTier}</b>
          {subscriptionExpiresAt && subscriptionTier !== 'NONE' && (
            <> — expires {new Date(subscriptionExpiresAt).toLocaleDateString()}</>
          )}
        </p>
      )}

      {error && <div className="error" style={{ marginBottom: 16 }}>{error}</div>}
      {ok && <div className="ok" style={{ marginBottom: 16 }}>{ok}</div>}

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: 16 }}>
        {TIERS.map((tier) => (
          <div key={tier} className="card">
            <h3 style={{ marginTop: 0 }}>{tier}</h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {CYCLES.map(({ key, label }) => {
                const busyThisKey = `${tier}-${key}`
                const isCurrent = isAuthenticated && subscriptionTier === tier
                return (
                  <div key={key} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 10 }}>
                    <span className="muted">{label}</span>
                    <span style={{ fontWeight: 700 }}>{money(priceFor(tier, key))}</span>
                    <button
                      type="button"
                      className="btn sm"
                      disabled={!isAuthenticated || busyKey === busyThisKey}
                      onClick={() => subscribe(tier, key)}
                    >
                      {busyKey === busyThisKey ? '…' : isCurrent ? 'Renew' : 'Subscribe'}
                    </button>
                  </div>
                )
              })}
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
