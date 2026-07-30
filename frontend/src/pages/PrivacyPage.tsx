export default function PrivacyPage() {
  return (
    <div className="container">
      <div className="section-head">
        <h1 className="page">Privacy Policy</h1>
      </div>
      <div className="card" style={{ maxWidth: 760, display: 'flex', flexDirection: 'column', gap: 16 }}>
        <section>
          <h3 style={{ marginBottom: 6 }}>What we collect</h3>
          <p className="muted">
            Account details (email, mobile number), KYC documents (Aadhaar/PAN, address) required for
            regulated bidding, and wallet/transaction history for every deposit, hold, bid, and withdrawal.
          </p>
        </section>
        <section>
          <h3 style={{ marginBottom: 6 }}>How we use it</h3>
          <p className="muted">
            To verify your identity before you can bid, to process your one-time registration fee, to
            enforce your wallet's bidding credit limit, and to contact you about auctions you've bid on
            or won.
          </p>
        </section>
        <section>
          <h3 style={{ marginBottom: 6 }}>Who we share it with</h3>
          <p className="muted">
            KYC data is shared only with our verification provider. Payment data is processed by Razorpay and
            never stored on our servers in raw form. We don't sell your data to third parties.
          </p>
        </section>
        <section>
          <h3 style={{ marginBottom: 6 }}>Your rights</h3>
          <p className="muted">
            You can request a copy of your data or ask us to delete your account (subject to regulatory
            record-keeping requirements for completed auctions) by writing to us — see <a href="/contact">Contact Us</a>.
          </p>
        </section>
      </div>
    </div>
  )
}
