import axios from 'axios'

const api = axios.create({ baseURL: '/' })

// Attach the JWT (if present) to every request.
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

export default api

// ---- Types ----
export interface LoginData {
  userId: string
  email: string
  mobileNumber: string
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
}
export interface Category {
  id: string
  name: string
  slug: string
  parentId: string | null
}
export interface WalletBalance {
  availableBalance: number
  heldBalance: number
}
export interface RegisterResult {
  message: string
  availableBalance: number
  heldBalance: number
}

// ApiResponse<T> envelope used by the auth endpoints.
interface ApiEnvelope<T> {
  success: boolean
  message: string
  data: T
}

function errorMessage(e: unknown): string {
  if (axios.isAxiosError(e)) {
    return (e.response?.data as { message?: string })?.message ?? e.message
  }
  return 'Unexpected error'
}

// ---- Auth ----
export async function login(emailOrMobile: string, password: string): Promise<LoginData> {
  const res = await api.post<ApiEnvelope<LoginData>>('/api/auth/login', { emailOrMobile, password })
  return res.data.data
}
export async function register(body: {
  email: string; mobileNumber: string; password: string; confirmPassword: string
}): Promise<string> {
  const res = await api.post<ApiEnvelope<string>>('/api/auth/register', body)
  return res.data.message
}
export async function verifyEmailOtp(email: string, otp: string): Promise<string> {
  const res = await api.post<ApiEnvelope<string>>('/api/auth/verify-email', { email, otp })
  return res.data.message
}

// ---- Catalog / auctions (plain DTOs) ----
export async function getAuctions(status?: string): Promise<Auction[]> {
  const res = await api.get<Auction[]>('/api/auctions', { params: status ? { status } : {} })
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
export async function registerToBid(id: string): Promise<RegisterResult> {
  const res = await api.post<RegisterResult>(`/api/auctions/${id}/register`)
  return res.data
}
export async function placeBid(id: string, amount: number): Promise<void> {
  await api.post(`/api/auctions/${id}/bids`, { amount })
}

// ---- Wallet ----
export async function getWallet(): Promise<WalletBalance> {
  const res = await api.get<WalletBalance>('/api/wallet')
  return res.data
}
export async function topUp(amount: number): Promise<WalletBalance> {
  const res = await api.post<WalletBalance>('/api/wallet/topup', { amount })
  return res.data
}

export { errorMessage }
