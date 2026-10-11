import { useState } from 'react'
import { api } from '../api.js'
import { Banner, RoleBadge, PageHeader } from './ui.jsx'

const ROLE_EXPLANATIONS = {
  FARMER: 'You publish produce listings, receive purchase requests and accept or reject them.',
  BUYER: 'You browse the marketplace and send purchase requests to farmers.',
  ADMIN: 'You manage users, listings and can review activity across GoFarm.'
}

function initials(name = '') {
  return name.split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0].toUpperCase()).join('') || '?'
}

export default function Profile({ user, onNavigate, onLogout, onUserUpdated }) {
  const [editingPhone, setEditingPhone] = useState(false)
  const [phoneDraft, setPhoneDraft] = useState('')
  const [savingPhone, setSavingPhone] = useState(false)
  const [phoneError, setPhoneError] = useState('')
  const [phoneNotice, setPhoneNotice] = useState('')

  if (!user) return null

  const startEditPhone = () => {
    setPhoneDraft(user.phone || '')
    setPhoneError('')
    setEditingPhone(true)
  }

  const savePhone = async (event) => {
    event.preventDefault()
    setSavingPhone(true)
    setPhoneError('')
    setPhoneNotice('')
    try {
      const updated = await api.put('/api/me/phone', { phone: phoneDraft })
      onUserUpdated?.(updated)
      setEditingPhone(false)
      setPhoneNotice(updated.phone
        ? 'Phone number saved — buyers/farmers in accepted requests can now call you too.'
        : 'Phone number cleared — only your Google email will be shared.')
    } catch (e) {
      setPhoneError(e.message)
    } finally {
      setSavingPhone(false)
    }
  }

  const homeView = user.role === 'FARMER' ? 'farmer' : user.role === 'ADMIN' ? 'admin' : 'buyer'

  return (
    <section>
      <PageHeader title="Profile" subtitle="Your GoFarm account information." />

      <Banner kind="success" onClose={() => setPhoneNotice('')}>{phoneNotice}</Banner>
      <Banner kind="error" onClose={() => setPhoneError('')}>{phoneError}</Banner>

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
              <div className="field-name">GoFarm role</div>
              <div className="field-value">
                {user.role === 'FARMER' ? 'Farmer' : user.role === 'ADMIN' ? 'Administrator' : 'Buyer'}
              </div>
            </div>
            <div>
              <div className="field-name">What you can do</div>
              <div className="field-value" style={{ fontWeight: 400 }}>
                {ROLE_EXPLANATIONS[user.role] || 'Choose how you use GoFarm to unlock your dashboard.'}
              </div>
            </div>
            <div>
              <div className="field-name">Phone (optional)</div>
              {editingPhone ? (
                <form className="row" onSubmit={savePhone} style={{ gap: 8 }}>
                  <input
                    type="text"
                    maxLength="30"
                    placeholder="e.g. +250 788 123 456"
                    value={phoneDraft}
                    onChange={(e) => setPhoneDraft(e.target.value)}
                    autoFocus
                  />
                  <button className="btn primary sm" type="submit" disabled={savingPhone}>
                    {savingPhone ? 'Saving…' : 'Save'}
                  </button>
                  <button
                    className="btn secondary sm"
                    type="button"
                    onClick={() => setEditingPhone(false)}
                  >
                    Cancel
                  </button>
                </form>
              ) : (
                <div className="field-value row" style={{ gap: 10 }}>
                  <span>{user.phone || 'Not set'}</span>
                  <button className="btn ghost sm" onClick={startEditPhone}>
                    {user.phone ? 'Edit' : 'Add'}
                  </button>
                </div>
              )}
              {!editingPhone && (
                <p className="hint small" style={{ marginTop: 4 }}>
                  Shared only with the buyer or farmer of a request you both accepted — never
                  on listings or to admins.
                </p>
              )}
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
