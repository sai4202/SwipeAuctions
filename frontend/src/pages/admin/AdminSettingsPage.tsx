import { useEffect, useState, type FormEvent } from 'react'
import { useAuth } from '../../auth'
import {
  getRegistrationFee, updateRegistrationFee, getSubscriptionPrices, updateSubscriptionPrices,
  getMembershipBenefits, createMembershipBenefit, updateMembershipBenefitTiers, deleteMembershipBenefit,
  getReferralSettings, updateReferralSettings,
  createAdmin, errorMessage,
  type SubscriptionPrice, type SubscriptionTier, type BillingCycle, type MembershipBenefit, type AdminRole,
} from '../../api'
import { money } from '../../util'
import { AdminPageHeader } from './shared'

const TIERS: SubscriptionTier[] = ['SILVER', 'GOLD', 'DIAMOND']
const CYCLES: { key: BillingCycle; label: string }[] = [
  { key: 'MONTHLY', label: 'Monthly' },
  { key: 'QUARTERLY', label: 'Quarterly' },
  { key: 'HALF_YEARLY', label: 'Half-yearly' },
  { key: 'YEARLY', label: 'Yearly' },
]

/** Registration fee + the 12 (tier x cycle) subscription prices. Both are informational/config
 *  only for now — no payment is actually collected against either (see SubscriptionService /
 *  PlatformSettingsService javadoc). */
