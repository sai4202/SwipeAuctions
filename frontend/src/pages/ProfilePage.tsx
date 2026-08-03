import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth'
import {
  getKycStatus, getMyReferrals, getReferralSettings, errorMessage,
  type KycStatusResult, type MyReferrals, type ReferralSettings,
} from '../api'
import { money } from '../util'
import ShareMenu from '../components/ShareMenu'

export default function ProfilePage() {
  const { isAuthenticated, email, role, kycCompleted } = useAuth()
  const [kyc, setKyc] = useState<KycStatusResult | null>(null)
  const [error, setError] = useState('')
  const [referrals, setReferrals] = useState<MyReferrals | null>(null)
  const [referralSettings, setReferralSettings] = useState<ReferralSettings | null>(null)

  useEffect(() => {
    if (!isAuthenticated) return
    getKycStatus().then(setKyc).catch((e) => setError(errorMessage(e)))
    getMyReferrals().then(setReferrals).catch(() => {})
    getReferralSettings().then(setReferralSettings).catch(() => {})
  }, [isAuthenticated])

  const inviteLink = referrals ? `/register?ref=${referrals.referralCode}` : ''

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

      {referrals && (
        <div className="card" style={{ maxWidth: 480, marginTop: 18 }}>
          <h2 style={{ fontSize: 15, margin: '0 0 6px' }}>Invite friends</h2>
          <p className="muted" style={{ fontSize: 12.5, marginTop: 0 }}>
            {referrals.totalReferred} friend{referrals.totalReferred === 1 ? '' : 's'} have joined through your link so far. Pick where to share it:
          </p>
          {referralSettings && referralSettings.bonusAmount > 0 && (
            <p className="muted" style={{ fontSize: 12.5, marginTop: 0 }}>
              Earn {money(referralSettings.bonusAmount)} when a friend deposits at least {money(referralSettings.minDeposit)}.
            </p>
          )}
          {referrals.successfulReferrals > 0 && (
            <p style={{ fontSize: 13, marginTop: 0, marginBottom: 12 }}>
              <strong>{referrals.successfulReferrals}</strong> successful referral{referrals.successfulReferrals === 1 ? '' : 's'} — you've earned <strong>{money(referrals.totalEarned)}</strong>.
            </p>
          )}
          <ShareMenu
            variant="inline"
            url={inviteLink}
            text="Join me on SwipeAuctions — India's premium auction marketplace for vehicles, properties, bank vehicles & insurance salvage!"
            emailSubject="Join me on SwipeAuctions"
          />
        </div>
      )}
    </div>
  )
}
