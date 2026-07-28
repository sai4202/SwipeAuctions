import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { setSessionExpiredHandler } from './api'

/** localStorage keys this provider owns — used to detect relevant cross-tab `storage` events. */
const AUTH_STORAGE_KEYS = ['token', 'email', 'role', 'lastRole', 'kycCompleted', 'registrationFeePaid', 'subscriptionTier', 'subscriptionExpiresAt']

interface SignInData {
  token: string; email: string; role: string; kycCompleted?: boolean; registrationFeePaid?: boolean
  subscriptionTier?: string; subscriptionExpiresAt?: string | null
}

interface AuthState {
  token: string | null
  email: string | null
  role: string | null
  /** Role of the most recent signed-in session, kept even after signOut/expiry — lets logged-out UI
   *  (e.g. the header's Login link) send an ex-admin back to admin login instead of the customer one. */
  lastRole: string | null
  kycCompleted: boolean
  registrationFeePaid: boolean
  subscriptionTier: string
  subscriptionExpiresAt: string | null
  signIn: (data: SignInData) => void
  signOut: () => void
  markKycCompleted: () => void
  markRegistrationFeePaid: () => void
  setSubscription: (tier: string, expiresAt: string | null) => void
  isAuthenticated: boolean
}

const AuthContext = createContext<AuthState | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(() => localStorage.getItem('token'))
  const [email, setEmail] = useState<string | null>(() => localStorage.getItem('email'))
  const [role, setRole] = useState<string | null>(() => localStorage.getItem('role'))
  const [lastRole, setLastRole] = useState<string | null>(() => localStorage.getItem('lastRole'))
  const [kycCompleted, setKycCompleted] = useState<boolean>(() => localStorage.getItem('kycCompleted') === 'true')
  const [registrationFeePaid, setRegistrationFeePaid] = useState<boolean>(() => localStorage.getItem('registrationFeePaid') === 'true')
  const [subscriptionTier, setSubscriptionTier] = useState<string>(() => localStorage.getItem('subscriptionTier') || 'NONE')
  const [subscriptionExpiresAt, setSubscriptionExpiresAt] = useState<string | null>(() => localStorage.getItem('subscriptionExpiresAt'))

  const signIn = (data: SignInData) => {
    localStorage.setItem('token', data.token)
    localStorage.setItem('email', data.email)
    localStorage.setItem('role', data.role)
    localStorage.setItem('lastRole', data.role)
    localStorage.setItem('kycCompleted', String(!!data.kycCompleted))
    localStorage.setItem('registrationFeePaid', String(!!data.registrationFeePaid))
    localStorage.setItem('subscriptionTier', data.subscriptionTier || 'NONE')
    if (data.subscriptionExpiresAt) localStorage.setItem('subscriptionExpiresAt', data.subscriptionExpiresAt)
    else localStorage.removeItem('subscriptionExpiresAt')
    setToken(data.token)
    setEmail(data.email)
    setRole(data.role)
    setLastRole(data.role)
    setKycCompleted(!!data.kycCompleted)
    setRegistrationFeePaid(!!data.registrationFeePaid)
    setSubscriptionTier(data.subscriptionTier || 'NONE')
    setSubscriptionExpiresAt(data.subscriptionExpiresAt || null)
  }
  const signOut = () => {
    localStorage.removeItem('token')
    localStorage.removeItem('email')
    localStorage.removeItem('role')
    localStorage.removeItem('kycCompleted')
    localStorage.removeItem('registrationFeePaid')
    localStorage.removeItem('subscriptionTier')
    localStorage.removeItem('subscriptionExpiresAt')
    setToken(null)
    setEmail(null)
    setRole(null)
    setKycCompleted(false)
    setRegistrationFeePaid(false)
    setSubscriptionTier('NONE')
    setSubscriptionExpiresAt(null)
  }
  const markKycCompleted = () => {
    localStorage.setItem('kycCompleted', 'true')
    setKycCompleted(true)
  }
  const markRegistrationFeePaid = () => {
    localStorage.setItem('registrationFeePaid', 'true')
    setRegistrationFeePaid(true)
  }
  const setSubscription = (tier: string, expiresAt: string | null) => {
    localStorage.setItem('subscriptionTier', tier)
    if (expiresAt) localStorage.setItem('subscriptionExpiresAt', expiresAt)
    else localStorage.removeItem('subscriptionExpiresAt')
    setSubscriptionTier(tier)
    setSubscriptionExpiresAt(expiresAt)
  }

  const navigate = useNavigate()
  const [sessionExpired, setSessionExpired] = useState(false)
  const [expiredRole, setExpiredRole] = useState<string | null>(null)

  // Always holds the latest `role` without forcing the handler-registration effect below to re-run
  // on every role change (and without the handler closing over a stale value from mount time).
  const roleRef = useRef(role)
  useEffect(() => { roleRef.current = role }, [role])

  // Guards against two authenticated requests 401-ing around the same time both invoking the
  // handler: without this, the second invocation would read localStorage.getItem('role') *after*
  // the first invocation's signOut() already cleared it, clobbering expiredRole with null and
  // sending e.g. an admin to the customer /login page instead of /admin-login.
  const handledExpiryRef = useRef(false)

  useEffect(() => {
    setSessionExpiredHandler(() => {
      if (handledExpiryRef.current) return
      handledExpiryRef.current = true
      setExpiredRole(roleRef.current)
      signOut()
      setSessionExpired(true)
    })
    return () => setSessionExpiredHandler(null)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const reLogin = () => {
    setSessionExpired(false)
    handledExpiryRef.current = false
    navigate(expiredRole === 'ADMIN' ? '/admin-login' : '/login')
  }

  // Cross-tab sync: another tab (or same-origin iframe "floating tab" — see FloatingTabsContext)
  // logging in/out only updates localStorage there; without this, this tab's UI keeps showing the
  // previous auth state indefinitely, silently sending unauthenticated requests once the token is
  // actually gone.
  useEffect(() => {
    const onStorage = (e: StorageEvent) => {
      if (e.key !== null && !AUTH_STORAGE_KEYS.includes(e.key)) return
      setToken(localStorage.getItem('token'))
      setEmail(localStorage.getItem('email'))
      setRole(localStorage.getItem('role'))
      setLastRole(localStorage.getItem('lastRole'))
      setKycCompleted(localStorage.getItem('kycCompleted') === 'true')
      setRegistrationFeePaid(localStorage.getItem('registrationFeePaid') === 'true')
      setSubscriptionTier(localStorage.getItem('subscriptionTier') || 'NONE')
      setSubscriptionExpiresAt(localStorage.getItem('subscriptionExpiresAt'))
    }
    window.addEventListener('storage', onStorage)
    return () => window.removeEventListener('storage', onStorage)
  }, [])

  return (
    <AuthContext.Provider value={{ token, email, role, lastRole, kycCompleted, registrationFeePaid, subscriptionTier, subscriptionExpiresAt, signIn, signOut, markKycCompleted, markRegistrationFeePaid, setSubscription, isAuthenticated: !!token }}>
      {children}
      {sessionExpired && (
        <div className="modal-backdrop">
          <div className="modal-card" style={{ maxWidth: 380 }}>
            <div className="modal-head"><h3>Session expired</h3></div>
            <div className="modal-body">
              <p>Your session has expired. Please log in again to continue.</p>
            </div>
            <div className="modal-foot">
              <button className="btn" onClick={reLogin}>Log in again</button>
            </div>
          </div>
        </div>
      )}
    </AuthContext.Provider>
  )
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
