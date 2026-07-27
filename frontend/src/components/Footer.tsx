import { Link } from 'react-router-dom'

const SUPPORT_EMAIL = 'support@swipeauctions.test'

export default function Footer() {
  return (
    <footer className="site-footer">
      <div className="footer-top">
        <div className="footer-col footer-brand-col">
          <div className="brand">
            <span className="mark">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round">
                <path d="M14 3l7 7" /><path d="M18 7l-9 9" /><path d="M3 21l5-5" /><path d="M8.5 11.5l4 4" />
              </svg>
            </span>
            <span className="name">SWIPE <span>AUCTIONS</span></span>
          </div>
          <p>India's premium marketplace for vehicle, property, bank and insurance-salvage auctions.</p>
        </div>

        <div className="footer-col">
          <h4>Contact Us</h4>
          <a href={`mailto:${SUPPORT_EMAIL}`}>{SUPPORT_EMAIL}</a>
          <span>+91 99999 99915</span>
          <span>Mon–Sat, 9am–7pm IST</span>
        </div>

        <div className="footer-col">
          <h4>Company</h4>
          <Link to="/about">About Us</Link>
          <Link to="/contact">Contact Us</Link>
          <Link to="/subscription">Subscription</Link>
        </div>

        <div className="footer-col">
          <h4>Legal</h4>
          <Link to="/privacy">Privacy Policy</Link>
          <Link to="/terms">Terms &amp; Conditions</Link>
        </div>
      </div>

      <div className="footer-bottom">
        <span>© {new Date().getFullYear()} SwipeAuctions. All rights reserved.</span>
        <span>SARFAESI Compliant · RBI Regulated · ISO 27001 Certified</span>
      </div>
    </footer>
  )
}
