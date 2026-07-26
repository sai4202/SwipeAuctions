import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { adminLogin, errorMessage } from '../api'
import { useAuth } from '../auth'

export default function AdminLoginPage() {
  const { signIn } = useAuth()
  const navigate = useNavigate()
  const [identifier, setIdentifier] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const submit = async (e: FormEvent) => {
    e.preventDefault(); setError(''); setBusy(true)
    try {
      const data = await adminLogin(identifier, password)
      signIn({ token: data.token, email: data.email, role: data.role })
      navigate('/admin')
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="login-wrap">
      <section className="login-panel">
        <form className="login-card" onSubmit={submit}>
          <h1>Admin sign in</h1>
          <label>Email</label>
          <input style={{ width: '100%' }} value={identifier} onChange={(e) => setIdentifier(e.target.value)} placeholder="admin@example.com" />
          <label>Password</label>
          <input style={{ width: '100%' }} type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="••••••••" />
          {error && <div className="error">{error}</div>}
          <div style={{ marginTop: 20 }}>
            <button className="btn block" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'}</button>
          </div>
        </form>
      </section>
    </div>
  )
}
