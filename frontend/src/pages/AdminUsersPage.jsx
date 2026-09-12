import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { fetchAdminUsers, updateUserStatus } from '../api/admin.js'
import { extractErrorMessage } from '../api/client.js'
import { Spinner } from '../components/Spinner.jsx'
import { SearchIcon, UsersIcon } from '../components/icons.jsx'
import useDocumentTitle from '../hooks/useDocumentTitle.js'

const STATUS_BADGE = {
  active: 'bg-success',
  inactive: 'bg-secondary',
  suspended: 'bg-danger',
}

const ROLE_BADGE = {
  admin: 'bg-dark',
  worker: 'bg-primary',
  customer: 'bg-info text-dark',
}

export default function AdminUsersPage() {
  useDocumentTitle('Manage Users')
  const [users, setUsers] = useState(null)
  const [error, setError] = useState('')
  const [actionError, setActionError] = useState('')
  const [busyUserId, setBusyUserId] = useState(null)
  const [role, setRole] = useState('')
  const [status, setStatus] = useState('')
  const [search, setSearch] = useState('')
  const [searchInput, setSearchInput] = useState('')

  const load = useCallback(async () => {
    try {
      setError('')
      const data = await fetchAdminUsers({
        role: role || undefined,
        status: status || undefined,
        search: search || undefined,
      })
      setUsers(data)
    } catch (err) {
      setError(extractErrorMessage(err, 'Could not load users.'))
    }
  }, [role, status, search])

  useEffect(() => {
    load()
  }, [load])

  async function handleToggleStatus(user) {
    // Three real statuses now (active/suspended/inactive - the latter
    // reachable via self-deactivation, see AccountPage.jsx): both
    // suspended (admin-imposed) and inactive (self-deactivated) reactivate
    // to active; only an active user can be suspended from here.
    const nextStatus = user.status === 'active' ? 'suspended' : 'active'
    setActionError('')
    setBusyUserId(user.userId)
    try {
      const updated = await updateUserStatus(user.userId, nextStatus)
      setUsers((prev) => prev.map((u) => (u.userId === updated.userId ? updated : u)))
    } catch (err) {
      setActionError(extractErrorMessage(err, 'Could not update this user.'))
    } finally {
      setBusyUserId(null)
    }
  }

  function handleSearchSubmit(e) {
    e.preventDefault()
    setSearch(searchInput.trim())
  }

  return (
    <div className="container">
      <h1 className="h3 mb-1 fade-in-up d-flex align-items-center gap-2">
        <UsersIcon /> Manage users
      </h1>
      <p className="text-muted">
        <Link to="/admin">← Back to dashboard</Link>
      </p>

      <div className="card p-3 mb-3">
        <div className="row g-2 align-items-end">
          <div className="col-sm-3">
            <label className="form-label small mb-1">Role</label>
            <select className="form-select" value={role} onChange={(e) => setRole(e.target.value)}>
              <option value="">All roles</option>
              <option value="customer">Customer</option>
              <option value="worker">Worker</option>
              <option value="admin">Admin</option>
            </select>
          </div>
          <div className="col-sm-3">
            <label className="form-label small mb-1">Status</label>
            <select className="form-select" value={status} onChange={(e) => setStatus(e.target.value)}>
              <option value="">All statuses</option>
              <option value="active">Active</option>
              <option value="inactive">Inactive</option>
              <option value="suspended">Suspended</option>
            </select>
          </div>
          <div className="col-sm-6">
            <label className="form-label small mb-1">Search name or email</label>
            <form className="d-flex gap-2" onSubmit={handleSearchSubmit}>
              <input
                type="text"
                className="form-control"
                value={searchInput}
                onChange={(e) => setSearchInput(e.target.value)}
                placeholder="e.g. priya or priya@example.com"
              />
              <button type="submit" className="btn btn-outline-primary">
                <SearchIcon width={16} height={16} />
              </button>
            </form>
          </div>
        </div>
      </div>

      {actionError && <div className="alert alert-danger">{actionError}</div>}
      {error && <div className="alert alert-danger">{error}</div>}

      {!users && !error && <Spinner label="Loading users…" />}

      {users && users.length === 0 && <p className="text-muted">No users match these filters.</p>}

      {users && users.length > 0 && (
        <div className="table-responsive">
          <table className="table align-middle">
            <thead>
              <tr>
                <th>Name</th>
                <th>Email</th>
                <th>Phone</th>
                <th>Role</th>
                <th>Status</th>
                <th>Joined</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {users.map((u) => (
                <tr key={u.userId}>
                  <td>{u.fullName}</td>
                  <td>{u.email}</td>
                  <td>{u.phone || '—'}</td>
                  <td>
                    <span className={`badge ${ROLE_BADGE[u.role] || 'bg-secondary'} text-uppercase`}>{u.role}</span>
                  </td>
                  <td>
                    <span className={`badge ${STATUS_BADGE[u.status] || 'bg-secondary'} text-uppercase`}>
                      {u.status}
                    </span>
                  </td>
                  <td className="text-muted small">
                    {u.createdAt ? new Date(u.createdAt).toLocaleDateString() : '—'}
                  </td>
                  <td>
                    {u.role === 'admin' ? (
                      <span className="text-muted small">—</span>
                    ) : (
                      <button
                        type="button"
                        className={`btn btn-sm ${u.status === 'active' ? 'btn-outline-danger' : 'btn-outline-success'}`}
                        disabled={busyUserId === u.userId}
                        onClick={() => handleToggleStatus(u)}
                      >
                        {busyUserId === u.userId ? 'Working…' : u.status === 'active' ? 'Suspend' : 'Reactivate'}
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
