import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { getWallet, topUp, errorMessage, type WalletBalance } from '../api'
import { useAuth } from '../auth'
import { money } from '../util'

export default function WalletPage() {
  const { isAuthenticated } = useAuth()
  const [wallet, setWallet] = useState<WalletBalance | null>(null)
  const [amount, setAmount] = useState('1000')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (isAuthenticated) getWallet().then(setWallet).catch((e) => setError(errorMessage(e)))
  }, [isAuthenticated])

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setError(''); setBusy(true)
    try {
      setWallet(await topUp(Number(amount)))
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  if (!isAuthenticated) {
    return <div className="card">Please <Link to="/login">sign in</Link> to view your wallet.</div>
  }

  return (
    <div className="card" style={{ maxWidth: 460 }}>
      <h1 style={{ marginTop: 0 }}>Wallet</h1>
      <div className="stat">
        <div><div className="k">Available</div><div className="v">{money(wallet?.availableBalance)}</div></div>
        <div><div className="k">Held (deposits)</div><div className="v">{money(wallet?.heldBalance)}</div></div>
      </div>
      <form onSubmit={submit}>
        <label>Top up (dev funding)</label>
        <input type="number" value={amount} onChange={(e) => setAmount(e.target.value)} min="1" />
        {error && <div className="error">{error}</div>}
        <div style={{ marginTop: 14 }}>
          <button className="btn" disabled={busy}>{busy ? 'Adding…' : 'Add funds'}</button>
        </div>
      </form>
      <p className="muted" style={{ marginTop: 16 }}>Real Stripe funding arrives in Phase 2.</p>
    </div>
  )
}
