import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth'
import {
  getSubscriptionPrices, getMembershipBenefits, createSubscriptionOrder, verifySubscription, errorMessage,
  type SubscriptionPrice, type MembershipBenefit, type SubscriptionTier, type BillingCycle, type OrderIntent,
} from '../api'
import { money } from '../util'
import TierCards, { type SelectedAddon } from '../components/TierCards'
import RazorpayCheckout from '../components/RazorpayCheckout'

const TIERS: SubscriptionTier[] = ['SILVER', 'GOLD', 'DIAMOND']
const CYCLES: { key: BillingCycle; label: string }[] = [
  { key: 'MONTHLY', label: 'Monthly' },
  { key: 'QUARTERLY', label: 'Quarterly' },
  { key: 'HALF_YEARLY', label: 'Half-yearly' },
  { key: 'YEARLY', label: 'Yearly' },
]
const CYCLE_LABEL: Record<BillingCycle, string> = Object.fromEntries(CYCLES.map((c) => [c.key, c.label])) as Record<BillingCycle, string>

interface ResolvedAddon { id: string; name: string; price: number }
/** Every paid add-on available on {@code tier} (not just ones the buyer already checked on a card)
 *  — the confirmation step re-offers all of them so a buyer who clicked Subscribe without opting in
 *  gets one more clear chance, with the exact price, before paying. */
interface PendingAddon extends ResolvedAddon { checked: boolean }

interface PendingSubscription {
  tier: SubscriptionTier
  cycle: BillingCycle
  addons: PendingAddon[]
}

