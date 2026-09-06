import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { fetchMyWorkerProfile, fetchWorkerBookings, fetchWorkerReviews, fetchWorkerStats } from '../api/workers.js'
import { extractErrorMessage } from '../api/client.js'
import { Spinner } from '../components/Spinner.jsx'
import {
  CalendarIcon, CheckCircleIcon, ClockIcon, LayoutDashboardIcon, StarIcon,
} from '../components/icons.jsx'

const STATUS_BADGE = {
  pending: 'bg-secondary',
  confirmed: 'bg-primary',
  in_progress: 'bg-warning text-dark badge-pulse',
  completed: 'bg-success',
  cancelled: 'bg-danger',
}

export default function WorkerDashboardPage() {
  const [profile, setProfile] = useState(null)
  const [stats, setStats] = useState(null)
  const [bookings, setBookings] = useState([])
  const [reviews, setReviews] = useState([])
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false
    async function load() {
      try {
        const p = await fetchMyWorkerProfile()
        if (cancelled) return
        setProfile(p)
        const [s, b, r] = await Promise.all([
          fetchWorkerStats(p.workerId),
          fetchWorkerBookings(p.workerId),
          fetchWorkerReviews(p.workerId),
        ])
        if (cancelled) return
        setStats(s)
        setBookings(b)
        setReviews(r)
      } catch (err) {
        if (!cancelled) setError(extractErrorMessage(err, 'Could not load your worker dashboard.'))
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

  if (!profile || !stats) {
    return (
      <div className="container">
        <Spinner label="Loading your dashboard…" />
      </div>
    )
  }

  const statCards = [
    { icon: LayoutDashboardIcon, label: 'Total bookings', value: stats.totalBookings },
    { icon: CheckCircleIcon, label: 'Completed', value: stats.completedBookings },
    { icon: ClockIcon, label: 'Active', value: stats.activeBookings },
    { icon: StarIcon, label: 'Avg rating', value: stats.reviewCount > 0 ? `${Number(stats.avgRating).toFixed(1)} (${stats.reviewCount})` : '—' },
  ]

  return (
    <div className="container">
      <h1 className="h3 mb-1 fade-in-up d-flex align-items-center gap-2">
        <LayoutDashboardIcon /> Worker dashboard
      </h1>
      <p className="text-muted">Worker #{profile.workerId} — {profile.experienceYears} yr(s) experience</p>

      {!profile.hasLocation && (
        <div className="alert alert-warning d-flex justify-content-between align-items-center flex-wrap gap-2">
          <span>
            No registered location on file yet, so you won't show up in nearby-worker or
            allocation searches.
          </span>
          <Link to="/worker/profile" className="btn btn-sm btn-warning">Complete your profile</Link>
        </div>
      )}

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

      <h2 className="h5 mb-3 d-flex align-items-center gap-2"><CalendarIcon width={20} height={20} /> Your bookings</h2>
      {bookings.length === 0 && <p className="text-muted">No bookings assigned yet.</p>}
      <div className="d-flex flex-column gap-3 mb-4 stagger">
        {bookings.map((b) => (
          <Link
            key={b.bookingId}
            to={`/bookings/${b.bookingId}`}
            className="card card-interactive p-3 text-decoration-none text-reset"
          >
            <div className="d-flex justify-content-between align-items-start">
              <div>
                <div className="fw-semibold">Booking #{b.bookingId}</div>
                <div className="text-muted small">
                  {new Date(b.scheduledStart).toLocaleString()} – {new Date(b.scheduledEnd).toLocaleTimeString()}
                </div>
              </div>
              <span className={`badge ${STATUS_BADGE[b.status] || 'bg-secondary'} text-uppercase`}>
                {b.status.replace('_', ' ')}
              </span>
            </div>
          </Link>
        ))}
      </div>

      <h2 className="h5 mb-3 d-flex align-items-center gap-2"><StarIcon width={20} height={20} /> Reviews</h2>
      {reviews.length === 0 && <p className="text-muted">No reviews yet.</p>}
      <div className="d-flex flex-column gap-2 stagger">
        {reviews.map((r) => (
          <div className="card p-3" key={r.reviewId}>
            <div className="fw-semibold text-warning">
              {Array.from({ length: 5 }).map((_, i) => (
                <StarIcon key={i} width={16} height={16} style={{ fill: i < r.rating ? 'currentColor' : 'none' }} />
              ))}
            </div>
            {r.comment && <div className="text-muted">{r.comment}</div>}
          </div>
        ))}
      </div>
    </div>
  )
}
