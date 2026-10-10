import { useEffect, useState } from 'react'
import { formatDateTime } from '../api.js'
import { RoleBadge } from './ui.jsx'

function initials(name = '') {
  return name.split(/\s+/).filter(Boolean).slice(0, 2).map((part) => part[0].toUpperCase()).join('') || '?'
}

export default function Navbar({ user, view, notifications = [], onNavigate, onLogin, onLogout }) {
  const [openPanel, setOpenPanel] = useState(null)

  const close = () => setOpenPanel(null)

  useEffect(() => {
    const onKey = (e) => { if (e.key === 'Escape') close() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  const navigate = (nextView) => {
    close()
    onNavigate(nextView)
  }

  const pendingSetup = user && !user.role

  return (
    <header className="navbar">
      {openPanel && <div className="dropdown-overlay" onClick={close} />}
      <div className="container navbar-inner">
        <button className="brand" onClick={() => navigate('marketplace')} aria-label="FarmLink home">
          <span className="brand-mark" aria-hidden="true">🌾</span>
          <span>
            FarmLink
            <span className="brand-sub">Local produce marketplace</span>
          </span>
        </button>

        <nav className="nav-links" aria-label="Main navigation">
          <button
            className={view === 'marketplace' || view === 'listing' ? 'active' : ''}
            onClick={() => navigate('marketplace')}
          >
            Marketplace
          </button>
          {user?.role === 'FARMER' && (
            <button className={view === 'farmer' ? 'active' : ''} onClick={() => navigate('farmer')}>
              Farmer dashboard
            </button>
          )}
          {user?.role === 'BUYER' && (
            <button className={view === 'buyer' ? 'active' : ''} onClick={() => navigate('buyer')}>
              My requests
            </button>
          )}
          {user?.role === 'ADMIN' && (
            <button className={view === 'admin' ? 'active' : ''} onClick={() => navigate('admin')}>
              Admin
            </button>
          )}
          {pendingSetup && (
            <button className={view === 'onboarding' ? 'active' : ''} onClick={() => navigate('onboarding')}>
              Finish setup
            </button>
          )}
        </nav>

        <div className="nav-auth">
          {user ? (
            <>
              <div className="dropdown-anchor">
                <button
                  className="icon-btn"
                  aria-label={`Notifications (${notifications.length})`}
                  title="Notifications"
                  onClick={() => setOpenPanel(openPanel === 'notifications' ? null : 'notifications')}
                >
                  🔔
                  {notifications.length > 0 && (
                    <span className="count-badge">{notifications.length > 9 ? '9+' : notifications.length}</span>
                  )}
                </button>
                {openPanel === 'notifications' && (
                  <div className="dropdown">
                    <div className="dropdown-header">
                      <span>Notifications</span>
                      <span className="muted small">latest first</span>
                    </div>
                    <div className="dropdown-body">
                      {notifications.length === 0 ? (
                        <div className="notif-item">
                          <span className="notif-message muted">
                            No notifications yet. You&apos;ll be notified when a purchase request
                            or listing needs your attention.
                          </span>
                        </div>
                      ) : (
                        notifications.map((n) => (
                          <div className="notif-item" key={n.id}>
                            <span className="notif-message">{n.message}</span>
                            <span className="notif-time">{formatDateTime(n.createdAt)}</span>
                          </div>
                        ))
                      )}
                    </div>
                  </div>
                )}
              </div>

              <div className="dropdown-anchor">
                <button
                  className="user-chip"
                  onClick={() => setOpenPanel(openPanel === 'profile' ? null : 'profile')}
                  aria-haspopup="menu"
                >
                  <span className="avatar" aria-hidden="true">{initials(user.name)}</span>
                  <span className="name">{user.name}</span>
                  <RoleBadge role={user.role} />
                </button>
                {openPanel === 'profile' && (
                  <div className="dropdown" style={{ width: 260 }}>
                    <div className="profile-menu">
                      <div className="menu-note muted small">{user.email}</div>
                      <button className="menu-item" onClick={() => navigate('profile')}>
                        Profile &amp; account
                      </button>
                      <div className="menu-sep" />
                      <button className="menu-item" onClick={() => { close(); onLogout() }}>
                        Log out
                      </button>
                    </div>
                  </div>
                )}
              </div>
            </>
          ) : (
            <button className="btn primary" onClick={onLogin}>Login with Google</button>
          )}
        </div>
      </div>
    </header>
  )
}
