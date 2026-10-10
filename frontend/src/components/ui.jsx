export function Loading({ label = 'Loading…' }) {
  return (
    <div className="state" role="status" aria-live="polite">
      <span className="spinner" aria-hidden="true" />
      <p className="state-text">{label}</p>
    </div>
  )
}

export function SkeletonGrid({ count = 6 }) {
  return (
    <div className="grid" aria-hidden="true">
      {Array.from({ length: count }).map((_, i) => (
        <div className="card skeleton-card" key={i}>
          <div className="skeleton line w40" />
          <div className="skeleton line w80" />
          <div className="skeleton line w60" />
          <div className="skeleton line w90" />
        </div>
      ))}
    </div>
  )
}

export function EmptyState({ title, description, action }) {
  return (
    <div className="state card">
      <p className="state-title">{title}</p>
      {description && <p className="state-text">{description}</p>}
      {action}
    </div>
  )
}

export function ErrorState({ message, onRetry }) {
  return (
    <div className="state card state-error">
      <p className="state-title">Something went wrong</p>
      <p className="state-text">{message || 'Please try again.'}</p>
      {onRetry && (
        <button className="btn secondary" onClick={onRetry}>Try again</button>
      )}
    </div>
  )
}

const STATUS_LABELS = {
  PENDING: 'Pending',
  ACCEPTED: 'Accepted',
  REJECTED: 'Rejected',
  ACTIVE: 'Active',
  INACTIVE: 'Inactive'
}

export function StatusBadge({ status }) {
  const key = String(status || '').toUpperCase()
  return (
    <span className={`badge status-${key.toLowerCase()}`}>
      {STATUS_LABELS[key] || status || 'Unknown'}
    </span>
  )
}

const ROLE_LABELS = {
  FARMER: 'Farmer',
  BUYER: 'Buyer',
  ADMIN: 'Admin',
  PENDING: 'Setup needed'
}

export function RoleBadge({ role }) {
  const key = String(role || 'PENDING').toUpperCase()
  return (
    <span className={`badge role-${key.toLowerCase()}`}>
      {ROLE_LABELS[key] || role}
    </span>
  )
}

export function StatCard({ value, label, hint }) {
  return (
    <div className="card stat">
      <div className="stat-top">
        <span className="stat-value">{value ?? '—'}</span>
      </div>
      <span className="stat-label">{label}</span>
      {hint && <span className="stat-hint">{hint}</span>}
    </div>
  )
}

export function PageHeader({ title, subtitle, children }) {
  return (
    <div className="page-header">
      <div>
        <h1 className="page-title">{title}</h1>
        {subtitle && <p className="page-subtitle">{subtitle}</p>}
      </div>
      {children && <div className="page-header-actions">{children}</div>}
    </div>
  )
}

export function Banner({ kind = 'info', children, onClose }) {
  if (!children) return null
  return (
    <div className={`banner ${kind}`} role={kind === 'error' ? 'alert' : 'status'}>
      <span>{children}</span>
      {onClose && (
        <button className="banner-close" onClick={onClose} aria-label="Dismiss">×</button>
      )}
    </div>
  )
}
