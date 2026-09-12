import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'
import { extractErrorMessage } from '../api/client.js'
import { ArrowRightIcon, UserIcon } from '../components/icons.jsx'
import useDocumentTitle from '../hooks/useDocumentTitle.js'

export default function LoginPage() {
  useDocumentTitle('Log in')
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const infoMessage = location.state?.message

  async function handleSubmit(e) {
    e.preventDefault()
    setError('')
    setSubmitting(true)
    try {
      await login(email, password)
      const redirectTo = location.state?.from?.pathname || '/'
      navigate(redirectTo, { replace: true })
    } catch (err) {
      setError(extractErrorMessage(err, 'Login failed. Check your email and password.'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="container" style={{ maxWidth: 420 }}>
      <h1 className="h3 mb-4 d-flex align-items-center gap-2"><UserIcon /> Log in</h1>
      <div className="card p-4 fade-in-up">
      {infoMessage && <div className="alert alert-info">{infoMessage}</div>}
      {error && <div className="alert alert-danger">{error}</div>}
      <form onSubmit={handleSubmit}>
        <div className="mb-3">
          <label className="form-label" htmlFor="email">Email</label>
          <input
            id="email"
            type="email"
            className="form-control"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
        </div>
        <div className="mb-3">
          <div className="d-flex justify-content-between align-items-baseline">
            <label className="form-label" htmlFor="password">Password</label>
            <Link to="/forgot-password" className="small">Forgot password?</Link>
          </div>
          <input
            id="password"
            type="password"
            className="form-control"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </div>
        <button type="submit" className="btn btn-primary w-100" disabled={submitting}>
          {submitting ? 'Logging in…' : <>Log in <ArrowRightIcon width={16} height={16} /></>}
        </button>
      </form>
      </div>
      <p className="mt-3 text-center">
        No account? <Link to="/register">Sign up</Link>
      </p>
    </div>
  )
}
