import { useCallback, useEffect, useState } from 'react'
import { api, formatDate, formatDateTime, money } from '../api.js'
import {
  Banner, EmptyState, ErrorState, Loading, PageHeader, RoleBadge, StatCard, StatusBadge
} from './ui.jsx'

export default function AdminDashboard() {
  const [stats, setStats] = useState(null)
  const [activity, setActivity] = useState([])
  const [users, setUsers] = useState(null)
  const [listings, setListings] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [tab, setTab] = useState('overview')
  const [notice, setNotice] = useState('')
  const [reloadKey, setReloadKey] = useState(0)

  const load = useCallback(async () => {
    try {
      setError('')
      const [statsData, activityData] = await Promise.all([
        api.get('/api/admin/stats'),
        api.get('/api/admin/activity')
      ])
      setStats(statsData)
      setActivity(activityData)

      if (tab === 'users' && users === null) {
        setUsers(await api.get('/api/admin/users'))
      }
      if (tab === 'listings' && listings === null) {
        setListings(await api.get('/api/admin/listings'))
      }
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }, [tab, users, listings])

  useEffect(() => { load() }, [load, reloadKey])

  const toggleListingStatus = async (listing) => {
    setNotice('')
    setError('')
    try {
      const next = listing.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
      await api.put(`/api/listings/${listing.id}`, {
        name: listing.name,
        category: listing.category,
        quantity: listing.quantity,
        pricePerUnit: listing.pricePerUnit,
        location: listing.location,
        availableFrom: listing.availableFrom,
        description: listing.description,
        status: next
      })
      setListings((current) => current.map((l) => (l.id === listing.id ? { ...l, status: next } : l)))
      setNotice(next === 'INACTIVE'
        ? `Listing “${listing.name}” deactivated.`
        : `Listing “${listing.name}” reactivated.`)
      setReloadKey((k) => k + 1)
    } catch (e) {
      setError(e.message)
    }
  }

  if (loading) return <Loading label="Loading admin dashboard…" />
  if (error && !stats) return <ErrorState message={error} onRetry={() => setReloadKey((k) => k + 1)} />

  return (
    <section>
      <PageHeader
        title="Admin dashboard"
        subtitle="Users, listings, requests and recent activity across GoFarm."
      />

      <Banner kind="success" onClose={() => setNotice('')}>{notice}</Banner>
      <Banner kind="error" onClose={() => setError('')}>{error}</Banner>

      <div className="tabs" role="tablist">
        {[
          ['overview', 'Overview'],
          ['users', 'Users'],
          ['listings', 'Listings']
        ].map(([key, label]) => (
          <button
            key={key}
            role="tab"
            aria-selected={tab === key}
            className={tab === key ? 'active' : ''}
            onClick={() => setTab(key)}
          >
            {label}
          </button>
        ))}
      </div>

      {tab === 'overview' && (
        <>
          <div className="stat-grid">
            <StatCard value={stats?.users} label="Total users" />
            <StatCard value={stats?.farmers} label="Farmers" />
            <StatCard value={stats?.buyers} label="Buyers" />
            <StatCard value={stats?.activeListings} label="Active listings" />
            <StatCard value={stats?.purchaseRequests} label="Purchase requests" />
            <StatCard value={stats?.pendingRequests} label="Pending requests" />
          </div>

          <div className="card dash-section">
            <div className="card-head">
              <div>
                <h2>Recent activity</h2>
                <p className="hint">Audit trail stored in MongoDB.</p>
              </div>
            </div>
            {activity.length === 0 ? (
              <EmptyState title="No activity yet" description="Actions like creating listings or sending requests appear here." />
            ) : (
              <div className="stack">
                {activity.map((event) => (
                  <div className="row activity-row" key={event.id}>
                    <span className="activity-type">
                      {String(event.type || '').replaceAll('_', ' ')}
                    </span>
                    <div className="row-main">
                      <span>{event.description}</span>
                    </div>
                    <span className="muted small">{formatDateTime(event.timestamp)}</span>
                  </div>
                ))}
              </div>
            )}
          </div>
        </>
      )}

      {tab === 'users' && (
        <div className="card">
          <div className="card-head">
            <div>
              <h2>Users</h2>
              <p className="hint">Roles come from Google login, onboarding choice or the admin allowlist.</p>
            </div>
            <span className="badge category">{users?.length ?? 0} total</span>
          </div>
          {users === null ? (
            <Loading label="Loading users…" />
          ) : users.length === 0 ? (
            <EmptyState title="No users yet" />
          ) : (
            <div className="stack">
              {users.map((u) => (
                <div className="row wrap" key={u.id}>
                  <span className="avatar" aria-hidden="true">
                    {(u.name || '?').split(/\s+/).slice(0, 2).map((p) => p[0].toUpperCase()).join('')}
                  </span>
                  <div className="row-main">
                    <strong>{u.name}</strong>
                    <span className="row-sub">{u.email}</span>
                  </div>
                  <RoleBadge role={u.role} />
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {tab === 'listings' && (
        <div className="card">
          <div className="card-head">
            <div>
              <h2>Listings</h2>
              <p className="hint">Deactivate listings that don&apos;t belong on the marketplace.</p>
            </div>
          </div>
          {listings === null ? (
            <Loading label="Loading listings…" />
          ) : listings.length === 0 ? (
            <EmptyState title="No listings yet" description="Farmer listings will appear here." />
          ) : (
            <div className="stack">
              {listings.map((listing) => (
                <div className="row wrap" key={listing.id}>
                  <div className="row-main">
                    <div className="row-top" style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
                      <strong>{listing.name}</strong>
                      <StatusBadge status={listing.status} />
                      <span className="badge category">{listing.category}</span>
                    </div>
                    <span className="row-sub">
                      {listing.farmerName} · {listing.quantity} kg · {money(listing.pricePerUnit)}/kg · {listing.location} · from {formatDate(listing.availableFrom)}
                    </span>
                  </div>
                  <div className="row-actions">
                    <button
                      className={`btn sm ${listing.status === 'ACTIVE' ? 'danger' : 'secondary'}`}
                      onClick={() => toggleListingStatus(listing)}
                    >
                      {listing.status === 'ACTIVE' ? 'Deactivate' : 'Reactivate'}
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </section>
  )
}
