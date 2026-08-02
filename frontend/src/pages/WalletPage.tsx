import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useLocation } from 'react-router-dom'
import {
  createTopUpOrder, verifyTopUp, withdraw, getSellerPayoutStatus, saveSellerPayoutAccount,
  errorMessage, type SellerPayoutStatus, type OrderIntent,
} from '../api'
import { useAuth } from '../auth'
import { useWallet } from '../WalletContext'
import { useReveal } from '../useReveal'
import { money, moneyCompact } from '../util'
import RazorpayCheckout from '../components/RazorpayCheckout'

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
  const location = useLocation()
  useReveal()

  // Deep-linked from the header's Credit Limit dropdown (Top Up / Withdraw buttons) — jump straight
  // to the relevant section instead of leaving the visitor to scroll and find it themselves.
  useEffect(() => {
    if (!location.hash) return
    const el = document.querySelector(location.hash)
    el?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }, [location.hash])

  // Real Razorpay top-up.
  const [payAmount, setPayAmount] = useState('1000')
  const [checkoutOrder, setCheckoutOrder] = useState<OrderIntent | null>(null)
  const [payBusy, setPayBusy] = useState(false)
  const [payMsg, setPayMsg] = useState('')

  // Seller payouts (Razorpay bank account + withdraw).
  const [payoutStatus, setPayoutStatus] = useState<SellerPayoutStatus | null>(null)
  const [accountHolderName, setAccountHolderName] = useState('')
  const [accountNumber, setAccountNumber] = useState('')
  const [ifsc, setIfsc] = useState('')
  const [accountBusy, setAccountBusy] = useState(false)
  const [accountMsg, setAccountMsg] = useState('')
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
    getSellerPayoutStatus().then(setPayoutStatus).catch(() => {})
  }, [isAuthenticated])

  const startCheckout = async (e: FormEvent) => {
    e.preventDefault(); setPayMsg(''); setPayBusy(true)
    try { setCheckoutOrder(await createTopUpOrder(Number(payAmount))) }
    catch (err) { setPayMsg(errorMessage(err)) } finally { setPayBusy(false) }
  }

  const onCheckoutSuccess = async (paymentId: string, signature: string) => {
    if (!checkoutOrder) return
    try {
      await verifyTopUp(checkoutOrder.orderId, paymentId, signature)
      setPayMsg('Payment successful — your wallet has been credited.')
      refreshWallet()
    } catch (err) { setPayMsg(errorMessage(err)) } finally { setCheckoutOrder(null) }
  }

  const onCheckoutCancel = () => setCheckoutOrder(null)

  const submitPayoutAccount = async (e: FormEvent) => {
    e.preventDefault(); setAccountMsg(''); setAccountBusy(true)
    try {
      setPayoutStatus(await saveSellerPayoutAccount(accountHolderName, accountNumber, ifsc))
      setAccountMsg('Payout bank account saved.')
    } catch (err) { setAccountMsg(errorMessage(err)) } finally { setAccountBusy(false) }
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
                <div className="k">Your Deposit</div>
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
          </div>

          <div className="card" data-reveal id="topup">
            <span className="eyebrow">Add Funds</span>
            <div className="wallet-subsection">
              <label>Add funds with card / UPI / netbanking</label>
              {checkoutOrder == null ? (
                <form onSubmit={startCheckout}>
                  <input style={{ width: '100%' }} type="number" value={payAmount} onChange={(e) => setPayAmount(e.target.value)} min="1" />
                  <div style={{ marginTop: 10 }}>
                    <button className="btn" disabled={payBusy || Number(payAmount) <= 0}>{payBusy ? 'Starting…' : 'Continue to payment'}</button>
                  </div>
                </form>
              ) : (
                <div style={{ marginTop: 10 }}>
                  <RazorpayCheckout
                    orderId={checkoutOrder.orderId} amountPaise={checkoutOrder.amountPaise}
                    currency={checkoutOrder.currency} keyId={checkoutOrder.keyId}
                    description="Wallet top-up" onSuccess={onCheckoutSuccess} onCancel={onCheckoutCancel}
                  />
                </div>
              )}
              {payMsg && <div className={payMsg.toLowerCase().includes('fail') ? 'error' : 'ok'} style={{ marginTop: 10 }}>{payMsg}</div>}
            </div>
          </div>
        </div>

        <div className="wallet-side">
          <div className="card" data-reveal>
            <span className="eyebrow">Payouts (sellers &amp; dealers)</span>
            <p className="muted" style={{ marginTop: 10 }}>
              Withdraw your available balance to a real bank account via Razorpay.
            </p>
            {payoutStatus?.payoutsEnabled ? (
              <span className="verified-badge" style={{ marginTop: 10 }}><span className="dot" />Payout account saved</span>
            ) : (
              <>
                <span className="verified-badge pending" style={{ marginTop: 10 }}>
                  <span className="dot" />Not set up yet
                </span>
                <form onSubmit={submitPayoutAccount} className="wallet-subsection">
                  <label>Account holder name</label>
                  <input style={{ width: '100%' }} value={accountHolderName} onChange={(e) => setAccountHolderName(e.target.value)} required />
                  <label style={{ marginTop: 8 }}>Account number</label>
                  <input style={{ width: '100%' }} value={accountNumber} onChange={(e) => setAccountNumber(e.target.value)} required />
                  <label style={{ marginTop: 8 }}>IFSC code</label>
                  <input style={{ width: '100%' }} value={ifsc} onChange={(e) => setIfsc(e.target.value)} required />
                  <div style={{ marginTop: 10 }}>
                    <button type="submit" className="btn sm" disabled={accountBusy}>{accountBusy ? 'Saving…' : 'Save payout account'}</button>
                  </div>
                  {accountMsg && <div className={accountMsg.toLowerCase().includes('fail') || accountMsg.toLowerCase().includes('not configured') ? 'error' : 'ok'} style={{ marginTop: 10 }}>{accountMsg}</div>}
                </form>
              </>
            )}

            <div className="wallet-subsection" id="withdraw">
              {wallet && wallet.creditHeldAmount > 0 && (
                <p className="muted" style={{ fontSize: 12.5, marginBottom: 8 }}>
                  {money(wallet.creditHeldAmount)} of your deposit is held against currently committed bidding
                  credit — {money(wallet.withdrawableBalance)} is available to withdraw right now.
                </p>
              )}
              <form onSubmit={submitWithdraw}>
                <label>Withdraw amount</label>
                <input style={{ width: '100%' }} type="number" value={withdrawAmount} onChange={(e) => setWithdrawAmount(e.target.value)} min="1"
                  max={wallet?.withdrawableBalance} />
                <div style={{ marginTop: 10 }}>
                  <button className="btn" disabled={withdrawBusy || !payoutStatus?.payoutsEnabled}>{withdrawBusy ? 'Withdrawing…' : 'Withdraw'}</button>
                </div>
              </form>
              {withdrawMsg && <div className={withdrawMsg.toLowerCase().includes('fail') ? 'error' : 'ok'} style={{ marginTop: 10 }}>{withdrawMsg}</div>}
            </div>
          </div>
        </div>
      </div>

      {/* Full width, below the grid — the Payouts column above is much shorter than the wallet-main
          column next to it, so a card confined to that narrow column would leave the whole right
          side empty. This one spans the page and lays its 3 points out side by side instead. */}
      <div className="card wallet-explainer-full" data-reveal>
        <span className="eyebrow">How your credit limit works</span>
        <ul className="wallet-explainer-list wallet-explainer-list-row">
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
  )
}
