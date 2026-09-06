import { createContext, useCallback, useContext, useMemo, useState } from 'react'
import apiClient, { getStoredAuth, setStoredAuth } from '../api/client.js'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [auth, setAuth] = useState(() => getStoredAuth())

  const login = useCallback(async (email, password) => {
    const { data } = await apiClient.post('/auth/login', { email, password })
    setStoredAuth(data)
    setAuth(data)
    return data
  }, [])

  const register = useCallback(async ({ role, fullName, email, phone, password }) => {
    const { data } = await apiClient.post('/auth/register', { role, fullName, email, phone, password })
    setStoredAuth(data)
    setAuth(data)
    return data
  }, [])

  const logout = useCallback(() => {
    setStoredAuth(null)
    setAuth(null)
  }, [])

  const value = useMemo(
    () => ({
      user: auth,
      isAuthenticated: Boolean(auth?.token),
      login,
      register,
      logout,
    }),
    [auth, login, register, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return ctx
}
