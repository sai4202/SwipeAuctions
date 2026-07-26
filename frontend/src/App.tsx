import { Routes, Route, Link } from 'react-router-dom'
import Header from './components/Header'
import HomePage from './pages/HomePage'
import BrowsePage from './pages/BrowsePage'
import AuctionDetailPage from './pages/AuctionDetailPage'
import MultiBiddingPage from './pages/MultiBiddingPage'
import LoginPage from './pages/LoginPage'
import AdminLoginPage from './pages/AdminLoginPage'
import RegisterPage from './pages/RegisterPage'
import WalletPage from './pages/WalletPage'
import MyWinsPage from './pages/MyWinsPage'
import MyTransactionsPage from './pages/MyTransactionsPage'
import ProfilePage from './pages/ProfilePage'
import SubscriptionPage from './pages/SubscriptionPage'
import AdminDashboardPage from './pages/AdminDashboardPage'
import AboutPage from './pages/AboutPage'
import ContactPage from './pages/ContactPage'
import KycPage from './pages/KycPage'

export default function App() {
  return (
    <div>
      <Header />
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/auctions" element={<BrowsePage />} />
        <Route path="/auctions/:id" element={<AuctionDetailPage />} />
        <Route path="/wishlist" element={<MultiBiddingPage />} />
        <Route path="/swipe-stock" element={<BrowsePage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/admin-login" element={<AdminLoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/wallet" element={<WalletPage />} />
        <Route path="/my-wins" element={<MyWinsPage />} />
        <Route path="/my-transactions" element={<MyTransactionsPage />} />
        <Route path="/profile" element={<ProfilePage />} />
        <Route path="/subscription" element={<SubscriptionPage />} />
        <Route path="/kyc" element={<KycPage />} />
        <Route path="/admin" element={<AdminDashboardPage />} />
        <Route path="/about" element={<AboutPage />} />
        <Route path="/contact" element={<ContactPage />} />
        <Route path="*" element={
          <div className="container">
            <div className="card" style={{ maxWidth: 460, margin: '40px auto', textAlign: 'center' }}>
              <h1 className="page">Page not found</h1>
              <p className="muted">The page you're looking for doesn't exist or may have moved.</p>
              <Link to="/" className="btn sm">Back to home</Link>
            </div>
          </div>
        } />
      </Routes>
    </div>
  )
}
