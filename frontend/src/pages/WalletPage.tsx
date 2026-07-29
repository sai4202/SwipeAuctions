import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import {
  topUp, withdraw, getSellerStripeStatus, createSellerOnboardingLink,
  refreshSellerStripeStatus, errorMessage, type SellerStripeStatus,
} from '../api'
import { useAuth } from '../auth'
import { useWallet } from '../WalletContext'
import { useReveal } from '../useReveal'
import { money, moneyCompact } from '../util'
import StripeTopUpForm from '../components/StripeTopUpForm'

/** Counts a displayed value smoothly toward `value` over ~700ms instead of snapping — makes a
 *  balance update (top-up landing, a hold releasing) read as something real happening to your
 *  money rather than a number silently being swapped out. The very first real value is shown
 *  immediately (nothing to animate from yet). */
function useAnimatedNumber(value: number | null | undefined): number | undefined {
  const [display, setDisplay] = useState<number | undefined>(value ?? undefined)
  const prevRef = useRef<number | undefined>(value ?? undefined)
  const rafRef = useRef<number | undefined>(undefined)

  useEffect(() => {
    if (value == null) return
    const from = prevRef.current
    if (from == null || from === value) {
      setDisplay(value)
      prevRef.current = value
      return
    }
    const start = performance.now()
    const duration = 700
    const step = (now: number) => {
      const t = Math.min(1, (now - start) / duration)
      const eased = 1 - Math.pow(1 - t, 3)
      setDisplay(Math.round(from + (value - from) * eased))
      if (t < 1) {
        rafRef.current = requestAnimationFrame(step)
      } else {
        prevRef.current = value
      }
    }
    rafRef.current = requestAnimationFrame(step)
    return () => { if (rafRef.current) cancelAnimationFrame(rafRef.current) }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value])

  return display
}

/** Briefly true right after `value` changes (skips the initial mount) — drives the `.flash`
 *  highlight on a stat tile so a balance/hold change is noticeable, not just eventually correct. */
function useChangeFlash(value: number | null | undefined): boolean {
  const [flash, setFlash] = useState(false)
  const prevRef = useRef<number | null | undefined>(value)
  useEffect(() => {
    if (value != null && prevRef.current != null && value !== prevRef.current) {
      setFlash(true)
      const t = setTimeout(() => setFlash(false), 800)
      prevRef.current = value
      return () => clearTimeout(t)
    }
    prevRef.current = value
  }, [value])
  return flash
}

