import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'
import { extractErrorMessage } from '../api/client.js'
import { ArrowRightIcon, UserIcon } from '../components/icons.jsx'

export default function RegisterPage() {
  const { register } = useAuth()
  const navigate = useNavigate()
  const [form, setForm] = useState({
    role: 'customer',
    fullName: '',
    email: '',
    phone: '',
    password: '',
  })
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  function update(field) {
    return (e) => setForm((f) => ({ ...f, [field]: e.target.value }))
  }

  async function handleSubmit(e) {
    e.preventDefault()
    setError('')
    setSubmitting(true)
    try {
      await register(form)
      navigate('/', { replace: true })
    } catch (err) {
      setError(extractErrorMessage(err, 'Registration failed.'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="container" style={{ maxWidth: 480 }}>
      <h1 className="h3 mb-4 d-flex align-items-center gap-2"><UserIcon /> Create an account</h1>
      <div className="card p-4 fade-in-up">
      {error && <div className="alert alert-danger">{error}</div>}
      <form onSubmit={handleSubmit}>
        <div className="mb-3">
          <label className="form-label" htmlFor="role">I am a…</label>
          <select id="role" className="form-select" value={form.role} onChange={update('role')}>
            <option value="customer">Customer — I need help with a job</option>
            <option value="worker">Worker — I offer skilled services</option>
          </select>
        </div>
        <div className="mb-3">
          <label className="form-label" htmlFor="fullName">Full name</label>
          <input id="fullName" className="form-control" value={form.fullName} onChange={update('fullName')} required />
        </div>
        <div className="mb-3">
          <label className="form-label" htmlFor="email">Email</label>
          <input id="email" type="email" className="form-control" value={form.email} onChange={update('email')} required />
        </div>
        <div className="mb-3">
          <label className="form-label" htmlFor="phone">Phone</label>
          <input id="phone" className="form-control" value={form.phone} onChange={update('phone')} />
        </div>
        <div className="mb-3">
          <label className="form-label" htmlFor="password">Password</label>
          <input
            id="password"
            type="password"
            className="form-control"
            value={form.password}
            onChange={update('password')}
            minLength={8}
            required
          />
          <div className="form-text">At least 8 characters.</div>
        </div>
        <button type="submit" className="btn btn-primary w-100" disabled={submitting}>
          {submitting ? 'Creating account…' : <>Create account <ArrowRightIcon width={16} height={16} /></>}
        </button>
      </form>
      </div>
      <p className="mt-3 text-center">
        Already have an account? <Link to="/login">Log in</Link>
      </p>
    </div>
  )
}
