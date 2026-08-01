import axios from 'axios'

// In dev, relative paths go through Vite's proxy (vite.config.ts) to localhost:8080, so API_BASE
// stays '' there regardless (import.meta.env.PROD is false under `vite dev`/`vite test`). In a
// production build, fall back to the deployed Railway backend so the app works even if Vercel's
// VITE_API_URL env var isn't set — set VITE_API_URL there to override this without a code change
// (e.g. after moving to a custom domain).
export const API_BASE = import.meta.env.VITE_API_URL
  ?? (import.meta.env.PROD ? 'https://swipeauctions-production.up.railway.app' : '')

const api = axios.create({ baseURL: API_BASE || '/' })

// Attach the JWT (if present) to every request.
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// Lets AuthProvider react to a session expiring mid-app without api.ts depending on React/router.
let sessionExpiredHandler: (() => void) | null = null
export function setSessionExpiredHandler(handler: (() => void) | null) {
  sessionExpiredHandler = handler
}

// A 401 on an authenticated request means the token died server-side (expiry, forced logout,
// single-session kick) — only fires when we actually sent a token, so a plain wrong-password
// login attempt (no stored token yet) never triggers this. Login and logout endpoints are both
// excluded: login because a wrong-password attempt has nothing to do with an expired session, and
// logout because an already-expired token sent by an explicit logout action would otherwise pop the
// "session expired" modal on what the user meant as a clean, deliberate logout.
api.interceptors.response.use(
  (res) => res,
  (error) => {
    if (
      axios.isAxiosError(error) &&
      error.response?.status === 401 &&
      error.config?.headers?.Authorization &&
      !error.config?.url?.includes('/auth/login') &&
      !error.config?.url?.includes('/auth/logout')
    ) {
      sessionExpiredHandler?.()
    }
    return Promise.reject(error)
  },
)

export default api

