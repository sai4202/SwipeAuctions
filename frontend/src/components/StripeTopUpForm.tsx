import { useEffect, useMemo, useState } from 'react'
import { loadStripe, type Stripe } from '@stripe/stripe-js'
import { Elements, PaymentElement, useElements, useStripe } from '@stripe/react-stripe-js'
import { createTopUpIntent, errorMessage } from '../api'

function CheckoutInner({ onDone }: { onDone: (msg: string, ok: boolean) => void }) {
  const stripe = useStripe()
  const elements = useElements()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const submit = async () => {
    if (!stripe || !elements) return
    setBusy(true); setError('')
    const { error: submitError } = await elements.submit()
    if (submitError) { setError(submitError.message || 'Payment details are incomplete.'); setBusy(false); return }

    const { error: confirmError, paymentIntent } = await stripe.confirmPayment({
      elements,
      confirmParams: { return_url: window.location.href },
      redirect: 'if_required',
    })
    setBusy(false)
    if (confirmError) { setError(confirmError.message || 'Payment failed.'); return }
    if (paymentIntent?.status === 'succeeded' || paymentIntent?.status === 'processing') {
      onDone('Payment submitted — your wallet updates once Stripe confirms it (usually a few seconds).', true)
    } else {
      onDone(`Payment status: ${paymentIntent?.status ?? 'unknown'}.`, false)
    }
  }

  return (
    <div>
      <PaymentElement />
      {error && <div className="error" style={{ marginTop: 10 }}>{error}</div>}
      <div style={{ marginTop: 14 }}>
        <button type="button" className="btn" disabled={busy || !stripe} onClick={submit}>
          {busy ? 'Processing…' : 'Pay & add funds'}
        </button>
      </div>
    </div>
  )
}

/** Real Stripe-backed wallet top-up. Renders nothing usable until Stripe is configured server-side
 * (STRIPE_SECRET_KEY) — surfaces that as a plain message rather than a broken form. */
export default function StripeTopUpForm({ amount, onDone }: { amount: number; onDone: (msg: string, ok: boolean) => void }) {
  const [clientSecret, setClientSecret] = useState<string | null>(null)
  const [publishableKey, setPublishableKey] = useState<string | null>(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    setLoading(true); setError(''); setClientSecret(null)
    createTopUpIntent(amount)
      .then((intent) => {
        if (cancelled) return
        setClientSecret(intent.clientSecret)
        setPublishableKey(intent.publishableKey)
      })
      .catch((e) => { if (!cancelled) setError(errorMessage(e)) })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [amount])

  const stripePromise = useMemo<Promise<Stripe | null> | null>(
    () => (publishableKey ? loadStripe(publishableKey) : null),
    [publishableKey],
  )

  if (loading) return <p className="muted">Starting payment…</p>
  if (error) return <div className="error">{error}</div>
  if (!clientSecret || !stripePromise) return null

  return (
    <Elements stripe={stripePromise} options={{ clientSecret }}>
      <CheckoutInner onDone={onDone} />
    </Elements>
  )
}
