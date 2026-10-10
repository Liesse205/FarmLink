import { useCallback, useEffect, useState } from 'react'
import { api, formatDate, money } from '../api.js'
import {
  Banner, EmptyState, ErrorState, Loading, PageHeader, StatCard, StatusBadge
} from './ui.jsx'

const EMPTY_FORM = {
  name: '',
  category: 'Vegetables',
  quantity: '',
  pricePerUnit: '',
  location: '',
  availableFrom: '',
  description: ''
}

const CATEGORIES = [
  'Vegetables', 'Fruits', 'Cereals & Grains', 'Legumes', 'Tubers',
  'Herbs & Spices', 'Dairy', 'Coffee & Tea', 'Other'
]

export default function FarmerDashboard({ user, onRefreshNotifications }) {
  const [listings, setListings] = useState([])
  const [requests, setRequests] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [reloadKey, setReloadKey] = useState(0)

  const [notice, setNotice] = useState('')

  const [showForm, setShowForm] = useState(false)
  const [editingId, setEditingId] = useState(null)
  const [form, setForm] = useState(EMPTY_FORM)
  const [savingListing, setSavingListing] = useState(false)
  const [formError, setFormError] = useState('')
  const [busyRequestId, setBusyRequestId] = useState(null)

  const load = useCallback(async () => {
    try {
      setError('')
      const [mine, incoming] = await Promise.all([
        api.get('/api/listings/mine'),
        api.get('/api/requests')
      ])
      setListings(mine)
      setRequests(incoming)
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { load() }, [load, reloadKey])

  const setField = (key) => (event) => setForm({ ...form, [key]: event.target.value })

  const openCreateForm = () => {
    setEditingId(null)
    setForm(EMPTY_FORM)
    setFormError('')
    setShowForm(true)
  }

  const openEditForm = (listing) => {
    setEditingId(listing.id)
    setForm({
      name: listing.name,
      category: listing.category,
      quantity: String(listing.quantity),
      pricePerUnit: String(listing.pricePerUnit),
      location: listing.location,
      availableFrom: listing.availableFrom,
      description: listing.description || ''
    })
    setFormError('')
    setShowForm(true)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  const saveListing = async (event) => {
    event.preventDefault()
    setFormError('')
    setSavingListing(true)
    setNotice('')
    try {
      const payload = {
        ...form,
        quantity: Number(form.quantity),
        pricePerUnit: Number(form.pricePerUnit)
      }
      if (editingId) {
        const existing = listings.find((l) => l.id === editingId)
        await api.put(`/api/listings/${editingId}`, { ...payload, status: existing?.status })
        setNotice(`“${form.name}” updated.`)
      } else {
        await api.post('/api/listings', payload)
        setNotice(`“${form.name}” is now live on the marketplace.`)
      }
      setShowForm(false)
      setEditingId(null)
      setForm(EMPTY_FORM)
      setReloadKey((k) => k + 1)
    } catch (e) {
      setFormError(e.message)
    } finally {
      setSavingListing(false)
    }
  }

  const toggleStatus = async (listing) => {
    setError('')
    setNotice('')
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
      setNotice(next === 'INACTIVE'
        ? `“${listing.name}” deactivated — it's hidden from the marketplace.`
        : `“${listing.name}” is live again.`)
      setReloadKey((k) => k + 1)
    } catch (e) {
      setError(e.message)
    }
  }

  const processRequest = (id, action) => async () => {
    setError('')
    setNotice('')
    setBusyRequestId(id)
    try {
      await api.put(`/api/requests/${id}/${action}`)
      setNotice(action === 'accept' ? 'Request accepted — the buyer was notified.' : 'Request rejected.')
      setReloadKey((k) => k + 1)
      onRefreshNotifications?.()
    } catch (e) {
      setError(e.message)
    } finally {
      setBusyRequestId(null)
    }
  }

  if (loading) return <Loading label="Loading your dashboard…" />
  if (error && listings.length === 0 && requests.length === 0) {
    return <ErrorState message={error} onRetry={() => { setReloadKey((k) => k + 1); setLoading(true) }} />
  }

  const activeListings = listings.filter((l) => l.status === 'ACTIVE')
  const pendingRequests = requests.filter((r) => r.status === 'PENDING')
  const acceptedRequests = requests.filter((r) => r.status === 'ACCEPTED')
  const recentListings = [...listings].sort((a, b) => b.id - a.id).slice(0, 3)
  const categoryOptions = [
    ...new Set([...CATEGORIES, ...listings.map((l) => l.category)])
  ]

  return (
    <section>
      <PageHeader
        title={`Farmer dashboard`}
        subtitle={`Welcome back${user?.name ? `, ${user.name.split(' ')[0]}` : ''} — here's what's happening on your farm.`}
      >
        <button className="btn primary" onClick={openCreateForm}>+ New listing</button>
      </PageHeader>

      <Banner kind="success" onClose={() => setNotice('')}>{notice}</Banner>
      <Banner kind="error" onClose={() => setError('')}>{error}</Banner>

      <div className="stat-grid">
        <StatCard value={activeListings.length} label="Active listings" hint="visible to buyers" />
        <StatCard value={pendingRequests.length} label="Pending requests" hint="waiting for your answer" />
        <StatCard value={acceptedRequests.length} label="Accepted requests" />
        <StatCard value={listings.length} label="Total listings" />
      </div>

      {showForm && (
        <div className="card dash-section">
          <div className="card-head">
            <h2>{editingId ? 'Edit listing' : 'Create a produce listing'}</h2>
            <button className="btn ghost sm" onClick={() => { setShowForm(false); setEditingId(null) }}>
              Cancel
            </button>
          </div>
          {formError && <Banner kind="error">{formError}</Banner>}
          <form className="form-grid" onSubmit={saveListing}>
            <label>
              Produce name *
              <input type="text" maxLength="120" value={form.name} onChange={setField('name')} required />
            </label>
            <label>
              Category *
              <select value={form.category} onChange={setField('category')} required>
                {categoryOptions.map((c) => <option key={c} value={c}>{c}</option>)}
              </select>
            </label>
            <label>
              Quantity (kg) *
              <input type="number" min="1" value={form.quantity} onChange={setField('quantity')} required />
            </label>
            <label>
              Price per kg (RWF) *
              <input type="number" min="1" step="0.01" value={form.pricePerUnit} onChange={setField('pricePerUnit')} required />
            </label>
            <label>
              Location *
              <input type="text" maxLength="120" placeholder="e.g. Kigali" value={form.location} onChange={setField('location')} required />
            </label>
            <label>
              Available from *
              <input type="date" value={form.availableFrom} onChange={setField('availableFrom')} required />
            </label>
            <label className="wide">
              Description
              <textarea
                rows="3"
                maxLength="2000"
                placeholder="Quality, harvest time, packaging, minimum order…"
                value={form.description}
                onChange={setField('description')}
              />
            </label>
            <div className="wide form-actions">
              <button className="btn primary" type="submit" disabled={savingListing}>
                {savingListing ? 'Saving…' : editingId ? 'Save changes' : 'Create listing'}
              </button>
              <button
                className="btn secondary"
                type="button"
                onClick={() => { setShowForm(false); setEditingId(null) }}
              >
                Cancel
              </button>
            </div>
          </form>
        </div>
      )}

      <div className="card dash-section">
        <div className="card-head">
          <div>
            <h2>My listings</h2>
            <p className="hint">Newest first · edit or deactivate at any time.</p>
          </div>
        </div>

        {listings.length === 0 ? (
          <EmptyState
            title="You haven't listed anything yet"
            description="Publish your first produce listing — buyers can find it in the marketplace immediately."
            action={<button className="btn primary" onClick={openCreateForm}>Create your first listing</button>}
          />
        ) : (
          <div className="stack">
            {listings.map((listing) => (
              <div className="row wrap manage-listing" key={listing.id}>
                <div className="row-main">
                  <div className="row-top">
                    <strong>{listing.name}</strong>
                    <StatusBadge status={listing.status} />
                    <span className="badge category">{listing.category}</span>
                  </div>
                  <span className="row-sub">
                    {listing.quantity} kg · {money(listing.pricePerUnit)}/kg · {listing.location} · available from {formatDate(listing.availableFrom)}
                  </span>
                </div>
                <div className="row-actions">
                  <button className="btn secondary sm" onClick={() => openEditForm(listing)}>Edit</button>
                  <button
                    className={`btn sm ${listing.status === 'ACTIVE' ? 'danger' : 'secondary'}`}
                    onClick={() => toggleStatus(listing)}
                  >
                    {listing.status === 'ACTIVE' ? 'Deactivate' : 'Activate'}
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      <div className="card dash-section">
        <div className="card-head">
          <div>
            <h2>Incoming purchase requests</h2>
            <p className="hint">Accept or reject pending requests — the buyer is notified instantly via RabbitMQ.</p>
          </div>
          {pendingRequests.length > 0 && (
            <span className="badge status-pending">{pendingRequests.length} to review</span>
          )}
        </div>

        {requests.length === 0 ? (
          <EmptyState
            title="No purchase requests yet"
            description="Requests from buyers show up here as soon as someone is interested in your produce."
          />
        ) : (
          <div className="stack">
            {requests.map((request) => (
              <div className="row wrap" key={request.id}>
                <div className="row-main">
                  <div className="row-top" style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
                    <strong>{request.listingName}</strong>
                    <StatusBadge status={request.status} />
                  </div>
                  <span className="row-sub">
                    {request.quantity} kg from {request.buyerName} · {formatDate(request.createdAt)}
                  </span>
                  {request.message && <span className="row-sub">“{request.message}”</span>}
                </div>
                {request.status === 'PENDING' && (
                  <div className="row-actions">
                    <button
                      className="btn primary sm"
                      onClick={processRequest(request.id, 'accept')}
                      disabled={busyRequestId === request.id}
                    >
                      Accept
                    </button>
                    <button
                      className="btn danger sm"
                      onClick={processRequest(request.id, 'reject')}
                      disabled={busyRequestId === request.id}
                    >
                      Reject
                    </button>
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </div>

      <div className="card dash-section">
        <div className="card-head">
          <div>
            <h2>Recent listings</h2>
            <p className="hint">Your three newest listings at a glance.</p>
          </div>
        </div>
        {recentListings.length === 0 ? (
          <p className="hint">Nothing here yet — create a listing to get started.</p>
        ) : (
          <div className="stack">
            {recentListings.map((listing) => (
              <div className="row" key={listing.id}>
                <div className="row-main">
                  <strong>{listing.name}</strong>
                  <span className="row-sub">{listing.location} · {formatDate(listing.availableFrom)}</span>
                </div>
                <StatusBadge status={listing.status} />
              </div>
            ))}
          </div>
        )}
      </div>
    </section>
  )
}