// ---- Types ----
export interface SessionInfo {
  sessionId: string
  deviceId: string | null
  deviceName: string | null
  ipAddress: string | null
  active: boolean
  loginTime: string
  lastActivityTime: string | null
}
export interface LoginData {
  userId: string
  email: string
  mobileNumber: string
  token: string
  role: string
  active: boolean
  kycCompleted?: boolean
  registrationFeePaid?: boolean
  subscriptionTier?: SubscriptionTier
  subscriptionExpiresAt?: string | null
  deviceLimitReached?: boolean
  message?: string
  activeSessions?: SessionInfo[]
}
export type SubscriptionTier = 'NONE' | 'SILVER' | 'GOLD' | 'DIAMOND'
export type BillingCycle = 'MONTHLY' | 'QUARTERLY' | 'HALF_YEARLY' | 'YEARLY'
export interface SubscriptionPrice {
  tier: SubscriptionTier
  billingCycle: BillingCycle
  price: number
}
export interface MySubscription {
  tier: SubscriptionTier
  expiresAt: string | null
}
export interface KycStatusResult {
  kycCompleted: boolean
  status: string
  fullName?: string
  submittedAt?: string
  verifiedAt?: string
  remarks?: string
}
export interface AdminLoginData {
  adminId: string
  email: string
  token: string
  role: string
  active: boolean
}
export interface Auction {
  id: string
  listingId: string
  title: string
  basePrice: number
  currentHighestBid: number | null
  status: string
  startTime: string
  currentEndTime: string
  bidCount: number
  categoryName: string
  categoryId: string
  brand: string | null
  condition: string
  city: string | null
  state: string | null
  zip: string | null
  coverImageUrl: string | null
  images: string[]
  yourBid: number | null
  attributes: Record<string, string>
  isWinner: boolean
  settlementPaid: boolean
  eventId: string | null
  eventName: string | null
  sellerEmail: string
  swipeStock: boolean
  requiredTier: SubscriptionTier
  registered: boolean
  bidsRemaining: number | null
  currentWinnerId: string | null
  currentWinnerEmail: string | null
}
export interface AuctionEvent {
  id: string
  name: string
  location: string | null
  startTime: string
  closingTime: string
  itemCount: number
  sellerEmail: string
  categoryId: string
  categorySlug: string
}
export interface Category {
  id: string
  name: string
  slug: string
  parentId: string | null
}
export interface Listing {
  id: string
  title: string
  brand: string | null
  condition: string
  categoryId: string
  categoryName: string
  city: string | null
  state: string | null
  reservePrice: number
  status: string
  sellerEmail: string
}
export interface ListingImageResult {
  id: string
  url: string
  cover: boolean
}
export interface WalletBalance {
  availableBalance: number
  heldBalance: number
  creditLimit: number
  /** creditLimit minus whatever's already committed to this user's other open bids — what they can
   *  actually still bid up to right now, across every auction at once. */
  availableCreditLimit: number
}
export interface RegisterResult {
  message: string
  availableBalance: number
  heldBalance: number
}
export interface AdminUser {
  id: string
  email: string
  mobileNumber: string
  role: string
  active: boolean
  kycStatus: string
  emailVerified: boolean
  mobileVerified: boolean
  createdAt: string
  walletAvailableBalance: number
  walletHeldBalance: number
  walletCreditLimit: number
  subscriptionTier: SubscriptionTier
  subscriptionExpiresAt: string | null
  activeBidCount: number
}
export interface AdminHold {
  id: string
  auctionId: string
  listingTitle: string
  amount: number
  createdAt: string
}
export interface AdminUserBid {
  auctionId: string
  listingTitle: string
  categoryName: string
  yourBid: number
  currentHighestBid: number | null
  auctionStatus: string
  leading: boolean
  placedAt: string
  currentEndTime: string
}
export interface ReleaseHoldResult {
  availableBalance: number
  heldBalance: number
  creditLimit: number
}
export interface AdminListing {
  id: string
  title: string
  sellerEmail: string
  categoryName: string
  status: string
  reservePrice: number
  createdAt: string
  requiredTier: SubscriptionTier
}
export interface AdminAuction {
  id: string
  listingId: string
  title: string
  sellerEmail: string
  basePrice: number
  currentHighestBid: number | null
  status: string
  startTime: string
  currentEndTime: string
  bidCount: number
  currentWinnerId: string | null
  currentWinnerEmail: string | null
}
export interface Dispute {
  id: string
  auctionId: string
  auctionTitle: string
  raisedByEmail: string
  reason: string
  status: string
  adminNotes: string | null
  resolvedAt: string | null
  createdAt: string
}
export interface AdminStats {
  totalUsers: number
  openAuctions: number
  gmv: number
  openDisputes: number
}
export interface AdminCategory {
  id: string
  name: string
  slug: string
  parentId: string | null
}
export interface AdminKyc {
  userId: string
  email: string
  fullName: string | null
  dateOfBirth: string | null
  address: string | null
  city: string | null
  state: string | null
  pincode: string | null
  aadhaarMasked: string | null
  panNumberMasked: string | null
  status: 'NOT_SUBMITTED' | 'PENDING' | 'APPROVED' | 'REJECTED'
  provider: string | null
  submittedAt: string | null
  verifiedAt: string | null
  remarks: string | null
  reviewedBy: string | null
}
export interface AdminCategoryAttribute {
  id: string
  key: string
  label: string
  valueType: 'TEXT' | 'NUMBER' | 'BOOLEAN' | 'ENUM'
  filterable: boolean
  sortOrder: number
}
export interface StockListing {
  id: string
  title: string
  categoryId: string
  categoryName: string
  swipeStock: boolean
  requiredTier: SubscriptionTier
}
export interface StockAuction {
  auctionId: string
  listingId: string
  status: string
}
export interface StockImage {
  id: string
  url: string
  cover: boolean
}
export interface BulkImportRowError {
  row: number
  message: string
}
export interface BulkImportResult {
  totalRows: number
  created: number
  errors: BulkImportRowError[]
}
export interface OrderIntent {
  orderId: string
  amountPaise: number
  currency: string
  keyId: string
}
export interface WithdrawResult {
  status: string
  availableBalance: number
}
export interface SellerPayoutStatus {
  connected: boolean
  payoutsEnabled: boolean
}
export interface WalletTransaction {
  id: string
  type: string
  amount: number
  referenceType: string | null
  referenceId: string | null
  createdAt: string
}

// ApiResponse<T> envelope used by the auth endpoints.
interface ApiEnvelope<T> {
  success: boolean
  message: string
  data: T
}

// PageResponse<T> envelope used by paginated admin dashboard endpoints.
export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

function errorMessage(e: unknown): string {
  if (axios.isAxiosError(e)) {
    return (e.response?.data as { message?: string })?.message ?? e.message
  }
  return 'Unexpected error'
}

