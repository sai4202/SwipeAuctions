import { useEffect, useState } from 'react'
import { getDashboardSummary, errorMessage, type DashboardSummary } from '../../api'
import { money } from '../../util'
import { StatTilesSkeleton } from '../../components/Skeleton'
import AdminAnalytics from '../../components/AdminAnalytics'
import KycStatusPie from '../../components/KycStatusPie'
import RegistrationsChart from '../../components/RegistrationsChart'
import CashFlowChart from '../../components/CashFlowChart'
import QuickStatsPanel from '../../components/QuickStatsPanel'
import PendingKycPreview from '../../components/PendingKycPreview'
import PendingPaymentsPreview from '../../components/PendingPaymentsPreview'
import GeoBreakdown from '../../components/GeoBreakdown'
import { AdminPageHeader, StatTile } from './shared'

export default function AdminOverviewPage() {
  const [summary, setSummary] = useState<DashboardSummary | null>(null)
  const [error, setError] = useState('')

  useEffect(() => { getDashboardSummary().then(setSummary).catch((e) => setError(errorMessage(e))) }, [])

  return (
    <div>
      <AdminPageHeader section="Dashboard" title="Admin Control Panel" subtitle="Platform activity at a glance." />

      {error && <div className="error">{error}</div>}
      {!summary ? <StatTilesSkeleton /> : (
        <div className="stat-tiles">
          <StatTile label="Total revenue" value={money(summary.gmv)} icon="💰" color="blue"
                    hint={`Settled: ${summary.openAuctions} open auctions`} />
          <StatTile label="Transactions today" value={String(summary.transactionsToday)} icon="📋" color="green"
                    hint={`Processed: ${money(summary.transactionsAmountToday)}`} />
          <StatTile label="Registered users" value={String(summary.totalUsers)} icon="👥" color="purple"
                    hint={`KYC: ${summary.kycVerified} verified`} />
          <StatTile label="Pending approvals" value={String(summary.pendingApprovals)} icon="✅" color="amber"
                    hint={`Rejected: ${summary.kycRejected}`} />
        </div>
      )}

      <div className="chart-grid" style={{ gridTemplateColumns: '2fr 1fr', alignItems: 'start' }}>
        <CashFlowChart />
        <KycStatusPie />
      </div>

      <div style={{ marginTop: 18 }}>
        <RegistrationsChart />
      </div>

      <div className="chart-grid" style={{ marginTop: 18, gridTemplateColumns: '2fr 1fr', alignItems: 'start' }}>
        <PendingKycPreview />
        {summary && <QuickStatsPanel summary={summary} />}
      </div>

      <div style={{ marginTop: 18 }}>
        <PendingPaymentsPreview />
      </div>

      <div style={{ marginTop: 18 }}>
        <GeoBreakdown />
      </div>

      <AdminAnalytics />
    </div>
  )
}
