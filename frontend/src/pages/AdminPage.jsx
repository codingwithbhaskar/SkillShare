import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { fetchAdminStats } from '../api/admin.js'
import { extractErrorMessage } from '../api/client.js'
import { Spinner } from '../components/Spinner.jsx'
import {
  BarChartIcon, CreditCardIcon, LayoutDashboardIcon, ListIcon, ShieldCheckIcon, UsersIcon, WrenchIcon,
} from '../components/icons.jsx'
import useDocumentTitle from '../hooks/useDocumentTitle.js'

const STATUS_BADGE = {
  pending: 'bg-secondary',
  confirmed: 'bg-primary',
  in_progress: 'bg-warning text-dark',
  completed: 'bg-success',
  cancelled: 'bg-danger',
}

const STATUS_LABEL = {
  pending: 'Pending',
  confirmed: 'Confirmed',
  in_progress: 'In progress',
  completed: 'Completed',
  cancelled: 'Cancelled',
}

export default function AdminPage() {
  useDocumentTitle('Admin Dashboard')
  const [stats, setStats] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false
    async function load() {
      try {
        const data = await fetchAdminStats()
        if (!cancelled) setStats(data)
      } catch (err) {
        if (!cancelled) setError(extractErrorMessage(err, 'Could not load the admin dashboard.'))
      }
    }
    load()
    return () => {
      cancelled = true
    }
  }, [])

  if (error) {
    return (
      <div className="container">
        <div className="alert alert-danger">{error}</div>
      </div>
    )
  }

  if (!stats) {
    return (
      <div className="container">
        <Spinner label="Loading admin dashboard…" />
      </div>
    )
  }

  const statCards = [
    { icon: UsersIcon, label: 'Total users', value: stats.totalUsers },
    { icon: WrenchIcon, label: 'Workers', value: stats.totalWorkers },
    { icon: ListIcon, label: 'Total bookings', value: stats.totalBookings },
    { icon: CreditCardIcon, label: 'Total revenue', value: `₹${Number(stats.totalRevenue).toFixed(2)}` },
  ]

  return (
    <div className="container">
      <h1 className="h3 mb-1 fade-in-up d-flex align-items-center gap-2">
        <ShieldCheckIcon /> Admin dashboard
      </h1>
      <p className="text-muted">
        {stats.totalCustomers} customer(s), {stats.totalWorkers} worker(s), {stats.totalAdmins} admin(s) —{' '}
        {stats.activeUsers} active, {stats.suspendedUsers} suspended.
      </p>

      <div className="row g-3 mb-4 stagger">
        {statCards.map(({ icon: Icon, label, value }) => (
          <div className="col-6 col-md-3" key={label}>
            <div className="card stat-card p-3 text-center h-100">
              <Icon width={22} height={22} className="mx-auto mb-1 text-primary" />
              <div className="text-muted small">{label}</div>
              <div className="h4 mb-0">{value}</div>
            </div>
          </div>
        ))}
      </div>

      <div className="row g-3 mb-4">
        <div className="col-12">
          <div className="card p-3">
            <h2 className="h6 mb-3">Bookings by status</h2>
            <div className="d-flex flex-wrap gap-2">
              {Object.entries(stats.bookingsByStatus).map(([status, count]) => (
                <span key={status} className={`badge ${STATUS_BADGE[status] || 'bg-secondary'}`}>
                  {STATUS_LABEL[status] || status}: {count}
                </span>
              ))}
            </div>
          </div>
        </div>
      </div>

      <h2 className="h5 mb-3">Quick actions</h2>
      <div className="row g-3 mb-4 stagger">
        <div className="col-md-4">
          <Link to="/admin/users" className="card card-interactive p-3 text-decoration-none text-reset h-100">
            <div className="d-flex align-items-center gap-2 fw-semibold">
              <UsersIcon width={20} height={20} className="text-primary" /> Manage users
            </div>
            <div className="text-muted small mt-1">View accounts, filter by role, suspend or reactivate.</div>
          </Link>
        </div>
        <div className="col-md-4">
          <Link to="/admin/catalog" className="card card-interactive p-3 text-decoration-none text-reset h-100">
            <div className="d-flex align-items-center gap-2 fw-semibold">
              <WrenchIcon width={20} height={20} className="text-primary" /> Manage services &amp; skills
            </div>
            <div className="text-muted small mt-1">
              {stats.totalServices} service(s), {stats.totalSkills} skill(s) in the catalogue.
            </div>
          </Link>
        </div>
        <div className="col-md-4">
          <Link to="/admin/reports" className="card card-interactive p-3 text-decoration-none text-reset h-100">
            <div className="d-flex align-items-center gap-2 fw-semibold">
              <BarChartIcon width={20} height={20} className="text-primary" /> Reports &amp; analytics
            </div>
            <div className="text-muted small mt-1">Revenue and top performers over a date range.</div>
          </Link>
        </div>
      </div>

      <h2 className="h5 mb-3 d-flex align-items-center gap-2">
        <LayoutDashboardIcon width={20} height={20} /> Recent bookings
      </h2>
      {stats.recentBookings.length === 0 && <p className="text-muted">No bookings yet.</p>}
      <div className="d-flex flex-column gap-2 stagger">
        {stats.recentBookings.map((b) => (
          <Link
            key={b.bookingId}
            to={`/bookings/${b.bookingId}`}
            className="card card-interactive p-3 text-decoration-none text-reset"
          >
            <div className="d-flex justify-content-between align-items-start flex-wrap gap-2">
              <div>
                <div className="fw-semibold">
                  Booking #{b.bookingId} — {b.serviceName}
                </div>
                <div className="text-muted small">
                  {b.customerName} {b.workerName ? `→ ${b.workerName}` : '(unassigned)'} ·{' '}
                  {new Date(b.scheduledStart).toLocaleString()}
                </div>
              </div>
              <div className="d-flex align-items-center gap-2">
                {b.totalAmount != null && <span className="text-muted small">₹{b.totalAmount}</span>}
                <span className={`badge ${STATUS_BADGE[b.status] || 'bg-secondary'} text-uppercase`}>
                  {b.status.replace('_', ' ')}
                </span>
              </div>
            </div>
          </Link>
        ))}
      </div>
    </div>
  )
}