// ---- Auth ----
export async function login(emailOrMobile: string, password: string, clientDeviceModel?: string): Promise<LoginData> {
  const res = await api.post<ApiEnvelope<LoginData>>('/api/auth/login', { emailOrMobile, password, clientDeviceModel })
  return res.data.data
}
export async function adminLogin(email: string, password: string): Promise<AdminLoginData> {
  const res = await api.post<ApiEnvelope<AdminLoginData>>('/api/admin/auth/login', { email, password })
  return res.data.data
}
export async function register(body: {
  email: string; mobileNumber: string; password: string; confirmPassword: string; role?: 'USER' | 'DEALER'
}): Promise<string> {
  const res = await api.post<ApiEnvelope<string>>('/api/auth/register', body)
  return res.data.message
}
export async function verifyEmailOtp(email: string, otp: string): Promise<string> {
  const res = await api.post<ApiEnvelope<string>>('/api/auth/verify-email', { email, otp })
  return res.data.message
}
export async function verifyMobileOtp(email: string, otp: string): Promise<string> {
  const res = await api.post<ApiEnvelope<string>>('/api/auth/verify-mobile', { email, otp })
  return res.data.message
}
export async function resendOtp(email: string): Promise<string> {
  const res = await api.post<ApiEnvelope<string>>('/api/auth/resend-otp', { email })
  return res.data.message
}
// Final step of registration: real Razorpay payment of the one-time platform registration fee.
// Requires a JWT — RegisterPage signs the user in right after mobile-OTP verification for this.
// Order then verify, same pattern as wallet top-up — see RazorpayCheckout.
export async function createRegistrationFeeOrder(): Promise<OrderIntent> {
  const res = await api.post<ApiEnvelope<OrderIntent>>('/api/auth/registration-fee/order')
  return res.data.data
}
export async function verifyRegistrationFee(orderId: string, paymentId: string, signature: string): Promise<string> {
  const res = await api.post<ApiEnvelope<string>>('/api/auth/registration-fee/verify', { orderId, paymentId, signature })
  return res.data.message
}
export async function requestLoginOtp(emailOrMobile: string): Promise<string> {
  const res = await api.post<ApiEnvelope<string>>('/api/auth/login/otp/request', { emailOrMobile })
  return res.data.message
}
export async function verifyLoginOtp(emailOrMobile: string, otp: string, clientDeviceModel?: string): Promise<LoginData> {
  const res = await api.post<ApiEnvelope<LoginData>>('/api/auth/login/otp/verify', { emailOrMobile, otp, clientDeviceModel })
  return res.data.data
}
// Logs out a specific device from the "device limit reached" login prompt (re-verifies credentials
// since the caller has no JWT yet), so a retried login can succeed.
export async function logout(): Promise<string> {
  const res = await api.post<ApiEnvelope<string>>('/api/auth/logout')
  return res.data.message
}
export async function adminLogout(): Promise<string> {
  const res = await api.post<ApiEnvelope<string>>('/api/admin/auth/logout')
  return res.data.message
}
export async function logoutDevice(emailOrMobile: string, password: string, sessionId: string): Promise<string> {
  const res = await api.post<ApiEnvelope<string>>('/api/auth/logout-device', { emailOrMobile, password, sessionId })
  return res.data.message
}

// ---- KYC ----
export async function getKycStatus(): Promise<KycStatusResult> {
  const res = await api.get<ApiEnvelope<KycStatusResult>>('/api/users/kyc')
  return res.data.data
}
export async function submitKyc(body: {
  fullName: string; dateOfBirth?: string; gender?: string; address: string
  city: string; state: string; pincode: string; aadhaarNumber: string; panNumber: string
}): Promise<KycStatusResult> {
  const res = await api.post<ApiEnvelope<KycStatusResult>>('/api/users/kyc', body)
  return res.data.data
}

