import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../auth'

/**
 * Gates a route behind sign-in: anonymous visitors are sent to /login (carrying the page they
 * were headed to, so LoginPage can return them there) instead of being able to load the page.
 * Only the Home page (and the sign-in/registration flow itself) stays reachable while signed out.
 */
export default function RequireAuth({ children }: { children: ReactNode }) {
  const { isAuthenticated } = useAuth()
  const location = useLocation()
  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location.pathname + location.search }} replace />
  }
  return <>{children}</>
}
