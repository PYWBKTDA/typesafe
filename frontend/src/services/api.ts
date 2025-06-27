export async function register(data: { username: string; password: string }) {
  const res = await fetch('/api/register', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  })

  if (!res.ok) {
    const text = await res.text()
    return { error: text }
  }

  return { status: 'ok' }
}

export async function login(data: { username: string; password: string }) {
  const res = await fetch('/api/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  })

  if (!res.ok) {
    const text = await res.text()
    return { error: text }
  }

  const json = await res.json()
  return { user: json.user }
}

export async function changePassword(data: { username: string; oldPassword: string; newPassword: string }) {
  const res = await fetch('/api/change-password', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  })

  if (!res.ok) {
    const text = await res.text()
    return { error: text }
  }

  return { status: 'ok' }
}
