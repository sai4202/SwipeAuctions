import type { DashboardSummary } from '../api'

/** Reference dashboard's "Quick Stats" side panel. "Active Bank Rails" doesn't map to anything
 *  real here (SwipeAuctions settles through a single configured Razorpay account, not
 *  admin-managed bank rails) — reframed as the two gateway-configured flags instead. */
export default function QuickStatsPanel({ summary }: { summary: DashboardSummary }) {
  const rows: { label: string; value: string; tone: 'blue' | 'amber' | 'green' }[] = [
    { label: 'Verified Users', value: String(summary.kycVerified), tone: 'blue' },
    { label: 'Pending Payments', value: String(summary.pendingPayments), tone: 'amber' },
    { label: 'Payments Gateway', value: summary.paymentsGatewayConfigured ? 'Live' : 'Off', tone: summary.paymentsGatewayConfigured ? 'green' : 'amber' },
    { label: 'Payouts Gateway', value: summary.payoutsGatewayConfigured ? 'Live' : 'Off', tone: summary.payoutsGatewayConfigured ? 'green' : 'amber' },
    { label: 'Total Plans', value: String(summary.totalPlans), tone: 'blue' },
    { label: 'Pending KYC', value: String(summary.kycPending), tone: 'amber' },
  ]

  return (
    <div className="card">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
        <h3 style={{ margin: 0, fontSize: 14 }}>Quick Stats</h3>
        <span style={{ display: 'flex', alignItems: 'center', gap: 5, fontSize: 11.5 }} className="muted">
          <span style={{ width: 7, height: 7, borderRadius: '50%', background: 'var(--red)', display: 'inline-block' }} />
          Live
        </span>
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
        {rows.map((r) => (
          <div key={r.label} style={{
            display: 'flex', justifyContent: 'space-between', alignItems: 'center',
            padding: '9px 12px', border: '1px solid var(--border)', borderRadius: 10,
          }}>
            <span style={{ fontSize: 13 }}>{r.label}</span>
            <span className={`badge ${r.tone === 'green' ? 'ACTIVE' : r.tone === 'amber' ? 'PENDING' : ''}`}
                  style={r.tone === 'blue' ? { background: 'rgba(37,99,235,.12)', color: '#2563eb' } : undefined}>
              {r.value}
            </span>
          </div>
        ))}
      </div>
    </div>
  )
}
