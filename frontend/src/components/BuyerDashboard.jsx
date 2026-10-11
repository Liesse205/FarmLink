import { useEffect, useMemo, useState } from 'react'
import { api, formatDate } from '../api.js'
import RequestContact from './RequestContact.jsx'
import {
  Banner, EmptyState, ErrorState, Loading, PageHeader, StatCard, StatusBadge
} from './ui.jsx'

export default function BuyerDashboard({ user, onOpenListing, onBrowse }) {
  const [requests, setRequests] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [reloadKey, setReloadKey] = useState(0)
  const [tab, setTab] = useState('ALL')

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError('')
    api.get('/api/requests')
      .then((data) => { if (!cancelled) setRequests(data) })
      .catch((e) => { if (!cancelled) setError(e.message) })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [reloadKey])

  const counts = useMemo(() => ({
    ALL: requests.length,
    PENDING: requests.filter((r) => r.status === 'PENDING').length,
    ACCEPTED: requests.filter((r) => r.status === 'ACCEPTED').length,
    REJECTED: requests.filter((r) => r.status === 'REJECTED').length
  }), [requests])

  const visible = tab === 'ALL' ? requests : requests.filter((r) => r.status === tab)

  if (loading) return <Loading label="Loading your requests…" />
  if (error && requests.length === 0) {
    return <ErrorState message={error} onRetry={() => { setReloadKey((k) => k + 1); setLoading(true) }} />
  }

  return (
    <section>
      <PageHeader
        title="Buyer dashboard"
        subtitle={`Track every purchase request${user?.name ? `, ${user.name.split(' ')[0]}` : ''} — from sent to accepted.`}
      >
        <button className="btn primary" onClick={onBrowse}>Browse marketplace</button>
      </PageHeader>

      <Banner kind="info">
        GoFarm notifications arrive in-app: when a farmer answers your request you&apos;ll see
        the bell in the top bar light up.
      </Banner>

      <div className="stat-grid">
        <StatCard value={counts.ALL} label="Total requests" />
        <StatCard value={counts.PENDING} label="Pending" hint="waiting for the farmer" />
        <StatCard value={counts.ACCEPTED} label="Accepted" />
        <StatCard value={counts.REJECTED} label="Rejected" />
      </div>

      <div className="card dash-section">
        <div className="card-head">
          <div>
            <h2>My purchase requests</h2>
            <p className="hint">Newest first · click a listing to open it.</p>
          </div>
        </div>

        <div className="tabs" role="tablist">
          {['ALL', 'PENDING', 'ACCEPTED', 'REJECTED'].map((key) => (
            <button
              key={key}
              role="tab"
              aria-selected={tab === key}
              className={tab === key ? 'active' : ''}
              onClick={() => setTab(key)}
            >
              {key === 'ALL' ? 'All' : key.charAt(0) + key.slice(1).toLowerCase()}
              <span className="tab-count">{counts[key]}</span>
            </button>
          ))}
        </div>

        {requests.length === 0 ? (
          <EmptyState
            title="You haven't requested any produce yet"
            description="Browse the marketplace, open a listing and send your first purchase request — it takes a few seconds."
            action={<button className="btn primary" onClick={onBrowse}>Browse produce</button>}
          />
        ) : visible.length === 0 ? (
          <EmptyState
            title={`No ${tab.toLowerCase()} requests`}
            description="Nothing in this category right now."
            action={<button className="btn secondary" onClick={() => setTab('ALL')}>Show all requests</button>}
          />
        ) : (
          <div className="stack">
            {visible.map((request) => (
              <div className="row wrap" key={request.id}>
                <div className="row-main">
                  <div className="row-top" style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
                    <strong
                      style={{ cursor: 'pointer', color: 'var(--green-700)' }}
                      onClick={() => onOpenListing?.(request.listingId)}
                      title="Open listing"
                    >
                      {request.listingName}
                    </strong>
                    <StatusBadge status={request.status} />
                  </div>
                  <span className="row-sub">
                    {request.quantity} kg · sent {formatDate(request.createdAt)}
                    {request.message ? ` · “${request.message}”` : ''}
                  </span>
                </div>
                {request.status === 'ACCEPTED' && (
                  <RequestContact requestId={request.id} />
                )}
                <div className="row-actions">
                  <button className="btn secondary sm" onClick={() => onOpenListing?.(request.listingId)}>
                    View listing
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </section>
  )
}
