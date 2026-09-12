import { useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { resetPassword } from '../api/auth.js'
import { extractErrorMessage } from '../api/client.js'
import { ArrowRightIcon, UserIcon } from '../components/icons.jsx'
import useDocumentTitle from '../hooks/useDocumentTitle.js'

export default function ResetPasswordPage() {
  useDocumentTitle('Choose a new password')
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const token = searchParams.get('token') || ''
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(e) {
    e.preventDefault()
    setError('')
    if (password !== confirm) {
      setError('The two passwords don’t match.')
      return
    }
    setSubmitting(true)
    try {
      await resetPassword(token, password)
      navigate('/login', {
        replace: true,
        state: { message: 'Your password has been reset. Log in with your new password.' },
      })
    } catch (err) {
      setError(extractErrorMessage(err, 'Could not reset your password. The link may have expired.'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="container" style={{ maxWidth: 420 }}>
      <h1 className="h3 mb-4 d-flex align-items-center gap-2"><UserIcon /> Choose a new password</h1>
      <div className="card p-4 fade-in-up">
        {!token ? (
          <div className="alert alert-danger mb-0">
            This reset link is missing its token. Request a new one from the{' '}
            <Link to="/forgot-password">forgot password</Link> page.
          </div>
        ) : (
          <>
            {error && <div className="alert alert-danger">{error}</div>}
            <form onSubmit={handleSubmit}>
              <div className="mb-3">
                <label className="form-label" htmlFor="password">New password</label>
                <input
                  id="password"
                  type="password"
                  className="form-control"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  minLength={8}
                  required
                />
                <div className="form-text">At least 8 characters.</div>
              </div>
              <div className="mb-3">
                <label className="form-label" htmlFor="confirm">Confirm new password</label>
                <input
                  id="confirm"
                  type="password"
                  className="form-control"
                  value={confirm}
                  onChange={(e) => setConfirm(e.target.value)}
                  minLength={8}
                  required
                />
              </div>
              <button type="submit" className="btn btn-primary w-100" disabled={submitting}>
                {submitting ? 'Saving…' : <>Set new password <ArrowRightIcon width={16} height={16} /></>}
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
