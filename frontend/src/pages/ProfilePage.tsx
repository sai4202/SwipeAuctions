import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth'
import { getKycStatus, errorMessage, type KycStatusResult } from '../api'

export default function ProfilePage() {
  const { isAuthenticated, email, role, kycCompleted } = useAuth()
  const [kyc, setKyc] = useState<KycStatusResult | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!isAuthenticated) return
    getKycStatus().then(setKyc).catch((e) => setError(errorMessage(e)))
  }, [isAuthenticated])

  if (!isAuthenticated) {
    return <div className="container"><div className="card" style={{ maxWidth: 460 }}>Please <Link to="/login">sign in</Link> to view your profile.</div></div>
  }

  return (
    <div className="container">
      <div className="section-head">
        <h1 className="page">Profile</h1>
      </div>
      {error && <div className="error">{error}</div>}
      <div className="card" style={{ maxWidth: 480 }}>
        {kyc?.fullName && (
          <div className="fgroup" style={{ marginBottom: 14 }}>
            <small>Name</small>
            <div style={{ fontSize: 15, fontWeight: 700 }}>{kyc.fullName}</div>
          </div>
        )}
        <div className="fgroup" style={{ marginBottom: 14 }}>
          <small>Email</small>
          <div style={{ fontSize: 15 }}>{email}</div>
        </div>
        <div className="fgroup" style={{ marginBottom: 14 }}>
          <small>Account type</small>
          <div style={{ fontSize: 15 }}>{role === 'DEALER' ? 'Dealer' : 'Customer'}</div>
        </div>
        <div className="fgroup" style={{ marginBottom: 4 }}>
          <small>KYC status</small>
          <div style={{ fontSize: 15, display: 'flex', alignItems: 'center', gap: 10 }}>
            <span className={`badge ${kycCompleted ? 'OPEN' : 'UNSOLD'}`}>
              {kycCompleted ? 'Verified' : (kyc?.status ?? 'Not verified')}
            </span>
            <Link to="/kyc" className="linkbtn">{kycCompleted ? 'View / update' : 'Complete KYC'}</Link>
          </div>
        </div>
      </div>
    </div>
  )
}
