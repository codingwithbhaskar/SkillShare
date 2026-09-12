import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'
import { changeMyPassword, deactivateMyAccount } from '../api/users.js'
import { extractErrorMessage } from '../api/client.js'
import { TrashIcon, UserIcon } from '../components/icons.jsx'

/** "My account" page - every role (customer/worker/admin) can edit their
 *  own name/phone and change their password here; worker-specific fields
 *  (bio, rate, location, skills, availability) stay on the separate
 *  Worker Profile page, which only a worker has any use for. Admin
 *  accounts never see the danger zone (mirrors the backend's
 *  AuthService.deactivateSelf guard, which rejects it outright for
 *  role = admin - locking the only/last admin out with no recovery
 *  path). */
export default function AccountPage() {
  const { user, updateProfile, logout } = useAuth()
  const navigate = useNavigate()

  const [fullName, setFullName] = useState(user.fullName || '')
  const [phone, setPhone] = useState(user.phone || '')
  const [profileError, setProfileError] = useState('')
  const [profileSaved, setProfileSaved] = useState(false)
  const [savingProfile, setSavingProfile] = useState(false)

  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [passwordError, setPasswordError] = useState('')
  const [passwordSaved, setPasswordSaved] = useState(false)
  const [savingPassword, setSavingPassword] = useState(false)

  const [confirming, setConfirming] = useState(false)
  const [deactivating, setDeactivating] = useState(false)
  const [deactivateError, setDeactivateError] = useState('')

  async function handleProfileSubmit(e) {
    e.preventDefault()
    setProfileError('')
    setProfileSaved(false)
    setSavingProfile(true)
    try {
      await updateProfile({ fullName, phone })
      setProfileSaved(true)
    } catch (err) {
      setProfileError(extractErrorMessage(err, 'Could not update your profile.'))
    } finally {
      setSavingProfile(false)
    }
  }

  async function handlePasswordSubmit(e) {
    e.preventDefault()
    setPasswordError('')
    setPasswordSaved(false)
    if (newPassword !== confirmPassword) {
      setPasswordError('The two new passwords don’t match.')
      return
    }
    setSavingPassword(true)
    try {
      await changeMyPassword(currentPassword, newPassword)
      setPasswordSaved(true)
      setCurrentPassword('')
      setNewPassword('')
      setConfirmPassword('')
    } catch (err) {
      setPasswordError(extractErrorMessage(err, 'Could not change your password.'))
    } finally {
      setSavingPassword(false)
    }
  }

  async function handleDeactivate() {
    if (!confirming) {
      setConfirming(true)
      return
    }
    setDeactivateError('')
    setDeactivating(true)
    try {
      await deactivateMyAccount()
      logout()
      navigate('/login', {
        replace: true,
        state: { message: 'Your account has been deactivated. Contact support to reactivate it.' },
      })
    } catch (err) {
      setDeactivateError(extractErrorMessage(err, 'Could not deactivate your account.'))
      setConfirming(false)
    } finally {
      setDeactivating(false)
    }
  }

  return (
    <div className="container" style={{ maxWidth: 560 }}>
      <h1 className="h3 mb-4 d-flex align-items-center gap-2"><UserIcon /> My Account</h1>

      <div className="card p-4 fade-in-up mb-4">
        <h2 className="h6 mb-3">Profile</h2>
        {profileError && <div className="alert alert-danger">{profileError}</div>}
        {profileSaved && <div className="alert alert-success">Profile updated.</div>}
        <form onSubmit={handleProfileSubmit}>
          <div className="mb-3">
            <label className="form-label" htmlFor="email">Email</label>
            <input id="email" className="form-control" value={user.email} disabled />
            <div className="form-text">Email can't be changed from here.</div>
          </div>
          <div className="mb-3">
            <label className="form-label" htmlFor="role">Role</label>
            <input id="role" className="form-control text-capitalize" value={user.role} disabled />
          </div>
          <div className="mb-3">
            <label className="form-label" htmlFor="fullName">Full name</label>
            <input
              id="fullName"
              className="form-control"
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
              required
            />
          </div>
          <div className="mb-3">
            <label className="form-label" htmlFor="phone">Phone</label>
            <input id="phone" className="form-control" value={phone} onChange={(e) => setPhone(e.target.value)} />
          </div>
          <button type="submit" className="btn btn-primary" disabled={savingProfile}>
            {savingProfile ? 'Saving…' : 'Save profile'}
          </button>
        </form>
      </div>

      <div className="card p-4 fade-in-up mb-4">
        <h2 className="h6 mb-3">Change password</h2>
        {passwordError && <div className="alert alert-danger">{passwordError}</div>}
        {passwordSaved && <div className="alert alert-success">Password changed.</div>}
        <form onSubmit={handlePasswordSubmit}>
          <div className="mb-3">
            <label className="form-label" htmlFor="currentPassword">Current password</label>
            <input
              id="currentPassword"
              type="password"
              className="form-control"
              value={currentPassword}
              onChange={(e) => setCurrentPassword(e.target.value)}
              required
            />
          </div>
          <div className="mb-3">
            <label className="form-label" htmlFor="newPassword">New password</label>
            <input
              id="newPassword"
              type="password"
              className="form-control"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              minLength={8}
              required
            />
            <div className="form-text">At least 8 characters.</div>
          </div>
          <div className="mb-3">
            <label className="form-label" htmlFor="confirmPassword">Confirm new password</label>
            <input
              id="confirmPassword"
              type="password"
              className="form-control"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              minLength={8}
              required
            />
          </div>
          <button type="submit" className="btn btn-primary" disabled={savingPassword}>
            {savingPassword ? 'Saving…' : 'Change password'}
          </button>
        </form>
      </div>

      {user.role !== 'admin' && (
        <div className="card p-4 border-danger fade-in-up">
          <h2 className="h6 text-danger">Danger zone</h2>
          <p className="text-muted small mb-3">
            Deactivating your account signs you out and blocks future logins until you contact support
            to reactivate it. This does not delete your existing bookings or reviews.
          </p>
          {deactivateError && <div className="alert alert-danger">{deactivateError}</div>}
          <button
            type="button"
            className={`btn btn-sm ${confirming ? 'btn-danger' : 'btn-outline-danger'}`}
            onClick={handleDeactivate}
            disabled={deactivating}
          >
            <TrashIcon width={14} height={14} />{' '}
            {deactivating ? 'Deactivating…' : confirming ? 'Click again to confirm' : 'Deactivate my account'}
          </button>
        </div>
      )}
    </div>
  )
}
