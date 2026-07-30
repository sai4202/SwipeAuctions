import { useEffect, useRef, useState } from 'react'

interface RazorpaySuccessResponse {
  razorpay_order_id: string
  razorpay_payment_id: string
  razorpay_signature: string
}

interface RazorpayOptions {
  key: string
  order_id: string
  amount: number
  currency: string
  name: string
  description?: string
  prefill?: { name?: string; email?: string; contact?: string }
  theme?: { color?: string }
  handler: (response: RazorpaySuccessResponse) => void
  modal?: { ondismiss?: () => void }
}

declare global {
  interface Window {
    Razorpay: new (options: RazorpayOptions) => { open: () => void }
  }
}

const CHECKOUT_SRC = 'https://checkout.razorpay.com/v1/checkout.js'
let scriptPromise: Promise<void> | null = null

/** Loads Razorpay's hosted Checkout script once and caches the promise — every checkout on the
 *  page reuses the same `<script>` tag instead of injecting a new one each time. */
function loadCheckoutScript(): Promise<void> {
  if (scriptPromise) return scriptPromise
  scriptPromise = new Promise((resolve, reject) => {
    if (document.querySelector(`script[src="${CHECKOUT_SRC}"]`)) { resolve(); return }
    const script = document.createElement('script')
    script.src = CHECKOUT_SRC
    script.onload = () => resolve()
    script.onerror = () => reject(new Error('Could not load Razorpay Checkout'))
    document.body.appendChild(script)
  })
  return scriptPromise
}

interface RazorpayCheckoutProps {
  orderId: string
  amountPaise: number
  currency: string
  keyId: string
  description: string
  onSuccess: (paymentId: string, signature: string) => void
  onCancel: () => void
}

/**
 * Opens Razorpay's hosted Checkout modal for a pre-created Order. Used by wallet top-up,
 * registration fee, and subscription purchase — all three follow the same order → Checkout →
 * verify shape (see api.ts's createTopUpOrder/createRegistrationFeeOrder/createSubscriptionOrder +
 * their matching verify* calls); only what "verify" does server-side differs per flow.
 */
export default function RazorpayCheckout({
  orderId, amountPaise, currency, keyId, description, onSuccess, onCancel,
}: RazorpayCheckoutProps) {
  const [error, setError] = useState('')
  const openedForOrder = useRef<string | null>(null)

  useEffect(() => {
    if (openedForOrder.current === orderId) return
    openedForOrder.current = orderId
    let cancelled = false
    loadCheckoutScript()
      .then(() => {
        if (cancelled) return
        const razorpay = new window.Razorpay({
          key: keyId,
          order_id: orderId,
          amount: amountPaise,
          currency,
          name: 'SwipeAuctions',
          description,
          theme: { color: '#ef3d2e' },
          handler: (response) => onSuccess(response.razorpay_payment_id, response.razorpay_signature),
          modal: { ondismiss: onCancel },
        })
        razorpay.open()
      })
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Could not start payment.'))
    return () => { cancelled = true }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [orderId])

  if (error) return <div className="error">{error}</div>
  return <p className="muted">Opening Razorpay Checkout…</p>
}
