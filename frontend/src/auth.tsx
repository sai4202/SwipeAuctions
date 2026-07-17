import { createContext, useContext, useState, type ReactNode } from 'react'
import type { LoginData } from './api'

interface AuthState {
  token: string | null
  email: string | null
  signIn: (data: LoginData) => void
  signOut: () => void
  isAuthenticated: boolean
}

const AuthContext = createContext<AuthState | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(() => localStorage.getItem('token'))
  const [email, setEmail] = useState<string | null>(() => localStorage.getItem('email'))

  const signIn = (data: LoginData) => {
    localStorage.setItem('token', data.token)
    localStorage.setItem('email', data.email)
    setToken(data.token)
    setEmail(data.email)
  }
  const signOut = () => {
    localStorage.removeItem('token')
    localStorage.removeItem('email')
    setToken(null)
    setEmail(null)
  }

  return (
    <AuthContext.Provider value={{ token, email, signIn, signOut, isAuthenticated: !!token }}>
      {children}
    </AuthContext.Provider>
  )
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
