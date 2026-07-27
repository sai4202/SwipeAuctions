export default function TermsPage() {
  return (
    <div className="container">
      <div className="section-head">
        <h1 className="page">Terms & Conditions</h1>
      </div>
      <div className="card" style={{ maxWidth: 760, display: 'flex', flexDirection: 'column', gap: 16 }}>
        <section>
          <h3 style={{ marginBottom: 6 }}>Registering to bid</h3>
          <p className="muted">
            You must complete KYC verification before bidding. Most auctions require a refundable EMD
            (Earnest Money Deposit), held from your wallet balance when you register — it's fully refunded
            within 48 hours if you don't win. Dealers are exempt from the EMD but still require KYC.
          </p>
        </section>
        <section>
          <h3 style={{ marginBottom: 6 }}>Bidding rules</h3>
          <p className="muted">
            Bids are binding once placed. Every bid must meet the minimum increment over the current
            highest bid, and cannot exceed your wallet's bidding credit limit. Each user is limited to 20
            bids per auction. A late bid inside the anti-snipe window automatically extends the auction's
            closing time.
          </p>
        </section>
        <section>
          <h3 style={{ marginBottom: 6 }}>Winning & settlement</h3>
          <p className="muted">
            The winning bidder's EMD is captured toward the final price; any remaining balance owed must be
            settled from wallet funds to complete the purchase. Listings requiring a subscription tier can
            only be bid on by accounts holding that tier or higher.
          </p>
        </section>
        <section>
          <h3 style={{ marginBottom: 6 }}>Disputes</h3>
          <p className="muted">
            If something's wrong with a completed auction, raise a dispute from the auction page within the
            escrow window. Our team reviews every dispute before releasing or reversing seller proceeds.
          </p>
        </section>
      </div>
    </div>
  )
}
