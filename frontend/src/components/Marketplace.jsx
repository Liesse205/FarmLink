import { useEffect, useMemo, useState } from 'react'
import { api, formatDate, money } from '../api.js'
import { EmptyState, ErrorState, SkeletonGrid } from './ui.jsx'

export default function Marketplace({ user, onOpen, onLogin }) {
  const [search, setSearch] = useState('')
  const [category, setCategory] = useState('')
  const [location, setLocation] = useState('')
  const [facets, setFacets] = useState({ categories: [], locations: [] })

  const [listings, setListings] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [reloadKey, setReloadKey] = useState(0)
  const [filters, setFilters] = useState({ search: '', category: '', location: '' })

  const setFiltersNow = () => setFilters({
    search: search.trim(),
    category: category.trim(),
    location: location.trim()
  })

  useEffect(() => {
    api.get('/api/listings/facets').then(setFacets).catch(() => {})
  }, [])

  useEffect(() => {
    const timer = setTimeout(() => setFiltersNow(), 250)
    return () => clearTimeout(timer)
  }, [search, location])

  useEffect(() => {
    const params = new URLSearchParams()
    if (filters.search) params.set('search', filters.search)
    if (filters.category) params.set('category', filters.category)
    if (filters.location) params.set('location', filters.location)

    let cancelled = false
    setLoading(true)
    setError('')
    api.get(`/api/listings?${params.toString()}`)
      .then((data) => { if (!cancelled) setListings(data) })
      .catch((e) => { if (!cancelled) setError(e.message) })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [filters, reloadKey])

  const hasFilters = Boolean(filters.search || filters.category || filters.location)

  const clearFilters = () => {
    setSearch('')
    setCategory('')
    setLocation('')
    setFilters({ search: '', category: '', location: '' })
  }

  const activeCategories = useMemo(
    () => [category, ...facets.categories].filter((c, i, all) => c && all.indexOf(c) === i),
    [category, facets.categories]
  )

  return (
    <section>
      <div className="hero">
        <div className="hero-copy">
          <h1>Fresh local produce, straight from the farm</h1>
          <p className="lede">
            GoFarm connects smallholder farmers with local businesses — discover fresh produce,
            see transparent prices and order directly. No middlemen.
          </p>
          <div className="hero-actions">
            <button className="btn primary" onClick={() => document.getElementById('marketplace-list')?.scrollIntoView({ behavior: 'smooth' })}>
              Browse produce
            </button>
            {!user && (
              <button className="btn secondary" onClick={onLogin}>Login with Google</button>
            )}
          </div>
        </div>

        <div className="hero-media">
          <img src="/farm-hero.webp" alt="Fresh local produce harvested by smallholder farmers" />
        </div>

        <div className="features">
          <div className="feature">
            <div>
              <strong>Direct from farmers</strong>
              <span>Buy from the people who grow the food.</span>
            </div>
          </div>
          <div className="feature">
            <div>
              <strong>Transparent pricing</strong>
              <span>Price per kilogram on every listing.</span>
            </div>
          </div>
          <div className="feature">
            <div>
              <strong>Simple ordering</strong>
              <span>Send a purchase request, get a clear answer.</span>
            </div>
          </div>
        </div>
      </div>

      <div id="marketplace-list">
        <form className="card filters" onSubmit={(e) => { e.preventDefault(); setFiltersNow() }}>
          <div className="field">
            <label htmlFor="f-search">Produce name</label>
            <input
              id="f-search"
              type="search"
              placeholder="e.g. tomatoes, maize…"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>
          <div className="field">
            <label htmlFor="f-category">Category</label>
            <select id="f-category" value={category} onChange={(e) => { setCategory(e.target.value); setFilters({ ...filters, category: e.target.value }) }}>
              <option value="">All categories</option>
              {activeCategories.map((c) => <option key={c} value={c}>{c}</option>)}
            </select>
          </div>
          <div className="field">
            <label htmlFor="f-location">Location</label>
            <input
              id="f-location"
              type="text"
              list="location-options"
              placeholder="Any location"
              value={location}
              onChange={(e) => setLocation(e.target.value)}
            />
            <datalist id="location-options">
              {facets.locations.map((l) => <option key={l} value={l} />)}
            </datalist>
          </div>
          <div className="filter-actions">
            <button className="btn secondary" type="button" onClick={clearFilters} disabled={!hasFilters && !search && !category && !location}>
              Clear
            </button>
          </div>
        </form>

        <div className="results-meta">
          <span>
            {loading ? 'Searching produce…'
              : `${listings.length} listing${listings.length === 1 ? '' : 's'}${hasFilters ? ' match your filters' : ' available'}`}
          </span>
        </div>

        {error && <ErrorState message={error} onRetry={() => setReloadKey((k) => k + 1)} />}

        {loading && !error && <SkeletonGrid count={6} />}

        {!loading && !error && listings.length === 0 && (
          hasFilters ? (
            <EmptyState
              title="No produce matches your search"
              description="Try a different keyword, or clear the filters to see everything that is available."
              action={<button className="btn secondary" onClick={clearFilters}>Clear filters</button>}
            />
          ) : (
            <EmptyState
              title="No produce listed yet"
              description="When farmers publish listings they appear here. Know a farmer? Invite them to GoFarm — it takes one Google login to get started."
              action={!user && <button className="btn primary" onClick={onLogin}>Join as a farmer</button>}
            />
          )
        )}

        {!loading && !error && listings.length > 0 && (
          <div className="grid">
            {listings.map((listing) => (
              <article className="card listing-card" key={listing.id}>
                <div className="listing-top">
                  <div>
                    <h3>{listing.name}</h3>
                    <span className="listing-location">{listing.location}</span>
                  </div>
                </div>

                <div className="listing-figures">
                  <span className="price">{money(listing.pricePerUnit)}<small>/kg</small></span>
                  <span className="qty">{listing.quantity} kg available</span>
                </div>

                <div className="listing-foot">
                  <div className="stack" style={{ gap: 4 }}>
                    <span className="badge category">{listing.category}</span>
                    <span className="muted small">Available from {formatDate(listing.availableFrom)}</span>
                  </div>
                  <button className="btn primary sm" onClick={() => onOpen(listing.id)}>View details</button>
                </div>
              </article>
            ))}
          </div>
        )}
      </div>
    </section>
  )
}