// ---- Catalog / auctions (plain DTOs) ----
export async function getAuctions(params?: { status?: string; eventId?: string }): Promise<Auction[]> {
  const res = await api.get<Auction[]>('/api/auctions', { params: params ?? {} })
  return res.data
}
export async function getAuction(id: string): Promise<Auction> {
  const res = await api.get<Auction>(`/api/auctions/${id}`)
  return res.data
}
export async function getCategories(): Promise<Category[]> {
  const res = await api.get<Category[]>('/api/categories')
  return res.data
}
export interface CategoryFilter {
  key: string
  label: string
  valueType: 'TEXT' | 'NUMBER' | 'BOOLEAN' | 'ENUM'
  options: string[]
}
export async function getCategoryFilters(categoryId: string): Promise<CategoryFilter[]> {
  const res = await api.get<CategoryFilter[]>(`/api/categories/${categoryId}/filters`)
  return res.data
}
export async function createListing(body: {
  categoryId: string; title: string; description: string; brand: string; condition: string
  city: string; state: string; zip: string; reservePrice: number; attributes: Record<string, string>
}): Promise<Listing> {
  const res = await api.post<Listing>('/api/listings', body)
  return res.data
}
export async function uploadListingImage(listingId: string, file: File, cover: boolean): Promise<ListingImageResult> {
  const form = new FormData()
  form.append('file', file)
  form.append('cover', String(cover))
  const res = await api.post<ListingImageResult>(`/api/listings/${listingId}/images`, form)
  return res.data
}
export async function createAuction(body: {
  listingId: string; basePrice: number | null; startTime: string; endTime: string; eventId?: string | null
}): Promise<Auction> {
  const res = await api.post<Auction>('/api/auctions', body)
  return res.data
}

// ---- Auction events (seller-created groupings, scoped to one category e.g. Bank Vehicles/Insurance) ----
export async function getEvents(categorySlug?: string): Promise<AuctionEvent[]> {
  const res = await api.get<AuctionEvent[]>('/api/events', { params: categorySlug ? { category: categorySlug } : {} })
  return res.data
}
export async function getMyEvents(): Promise<AuctionEvent[]> {
  const res = await api.get<AuctionEvent[]>('/api/events/mine')
  return res.data
}
export async function getEvent(id: string): Promise<AuctionEvent> {
  const res = await api.get<AuctionEvent>(`/api/events/${id}`)
  return res.data
}
export async function createEvent(body: {
  name: string; location: string; startTime: string; closingTime: string; categoryId: string
}): Promise<AuctionEvent> {
  const res = await api.post<AuctionEvent>('/api/events', body)
  return res.data
}
export async function registerToBid(id: string): Promise<RegisterResult> {
  const res = await api.post<RegisterResult>(`/api/auctions/${id}/register`)
  return res.data
}
export interface PlaceBidResult { currentEndTime: string; currentHighestBid: number; leading: boolean }
export async function placeBid(id: string, amount: number): Promise<PlaceBidResult> {
  const res = await api.post<PlaceBidResult>(`/api/auctions/${id}/bids`, { amount })
  return res.data
}
export async function getMyWins(): Promise<Auction[]> {
  const res = await api.get<Auction[]>('/api/auctions/mine/won')
  return res.data
}

// ---- Wallet ----
export async function getWallet(): Promise<WalletBalance> {
  const res = await api.get<WalletBalance>('/api/wallet')
  return res.data
}
// Real top-up: creates a Razorpay Order for the frontend to open Checkout against.
export async function createTopUpOrder(amount: number): Promise<OrderIntent> {
  const res = await api.post<OrderIntent>('/api/wallet/topup/order', { amount })
  return res.data
}
// Called right after Razorpay Checkout succeeds — verifies the signature and credits the wallet.
export async function verifyTopUp(orderId: string, paymentId: string, signature: string): Promise<WalletBalance> {
  const res = await api.post<WalletBalance>('/api/wallet/topup/verify', { orderId, paymentId, signature })
  return res.data
}
export async function withdraw(amount: number): Promise<WithdrawResult> {
  const res = await api.post<WithdrawResult>('/api/wallet/withdraw', { amount })
  return res.data
}
export async function getMyTransactions(): Promise<WalletTransaction[]> {
  const res = await api.get<WalletTransaction[]>('/api/wallet/transactions')
  return res.data
}

// ---- Seller Razorpay payouts ----
// No OAuth-redirect onboarding like Stripe Connect — bank details are submitted directly.
export async function getSellerPayoutStatus(): Promise<SellerPayoutStatus> {
  const res = await api.get<SellerPayoutStatus>('/api/seller/payouts/status')
  return res.data
}
export async function saveSellerPayoutAccount(
  accountHolderName: string, accountNumber: string, ifsc: string,
): Promise<SellerPayoutStatus> {
  const res = await api.post<SellerPayoutStatus>('/api/seller/payouts/account', { accountHolderName, accountNumber, ifsc })
  return res.data
}

// ---- Auction settlement ----
export async function completeAuctionPayment(auctionId: string): Promise<Auction> {
  const res = await api.post<Auction>(`/api/auctions/${auctionId}/complete-payment`)
  return res.data
}

