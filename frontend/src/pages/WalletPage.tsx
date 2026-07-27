import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import {
  topUp, withdraw, getSellerStripeStatus, createSellerOnboardingLink,
  refreshSellerStripeStatus, errorMessage, type SellerStripeStatus,
} from '../api'
import { useAuth } from '../auth'
import { useWallet } from '../WalletContext'
import { money, moneyCompact } from '../util'
import StripeTopUpForm from '../components/StripeTopUpForm'

export default function WalletPage() {
  const { isAuthenticated } = useAuth()
  const { wallet, refreshWallet } = useWallet()
  const [error, setError] = useState('')

  // Dev-only instant credit (bypasses Stripe) — kept for quick local/demo testing.
  const [devAmount, setDevAmount] = useState('10000')
  const [devBusy, setDevBusy] = useState(false)

  // Real Stripe top-up.
  const [payAmount, setPayAmount] = useState('1000')
  const [checkoutAmount, setCheckoutAmount] = useState<number | null>(null)
  const [payMsg, setPayMsg] = useState('')

  // Seller payouts (Stripe Connect + withdraw).
  const [stripeStatus, setStripeStatus] = useState<SellerStripeStatus | null>(null)
  const [connectBusy, setConnectBusy] = useState(false)
  const [withdrawAmount, setWithdrawAmount] = useState('1000')
  const [withdrawBusy, setWithdrawBusy] = useState(false)
  const [withdrawMsg, setWithdrawMsg] = useState('')

  useEffect(() => {
    if (!isAuthenticated) return
    getSellerStripeStatus().then(setStripeStatus).catch(() => {})
    // Returning from Stripe's onboarding redirect (?connect=return) — re-check status.
    if (new URLSearchParams(window.location.search).get('connect') === 'return') {
      refreshSellerStripeStatus().then(setStripeStatus).catch(() => {})
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isAuthenticated])

  const submitDev = async (e: FormEvent) => {
    e.preventDefault(); setError(''); setDevBusy(true)
    try { await topUp(Number(devAmount)); refreshWallet() }
    catch (err) { setError(errorMessage(err)) } finally { setDevBusy(false) }
  }

  const startCheckout = (e: FormEvent) => {
    e.preventDefault(); setPayMsg('')
    setCheckoutAmount(Number(payAmount))
  }

  const onCheckoutDone = (msg: string, ok: boolean) => {
    setPayMsg(msg)
    setCheckoutAmount(null)
    if (ok) setTimeout(refreshWallet, 2000)
  }

  const connectPayouts = async () => {
    setConnectBusy(true); setError('')
    try {
      const url = await createSellerOnboardingLink()
      window.location.href = url
    } catch (err) { setError(errorMessage(err)); setConnectBusy(false) }
  }

  const submitWithdraw = async (e: FormEvent) => {
    e.preventDefault(); setWithdrawMsg(''); setWithdrawBusy(true)
    try {
      const res = await withdraw(Number(withdrawAmount))
      refreshWallet()
      setWithdrawMsg(`Withdrawal ${res.status.toLowerCase()}.`)
    } catch (err) { setWithdrawMsg(errorMessage(err)) } finally { setWithdrawBusy(false) }
  }

  if (!isAuthenticated) {
    return <div className="container"><div className="card" style={{ maxWidth: 460 }}>Please <Link to="/login">sign in</Link> to view your wallet.</div></div>
  }

  return (
    <div className="container">
      <div className="section-head">
        <h1 className="page">Wallet</h1>
      </div>
      <div className="card" style={{ maxWidth: 460 }}>
        <span className="eyebrow">Your Wallet</span>
        <div className="stat" style={{ marginTop: 16 }}>
          <div><div className="k">Available</div><div className="v">{money(wallet?.availableBalance)}</div></div>
          <div><div className="k">Held (EMD)</div><div className="v">{money(wallet?.heldBalance)}</div></div>
          <div><div className="k">Credit Limit</div><div className="v">{moneyCompact(wallet?.creditLimit)}</div></div>
        </div>
        {error && <div className="error" style={{ marginTop: 12 }}>{error}</div>}

        <div style={{ marginTop: 20, paddingTop: 16, borderTop: '1px dashed var(--border)' }}>
          <label>Add funds with card</label>
          {checkoutAmount == null ? (
            <form onSubmit={startCheckout}>
              <input style={{ width: '100%' }} type="number" value={payAmount} onChange={(e) => setPayAmount(e.target.value)} min="1" />
              <div style={{ marginTop: 10 }}><button className="btn" disabled={Number(payAmount) <= 0}>Continue to payment</button></div>
            </form>
          ) : (
            <div style={{ marginTop: 10 }}>
              <StripeTopUpForm amount={checkoutAmount} onDone={onCheckoutDone} />
              <button type="button" className="btn ghost sm" style={{ marginTop: 10 }} onClick={() => setCheckoutAmount(null)}>Cancel</button>
            </div>
          )}
          {payMsg && <div className={payMsg.toLowerCase().includes('fail') ? 'error' : 'ok'} style={{ marginTop: 10 }}>{payMsg}</div>}
        </div>

        <div style={{ marginTop: 20, paddingTop: 16, borderTop: '1px dashed var(--border)' }}>
          <form onSubmit={submitDev}>
            <label>Dev top-up (testing only, bypasses Stripe)</label>
            <input style={{ width: '100%' }} type="number" value={devAmount} onChange={(e) => setDevAmount(e.target.value)} min="1" />
            <div style={{ marginTop: 10 }}><button className="btn ghost" disabled={devBusy}>{devBusy ? 'Adding…' : 'Add dev funds'}</button></div>
          </form>
        </div>
      </div>

      <div className="card" style={{ maxWidth: 460, marginTop: 20 }}>
        <span className="eyebrow">Payouts (sellers &amp; dealers)</span>
        <p className="muted" style={{ marginTop: 10 }}>
          Withdraw your available balance to a real bank account via Stripe Connect.
        </p>
        {stripeStatus?.payoutsEnabled ? (
          <div className="ok" style={{ marginTop: 10 }}>Payout account connected and verified.</div>
        ) : (
          <>
            <div className="muted" style={{ marginTop: 10 }}>
              {stripeStatus?.connected ? 'Onboarding started — finish it with Stripe to enable payouts.' : 'Not connected yet.'}
            </div>
            <button type="button" className="btn sm" style={{ marginTop: 10 }} disabled={connectBusy} onClick={connectPayouts}>
              {connectBusy ? 'Redirecting…' : stripeStatus?.connected ? 'Continue onboarding' : 'Connect payout account'}
            </button>
          </>
        )}

        <form onSubmit={submitWithdraw} style={{ marginTop: 16 }}>
          <label>Withdraw amount</label>
          <input style={{ width: '100%' }} type="number" value={withdrawAmount} onChange={(e) => setWithdrawAmount(e.target.value)} min="1" />
          <div style={{ marginTop: 10 }}>
            <button className="btn" disabled={withdrawBusy || !stripeStatus?.payoutsEnabled}>{withdrawBusy ? 'Withdrawing…' : 'Withdraw'}</button>
          </div>
        </form>
        {withdrawMsg && <div className={withdrawMsg.toLowerCase().includes('fail') ? 'error' : 'ok'} style={{ marginTop: 10 }}>{withdrawMsg}</div>}
      </div>
    </div>
  )
}
