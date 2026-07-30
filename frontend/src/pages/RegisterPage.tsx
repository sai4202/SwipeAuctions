import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate, useLocation, Link } from 'react-router-dom'
import {
  register, verifyEmailOtp, verifyMobileOtp, resendOtp, getRegistrationFee, login,
  createRegistrationFeeOrder, verifyRegistrationFee, errorMessage, type OrderIntent,
} from '../api'
import { useAuth } from '../auth'
import { money } from '../util'
import RazorpayCheckout from '../components/RazorpayCheckout'

const RESEND_COOLDOWN_SECONDS = 30

export default function RegisterPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const { signIn, markRegistrationFeePaid } = useAuth()
  // Anonymous visitors bounced here from an auction detail page (RequireAuth redirectTo="/register")
  // carry the page they were headed to — send them back there once the fee is paid.
  const returnTo = (location.state as { from?: string } | null)?.from
  const [step, setStep] = useState<'form' | 'email-otp' | 'mobile-otp' | 'pay-fee'>('form')
  const [email, setEmail] = useState('')
  const [mobileNumber, setMobileNumber] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [otp, setOtp] = useState('')
  const [error, setError] = useState('')
  const [ok, setOk] = useState('')
  const [busy, setBusy] = useState(false)
  const [resendIn, setResendIn] = useState(RESEND_COOLDOWN_SECONDS)
  const [resending, setResending] = useState(false)
  const [unverifiedExisting, setUnverifiedExisting] = useState(false)
  const [registrationFee, setRegistrationFee] = useState<number | null>(null)
  const [feeOrder, setFeeOrder] = useState<OrderIntent | null>(null)

  useEffect(() => {
    getRegistrationFee().then(setRegistrationFee).catch(() => {})
  }, [])

  useEffect(() => {
    if (step === 'form' || resendIn <= 0) return
    const timer = setInterval(() => setResendIn((s) => s - 1), 1000)
    return () => clearInterval(timer)
  }, [step, resendIn])

  const submitForm = async (e: FormEvent) => {
    e.preventDefault(); setError(''); setOk(''); setUnverifiedExisting(false)
    if (password !== confirmPassword) {
      setError('Password and Confirm Password do not match')
      return
    }
    setBusy(true)
    try {
      const msg = await register({ email, mobileNumber, password, confirmPassword })
      setOk(msg + ' Enter the OTP sent to your email.')
      setOtp('')
      setResendIn(RESEND_COOLDOWN_SECONDS)
      setStep('email-otp')
    } catch (err) {
      const msg = errorMessage(err)
      setError(msg)
      setUnverifiedExisting(msg.includes('not verified'))
    } finally { setBusy(false) }
  }

  const resumeVerification = async () => {
    setError(''); setOk(''); setResending(true)
    try {
      const msg = await resendOtp(email)
      setOk(msg)
      setOtp('')
      setResendIn(RESEND_COOLDOWN_SECONDS)
      setStep('email-otp')
    } catch (err) { setError(errorMessage(err)) } finally { setResending(false) }
  }

  const submitEmailOtp = async (e: FormEvent) => {
    e.preventDefault(); setError(''); setOk(''); setBusy(true)
    try {
      await verifyEmailOtp(email, otp)
      setOk('Email verified! Enter the OTP sent to your mobile number.')
      setOtp('')
      setResendIn(RESEND_COOLDOWN_SECONDS)
      setStep('mobile-otp')
    } catch (err) { setError(errorMessage(err)) } finally { setBusy(false) }
  }

  const submitMobileOtp = async (e: FormEvent) => {
    e.preventDefault(); setError(''); setOk(''); setBusy(true)
    try {
      await verifyMobileOtp(email, otp)
      // Sign in immediately (both OTPs are now verified, so the account is active) so the final
      // "pay the registration fee" step can call an authenticated endpoint, instead of making the
      // user log in separately just to reach it.
      const data = await login(email, password)
      if (data.deviceLimitReached) {
        setOk('Mobile verified! Redirecting to sign in…')
        setTimeout(() => navigate('/login'), 1200)
        return
      }
      signIn({
        token: data.token, email: data.email, role: data.role, kycCompleted: data.kycCompleted,
        registrationFeePaid: data.registrationFeePaid,
        subscriptionTier: data.subscriptionTier, subscriptionExpiresAt: data.subscriptionExpiresAt,
      })
      setOk('')
      setStep('pay-fee')
    } catch (err) { setError(errorMessage(err)) } finally { setBusy(false) }
  }

  const doPayFee = async () => {
    setError(''); setOk(''); setBusy(true)
    try { setFeeOrder(await createRegistrationFeeOrder()) }
    catch (err) { setError(errorMessage(err)) } finally { setBusy(false) }
  }

  const onFeePaymentSuccess = async (paymentId: string, signature: string) => {
    if (!feeOrder) return
    setError(''); setBusy(true)
    try {
      await verifyRegistrationFee(feeOrder.orderId, paymentId, signature)
      markRegistrationFeePaid()
      navigate(returnTo || '/')
    } catch (err) { setError(errorMessage(err)) } finally { setBusy(false); setFeeOrder(null) }
  }

  const onFeePaymentCancel = () => setFeeOrder(null)

  const handleResend = async () => {
    setError(''); setOk(''); setResending(true)
    try {
      const msg = await resendOtp(email)
      setOk(msg)
      setResendIn(RESEND_COOLDOWN_SECONDS)
    } catch (err) { setError(errorMessage(err)) } finally { setResending(false) }
  }

  return (
    <div className="container">
      <div className="card" style={{ maxWidth: 420, margin: '10px auto' }}>
        {step === 'form' && (
          <form onSubmit={submitForm}>
            <span className="eyebrow">Join SwipeAuctions</span>
            <h1 className="page" style={{ marginTop: 12 }}>Create account</h1>
            {!!registrationFee && (
              <p className="muted" style={{ fontSize: 13 }}>
                A one-time registration fee of {money(registrationFee)} applies to new accounts.
              </p>
            )}
            <label>Email</label>
            <input style={{ width: '100%' }} value={email} onChange={(e) => setEmail(e.target.value)} placeholder="you@example.com" />
            <label>Mobile number</label>
            <input style={{ width: '100%' }} value={mobileNumber} onChange={(e) => setMobileNumber(e.target.value)} placeholder="9876543210" />
            <label>Password</label>
            <input style={{ width: '100%' }} type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
            <label>Confirm password</label>
            <input style={{ width: '100%' }} type="password" value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)} />
            {ok && <div className="ok">{ok}</div>}
            {error && <div className="error">{error}</div>}
            {unverifiedExisting && (
              <p className="muted" style={{ marginTop: 8, fontSize: 13 }}>
                <button type="button" className="linkbtn" onClick={resumeVerification} disabled={resending}>
                  {resending ? 'Sending…' : 'Resend OTP and continue verifying that account'}
                </button>
              </p>
            )}
            <div style={{ marginTop: 18 }}><button className="btn block" disabled={busy}>{busy ? 'Creating…' : 'Register'}</button></div>
            <p className="muted" style={{ marginTop: 16 }}>Have an account? <Link to="/login">Sign in</Link></p>
          </form>
        )}
        {step === 'email-otp' && (
          <form onSubmit={submitEmailOtp}>
            <h1 className="page">Verify email</h1>
            <p className="muted">We sent a one-time code to {email}.</p>
            <label>OTP</label>
            <input style={{ width: '100%' }} value={otp} onChange={(e) => setOtp(e.target.value)} placeholder="6-digit code" />
            {ok && <div className="ok">{ok}</div>}
            {error && <div className="error">{error}</div>}
            <div style={{ marginTop: 18 }}><button className="btn block" disabled={busy}>{busy ? 'Verifying…' : 'Verify'}</button></div>
            <p className="muted" style={{ marginTop: 12, fontSize: 13 }}>
              {resendIn > 0
                ? <>Resend OTP in {resendIn}s</>
                : <button type="button" className="linkbtn" onClick={handleResend} disabled={resending}>{resending ? 'Resending…' : 'Resend OTP'}</button>}
            </p>
          </form>
        )}
        {step === 'mobile-otp' && (
          <form onSubmit={submitMobileOtp}>
            <h1 className="page">Verify mobile number</h1>
            <p className="muted">We sent a one-time code to {mobileNumber}.</p>
            <label>OTP</label>
            <input style={{ width: '100%' }} value={otp} onChange={(e) => setOtp(e.target.value)} placeholder="6-digit code" />
            {ok && <div className="ok">{ok}</div>}
            {error && <div className="error">{error}</div>}
            <div style={{ marginTop: 18 }}><button className="btn block" disabled={busy}>{busy ? 'Verifying…' : 'Verify'}</button></div>
            <p className="muted" style={{ marginTop: 12, fontSize: 13 }}>
              {resendIn > 0
                ? <>Resend OTP in {resendIn}s</>
                : <button type="button" className="linkbtn" onClick={handleResend} disabled={resending}>{resending ? 'Resending…' : 'Resend OTP'}</button>}
            </p>
          </form>
        )}
        {step === 'pay-fee' && (
          <div>
            <h1 className="page">Complete registration</h1>
            <p className="muted">
              {registrationFee
                ? `Pay a one-time registration fee of ${money(registrationFee)} to activate your account and start browsing and bidding.`
                : 'Complete your one-time registration payment to activate your account.'}
            </p>
            {error && <div className="error">{error}</div>}
            {feeOrder == null ? (
              <div style={{ marginTop: 18 }}>
                <button className="btn block" disabled={busy} onClick={doPayFee}>
                  {busy ? 'Starting…' : `Pay ${registrationFee ? money(registrationFee) : ''} to complete registration`}
                </button>
              </div>
            ) : (
              <div style={{ marginTop: 18 }}>
                <RazorpayCheckout
                  orderId={feeOrder.orderId} amountPaise={feeOrder.amountPaise}
                  currency={feeOrder.currency} keyId={feeOrder.keyId}
                  description="One-time registration fee" onSuccess={onFeePaymentSuccess} onCancel={onFeePaymentCancel}
                />
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}