// ---- Disputes (user-facing) ----
export async function raiseDispute(auctionId: string, reason: string): Promise<Dispute> {
  const res = await api.post<Dispute>('/api/disputes', { auctionId, reason })
  return res.data
}
export async function getMyDisputes(): Promise<Dispute[]> {
  const res = await api.get<Dispute[]>('/api/disputes/mine')
  return res.data
}

// ---- Admin ----
export async function getAdminStats(): Promise<AdminStats> {
  const res = await api.get<AdminStats>('/api/admin/stats')
  return res.data
}
export async function getAdminUsers(params?: { search?: string; role?: string; active?: boolean; page?: number; size?: number }): Promise<PageResponse<AdminUser>> {
  const res = await api.get<PageResponse<AdminUser>>('/api/admin/users', { params })
  return res.data
}
export async function getAdminUser(id: string): Promise<AdminUser> {
  const res = await api.get<AdminUser>(`/api/admin/users/${id}`)
  return res.data
}
export async function suspendUser(id: string): Promise<AdminUser> {
  const res = await api.post<AdminUser>(`/api/admin/users/${id}/suspend`)
  return res.data
}
export async function reactivateUser(id: string): Promise<AdminUser> {
  const res = await api.post<AdminUser>(`/api/admin/users/${id}/reactivate`)
  return res.data
}
export async function getAdminUserHolds(id: string): Promise<AdminHold[]> {
  const res = await api.get<AdminHold[]>(`/api/admin/users/${id}/holds`)
  return res.data
}
export async function getAdminUserBids(id: string): Promise<AdminUserBid[]> {
  const res = await api.get<AdminUserBid[]>(`/api/admin/users/${id}/bids`)
  return res.data
}
export async function releaseAdminHold(holdId: string): Promise<ReleaseHoldResult> {
  const res = await api.post<ReleaseHoldResult>(`/api/admin/holds/${holdId}/release`)
  return res.data
}
export async function getAdminListings(status?: string, page = 0, size = 20): Promise<PageResponse<AdminListing>> {
  const res = await api.get<PageResponse<AdminListing>>('/api/admin/listings', { params: { ...(status ? { status } : {}), page, size } })
  return res.data
}
export async function getAdminAuctions(status?: string, page = 0, size = 20): Promise<PageResponse<AdminAuction>> {
  const res = await api.get<PageResponse<AdminAuction>>('/api/admin/auctions', { params: { ...(status ? { status } : {}), page, size } })
  return res.data
}
export async function forceCloseAuction(id: string): Promise<AdminAuction> {
  const res = await api.post<AdminAuction>(`/api/admin/auctions/${id}/force-close`)
  return res.data
}
export async function updateAuction(id: string, body: {
  title: string; basePrice: number; startTime: string; endTime: string
}): Promise<AdminAuction> {
  const res = await api.patch<AdminAuction>(`/api/admin/auctions/${id}`, body)
  return res.data
}
export async function getAdminDisputes(status?: string, page = 0, size = 20): Promise<PageResponse<Dispute>> {
  const res = await api.get<PageResponse<Dispute>>('/api/admin/disputes', { params: { ...(status ? { status } : {}), page, size } })
  return res.data
}
export async function resolveDispute(id: string, adminNotes: string, refundBuyer = false): Promise<Dispute> {
  const res = await api.post<Dispute>(`/api/admin/disputes/${id}/resolve`, { adminNotes, refundBuyer })
  return res.data
}
export async function getAdminKycQueue(status?: string, page = 0, size = 20): Promise<PageResponse<AdminKyc>> {
  const res = await api.get<PageResponse<AdminKyc>>('/api/admin/kyc', { params: { ...(status ? { status } : {}), page, size } })
  return res.data
}
export async function approveKyc(userId: string, remarks?: string): Promise<AdminKyc> {
  const res = await api.post<AdminKyc>(`/api/admin/kyc/${userId}/approve`, remarks ? { remarks } : undefined)
  return res.data
}
export async function rejectKyc(userId: string, remarks: string): Promise<AdminKyc> {
  const res = await api.post<AdminKyc>(`/api/admin/kyc/${userId}/reject`, { remarks })
  return res.data
}
export async function getAdminCategories(): Promise<AdminCategory[]> {
  const res = await api.get<AdminCategory[]>('/api/admin/categories')
  return res.data
}
export async function createAdminCategory(body: { name: string; slug: string; parentId?: string | null }): Promise<AdminCategory> {
  const res = await api.post<AdminCategory>('/api/admin/categories', body)
  return res.data
}
export async function getAdminCategoryAttributes(categoryId: string): Promise<AdminCategoryAttribute[]> {
  const res = await api.get<AdminCategoryAttribute[]>(`/api/admin/categories/${categoryId}/attributes`)
  return res.data
}
export async function createAdminCategoryAttribute(categoryId: string, body: {
  key: string; label: string; valueType: 'TEXT' | 'NUMBER' | 'BOOLEAN' | 'ENUM'; filterable?: boolean; sortOrder?: number
}): Promise<AdminCategoryAttribute> {
  const res = await api.post<AdminCategoryAttribute>(`/api/admin/categories/${categoryId}/attributes`, body)
  return res.data
}

