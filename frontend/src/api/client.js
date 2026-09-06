import axios from 'axios'

const baseURL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'

const apiClient = axios.create({ baseURL })

const AUTH_STORAGE_KEY = 'skillshare_auth'

export function getStoredAuth() {
  try {
    const raw = localStorage.getItem(AUTH_STORAGE_KEY)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

export function setStoredAuth(auth) {
  try {
    if (auth) {
      localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(auth))
    } else {
      localStorage.removeItem(AUTH_STORAGE_KEY)
    }
  } catch {
    // localStorage can throw in private-browsing/blocked-storage contexts;
    // auth just won't persist across a reload in that case.
  }
}

apiClient.interceptors.request.use((config) => {
  const auth = getStoredAuth()
  if (auth?.token) {
    config.headers.Authorization = `Bearer ${auth.token}`
  }
  return config
})

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const isLoginCall = error.config?.url?.includes('/auth/login')
    if (error.response?.status === 401 && !isLoginCall) {
      setStoredAuth(null)
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
    }
    return Promise.reject(error)
  },
)

// GlobalExceptionHandler on the backend returns a plain-text body
// (ex.getMessage()) for every handled error, not JSON — so the message
// is error.response.data itself (a string), not error.response.data.message.
export function extractErrorMessage(error, fallback = 'Something went wrong. Please try again.') {
  if (typeof error.response?.data === 'string' && error.response.data.length > 0) {
    return error.response.data
  }
  return error.message || fallback
}

export default apiClient
