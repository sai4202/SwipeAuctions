import { useState, type FormEvent } from 'react'
import { useNavigate, useLocation, Link } from 'react-router-dom'
import { login, requestLoginOtp, verifyLoginOtp, logoutDevice, errorMessage, type LoginData, type SessionInfo } from '../api'
import { useAuth } from '../auth'

type Mode = 'password' | 'otp'

function formatWhen(iso: string | null): string {
  if (!iso) return '—'
  try { return new Date(iso).toLocaleString() } catch { return iso }
}

export default function LoginPage() {
  const { signIn } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  // RequireAuth (App.tsx) sends anonymous visitors here with the page they were trying to reach —
  // land them back there instead of always dumping them on /auctions after signing in.
  const returnTo = (location.state as { from?: string } | null)?.from
  const [mode, setMode] = useState<Mode>('password')
  const [identifier, setIdentifier] = useState('')
  const [password, setPassword] = useState('')
  const [otpStep, setOtpStep] = useState<'identifier' | 'code'>('identifier')
  const [otp, setOtp] = useState('')
  const [error, setError] = useState('')
  const [ok, setOk] = useState('')
  const [busy, setBusy] = useState(false)

  // Populated when the backend reports the device-limit is hit: the user picks one to log out.
  const [deviceChoice, setDeviceChoice] = useState<SessionInfo[] | null>(null)
  const [limitMsg, setLimitMsg] = useState('')
  const [loggingOutId, setLoggingOutId] = useState<string | null>(null)

  const switchMode = (m: Mode) => { setMode(m); setError(''); setOk(''); setOtpStep('identifier'); setOtp(''); setDeviceChoice(null) }

  const completeSignIn = (data: LoginData) => {
    if (data.role === 'DEALER') {
      setError("Dealer accounts aren't supported here.")
      return
    }
    signIn({
      token: data.token, email: data.email, role: data.role, kycCompleted: data.kycCompleted,
      subscriptionTier: data.subscriptionTier, subscriptionExpiresAt: data.subscriptionExpiresAt,
    })
    navigate(returnTo || '/auctions')
  }

  const submitPassword = async (e: FormEvent) => {
    e.preventDefault(); setError(''); setBusy(true)
    try {
      const data = await login(identifier, password)
      if (data.deviceLimitReached) {
        setDeviceChoice(data.activeSessions ?? [])
        setLimitMsg(data.message ?? 'Maximum device limit reached. Log out a device below to continue.')
        return
      }
      completeSignIn(data)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  const sendOtp = async (e: FormEvent) => {
    e.preventDefault(); setError(''); setOk(''); setBusy(true)
    try {
      const msg = await requestLoginOtp(identifier)
      setOk(msg)
      setOtpStep('code')
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  const resendOtp = async () => {
    setError(''); setOk(''); setBusy(true)
    try {
      const msg = await requestLoginOtp(identifier)
      setOk(msg)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  const submitOtp = async (e: FormEvent) => {
    e.preventDefault(); setError(''); setBusy(true)
    try {
      const data = await verifyLoginOtp(identifier, otp)
      if (data.deviceLimitReached) {
        setDeviceChoice(data.activeSessions ?? [])
        setLimitMsg(data.message ?? 'Maximum device limit reached. Log out a device below to continue.')
        return
      }
      completeSignIn(data)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  const chooseDevice = async (sessionId: string) => {
    // logoutDevice re-verifies identity with the account's real password — there's no OTP-based
    // equivalent, so OTP-mode users can't use this picker (see the mode==='otp' branch in the JSX
    // below, which points them at password login instead rather than calling this with the OTP code).
    if (mode !== 'password') return
    setError('')
    setLoggingOutId(sessionId)
    try {
      await logoutDevice(identifier, password, sessionId)
      setDeviceChoice(null)
      const data = await login(identifier, password)
      if (data.deviceLimitReached) {
        // Shouldn't happen (we just freed a slot), but re-show the picker defensively.
        setDeviceChoice(data.activeSessions ?? [])
        setLimitMsg(data.message ?? 'Maximum device limit reached. Log out a device below to continue.')
        return
      }
      completeSignIn(data)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setLoggingOutId(null)
    }
  }

  return (
    <div className="login-wrap">
      <aside className="login-brand">
        <div className="eyebrow">India's Premium Auction Marketplace</div>
        <h2>Bid on Premium Assets <span className="accent">for</span> Superstars</h2>
        <p>Vehicles, properties, bank assets and insurance salvage — transparent, compliant and real-time, across India.</p>
        <ul>
          <li>🔒 <b>SARFAESI</b> compliant &amp; RBI regulated</li>
          <li>⚡ 48-hour EMD refund on losing bids</li>
          <li>🛡️ Bank-grade security · ISO 27001 certified</li>
        </ul>
      </aside>

      <section className="login-panel">
        {deviceChoice ? (
          <div className="login-card">
            <h1>Device limit reached</h1>
            <p className="lead">{limitMsg}</p>
            {mode === 'otp' ? (
              <p className="muted">
                Managing devices requires re-entering your password. Please{' '}
                <button type="button" className="linkbtn" onClick={() => { setDeviceChoice(null); switchMode('password') }}>sign in with your password</button>{' '}
                instead to log out a device.
              </p>
            ) : deviceChoice.length === 0 ? (
              <p className="muted">No other active sessions were found — try signing in again.</p>
            ) : (
              <ul style={{ listStyle: 'none', padding: 0, margin: '16px 0' }}>
                {deviceChoice.map((s) => (
                  <li key={s.sessionId} className="card" style={{ marginBottom: 10, padding: 14 }}>
                    <div style={{ fontWeight: 600 }}>{s.deviceName || 'Unknown device'}</div>
                    <div className="muted" style={{ fontSize: 13 }}>
                      IP {s.ipAddress || '—'} · Signed in {formatWhen(s.loginTime)}
                      {s.lastActivityTime && <> · Last active {formatWhen(s.lastActivityTime)}</>}
                    </div>
                    <button
                      type="button"
                      className="btn sm"
                      style={{ marginTop: 10 }}
                      disabled={loggingOutId === s.sessionId}
                      onClick={() => chooseDevice(s.sessionId)}
                    >
                      {loggingOutId === s.sessionId ? 'Logging out…' : 'Log out this device & sign in here'}
                    </button>
                  </li>
                ))}
              </ul>
            )}
            {error && <div className="error">{error}</div>}
            <button type="button" className="btn ghost sm" onClick={() => { setDeviceChoice(null); setError('') }}>
              Cancel
            </button>
          </div>
        ) : (
          <div className="login-card">
            <h1>Sign in</h1>
            <p className="lead">Welcome back to SwipeAuctions.</p>

            <div className="tabs" style={{ marginBottom: 6 }}>
              <button type="button" className={'tab' + (mode === 'password' ? ' active' : '')} onClick={() => switchMode('password')}>Login with Password</button>
              <button type="button" className={'tab' + (mode === 'otp' ? ' active' : '')} onClick={() => switchMode('otp')}>Login with OTP</button>
            </div>

            {mode === 'password' ? (
              <form onSubmit={submitPassword}>
                <label>Email or mobile number</label>
                <input style={{ width: '100%' }} value={identifier} onChange={(e) => setIdentifier(e.target.value)} placeholder="you@example.com" />
                <label>Password</label>
                <input style={{ width: '100%' }} type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="••••••••" />
                {error && <div className="error">{error}</div>}
                <div style={{ marginTop: 20 }}>
                  <button className="btn block" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'}</button>
                </div>
              </form>
            ) : otpStep === 'identifier' ? (
              <form onSubmit={sendOtp}>
                <label>Email or mobile number</label>
                <input style={{ width: '100%' }} value={identifier} onChange={(e) => setIdentifier(e.target.value)} placeholder="you@example.com" />
                {error && <div className="error">{error}</div>}
                <div style={{ marginTop: 20 }}>
                  <button className="btn block" disabled={busy}>{busy ? 'Sending…' : 'Send OTP'}</button>
                </div>
              </form>
            ) : (
              <form onSubmit={submitOtp}>
                <p className="muted">We sent a one-time code to your registered email.</p>
                <label>OTP</label>
                <input style={{ width: '100%' }} value={otp} onChange={(e) => setOtp(e.target.value)} placeholder="6-digit code" />
                {ok && <div className="ok">{ok}</div>}
                {error && <div className="error">{error}</div>}
                <div style={{ marginTop: 20 }}>
                  <button className="btn block" disabled={busy}>{busy ? 'Verifying…' : 'Verify & sign in'}</button>
                </div>
                <p className="muted" style={{ marginTop: 12, fontSize: 13 }}>
                  <button type="button" className="linkbtn" onClick={resendOtp} disabled={busy}>Resend OTP</button>
                  {' · '}
                  <button type="button" className="linkbtn" onClick={() => { setOtpStep('identifier'); setOtp(''); setError(''); setOk('') }}>Change email/mobile</button>
                </p>
              </form>
            )}

            <p className="register-cta">New here? <Link to="/register">Register now</Link> to start bidding</p>
            <div className="dev-hint">Demo login — <code>bidder@swipeauctions.test / Test@1234</code></div>
          </div>
        )}
      </section>
    </div>
  )
}