// ---- Admin: Add Stock (single item + bulk Excel import) ----
export async function createStockListing(body: {
  title: string; description?: string; categoryId?: string; categoryName?: string; brand?: string
  condition?: string; city?: string; state?: string; zip?: string; reservePrice: number
  attributes?: Record<string, string>; swipeStock: boolean; requiredTier?: SubscriptionTier
}): Promise<StockListing> {
  const res = await api.post<StockListing>('/api/admin/stock/listings', body)
  return res.data
}
export async function uploadStockImage(listingId: string, file: File, cover: boolean): Promise<StockImage> {
  const form = new FormData()
  form.append('file', file)
  form.append('cover', String(cover))
  const res = await api.post<StockImage>(`/api/admin/stock/listings/${listingId}/images`, form)
  return res.data
}
export async function createStockAuction(listingId: string, body: {
  basePrice?: number | null; startTime?: string | null; endTime?: string | null; eventId?: string | null
}): Promise<StockAuction> {
  const res = await api.post<StockAuction>(`/api/admin/stock/listings/${listingId}/auction`, body)
  return res.data
}
export async function bulkImportStock(file: File, swipeStock: boolean): Promise<BulkImportResult> {
  const form = new FormData()
  form.append('file', file)
  const res = await api.post<BulkImportResult>('/api/admin/stock/bulk', form, { params: { swipeStock } })
  return res.data
}
// Requires the admin Authorization header, so this can't be a plain <a href> link — fetch as a blob
// and trigger the download client-side instead.
export async function downloadStockTemplate(): Promise<void> {
  const res = await api.get('/api/admin/stock/template', { responseType: 'blob' })
  const url = URL.createObjectURL(res.data as Blob)
  const a = document.createElement('a')
  a.href = url
  a.download = 'swipe-stock-template.xlsx'
  a.click()
  URL.revokeObjectURL(url)
}

// ---- Platform settings (public reads) ----
export async function getRegistrationFee(): Promise<number> {
  const res = await api.get<number>('/api/settings/registration-fee')
  return res.data
}
export async function getMobileVerificationRequired(): Promise<boolean> {
  const res = await api.get<boolean>('/api/settings/mobile-verification-required')
  return res.data
}
export async function getSubscriptionPrices(): Promise<SubscriptionPrice[]> {
  const res = await api.get<SubscriptionPrice[]>('/api/settings/subscription-prices')
  return res.data
}

// ---- Platform settings (admin writes) ----
export async function updateRegistrationFee(fee: number): Promise<number> {
  const res = await api.put<number>('/api/admin/settings/registration-fee', { fee })
  return res.data
}
export async function updateSubscriptionPrices(prices: SubscriptionPrice[]): Promise<SubscriptionPrice[]> {
  const res = await api.put<SubscriptionPrice[]>('/api/admin/settings/subscription-prices', { prices })
  return res.data
}
export async function updateListingRequiredTier(listingId: string, requiredTier: SubscriptionTier): Promise<AdminListing> {
  const res = await api.patch<AdminListing>(`/api/admin/listings/${listingId}/required-tier`, { requiredTier })
  return res.data
}

// ---- Subscriptions — real Razorpay payment, order then verify ----
export async function getMySubscription(): Promise<MySubscription> {
  const res = await api.get<MySubscription>('/api/subscriptions/me')
  return res.data
}
export async function createSubscriptionOrder(tier: SubscriptionTier, billingCycle: BillingCycle): Promise<OrderIntent> {
  const res = await api.post<OrderIntent>('/api/subscriptions/order', { tier, billingCycle })
  return res.data
}
export async function verifySubscription(orderId: string, paymentId: string, signature: string): Promise<MySubscription> {
  const res = await api.post<MySubscription>('/api/subscriptions/verify', { orderId, paymentId, signature })
  return res.data
}

export { errorMessage }
