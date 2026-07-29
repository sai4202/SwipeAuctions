import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import './index.css'
import App from './App.tsx'
import { AuthProvider } from './auth.tsx'
import { WalletProvider } from './WalletContext.tsx'
import { StompProvider } from './StompContext.tsx'
import { NotificationProvider } from './NotificationContext.tsx'
import { FloatingTabsProvider } from './FloatingTabsContext.tsx'
import { ThemeProvider } from './ThemeContext.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <ThemeProvider>
        <AuthProvider>
          <WalletProvider>
            <StompProvider>
              <NotificationProvider>
                <FloatingTabsProvider>
                  <App />
                </FloatingTabsProvider>
              </NotificationProvider>
            </StompProvider>
          </WalletProvider>
        </AuthProvider>
      </ThemeProvider>
    </BrowserRouter>
  </StrictMode>,
)
