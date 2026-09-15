import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios'
import type { TokenResponse } from '@/types'

// Re-exported so auth.ts can call /auth/refresh without circular deps
export const BASE_URL = '/api/v1'

export const apiClient = axios.create({
  baseURL: BASE_URL,
  headers: { 'Content-Type': 'application/json' },
})

// ── Token helpers (read/write localStorage directly to avoid circular
//    imports with the Zustand store) ───────────────────────────────────
const getAccessToken = () => {
  try {
    const raw = localStorage.getItem('auth-storage')
    if (!raw) return null
    return JSON.parse(raw)?.state?.accessToken ?? null
  } catch {
    return null
  }
}

const getRefreshToken = () => {
  try {
    const raw = localStorage.getItem('auth-storage')
    if (!raw) return null
    return JSON.parse(raw)?.state?.refreshToken ?? null
  } catch {
    return null
  }
}

const setTokens = (tokens: TokenResponse) => {
  try {
    const raw = localStorage.getItem('auth-storage')
    const parsed = raw ? JSON.parse(raw) : { state: {} }
    parsed.state.accessToken = tokens.accessToken
    parsed.state.refreshToken = tokens.refreshToken
    parsed.state.isAuthenticated = true
    localStorage.setItem('auth-storage', JSON.stringify(parsed))
  } catch { /* ignore */ }
}

const clearAuth = () => {
  localStorage.removeItem('auth-storage')
}

// ── Request interceptor — attach JWT ─────────────────────────────────
apiClient.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = getAccessToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// ── Response interceptor — refresh on 401 ────────────────────────────
let isRefreshing = false
let failedQueue: Array<{ resolve: (value: string) => void; reject: (err: unknown) => void }> = []

const processQueue = (error: unknown, token: string | null = null) => {
  failedQueue.forEach(({ resolve, reject }) => {
    if (error) { reject(error) } else { resolve(token!) }
  })
  failedQueue = []
}

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean }

    if (error.response?.status !== 401 || originalRequest._retry) {
      return Promise.reject(error)
    }

    // Don't try to refresh on auth endpoints themselves
    if (originalRequest.url?.includes('/auth/')) {
      clearAuth()
      window.location.href = '/login'
      return Promise.reject(error)
    }

    if (isRefreshing) {
      return new Promise((resolve, reject) => {
        failedQueue.push({ resolve, reject })
      }).then((token) => {
        originalRequest.headers.Authorization = `Bearer ${token}`
        return apiClient(originalRequest)
      })
    }

    originalRequest._retry = true
    isRefreshing = true

    try {
      const refreshToken = getRefreshToken()
      if (!refreshToken) throw new Error('No refresh token')

      const { data } = await axios.post<TokenResponse>(`${BASE_URL}/auth/refresh`, { refreshToken })
      setTokens(data)
      processQueue(null, data.accessToken)
      originalRequest.headers.Authorization = `Bearer ${data.accessToken}`
      return apiClient(originalRequest)
    } catch (err) {
      processQueue(err, null)
      clearAuth()
      window.location.href = '/login'
      return Promise.reject(err)
    } finally {
      isRefreshing = false
    }
  }
)

export default apiClient
