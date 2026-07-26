import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { getWallet, type WalletBalance } from './api'
import { useAuth } from './auth'

interface WalletState {
  wallet: WalletBalance | null
  /** Re-fetch after anything that moves money — register/bid/top-up/withdraw — so every consumer
   *  (the header's "Available Credit", the wallet page, the bid form) shows the same live balance
   *  instead of each keeping its own snapshot that goes stale the moment another page updates it. */
  refreshWallet: () => void
}

const WalletContext = createContext<WalletState | undefined>(undefined)

export function WalletProvider({ children }: { children: ReactNode }) {
  const { isAuthenticated, role } = useAuth()
  const isAdmin = role === 'ADMIN'
  const [wallet, setWallet] = useState<WalletBalance | null>(null)

  const refreshWallet = () => {
    if (!isAuthenticated || isAdmin) { setWallet(null); return }
    getWallet().then(setWallet).catch(() => setWallet(null))
  }

  useEffect(refreshWallet, [isAuthenticated, isAdmin])

  return <WalletContext.Provider value={{ wallet, refreshWallet }}>{children}</WalletContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useWallet(): WalletState {
  const ctx = useContext(WalletContext)
  if (!ctx) throw new Error('useWallet must be used within WalletProvider')
  return ctx
}
