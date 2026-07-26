import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import './index.css'
import App from './App.tsx'
import { AuthProvider } from './auth.tsx'
import { WalletProvider } from './WalletContext.tsx'
import { NotificationProvider } from './NotificationContext.tsx'
import { FloatingTabsProvider } from './FloatingTabsContext.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <WalletProvider>
          <NotificationProvider>
            <FloatingTabsProvider>
              <App />
            </FloatingTabsProvider>
          </NotificationProvider>
        </WalletProvider>
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>,
)
