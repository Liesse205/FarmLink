import { useEffect, useState } from 'react'
import { api, formatDate, money } from '../api.js'
import { Banner, ErrorState, Loading, StatusBadge } from './ui.jsx'

export default function ListingDetails({ listingId, user, onBack, onLogin, onNavigate, onRequestSubmitted }) {
  const [listing, setListing] = useState(null)
  const [error, setError] = useState('')
  const [reloadKey, setReloadKey] = useState(0)

  const [quantity, setQuantity] = useState('')
  const [message, setMessage] = useState('')
  const [feedback, setFeedback] = useState(null)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (!listingId) {
      setError('No listing selected.')
      return
    }
    setError('')
    setListing(null)
    api.get(`/api/listings/${listingId}`)
      .then(setListing)
      .catch((e) => setError(e.message))
  }, [listingId, reloadKey])

  const submitRequest = async (event) => {
    event.preventDefault()
    setFeedback(null)

    const qty = Number(quantity)
    if (!Number.isInteger(qty) || qty < 1) {
      setFeedback({ ok: false, text: 'Enter a quantity of at least 1 kg.' })
      return
    }
    if (qty > listing.quantity) {
      setFeedback({ ok: false, text: `Only ${listing.quantity} kg are available.` })
      return
    }

    setSubmitting(true)
    try {
      await api.post('/api/requests', {
        listingId: listing.id,
        quantity: qty,
        message: message.trim() || null
      })
      setFeedback({
        ok: true,
        text: 'Purchase request sent to the farmer. You can track its status under “My requests”.'
      })
      setQuantity('')
      setMessage('')
      onRequestSubmitted?.()
    } catch (e) {
      setFeedback({ ok: false, text: e.message })
    } finally {
      setSubmitting(false)
    }
  }

  if (error) {
    return (
      <section>
        <ErrorState
          message={error}
          onRetry={listingId ? () => { setReloadKey((k) => k + 1); setError('') } : undefined}
        />
        <div style={{ textAlign: 'center' }}>
          <button className="btn secondary" onClick={onBack}>Back to marketplace</button>
        </div>
      </section>
    )
  }

  if (!listing) {
    return <Loading label="Loading listing…" />
  }

  const isActive = listing.status === 'ACTIVE'

  return (
    <section>
      <button className="btn ghost sm" onClick={onBack} style={{ marginBottom: 16 }}>
        ← Back to marketplace
      </button>

      <div className="details-layout">
        <div className="card">
          <div className="listing-top">
            <div>
              <div className="listing-figures" style={{ marginBottom: 6 }}>
                <span className="badge category">{listing.category}</span>
                <StatusBadge status={listing.status} />
              </div>
              <h1 className="page-title">{listing.name}</h1>
              <p className="listing-location">{listing.location}</p>
            </div>
          </div>

          <dl className="details-grid">
            <div>
              <dt>Quantity available</dt>
              <dd>{listing.quantity} kg</dd>
            </div>
            <div>
              <dt>Price per unit</dt>
              <dd>{money(listing.pricePerUnit)} / kg</dd>
            </div>
            <div>
              <dt>Location</dt>
              <dd>{listing.location}</dd>
            </div>
            <div>
              <dt>Available from</dt>
              <dd>{formatDate(listing.availableFrom)}</dd>
            </div>
            <div>
              <dt>Category</dt>
              <dd>{listing.category}</dd>
            </div>
            <div>
              <dt>Listed by</dt>
              <dd>{listing.farmerName}</dd>
            </div>
          </dl>

          <div className="description">
            <h2 style={{ fontSize: '1rem', marginBottom: 8 }}>Description</h2>
            <p className="muted">{listing.description || 'No description provided.'}</p>
          </div>

          <div className="farmer-card">
            <span className="avatar" aria-hidden="true">
              {(listing.farmerName || '?').split(/\s+/).slice(0, 2).map((p) => p[0].toUpperCase()).join('')}
            </span>
            <div>
              <span className="farmer-name">{listing.farmerName}</span>
              <div className="muted small">Farmer on GoFarm · {listing.location}</div>
            </div>
          </div>
        </div>

        <aside className="card side-card">
          <div className="side-price">
            {money(listing.pricePerUnit)} <small>/ kg</small>
          </div>

          <div className="side-facts">
            <div className="fact"><span>Available</span><strong>{listing.quantity} kg</strong></div>
            <div className="fact"><span>Available from</span><strong>{formatDate(listing.availableFrom)}</strong></div>
            <div className="fact"><span>Location</span><strong>{listing.location}</strong></div>
            <div className="fact"><span>Status</span><StatusBadge status={listing.status} /></div>
          </div>

          {feedback && (
            <Banner kind={feedback.ok ? 'success' : 'error'} onClose={() => setFeedback(null)}>
              {feedback.text}
            </Banner>
          )}

          {user?.role === 'BUYER' && isActive && (
            <form className="request-form" onSubmit={submitRequest}>
              <h2 style={{ fontSize: '1rem', marginBottom: 10 }}>Request this produce</h2>
              <label htmlFor="req-qty">Quantity (kg)</label>
              <div className="qty-input">
                <input
                  id="req-qty"
                  type="number"
                  min="1"
                  max={listing.quantity}
                  placeholder={`max ${listing.quantity}`}
                  value={quantity}
                  onChange={(e) => setQuantity(e.target.value)}
                  required
                />
                <span className="suffix">of {listing.quantity} kg</span>
              </div>

              <label htmlFor="req-msg" style={{ marginTop: 12 }}>Message to the farmer (optional)</label>
              <textarea
                id="req-msg"
                rows="3"
                maxLength="500"
                placeholder="e.g. Please keep 100 kg for our restaurant."
                value={message}
                onChange={(e) => setMessage(e.target.value)}
              />

              <button className="btn primary full" type="submit" disabled={submitting} style={{ marginTop: 14 }}>
                {submitting ? 'Sending…' : 'Request produce'}
              </button>

              <p className="muted small" style={{ marginTop: 10 }}>
                The farmer sees your request immediately and accepts or rejects it.
              </p>
            </form>
          )}

          {!user && isActive && (
            <div className="stack">
              <button className="btn primary full" onClick={onLogin}>Login with Google to request</button>
              <p className="muted small">
                New here? Logging in lets you choose whether you buy or sell on GoFarm.
              </p>
            </div>
          )}

          {user?.role === 'FARMER' && (
            <p className="hint">
              You&apos;re signed in as a farmer — buyers are the ones sending purchase requests.
              Manage your own listings from your dashboard.
            </p>
          )}

          {user?.role === 'BUYER' && !isActive && (
            <div className="stack">
              <p className="hint">This listing is no longer active.</p>
              <button className="btn secondary full" onClick={() => onNavigate?.('buyer')}>
                View my requests
              </button>
            </div>
          )}

          {user?.role === 'ADMIN' && (
            <p className="hint">Admins can deactivate listings from the admin dashboard.</p>
          )}
        </aside>
      </div>
    </section>
  )
}
