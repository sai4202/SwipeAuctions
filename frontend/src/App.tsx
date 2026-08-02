import { Routes, Route, Link } from 'react-router-dom'
import Header from './components/Header'
import Footer from './components/Footer'
import ChatWidget from './components/ChatWidget'
import { useAuth } from './auth'
import HomePage from './pages/HomePage'
import BrowsePage from './pages/BrowsePage'
import AuctionDetailPage from './pages/AuctionDetailPage'
import MultiBiddingPage from './pages/MultiBiddingPage'
import LoginPage from './pages/LoginPage'
import AdminLoginPage from './pages/AdminLoginPage'
import RegisterPage from './pages/RegisterPage'
import WalletPage from './pages/WalletPage'
import MyWinsPage from './pages/MyWinsPage'
import MyBidsPage from './pages/MyBidsPage'
import MyTransactionsPage from './pages/MyTransactionsPage'
import ProfilePage from './pages/ProfilePage'
import SubscriptionPage from './pages/SubscriptionPage'
import AdminLayout from './components/AdminLayout'
import AdminOverviewPage from './pages/admin/AdminOverviewPage'
import AdminUsersPage from './pages/admin/AdminUsersPage'
import AdminListingsPage from './pages/admin/AdminListingsPage'
import AdminAuctionsPage from './pages/admin/AdminAuctionsPage'
import AdminPlansPage from './pages/admin/AdminPlansPage'
import AdminHoldsPage from './pages/admin/AdminHoldsPage'
import AdminPaymentsPage from './pages/admin/AdminPaymentsPage'
import AdminWithdrawalsPage from './pages/admin/AdminWithdrawalsPage'
import AdminTransactionsPage from './pages/admin/AdminTransactionsPage'
import AdminBannersPage from './pages/admin/AdminBannersPage'
import AdminReferralsPage from './pages/admin/AdminReferralsPage'
import AdminChatPage from './pages/admin/AdminChatPage'
import AdminDisputesPage from './pages/admin/AdminDisputesPage'
import AdminCategoriesPage from './pages/admin/AdminCategoriesPage'
import AdminKycPage from './pages/admin/AdminKycPage'
import AdminAuditLogPage from './pages/admin/AdminAuditLogPage'
import AdminSettingsPage from './pages/admin/AdminSettingsPage'
import AboutPage from './pages/AboutPage'
import ContactPage from './pages/ContactPage'
import PrivacyPage from './pages/PrivacyPage'
import TermsPage from './pages/TermsPage'
import KycPage from './pages/KycPage'
import RequireAuth from './components/RequireAuth'

export default function App() {
  const { isAuthenticated, role } = useAuth()
  return (
    <div>
      <Header />
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/auctions" element={<BrowsePage />} />
        <Route path="/auctions/:id" element={<RequireAuth redirectTo="/register"><AuctionDetailPage /></RequireAuth>} />
        <Route path="/wishlist" element={<RequireAuth><MultiBiddingPage /></RequireAuth>} />
        <Route path="/swipe-stock" element={<RequireAuth><BrowsePage /></RequireAuth>} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/admin-login" element={<AdminLoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/wallet" element={<RequireAuth><WalletPage /></RequireAuth>} />
        <Route path="/my-wins" element={<RequireAuth><MyWinsPage /></RequireAuth>} />
        <Route path="/my-bids" element={<RequireAuth><MyBidsPage /></RequireAuth>} />
        <Route path="/my-transactions" element={<RequireAuth><MyTransactionsPage /></RequireAuth>} />
        <Route path="/profile" element={<RequireAuth><ProfilePage /></RequireAuth>} />
        <Route path="/subscription" element={<RequireAuth><SubscriptionPage /></RequireAuth>} />
        <Route path="/kyc" element={<RequireAuth><KycPage /></RequireAuth>} />
        <Route path="/admin" element={<AdminLayout />}>
          <Route index element={<AdminOverviewPage />} />
          <Route path="users" element={<AdminUsersPage />} />
          <Route path="listings" element={<AdminListingsPage />} />
          <Route path="auctions" element={<AdminAuctionsPage />} />
          <Route path="plans" element={<AdminPlansPage />} />
          <Route path="holds" element={<AdminHoldsPage />} />
          <Route path="payments" element={<AdminPaymentsPage />} />
          <Route path="withdrawals" element={<AdminWithdrawalsPage />} />
          <Route path="transactions" element={<AdminTransactionsPage />} />
          <Route path="referrals" element={<AdminReferralsPage />} />
          <Route path="chat" element={<AdminChatPage />} />
          <Route path="disputes" element={<AdminDisputesPage />} />
          <Route path="banners" element={<AdminBannersPage />} />
          <Route path="categories" element={<AdminCategoriesPage />} />
          <Route path="kyc" element={<AdminKycPage />} />
          <Route path="audit-log" element={<AdminAuditLogPage />} />
          <Route path="settings" element={<AdminSettingsPage />} />
        </Route>
        <Route path="/about" element={<AboutPage />} />
        <Route path="/contact" element={<ContactPage />} />
        <Route path="/privacy" element={<PrivacyPage />} />
        <Route path="/terms" element={<TermsPage />} />
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
      <Footer />
      {isAuthenticated && (role === 'USER' || role === 'DEALER') && <ChatWidget />}
    </div>
  )
}
