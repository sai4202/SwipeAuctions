import { useState } from 'react'

interface Props {
  title: string
  onAccept: () => void
  onCancel: () => void
}

const TERMS: { text: string; highlight?: boolean }[] = [
  { text: 'All bids are final and binding — once placed, a bid cannot be withdrawn or cancelled.', highlight: true },
  { text: 'Your refundable EMD (Earnest Money Deposit) is forfeited if you win this auction and do not complete settlement within the payment window.', highlight: true },
  { text: 'Items are sold on an "as-is, where-is" basis. Review the listing photos, specifications, and condition notes carefully before bidding.' },
  { text: 'A bid placed close to the closing time may trigger an anti-snipe extension, pushing the auction’s end time back to give other bidders a fair chance to respond.' },
  { text: 'Winning the auction obligates you to pay the full winning amount (beyond your EMD, if any remains) to claim the item.' },
  { text: 'Your KYC details must stay accurate and complete for the life of this auction — false or incomplete KYC can void your bid.' },
  { text: 'Any dispute about this auction or item must be raised through SwipeAuctions’ in-app reporting before pursuing any other remedy.' },
]

/**
 * Shown once per auction, right before a bidder's first bid on it (gated on `auction.yourBid ==
 * null` by the caller) — subsequent bids on the same item skip straight to the normal confirm step.
 */
export default function TermsModal({ title, onAccept, onCancel }: Props) {
  const [accepted, setAccepted] = useState(false)

  return (
    <div className="modal-backdrop" onClick={onCancel}>
      <div className="modal-card" onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <h3>Auction Terms &amp; Conditions</h3>
          <button type="button" className="modal-close" onClick={onCancel} aria-label="Close">✕</button>
        </div>
        <div className="modal-body">
          <p className="muted" style={{ margin: 0 }}>Before placing your first bid on "{title}", please review and accept:</p>
          <ul className="terms-list">
            {TERMS.map((t, i) => (
              <li key={i} className={t.highlight ? 'terms-highlight' : undefined}>{t.text}</li>
            ))}
          </ul>
          <label className="modal-check-row" style={{ marginTop: 4 }}>
            <input type="checkbox" checked={accepted} onChange={(e) => setAccepted(e.target.checked)} />
            I accept the Terms &amp; Conditions
          </label>
        </div>
        <div className="modal-foot">
          <button type="button" className="linkbtn" onClick={onCancel}>Cancel</button>
          <button type="button" className="btn" disabled={!accepted} onClick={onAccept}>Continue</button>
        </div>
      </div>
    </div>
  )
}
