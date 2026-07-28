import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../auth'

/**
 * Gates a route behind sign-in: anonymous visitors are sent to `redirectTo` (default /login),
 * carrying the page they were headed to so the destination can return them there afterward.
 * Auction detail pages pass redirectTo="/register" since an anonymous visitor there needs a full
 * account (plus the one-time registration fee) before viewing details, not just to sign back in.
 */
export default function RequireAuth({ children, redirectTo = '/login' }: { children: ReactNode; redirectTo?: string }) {
  const { isAuthenticated } = useAuth()
  const location = useLocation()
  if (!isAuthenticated) {
    return <Navigate to={redirectTo} state={{ from: location.pathname + location.search }} replace />
  }
  return <>{children}</>
}
