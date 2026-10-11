import { useCallback, useEffect, useState } from 'react'
import { api, login, logout } from './api.js'
import Navbar from './components/Navbar.jsx'
import Marketplace from './components/Marketplace.jsx'
import ListingDetails from './components/ListingDetails.jsx'
import Onboarding from './components/Onboarding.jsx'
import FarmerDashboard from './components/FarmerDashboard.jsx'
import BuyerDashboard from './components/BuyerDashboard.jsx'
import AdminDashboard from './components/AdminDashboard.jsx'
import Profile from './components/Profile.jsx'

export default function App() {
  const [user, setUser] = useState(null)
  const [booting, setBooting] = useState(true)
  const [view, setView] = useState('marketplace')
  const [listingId, setListingId] = useState(null)
  const [notifications, setNotifications] = useState([])
  const [banner, setBanner] = useState('')

  useEffect(() => {
    const params = new URLSearchParams(window.location.search)
    if (params.get('login') === 'error') {
      const reason = params.get('e')
      let message = 'Google login failed'
      if (reason === 'access_denied') {
        message += ': your Google OAuth app is in "Testing" mode — add this Google account as a test user in Google Cloud Console (or publish the app).'
      } else {
        message += reason ? ` (${reason}).` : '.'
        message += ' Check GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET and the redirect URI.'
      }
      setBanner(message)
      window.history.replaceState({}, '', '/')
    }
    api.get('/api/me')
      .then((me) => {
        setUser(me)
        if (!me.role) {

          setView('onboarding')
        } else {

          setView(homeFor(me.role))
        }
      })
      .catch(() => setUser(null))
      .finally(() => setBooting(false))
  }, [])

  const homeFor = (role) =>
    role === 'FARMER' ? 'farmer' : role === 'ADMIN' ? 'admin' : 'buyer'

  const refreshNotifications = useCallback(() => {
    if (!user) {
      setNotifications([])
      return
    }
    api.get('/api/notifications').then(setNotifications).catch(() => {})
  }, [user])

  useEffect(() => {
    refreshNotifications()
    const timer = setInterval(refreshNotifications, 15000)
    return () => clearInterval(timer)
  }, [refreshNotifications])

  const go = (nextView) => {
    setView(nextView)
    window.scrollTo(0, 0)
  }

  const openListing = (id) => {
    setListingId(id)
    go('listing')
  }

  const handleLogout = async () => {
    await logout()
    setUser(null)
    setNotifications([])
    go('marketplace')
  }

  const handleRoleChosen = (updatedUser) => {
    setUser(updatedUser)
    go(homeFor(updatedUser.role))
  }

  if (booting) {
    return (
      <div className="page-center">
        <span className="spinner" aria-hidden="true" />
        <p style={{ marginLeft: 12 }}>Loading GoFarm…</p>
      </div>
    )
  }

  const needsRole = user && !user.role

  return (
    <div className="app">
      <Navbar
        user={user}
        view={view}
        notifications={notifications}
        onNavigate={go}
        onLogin={login}
        onLogout={handleLogout}
      />

      <main className="container">
        {banner && (
          <div className="banner error" role="alert">
            <span>{banner}</span>
            <button className="banner-close" onClick={() => setBanner('')} aria-label="Dismiss">×</button>
          </div>
        )}

        {needsRole && view !== 'onboarding' && (
          <div className="banner info">
            <span>
              One step left: choose how you&apos;ll use GoFarm to unlock your dashboard.{' '}
              <button className="btn ghost sm" onClick={() => go('onboarding')}>Finish setup</button>
            </span>
          </div>
        )}

        {view === 'marketplace' && (
          <Marketplace user={user} onOpen={openListing} onLogin={login} />
        )}

        {view === 'listing' && (
          <ListingDetails
            listingId={listingId}
            user={user}
            onBack={() => go('marketplace')}
            onLogin={login}
            onNavigate={go}
            onRequestSubmitted={refreshNotifications}
          />
        )}

        {view === 'onboarding' && user && (
          <Onboarding onRoleChosen={handleRoleChosen} />
        )}

        {view === 'farmer' && user?.role === 'FARMER' && (
          <FarmerDashboard user={user} onRefreshNotifications={refreshNotifications} />
        )}

        {view === 'buyer' && user?.role === 'BUYER' && (
          <BuyerDashboard user={user} onOpenListing={openListing} onBrowse={() => go('marketplace')} />
        )}

        {view === 'admin' && user?.role === 'ADMIN' && <AdminDashboard />}

        {view === 'profile' && user && (
          <Profile user={user} onNavigate={go} onLogout={handleLogout} onUserUpdated={setUser} />
        )}

        {isBlocked(view, user) && (
          <div className="card">
            <h2>You are not allowed to view this page</h2>
            <p className="hint">
              This area needs a different GoFarm role than yours. Role access is enforced
              on the server, not just hidden in the menu.
            </p>
            <div className="form-actions">
              <button className="btn primary" onClick={() => go('marketplace')}>Back to marketplace</button>
            </div>
          </div>
        )}
      </main>

      <footer className="footer">
        <div className="container">
          <span><strong>GoFarm</strong> — local produce, straight from smallholder farmers to local businesses.</span>
        </div>
      </footer>
    </div>
  )
}

function isBlocked(view, user) {
  if (!user) return false
  if (view === 'farmer') return user.role !== 'FARMER'
  if (view === 'buyer') return user.role !== 'BUYER'
  if (view === 'admin') return user.role !== 'ADMIN'
  return false
}
