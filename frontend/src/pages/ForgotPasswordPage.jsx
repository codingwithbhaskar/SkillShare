import { useState } from 'react'
import { Link } from 'react-router-dom'
import { requestPasswordReset } from '../api/auth.js'
import { extractErrorMessage } from '../api/client.js'
import { ArrowRightIcon, UserIcon } from '../components/icons.jsx'
import useDocumentTitle from '../hooks/useDocumentTitle.js'

export default function ForgotPasswordPage() {
  useDocumentTitle('Reset password')
  const [email, setEmail] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [sent, setSent] = useState(false)
  const [error, setError] = useState('')

  async function handleSubmit(e) {
    e.preventDefault()
    setError('')
    setSubmitting(true)
    try {
      await requestPasswordReset(email)
      setSent(true)
    } catch (err) {
      setError(extractErrorMessage(err, 'Could not start the reset. Please try again.'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="container" style={{ maxWidth: 420 }}>
      <h1 className="h3 mb-4 d-flex align-items-center gap-2"><UserIcon /> Reset your password</h1>
      <div className="card p-4 fade-in-up">
        {sent ? (
          <div className="alert alert-success mb-0">
            If an account exists for <strong>{email}</strong>, we've sent a link to reset
            its password. The link is valid for 30 minutes — check your inbox (and spam
            folder).
          </div>
        ) : (
          <>
            {error && <div className="alert alert-danger">{error}</div>}
            <p className="text-muted">
              Enter the email you signed up with and we'll send you a link to choose a
              new password.
            </p>
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
              <button type="submit" className="btn btn-primary w-100" disabled={submitting}>
                {submitting ? 'Sending…' : <>Send reset link <ArrowRightIcon width={16} height={16} /></>}
              </button>
            </form>
          </>
        )}
      </div>
      <p className="mt-3 text-center">
        <Link to="/login">Back to log in</Link>
      </p>
    </div>
  )
}
