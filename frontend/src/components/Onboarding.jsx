import { useState } from 'react'
import { api } from '../api.js'

export default function Onboarding({ onRoleChosen }) {
  const [choice, setChoice] = useState(null)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  const submit = async (role) => {
    if (saving) return
    setChoice(role)
    setSaving(true)
    setError('')
    try {
      const updated = await api.post('/api/me/role', { role })
      onRoleChosen(updated)
    } catch (e) {
      setError(e.message)
      setSaving(false)
    }
  }

  return (
    <section className="onboard">
      <div className="card">
        <h1>Welcome to GoFarm</h1>
        <p className="hint">How will you use GoFarm? This decides which dashboard you get.</p>

        <div className="choice-grid">
          <button
            className="choice-card"
            onClick={() => submit('FARMER')}
            disabled={saving}
            aria-pressed={choice === 'FARMER'}
          >
            <strong>I&apos;m a Farmer</strong>
            <span>Publish produce listings, receive purchase requests and accept or reject them.</span>
          </button>

          <button
            className="choice-card"
            onClick={() => submit('BUYER')}
            disabled={saving}
            aria-pressed={choice === 'BUYER'}
          >
            <strong>I&apos;m a Buyer</strong>
            <span>Browse fresh local produce and send purchase requests directly to farmers.</span>
          </button>
        </div>

        {saving && <p className="hint" style={{ marginTop: 14 }}>Saving your choice…</p>}
        {error && <div className="banner error" role="alert"><span>{error}</span></div>}

        <p className="hint small" style={{ marginTop: 16 }}>
          Your role can&apos;t be changed later. Admin accounts are managed by the GoFarm
          administrators.
        </p>
      </div>
    </section>
  )
}