export default function SubscriptionPage() {
  const { isAuthenticated, subscriptionTier, subscriptionExpiresAt, setSubscription } = useAuth()
  const [prices, setPrices] = useState<SubscriptionPrice[]>([])
  const [error, setError] = useState('')
  const [busyKey, setBusyKey] = useState<string | null>(null)
  const [ok, setOk] = useState('')
  const [checkoutOrder, setCheckoutOrder] = useState<OrderIntent | null>(null)
  const [pending, setPending] = useState<PendingSubscription | null>(null)
  const [addonSelection, setAddonSelection] = useState<Partial<Record<SubscriptionTier, SelectedAddon[]>>>({})
  const [benefits, setBenefits] = useState<MembershipBenefit[]>([])

  useEffect(() => {
    getSubscriptionPrices().then(setPrices).catch((e) => setError(errorMessage(e)))
    getMembershipBenefits().then(setBenefits).catch(() => {})
  }, [])

  const priceFor = (tier: SubscriptionTier, cycle: BillingCycle) =>
    prices.find((p) => p.tier === tier && p.billingCycle === cycle)?.price ?? 0

  /** Resolves the currently-checked add-ons (from either card, whichever the buyer used) to their
   *  price at a specific cycle — used so the "Prices by billing cycle" grid below reflects the same
   *  selection the tier cards show, not just the monthly price the cards themselves display. */
  const resolveAddons = (tier: SubscriptionTier, cycle: BillingCycle): ResolvedAddon[] =>
    (addonSelection[tier] ?? []).map((a) => ({ id: a.id, name: a.name, price: a.prices[cycle] ?? 0 }))

  /** Every paid add-on offered on {@code tier}, priced at {@code cycle}, pre-checked for whichever
   *  ones the buyer already opted into on a card — this is what the confirmation modal offers, so a
   *  buyer who subscribed without checking anything still gets one clear last chance. */
  const allAddonsFor = (tier: SubscriptionTier, cycle: BillingCycle): PendingAddon[] => {
    const preChecked = new Set((addonSelection[tier] ?? []).map((a) => a.id))
    return benefits
      .filter((b) => b.paid && b.enabledTiers.includes(tier))
      .map((b) => ({ id: b.id, name: b.name, price: b.prices[cycle] ?? 0, checked: preChecked.has(b.id) }))
  }

  const togglePendingAddon = (id: string) => {
    setPending((prev) => prev && {
      ...prev,
      addons: prev.addons.map((a) => (a.id === id ? { ...a, checked: !a.checked } : a)),
    })
  }

  const startCheckout = async (tier: SubscriptionTier, cycle: BillingCycle, addonBenefitIds: string[]) => {
    const key = `${tier}-${cycle}`
    setBusyKey(key); setError(''); setOk('')
    try { setCheckoutOrder(await createSubscriptionOrder(tier, cycle, addonBenefitIds)) }
    catch (e) { setError(errorMessage(e)) } finally { setBusyKey(null) }
  }

  const confirmAndPay = () => {
    if (!pending) return
    const { tier, cycle, addons } = pending
    setPending(null)
    startCheckout(tier, cycle, addons.filter((a) => a.checked).map((a) => a.id))
  }

  const onCheckoutSuccess = async (paymentId: string, signature: string) => {
    if (!checkoutOrder) return
    try {
      const result = await verifySubscription(checkoutOrder.orderId, paymentId, signature)
      setSubscription(result.tier, result.expiresAt)
      setOk(`You're now on the ${result.tier} plan.`)
    } catch (e) { setError(errorMessage(e)) } finally { setCheckoutOrder(null) }
  }

  const onCheckoutCancel = () => setCheckoutOrder(null)

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

      {checkoutOrder && (
        <div className="card" style={{ maxWidth: 460, marginBottom: 20 }}>
          <RazorpayCheckout
            orderId={checkoutOrder.orderId} amountPaise={checkoutOrder.amountPaise}
            currency={checkoutOrder.currency} keyId={checkoutOrder.keyId}
            description="Subscription" onSuccess={onCheckoutSuccess} onCancel={onCheckoutCancel}
          />
        </div>
      )}

      {pending && (
        <div className="modal-backdrop" onClick={() => setPending(null)}>
          <div className="modal-card" style={{ maxWidth: 420 }} onClick={(e) => e.stopPropagation()}>
            <div className="modal-head">
              <h3>Confirm subscription</h3>
              <button type="button" className="modal-close" onClick={() => setPending(null)} aria-label="Close">✕</button>
            </div>
            <div className="modal-body">
              <div style={{ display: 'flex', justifyContent: 'space-between', fontWeight: 700 }}>
                <span>{pending.tier} · {CYCLE_LABEL[pending.cycle]}</span>
                <span>{money(priceFor(pending.tier, pending.cycle))}</span>
              </div>
              {pending.addons.length > 0 && (
                <div style={{ marginTop: 12 }}>
                  <div className="muted" style={{ fontSize: 11.5, textTransform: 'uppercase', letterSpacing: '.03em', marginBottom: 6 }}>
                    Optional add-ons
                  </div>
                  {pending.addons.map((a) => (
                    <label key={a.id}
                           style={{ display: 'flex', alignItems: 'center', gap: 8, justifyContent: 'space-between', padding: '5px 0', cursor: 'pointer' }}>
                      <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                        <input type="checkbox" checked={a.checked} onChange={() => togglePendingAddon(a.id)} />
                        {a.name}
                      </span>
                      <span className={a.checked ? '' : 'muted'} style={{ fontWeight: a.checked ? 700 : 400 }}>
                        +{money(a.price)}
                      </span>
                    </label>
                  ))}
                </div>
              )}
              <hr style={{ border: 'none', borderTop: '1px solid var(--border)', margin: '14px 0' }} />
              <div style={{ display: 'flex', justifyContent: 'space-between', fontWeight: 800, fontSize: 16 }}>
                <span>Total</span>
                <span>{money(priceFor(pending.tier, pending.cycle) + pending.addons.filter((a) => a.checked).reduce((s, a) => s + a.price, 0))}</span>
              </div>
              <div style={{ display: 'flex', gap: 10, marginTop: 18 }}>
                <button type="button" className="btn ghost sm block" onClick={() => setPending(null)}>Cancel</button>
                <button type="button" className="btn sm block" onClick={confirmAndPay}
                        disabled={busyKey === `${pending.tier}-${pending.cycle}`}>
                  {busyKey === `${pending.tier}-${pending.cycle}` ? '…' : 'Proceed to payment'}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      <TierCards
        currentTier={isAuthenticated ? (subscriptionTier as SubscriptionTier) : undefined}
        onSelectionChange={setAddonSelection}
        renderCta={(tier) => (
          <button
            type="button"
            className="btn block sm"
            disabled={!isAuthenticated || busyKey === `${tier}-MONTHLY`}
            onClick={() => setPending({ tier, cycle: 'MONTHLY', addons: allAddonsFor(tier, 'MONTHLY') })}
          >
            {busyKey === `${tier}-MONTHLY` ? '…' : isAuthenticated && subscriptionTier === tier ? 'Renew monthly' : 'Subscribe monthly'}
          </button>
        )}
      />

      <h2 style={{ fontSize: 17, margin: '30px 0 14px' }}>Prices by billing cycle</h2>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: 16 }}>
        {TIERS.map((tier) => (
          <div key={tier} className="card">
            <h3 style={{ marginTop: 0 }}>{tier}</h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
              {CYCLES.map(({ key, label }) => {
                const busyThisKey = `${tier}-${key}`
                const isCurrent = isAuthenticated && subscriptionTier === tier
                const addons = resolveAddons(tier, key)
                const addonTotal = addons.reduce((s, a) => s + a.price, 0)
                return (
                  <div key={key}>
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 10 }}>
                      <span className="muted">{label}</span>
                      <span style={{ fontWeight: 700 }}>{money(priceFor(tier, key) + addonTotal)}</span>
                      <button
                        type="button"
                        className="btn sm"
                        disabled={!isAuthenticated || busyKey === busyThisKey}
                        onClick={() => setPending({ tier, cycle: key, addons: allAddonsFor(tier, key) })}
                      >
                        {busyKey === busyThisKey ? '…' : isCurrent ? 'Renew' : 'Subscribe'}
                      </button>
                    </div>
                    {addons.length > 0 && (
                      <div className="muted" style={{ fontSize: 11.5, marginTop: 3 }}>
                        {money(priceFor(tier, key))} plan + {addons.map((a) => `${money(a.price)} ${a.name}`).join(' + ')}
                      </div>
                    )}
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
