async function request(path, options = {}) {
  let res
  try {
    res = await fetch(path, {
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      ...options
    })
  } catch {
    const error = new Error('Cannot reach the FarmLink server. Check your connection and try again.')
    error.status = 0
    throw error
  }

  if (res.status === 401) {
    const error = new Error('Please log in to continue.')
    error.status = 401
    throw error
  }

  const text = await res.text()
  let body = null
  if (text) {
    try {
      body = JSON.parse(text)
    } catch {
      body = null
    }
  }

  if (!res.ok) {
    const error = new Error(body?.message || `Request failed (${res.status})`)
    error.status = res.status
    throw error
  }
  return body
}

export const api = {
  get: (path) => request(path),
  post: (path, body) => request(path, { method: 'POST', body: JSON.stringify(body) }),
  put: (path, body) => request(path, { method: 'PUT', body: JSON.stringify(body) })
}

export function login() {
  window.location.href = '/oauth2/authorization/google'
}

export async function logout() {
  await fetch('/logout', { method: 'POST', credentials: 'include' })
}

export function money(value) {
  return `RWF ${Number(value || 0).toLocaleString()}`
}

export function formatDate(value) {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleDateString(undefined, {
    year: 'numeric', month: 'short', day: 'numeric'
  })
}

export function formatDateTime(value) {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString(undefined, {
    month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit'
  })
}
