import { useEffect, useState } from 'react'
import { api } from '../api.js'

/**
 * Shows the counterparty's contact details for an ACCEPTED purchase request.
 * The backend only returns data to the two participants of an accepted request,
 * so any error here (403 for pending/rejected/unauthorised) is simply displayed.
 */
export default function RequestContact({ requestId }) {
  const [contact, setContact] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false
    api.get(`/api/requests/${requestId}/contact`)
      .then((data) => { if (!cancelled) setContact(data) })
      .catch((e) => { if (!cancelled) setError(e.message) })
    return () => { cancelled = true }
  }, [requestId])

  if (error) {
    return (
      <div className="contact-box">
        <span className="hint">Contact details unavailable: {error}</span>
      </div>
    )
  }

  if (!contact) {
    return (
      <div className="contact-box">
        <span className="hint">Loading contact details…</span>
      </div>
    )
  }

  const iAmFarmer = contact.viewerId === contact.farmer.userId
  const counterpart = iAmFarmer ? contact.buyer : contact.farmer
  const roleLabel = iAmFarmer ? 'Buyer' : 'Farmer'

  return (
    <div className="contact-box">
      <span className="contact-title">{roleLabel} contact details</span>
      <strong>{counterpart.name}</strong>
      <span className="contact-line">
        <span className="field-name">Email</span>
        <a href={`mailto:${counterpart.email}`}>{counterpart.email}</a>
      </span>
      <span className="contact-line">
        <span className="field-name">Phone</span>
        {counterpart.phone
          ? <span>{counterpart.phone}</span>
          : <span className="hint">No phone number on file — email is the best way to reach them.</span>}
      </span>
      <span className="hint">{contact.instruction}</span>
    </div>
  )
}
