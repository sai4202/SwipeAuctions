import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate, useLocation, Link } from 'react-router-dom'
import { getKycStatus, submitKyc, errorMessage } from '../api'
import { useAuth } from '../auth'

export default function KycPage() {
  const { isAuthenticated, kycCompleted, markKycCompleted } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const returnTo = (location.state as { from?: string } | null)?.from

  const [fullName, setFullName] = useState('')
  const [dateOfBirth, setDateOfBirth] = useState('')
  const [gender, setGender] = useState<'MALE' | 'FEMALE' | 'OTHER'>('MALE')
  const [address, setAddress] = useState('')
  const [city, setCity] = useState('')
  const [state, setState] = useState('')
  const [pincode, setPincode] = useState('')
  const [aadhaarNumber, setAadhaarNumber] = useState('')
  const [panNumber, setPanNumber] = useState('')
  const [error, setError] = useState('')
  const [ok, setOk] = useState('')
  const [busy, setBusy] = useState(false)
  const [alreadyDone, setAlreadyDone] = useState(kycCompleted)

  useEffect(() => {
    if (!isAuthenticated) return
    getKycStatus().then((s) => setAlreadyDone(s.kycCompleted)).catch(() => {})
  }, [isAuthenticated])

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setError(''); setOk(''); setBusy(true)
    try {
      await submitKyc({
        fullName, dateOfBirth: dateOfBirth || undefined, gender, address, city, state, pincode,
        aadhaarNumber, panNumber,
      })
      markKycCompleted()
      setOk('KYC verified! You can now register and bid on auctions.')
      setAlreadyDone(true)
      setTimeout(() => navigate(returnTo || '/auctions'), 1200)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  if (!isAuthenticated) {
    return (
      <div className="container">
        <div className="card" style={{ maxWidth: 480, margin: '10px auto' }}>
          <p className="muted"><Link to="/login">Sign in</Link> to complete your KYC.</p>
        </div>
      </div>
    )
  }

  return (
    <div className="container">
      <div className="card" style={{ maxWidth: 480, margin: '10px auto' }}>
        <span className="eyebrow">Identity Verification</span>
        <h1 className="page" style={{ marginTop: 12 }}>Complete your KYC</h1>
        <p className="muted">Required once before you can register or place a bid on any auction.</p>

        {alreadyDone && !ok && (
          <div className="ok" style={{ marginTop: 10 }}>Your KYC is already verified. You're clear to bid.</div>
        )}

        <form onSubmit={submit}>
          <label>Full name (as per ID)</label>
          <input style={{ width: '100%' }} value={fullName} onChange={(e) => setFullName(e.target.value)} placeholder="Full legal name" />
          <label>Date of birth</label>
          <input style={{ width: '100%' }} type="date" value={dateOfBirth} onChange={(e) => setDateOfBirth(e.target.value)} />
          <label>Gender</label>
          <div className="tabs">
            {(['MALE', 'FEMALE', 'OTHER'] as const).map((g) => (
              <button type="button" key={g} className={'tab' + (gender === g ? ' active' : '')} onClick={() => setGender(g)}>
                {g[0] + g.slice(1).toLowerCase()}
              </button>
            ))}
          </div>
          <label>Address</label>
          <input style={{ width: '100%' }} value={address} onChange={(e) => setAddress(e.target.value)} placeholder="House no., street, area" />
          <label>City</label>
          <input style={{ width: '100%' }} value={city} onChange={(e) => setCity(e.target.value)} />
          <label>State</label>
          <input style={{ width: '100%' }} value={state} onChange={(e) => setState(e.target.value)} />
          <label>Pincode</label>
          <input style={{ width: '100%' }} value={pincode} onChange={(e) => setPincode(e.target.value)} placeholder="6-digit pincode" />
          <label>Aadhaar number</label>
          <input style={{ width: '100%' }} value={aadhaarNumber} onChange={(e) => setAadhaarNumber(e.target.value)} placeholder="XXXX XXXX XXXX" />
          <label>PAN number</label>
          <input style={{ width: '100%' }} value={panNumber} onChange={(e) => setPanNumber(e.target.value.toUpperCase())} placeholder="ABCDE1234F" />
          <p className="muted" style={{ fontSize: 12.5, marginTop: 8 }}>
            Only masked, last-4-digit references are stored — your full ID numbers are never saved in plain text.
          </p>
          {ok && <div className="ok">{ok}</div>}
          {error && <div className="error">{error}</div>}
          <div style={{ marginTop: 18 }}>
            <button className="btn block" disabled={busy}>{busy ? 'Submitting…' : 'Submit KYC'}</button>
          </div>
        </form>
      </div>
    </div>
  )
}
