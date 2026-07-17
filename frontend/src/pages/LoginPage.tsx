import { useState, type FormEvent } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { login, errorMessage } from '../api'
import { useAuth } from '../auth'

export default function LoginPage() {
  const { signIn } = useAuth()
  const navigate = useNavigate()
  const [emailOrMobile, setEmailOrMobile] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setError('')
    setBusy(true)
    try {
      const data = await login(emailOrMobile, password)
      signIn(data)
      navigate('/')
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="form card" onSubmit={submit}>
      <h1>Sign in</h1>
      <label>Email or mobile number</label>
      <input value={emailOrMobile} onChange={(e) => setEmailOrMobile(e.target.value)} placeholder="you@example.com" />
      <label>Password</label>
      <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
      {error && <div className="error">{error}</div>}
      <div style={{ marginTop: 18 }}>
        <button className="btn block" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'}</button>
      </div>
      <p className="muted" style={{ marginTop: 16 }}>No account? <Link to="/register">Register</Link></p>
      <p className="muted">Dev login: <code>bidder@swipeauctions.test</code> / <code>Test@1234</code></p>
    </form>
  )
}
