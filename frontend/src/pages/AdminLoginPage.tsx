import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { adminLogin, adminLogoutDevice, errorMessage, type SessionInfo, type AdminLoginData } from '../api'
import { useAuth } from '../auth'

function formatWhen(iso: string | null): string {
  if (!iso) return '—'
  try { return new Date(iso).toLocaleString() } catch { return iso }
}

export default function AdminLoginPage() {
  const { signIn } = useAuth()
  const navigate = useNavigate()
  const [identifier, setIdentifier] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  // Populated when the backend reports the single-session cap is held by another device — same
  // "pick a device to log out" UX as the user login flow (LoginPage.tsx).
  const [deviceChoice, setDeviceChoice] = useState<SessionInfo[] | null>(null)
  const [limitMsg, setLimitMsg] = useState('')
  const [loggingOutId, setLoggingOutId] = useState<string | null>(null)

  const completeSignIn = (data: AdminLoginData) => {
    signIn({ token: data.token, email: data.email, role: data.role, adminRole: data.adminRole })
    navigate('/admin')
  }

  const submit = async (e: FormEvent) => {
    e.preventDefault(); setError(''); setBusy(true)
    try {
      const data = await adminLogin(identifier, password)
      if (data.deviceLimitReached) {
        setDeviceChoice(data.activeSessions ?? [])
        setLimitMsg(data.message ?? 'Admin already logged in on another device. Log out that device below to continue.')
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
    setError('')
    setLoggingOutId(sessionId)
    try {
      await adminLogoutDevice(identifier, password, sessionId)
      setDeviceChoice(null)
      const data = await adminLogin(identifier, password)
      if (data.deviceLimitReached) {
        // Shouldn't happen (we just freed the slot), but re-show the picker defensively.
        setDeviceChoice(data.activeSessions ?? [])
        setLimitMsg(data.message ?? 'Admin already logged in on another device. Log out that device below to continue.')
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
      <aside className="login-brand admin-brand">
        <div className="eyebrow">Restricted Access</div>
        <h2>Run the <span className="accent">Marketplace</span></h2>
        <p>Manage listings, resolve wallet holds, and keep every live auction running smoothly — all from one console.</p>
        <ul>
          <li>🛠️ Manage users, listings &amp; categories</li>
          <li>🔓 Release or refund wallet holds in one click</li>
          <li>📊 Monitor live auctions &amp; settlements in real time</li>
        </ul>
      </aside>

      <section className="login-panel">
        {deviceChoice ? (
          <div className="login-card">
            <h1>Device limit reached</h1>
            <p className="lead">{limitMsg}</p>
            {deviceChoice.length === 0 ? (
              <p className="muted">No other active session was found — try signing in again.</p>
            ) : (
              <ul style={{ listStyle: 'none', padding: 0, margin: '16px 0' }}>
                {deviceChoice.map((s) => (
                  <li key={s.sessionId} className="card" style={{ marginBottom: 10, padding: 14 }}>
                    <div style={{ fontWeight: 600 }}>{s.deviceName || 'Unknown device'}</div>
                    <div className="muted" style={{ fontSize: 13 }}>
                      Signed in {formatWhen(s.loginTime)}
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
          <form className="login-card" onSubmit={submit}>
            <h1>Admin sign in</h1>
            <p className="lead">Authorized personnel only.</p>
            <label>Email</label>
            <input style={{ width: '100%' }} value={identifier} onChange={(e) => setIdentifier(e.target.value)} placeholder="admin@example.com" />
            <label>Password</label>
            <input style={{ width: '100%' }} type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="••••••••" />
            {error && <div className="error">{error}</div>}
            <div style={{ marginTop: 20 }}>
              <button className="btn block" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'}</button>
            </div>
          </form>
        )}
      </section>
    </div>
  )
}
