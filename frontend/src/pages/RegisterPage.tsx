import { useState, type FormEvent } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { register, verifyEmailOtp, errorMessage } from '../api'

export default function RegisterPage() {
  const navigate = useNavigate()
  const [step, setStep] = useState<'form' | 'otp'>('form')
  const [email, setEmail] = useState('')
  const [mobileNumber, setMobileNumber] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [otp, setOtp] = useState('')
  const [error, setError] = useState('')
  const [ok, setOk] = useState('')
  const [busy, setBusy] = useState(false)

  const submitForm = async (e: FormEvent) => {
    e.preventDefault()
    setError(''); setOk(''); setBusy(true)
    try {
      const msg = await register({ email, mobileNumber, password, confirmPassword })
      setOk(msg + ' Enter the OTP sent to your email.')
      setStep('otp')
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  const submitOtp = async (e: FormEvent) => {
    e.preventDefault()
    setError(''); setOk(''); setBusy(true)
    try {
      await verifyEmailOtp(email, otp)
      setOk('Email verified! You can sign in now.')
      setTimeout(() => navigate('/login'), 1200)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  if (step === 'otp') {
    return (
      <form className="form card" onSubmit={submitOtp}>
        <h1>Verify email</h1>
        <p className="muted">We sent a one-time code to {email}.</p>
        <label>OTP</label>
        <input value={otp} onChange={(e) => setOtp(e.target.value)} placeholder="6-digit code" />
        {ok && <div className="ok">{ok}</div>}
        {error && <div className="error">{error}</div>}
        <div style={{ marginTop: 18 }}>
          <button className="btn block" disabled={busy}>{busy ? 'Verifying…' : 'Verify'}</button>
        </div>
      </form>
    )
  }

  return (
    <form className="form card" onSubmit={submitForm}>
      <h1>Create account</h1>
      <label>Email</label>
      <input value={email} onChange={(e) => setEmail(e.target.value)} placeholder="you@example.com" />
      <label>Mobile number</label>
      <input value={mobileNumber} onChange={(e) => setMobileNumber(e.target.value)} placeholder="9876543210" />
      <label>Password</label>
      <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
      <label>Confirm password</label>
      <input type="password" value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)} />
      {ok && <div className="ok">{ok}</div>}
      {error && <div className="error">{error}</div>}
      <div style={{ marginTop: 18 }}>
        <button className="btn block" disabled={busy}>{busy ? 'Creating…' : 'Register'}</button>
      </div>
      <p className="muted" style={{ marginTop: 16 }}>Have an account? <Link to="/login">Sign in</Link></p>
    </form>
  )
}
