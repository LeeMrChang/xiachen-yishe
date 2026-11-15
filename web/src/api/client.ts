export class ApiError extends Error {
  code: number

  constructor(code: number, message: string) {
    super(message)
    this.code = code
  }
}

export interface StaffProfile {
  id: number
  username: string
  displayName: string
  role: string
  storeId: number | null
}

const TOKEN_KEY = 'xc.admin.token'
const STAFF_KEY = 'xc.admin.staff'

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function getStaff(): StaffProfile | null {
  const raw = localStorage.getItem(STAFF_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as StaffProfile
  } catch {
    return null
  }
}

export function saveSession(token: string, staff: StaffProfile) {
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(STAFF_KEY, JSON.stringify(staff))
}

export function clearSession() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(STAFF_KEY)
}

interface ApiEnvelope<T> {
  code: number
  message: string
  data: T
}

export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = {}
  if (!(options.body instanceof FormData)) {
    headers['Content-Type'] = 'application/json'
  }
  const token = getToken()
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }
  let response: Response
  try {
    response = await fetch(path, { ...options, headers })
  } catch {
    throw new ApiError(50000, '无法连接服务，请确认后台服务已启动')
  }
  let envelope: ApiEnvelope<T> | null = null
  try {
    envelope = (await response.json()) as ApiEnvelope<T>
  } catch {
    envelope = null
  }
  if (!envelope) {
    throw new ApiError(50000, `服务返回异常（HTTP ${response.status}）`)
  }
  if (envelope.code !== 0) {
    if (envelope.code === 40101) {
      clearSession()
      if (!location.pathname.startsWith('/login')) {
        location.assign('/login')
      }
    }
    throw new ApiError(envelope.code, envelope.message)
  }
  return envelope.data
}

export async function login(username: string, password: string) {
  const data = await api<{ token: string; expiresIn: number; staff: StaffProfile }>(
    '/api/admin/auth/login',
    { method: 'POST', body: JSON.stringify({ username, password }) }
  )
  saveSession(data.token, data.staff)
  return data
}