export default function AdminSettingsPage() {
  const { adminRole } = useAuth()
  const [showCreateAdmin, setShowCreateAdmin] = useState(false)
  const [createAdminMsg, setCreateAdminMsg] = useState('')

  const [fee, setFee] = useState('')
  const [feeSaving, setFeeSaving] = useState(false)
  const [feeMsg, setFeeMsg] = useState('')
  const [feeError, setFeeError] = useState('')

  const [referralBonus, setReferralBonus] = useState('')
  const [referralMinDeposit, setReferralMinDeposit] = useState('')
  const [referralSaving, setReferralSaving] = useState(false)
  const [referralMsg, setReferralMsg] = useState('')
  const [referralError, setReferralError] = useState('')

  const [prices, setPrices] = useState<SubscriptionPrice[]>([])
  const [priceSaving, setPriceSaving] = useState(false)
  const [priceMsg, setPriceMsg] = useState('')
  const [priceError, setPriceError] = useState('')

  const [benefits, setBenefits] = useState<MembershipBenefit[]>([])
  const [benefitsSaving, setBenefitsSaving] = useState(false)
  const [benefitsMsg, setBenefitsMsg] = useState('')
  const [benefitsError, setBenefitsError] = useState('')
  const [newBenefitName, setNewBenefitName] = useState('')
  const [newBenefitPaid, setNewBenefitPaid] = useState(false)
  const [newBenefitPrices, setNewBenefitPrices] = useState<Partial<Record<BillingCycle, string>>>({})
  const [newBenefitMinDepositReq, setNewBenefitMinDepositReq] = useState(false)
  const [newBenefitMinDeposit, setNewBenefitMinDeposit] = useState('')
  const [addingBenefit, setAddingBenefit] = useState(false)
  const [addBenefitError, setAddBenefitError] = useState('')

  useEffect(() => {
    getRegistrationFee().then((f) => setFee(String(f))).catch((e) => setFeeError(errorMessage(e)))
    getSubscriptionPrices().then(setPrices).catch((e) => setPriceError(errorMessage(e)))
    getMembershipBenefits().then(setBenefits).catch((e) => setBenefitsError(errorMessage(e)))
    getReferralSettings().then((s) => { setReferralBonus(String(s.bonusAmount)); setReferralMinDeposit(String(s.minDeposit)) }).catch((e) => setReferralError(errorMessage(e)))
  }, [])

  const priceFor = (tier: SubscriptionTier, cycle: BillingCycle) =>
    prices.find((p) => p.tier === tier && p.billingCycle === cycle)?.price ?? 0

  const setPriceFor = (tier: SubscriptionTier, cycle: BillingCycle, value: string) => {
    const n = Number(value)
    setPrices((prev) => {
      const exists = prev.some((p) => p.tier === tier && p.billingCycle === cycle)
      if (!exists) return [...prev, { tier, billingCycle: cycle, price: n }]
      return prev.map((p) => (p.tier === tier && p.billingCycle === cycle ? { ...p, price: n } : p))
    })
  }

  const submitFee = async (e: FormEvent) => {
    e.preventDefault()
    setFeeSaving(true); setFeeError(''); setFeeMsg('')
    try {
      const saved = await updateRegistrationFee(Number(fee))
      setFee(String(saved))
      setFeeMsg('Registration fee saved.')
    } catch (e2) { setFeeError(errorMessage(e2)) } finally { setFeeSaving(false) }
  }

  const submitReferralSettings = async (e: FormEvent) => {
    e.preventDefault()
    setReferralSaving(true); setReferralError(''); setReferralMsg('')
    try {
      const saved = await updateReferralSettings(Number(referralBonus), Number(referralMinDeposit))
      setReferralBonus(String(saved.bonusAmount))
      setReferralMinDeposit(String(saved.minDeposit))
      setReferralMsg('Referral settings saved.')
    } catch (e2) { setReferralError(errorMessage(e2)) } finally { setReferralSaving(false) }
  }

  const submitPrices = async (e: FormEvent) => {
    e.preventDefault()
    setPriceSaving(true); setPriceError(''); setPriceMsg('')
    try {
      const saved = await updateSubscriptionPrices(prices)
      setPrices(saved)
      setPriceMsg('Subscription prices saved.')
    } catch (e2) { setPriceError(errorMessage(e2)) } finally { setPriceSaving(false) }
  }

  const toggleBenefitTier = (benefitId: string, tier: SubscriptionTier, checked: boolean) => {
    setBenefits((prev) => prev.map((b) => b.id !== benefitId ? b : {
      ...b,
      enabledTiers: checked ? [...b.enabledTiers, tier] : b.enabledTiers.filter((t) => t !== tier),
    }))
  }

  const submitBenefitTiers = async (e: FormEvent) => {
    e.preventDefault()
    setBenefitsSaving(true); setBenefitsError(''); setBenefitsMsg('')
    try {
      const saved = await updateMembershipBenefitTiers(benefits.map((b) => ({ benefitId: b.id, enabledTiers: b.enabledTiers })))
      setBenefits(saved)
      setBenefitsMsg('Membership benefits saved.')
    } catch (e2) { setBenefitsError(errorMessage(e2)) } finally { setBenefitsSaving(false) }
  }

  /** Paid and min-deposit are mutually exclusive — a benefit is either free, requires extra
   *  payment, or is unlocked by a minimum wallet deposit, never more than one of those at once. */
  const checkPaid = (checked: boolean) => {
    setNewBenefitPaid(checked)
    if (checked) { setNewBenefitMinDepositReq(false); setNewBenefitMinDeposit('') }
  }
  const checkMinDeposit = (checked: boolean) => {
    setNewBenefitMinDepositReq(checked)
    if (checked) { setNewBenefitPaid(false); setNewBenefitPrices({}) }
  }

  const submitNewBenefit = async (e: FormEvent) => {
    e.preventDefault()
    if (newBenefitPaid && CYCLES.some((c) => !newBenefitPrices[c.key] || Number(newBenefitPrices[c.key]) <= 0)) {
      setAddBenefitError('Enter an amount for every billing cycle.')
      return
    }
    if (newBenefitMinDepositReq && !(Number(newBenefitMinDeposit) > 0)) {
      setAddBenefitError('Enter a minimum deposit amount.')
      return
    }
    setAddingBenefit(true); setAddBenefitError('')
    try {
      const prices: Partial<Record<BillingCycle, number>> = newBenefitPaid
        ? Object.fromEntries(CYCLES.map((c) => [c.key, Number(newBenefitPrices[c.key])]))
        : {}
      const minDeposit = newBenefitMinDepositReq ? Number(newBenefitMinDeposit) : null
      const created = await createMembershipBenefit(newBenefitName, newBenefitPaid, prices, minDeposit)
      setBenefits((prev) => [...prev, created])
      setNewBenefitName('')
      setNewBenefitPaid(false)
      setNewBenefitPrices({})
      setNewBenefitMinDepositReq(false)
      setNewBenefitMinDeposit('')
    } catch (e2) { setAddBenefitError(errorMessage(e2)) } finally { setAddingBenefit(false) }
  }

  const removeBenefit = async (id: string) => {
    if (!confirm('Remove this benefit? It will disappear from every tier card.')) return
    setBenefitsError('')
    try {
      await deleteMembershipBenefit(id)
      setBenefits((prev) => prev.filter((b) => b.id !== id))
    } catch (e2) { setBenefitsError(errorMessage(e2)) }
  }

  return (
    <div>
      <AdminPageHeader section="Settings" title="Settings" subtitle="Registration fee, subscription pricing, membership benefits, and admin accounts." />
      <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
        {adminRole === 'SUPER_ADMIN' && (
          <div className="card">
            <div className="section-head" style={{ marginBottom: 8 }}>
              <h2 style={{ fontSize: 15, margin: 0 }}>Admin accounts</h2>
              <button type="button" className="btn sm" onClick={() => setShowCreateAdmin(true)}>+ Create Admin</button>
            </div>
            <p className="muted" style={{ fontSize: 12.5, marginTop: 0 }}>
              Only Super Admins can create new admin accounts. A Super Admin can create other Super
              Admins or regular Admins; a regular Admin can't create anyone.
            </p>
            {createAdminMsg && <div className="ok">{createAdminMsg}</div>}
          </div>
        )}
        {showCreateAdmin && (
          <CreateAdminModal
            onClose={() => setShowCreateAdmin(false)}
            onCreated={(email) => setCreateAdminMsg(`Admin account "${email}" created.`)}
          />
        )}

        <div className="card">
          <h2 style={{ fontSize: 15, margin: '0 0 12px' }}>Registration fee</h2>
          <p className="muted" style={{ fontSize: 12.5, marginTop: 0 }}>
            One-time fee shown to new signups. Informational only for now — nothing is charged yet.
          </p>
          <form onSubmit={submitFee} style={{ display: 'flex', gap: 10, alignItems: 'flex-end', flexWrap: 'wrap' }}>
            <div className="fgroup" style={{ maxWidth: 200 }}>
              <small>Fee (₹)</small>
              <input type="number" min={0} value={fee} onChange={(e) => setFee(e.target.value)} />
            </div>
            <button type="submit" className="btn sm" disabled={feeSaving}>{feeSaving ? 'Saving…' : 'Save'}</button>
          </form>
          {feeError && <div className="error">{feeError}</div>}
          {feeMsg && <div className="ok">{feeMsg}</div>}
        </div>

        <div className="card">
          <h2 style={{ fontSize: 15, margin: '0 0 12px' }}>Referral bonus</h2>
          <p className="muted" style={{ fontSize: 12.5, marginTop: 0 }}>
            Paid to the referrer once their invited user deposits at least the minimum in a single top-up.
          </p>
          <form onSubmit={submitReferralSettings} style={{ display: 'flex', gap: 10, alignItems: 'flex-end', flexWrap: 'wrap' }}>
            <div className="fgroup" style={{ maxWidth: 200 }}>
              <small>Bonus amount (₹)</small>
              <input type="number" min={0} value={referralBonus} onChange={(e) => setReferralBonus(e.target.value)} />
            </div>
            <div className="fgroup" style={{ maxWidth: 200 }}>
              <small>Minimum deposit (₹)</small>
              <input type="number" min={0} value={referralMinDeposit} onChange={(e) => setReferralMinDeposit(e.target.value)} />
            </div>
            <button type="submit" className="btn sm" disabled={referralSaving}>{referralSaving ? 'Saving…' : 'Save'}</button>
          </form>
          {referralError && <div className="error">{referralError}</div>}
          {referralMsg && <div className="ok">{referralMsg}</div>}
        </div>

        <div className="card">
          <h2 style={{ fontSize: 15, margin: '0 0 12px' }}>Subscription prices</h2>
          <p className="muted" style={{ fontSize: 12.5, marginTop: 0 }}>
            Prices shown on the buyer subscription page. Informational only for now — subscribing doesn't
            charge anything yet.
          </p>
          <form onSubmit={submitPrices}>
            <div style={{ overflowX: 'auto' }}>
              <table className="admin-table">
                <thead>
                  <tr>
                    <th>Tier</th>
                    {CYCLES.map((c) => <th key={c.key}>{c.label}</th>)}
                  </tr>
                </thead>
                <tbody>
                  {TIERS.map((tier) => (
                    <tr key={tier}>
                      <td>{tier}</td>
                      {CYCLES.map((c) => (
                        <td key={c.key}>
                          <input
                            type="number"
                            min={0}
                            style={{ width: 100 }}
                            value={priceFor(tier, c.key)}
                            onChange={(e) => setPriceFor(tier, c.key, e.target.value)}
                          />
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <button type="submit" className="btn sm" style={{ marginTop: 12 }} disabled={priceSaving}>
              {priceSaving ? 'Saving…' : 'Save prices'}
            </button>
          </form>
          {priceError && <div className="error">{priceError}</div>}
          {priceMsg && <div className="ok">{priceMsg}</div>}
        </div>

        <div className="card">
          <h2 style={{ fontSize: 15, margin: '0 0 12px' }}>Membership benefits</h2>
          <p className="muted" style={{ fontSize: 12.5, marginTop: 0 }}>
            Shown under every tier on the membership page. Check a tier to include a benefit on that
            tier's card; unchecked benefits show as a red cross instead.
          </p>
          <form onSubmit={submitBenefitTiers}>
            <div style={{ overflowX: 'auto' }}>
              <table className="admin-table">
                <thead>
                  <tr>
                    <th>Benefit</th>
                    {TIERS.map((tier) => <th key={tier}>{tier}</th>)}
                    <th>Requirement</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  {benefits.map((b) => (
                    <tr key={b.id}>
                      <td>{b.name}</td>
                      {TIERS.map((tier) => (
                        <td key={tier}>
                          <input
                            type="checkbox"
                            checked={b.enabledTiers.includes(tier)}
                            onChange={(e) => toggleBenefitTier(b.id, tier, e.target.checked)}
                          />
                        </td>
                      ))}
                      <td style={{ fontSize: 12 }}>
                        {b.paid
                          ? CYCLES.map((c) => `${c.label}: ${money(b.prices[c.key] ?? 0)}`).join(' · ')
                          : b.minDeposit != null
                          ? `Min. deposit ${money(b.minDeposit)}`
                          : <span className="muted">Free</span>}
                      </td>
                      <td>
                        <button type="button" className="btn ghost sm" onClick={() => removeBenefit(b.id)}>Remove</button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <button type="submit" className="btn sm" style={{ marginTop: 12 }} disabled={benefitsSaving}>
              {benefitsSaving ? 'Saving…' : 'Save benefit tiers'}
            </button>
          </form>
          {benefitsError && <div className="error">{benefitsError}</div>}
          {benefitsMsg && <div className="ok">{benefitsMsg}</div>}

          <form onSubmit={submitNewBenefit} style={{ marginTop: 16 }}>
            <div style={{ display: 'flex', gap: 10, alignItems: 'flex-end', flexWrap: 'wrap' }}>
              <div className="fgroup" style={{ maxWidth: 320, flex: 1 }}>
                <small>New benefit</small>
                <input
                  type="text"
                  placeholder="e.g. Priority customer support"
                  value={newBenefitName}
                  onChange={(e) => setNewBenefitName(e.target.value)}
                />
              </div>
              <label style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 13, paddingBottom: 8 }}>
                <input
                  type="checkbox"
                  checked={newBenefitPaid}
                  onChange={(e) => checkPaid(e.target.checked)}
                />
                Requires extra payment
              </label>
              <label style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 13, paddingBottom: 8 }}>
                <input
                  type="checkbox"
                  checked={newBenefitMinDepositReq}
                  onChange={(e) => checkMinDeposit(e.target.checked)}
                />
                Requires minimum wallet deposit
              </label>
              <button type="submit" className="btn sm" disabled={addingBenefit || !newBenefitName.trim()}>
                {addingBenefit ? 'Adding…' : 'Add benefit'}
              </button>
            </div>
            {newBenefitPaid && (
              <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', marginTop: 10 }}>
                {CYCLES.map((c) => (
                  <div key={c.key} className="fgroup" style={{ maxWidth: 140 }}>
                    <small>{c.label} (₹)</small>
                    <input
                      type="number"
                      min={0}
                      value={newBenefitPrices[c.key] ?? ''}
                      onChange={(e) => setNewBenefitPrices((prev) => ({ ...prev, [c.key]: e.target.value }))}
                    />
                  </div>
                ))}
              </div>
            )}
            {newBenefitMinDepositReq && (
              <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', marginTop: 10 }}>
                <div className="fgroup" style={{ maxWidth: 200 }}>
                  <small>Minimum deposit (₹)</small>
                  <input
                    type="number"
                    min={0}
                    placeholder="e.g. 50000"
                    value={newBenefitMinDeposit}
                    onChange={(e) => setNewBenefitMinDeposit(e.target.value)}
                  />
                </div>
              </div>
            )}
          </form>
          {addBenefitError && <div className="error">{addBenefitError}</div>}
        </div>
      </div>
    </div>
  )
}

/** Only ever rendered for a Super Admin (see Settings) — the backend independently enforces the
 *  same restriction in AdminAuthServiceImpl#register, so this is UX, not the actual guard. */
function CreateAdminModal({ onClose, onCreated }: { onClose: () => void; onCreated: (email: string) => void }) {
  const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState('')
  const [email, setEmail] = useState('')
  const [mobileNumber, setMobileNumber] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [role, setRole] = useState<AdminRole>('ADMIN')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true); setError('')
    try {
      await createAdmin({ firstName, lastName, email, mobileNumber, password, confirmPassword, adminRole: role })
      onCreated(email.trim().toLowerCase())
      onClose()
    } catch (e2) { setError(errorMessage(e2)) } finally { setBusy(false) }
  }

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-card" style={{ maxWidth: 440 }} onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <h3>Create Admin</h3>
          <button type="button" className="modal-close" onClick={onClose} aria-label="Close">✕</button>
        </div>
        <div className="modal-body">
          <form onSubmit={submit} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            <div style={{ display: 'flex', gap: 10 }}>
              <div className="fgroup" style={{ flex: 1 }}>
                <small>First name</small>
                <input value={firstName} onChange={(e) => setFirstName(e.target.value)} required />
              </div>
              <div className="fgroup" style={{ flex: 1 }}>
                <small>Last name</small>
                <input value={lastName} onChange={(e) => setLastName(e.target.value)} required />
              </div>
            </div>
            <div className="fgroup">
              <small>Email</small>
              <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
            </div>
            <div className="fgroup">
              <small>Mobile number</small>
              <input value={mobileNumber} onChange={(e) => setMobileNumber(e.target.value)} placeholder="9876543210" required />
            </div>
            <div style={{ display: 'flex', gap: 10 }}>
              <div className="fgroup" style={{ flex: 1 }}>
                <small>Password</small>
                <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
              </div>
              <div className="fgroup" style={{ flex: 1 }}>
                <small>Confirm password</small>
                <input type="password" value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)} required />
              </div>
            </div>
            <div className="fgroup">
              <small>Role</small>
              <div style={{ display: 'flex', gap: 16, marginTop: 4 }}>
                <label style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 13 }}>
                  <input type="radio" name="createAdminRole" checked={role === 'ADMIN'} onChange={() => setRole('ADMIN')} />
                  Admin
                </label>
                <label style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 13 }}>
                  <input type="radio" name="createAdminRole" checked={role === 'SUPER_ADMIN'} onChange={() => setRole('SUPER_ADMIN')} />
                  Super Admin
                </label>
              </div>
              <p className="muted" style={{ fontSize: 11.5, marginTop: 4 }}>
                Super Admins can create other admin accounts; regular Admins can't.
              </p>
            </div>
            {error && <div className="error">{error}</div>}
            <button type="submit" className="btn block" disabled={busy}>{busy ? 'Creating…' : 'Create Admin'}</button>
          </form>
        </div>
      </div>
    </div>
  )
}
