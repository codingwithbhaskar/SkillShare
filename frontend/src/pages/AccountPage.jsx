import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'
import { deactivateMyAccount } from '../api/users.js'
import { extractErrorMessage } from '../api/client.js'
import { TrashIcon, UserIcon } from '../components/icons.jsx'

/** Minimal "my account" page - profile summary plus a danger zone for
 *  self-service deactivation. Admin accounts never see the danger zone
 *  (mirrors the backend's AuthService.deactivateSelf guard, which
 *  rejects it outright for role = admin - locking the only/last admin
 *  out with no recovery path). */
export default function AccountPage() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [confirming, setConfirming] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')

  async function handleDeactivate() {
    if (!confirming) {
      setConfirming(true)
      return
    }
    setError('')
    setSubmitting(true)
    try {
      await deactivateMyAccount()
      logout()
      navigate('/login', {
        replace: true,
        state: { message: 'Your account has been deactivated. Contact support to reactivate it.' },
      })
    } catch (err) {
      setError(extractErrorMessage(err, 'Could not deactivate your account.'))
      setConfirming(false)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="container" style={{ maxWidth: 560 }}>
      <h1 className="h3 mb-4 d-flex align-items-center gap-2"><UserIcon /> My Account</h1>

      <div className="card p-4 fade-in-up mb-4">
        <dl className="row mb-0">
          <dt className="col-4">Name</dt>
          <dd className="col-8">{user.fullName}</dd>
          <dt className="col-4">Email</dt>
          <dd className="col-8">{user.email}</dd>
          <dt className="col-4">Role</dt>
          <dd className="col-8"><span className="badge bg-primary text-uppercase">{user.role}</span></dd>
        </dl>
      </div>

      {user.role !== 'admin' && (
        <div className="card p-4 border-danger fade-in-up">
          <h2 className="h6 text-danger">Danger zone</h2>
          <p className="text-muted small mb-3">
            Deactivating your account signs you out and blocks future logins until you contact support
            to reactivate it. This does not delete your existing bookings or reviews.
          </p>
          {error && <div className="alert alert-danger">{error}</div>}
          <button
            type="button"
            className={`btn btn-sm ${confirming ? 'btn-danger' : 'btn-outline-danger'}`}
            onClick={handleDeactivate}
            disabled={submitting}
          >
            <TrashIcon width={14} height={14} />{' '}
            {submitting ? 'Deactivating…' : confirming ? 'Click again to confirm' : 'Deactivate my account'}
          </button>
        </div>
      )}
    </div>
  )
}
