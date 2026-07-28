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
            New accounts pay a one-time registration fee to unlock auction details and bidding — see the
            current fee on the registration page. You must also complete KYC verification before bidding.
            There's no separate deposit required per auction: your wallet's bidding credit limit (unlocked
            by depositing funds) is what determines what you can bid on any given item.
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
            The winning bidder must settle the full winning amount from their wallet to complete the
            purchase. Listings requiring a subscription tier can only be bid on by accounts holding that
            tier or higher.
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
