import { RoleBadge, PageHeader } from './ui.jsx'

const ROLE_EXPLANATIONS = {
  FARMER: 'You publish produce listings, receive purchase requests and accept or reject them.',
  BUYER: 'You browse the marketplace and send purchase requests to farmers.',
  ADMIN: 'You manage users, listings and can review activity across FarmLink.'
}

function initials(name = '') {
  return name.split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0].toUpperCase()).join('') || '?'
}

export default function Profile({ user, onNavigate, onLogout }) {
  if (!user) return null

  const homeView = user.role === 'FARMER' ? 'farmer' : user.role === 'ADMIN' ? 'admin' : 'buyer'

  return (
    <section>
      <PageHeader title="Profile" subtitle="Your FarmLink account information." />

      <div className="card profile-card">
        <span className="avatar-lg" aria-hidden="true">{initials(user.name)}</span>
        <div style={{ flex: 1, minWidth: 240 }}>
          <div className="listing-figures" style={{ marginBottom: 4 }}>
            <h2 style={{ fontSize: '1.2rem' }}>{user.name}</h2>
            <RoleBadge role={user.role} />
          </div>
          <p className="muted">{user.email}</p>

          <div className="profile-fields">
            <div>
              <div className="field-name">FarmLink role</div>
              <div className="field-value">
                {user.role === 'FARMER' ? 'Farmer' : user.role === 'ADMIN' ? 'Administrator' : 'Buyer'}
              </div>
            </div>
            <div>
              <div className="field-name">What you can do</div>
              <div className="field-value" style={{ fontWeight: 400 }}>
                {ROLE_EXPLANATIONS[user.role] || 'Choose how you use FarmLink to unlock your dashboard.'}
              </div>
            </div>
            <div>
              <div className="field-name">Signed in with</div>
              <div className="field-value">Google (OAuth2 / OIDC)</div>
            </div>
            <div>
              <div className="field-name">User ID</div>
              <div className="field-value">#{user.id}</div>
            </div>
          </div>

          <div className="form-actions">
            <button className="btn primary" onClick={() => onNavigate(homeView)}>Go to my dashboard</button>
            <button className="btn secondary" onClick={onLogout}>Log out</button>
          </div>
        </div>
      </div>
    </section>
  )
}