export default function WalletPage() {
  const { isAuthenticated } = useAuth()
  const { wallet, refreshWallet } = useWallet()
  const [error, setError] = useState('')
  useReveal()

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

  const displayAvailable = useAnimatedNumber(wallet?.availableBalance)
  const displayHeld = useAnimatedNumber(wallet?.heldBalance)
  const displayCredit = useAnimatedNumber(wallet?.availableCreditLimit)
  const availableFlash = useChangeFlash(wallet?.availableBalance)
  const heldFlash = useChangeFlash(wallet?.heldBalance)

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
        <div>
          <span className="eyebrow">Secure · Escrow-backed</span>
          <h1 className="page">Wallet</h1>
        </div>
      </div>

      <div className="wallet-layout">
        <div className="wallet-main">
          <div className="card wallet-hero" data-reveal>
            <div className="wallet-hero-top">
              <span className="eyebrow">Your Wallet</span>
              <span className="verified-badge"><span className="dot" />Live balance</span>
            </div>
            <div className="wallet-stats">
              <div className={`stat-tile ${availableFlash ? 'flash' : ''}`}>
                <div className="k">Available</div>
                <div className="v">{money(displayAvailable)}</div>
              </div>
              <div className={`stat-tile ${heldFlash ? 'flash' : ''}`}>
                <div className="k">Held</div>
                <div className="v">{money(displayHeld)}</div>
              </div>
              <div className="stat-tile wallet-stat-credit">
                <div className="k">Credit Limit</div>
                <div className="v">{moneyCompact(displayCredit)}</div>
              </div>
            </div>
            {wallet && wallet.creditLimit !== wallet.availableCreditLimit && (
              <p className="muted" style={{ marginTop: 10, marginBottom: 0, fontSize: 13 }}>
                {moneyCompact(wallet.creditLimit)} total, {moneyCompact(wallet.creditLimit - wallet.availableCreditLimit)} currently committed to your other open bids.
              </p>
            )}
            <div className="wallet-trust-row">
              <div className="trust-item"><span className="trust-icon">🔒</span><span className="trust-label">Bank-grade security</span></div>
              <div className="trust-item"><span className="trust-icon">🛡️</span><span className="trust-label">Escrow protected</span></div>
              <div className="trust-item"><span className="trust-icon">⚡</span><span className="trust-label">Instant top-up</span></div>
            </div>
            {error && <div className="error" style={{ marginTop: 12, marginBottom: 0 }}>{error}</div>}
          </div>

          <div className="card" data-reveal>
            <span className="eyebrow">Add Funds</span>
            <div className="wallet-subsection">
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

            <div className="wallet-subsection">
              <form onSubmit={submitDev}>
                <label>Dev top-up (testing only, bypasses Stripe)</label>
                <input style={{ width: '100%' }} type="number" value={devAmount} onChange={(e) => setDevAmount(e.target.value)} min="1" />
                <div style={{ marginTop: 10 }}><button className="btn ghost" disabled={devBusy}>{devBusy ? 'Adding…' : 'Add dev funds'}</button></div>
              </form>
            </div>
          </div>

          <div className="card" data-reveal>
            <span className="eyebrow">How your credit limit works</span>
            <ul className="wallet-explainer-list">
              <li>
                <span className="wallet-explainer-icon">📊</span>
                <span><b>Capped exposure.</b> Your credit limit is a shared cap across every auction you're bidding on at once — you can never commit more than your limit in total, no matter how many items you're active on.</span>
              </li>
              <li>
                <span className="wallet-explainer-icon">🔄</span>
                <span><b>Auto-released.</b> The moment an auction you didn't win closes, whatever you'd committed to it frees back up on its own — no request or wait required.</span>
              </li>
              <li>
                <span className="wallet-explainer-icon">🛡️</span>
                <span><b>Escrow settlement.</b> Funds only leave your wallet for real if you win — held in escrow until handover is confirmed.</span>
              </li>
            </ul>
          </div>
        </div>

        <div className="wallet-side">
          <div className="card" data-reveal>
            <span className="eyebrow">Payouts (sellers &amp; dealers)</span>
            <p className="muted" style={{ marginTop: 10 }}>
              Withdraw your available balance to a real bank account via Stripe Connect.
            </p>
            {stripeStatus?.payoutsEnabled ? (
              <span className="verified-badge" style={{ marginTop: 10 }}><span className="dot" />Payout account connected &amp; verified</span>
            ) : (
              <>
                <span className="verified-badge pending" style={{ marginTop: 10 }}>
                  <span className="dot" />{stripeStatus?.connected ? 'Onboarding started' : 'Not connected yet'}
                </span>
                <div style={{ marginTop: 10 }}>
                  <button type="button" className="btn sm" disabled={connectBusy} onClick={connectPayouts}>
                    {connectBusy ? 'Redirecting…' : stripeStatus?.connected ? 'Continue onboarding' : 'Connect payout account'}
                  </button>
                </div>
              </>
            )}

            <div className="wallet-subsection">
              <form onSubmit={submitWithdraw}>
                <label>Withdraw amount</label>
                <input style={{ width: '100%' }} type="number" value={withdrawAmount} onChange={(e) => setWithdrawAmount(e.target.value)} min="1" />
                <div style={{ marginTop: 10 }}>
                  <button className="btn" disabled={withdrawBusy || !stripeStatus?.payoutsEnabled}>{withdrawBusy ? 'Withdrawing…' : 'Withdraw'}</button>
                </div>
              </form>
              {withdrawMsg && <div className={withdrawMsg.toLowerCase().includes('fail') ? 'error' : 'ok'} style={{ marginTop: 10 }}>{withdrawMsg}</div>}
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
