import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
// Plus Jakarta Sans — self-hosted (not a Google Fonts CDN request) so the app's look doesn't
// depend on an external network call succeeding, and every weight the CSS actually uses is
// loaded up front.
import '@fontsource/plus-jakarta-sans/400.css'
import '@fontsource/plus-jakarta-sans/500.css'
import '@fontsource/plus-jakarta-sans/600.css'
import '@fontsource/plus-jakarta-sans/700.css'
import '@fontsource/plus-jakarta-sans/800.css'
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
