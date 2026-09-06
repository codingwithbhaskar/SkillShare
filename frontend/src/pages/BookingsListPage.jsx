import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { fetchMyBookings } from '../api/bookings.js'
import { extractErrorMessage } from '../api/client.js'
import { Spinner } from '../components/Spinner.jsx'
import { CalendarIcon, ListIcon, MapPinIcon, PlusCircleIcon } from '../components/icons.jsx'

const STATUS_BADGE = {
  pending: 'bg-secondary',
  confirmed: 'bg-primary',
  in_progress: 'bg-warning text-dark badge-pulse',
  completed: 'bg-success',
  cancelled: 'bg-danger',
}

export default function BookingsListPage() {
  const [bookings, setBookings] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false
    fetchMyBookings()
      .then((data) => {
        if (!cancelled) setBookings(data)
      })
      .catch((err) => {
        if (!cancelled) setError(extractErrorMessage(err, 'Could not load your bookings.'))
      })
    return () => {
      cancelled = true
    }
  }, [])

  return (
    <div className="container">
      <div className="d-flex justify-content-between align-items-center mb-3 fade-in-up">
        <h1 className="h3 mb-0 d-flex align-items-center gap-2"><ListIcon /> My bookings</h1>
        <Link to="/bookings/new" className="btn btn-primary btn-sm">
          <PlusCircleIcon width={16} height={16} /> Book a service
        </Link>
      </div>

      {error && <div className="alert alert-danger">{error}</div>}

      {bookings === null && !error && <Spinner label="Loading your bookings…" />}

      {bookings !== null && bookings.length === 0 && (
        <div className="app-panel p-4 text-center fade-in-up">
          <div className="feature-icon mx-auto"><CalendarIcon width={26} height={26} /></div>
          <p className="mb-3">You haven't booked anything yet.</p>
          <Link to="/bookings/new" className="btn btn-primary">Book your first service</Link>
        </div>
      )}

      <div className="d-flex flex-column gap-3 stagger">
        {bookings?.map((b) => (
          <Link
            key={b.bookingId}
            to={`/bookings/${b.bookingId}`}
            className="card card-interactive p-3 text-decoration-none text-reset"
          >
            <div className="d-flex justify-content-between align-items-start">
              <div>
                <div className="fw-semibold">Booking #{b.bookingId}</div>
                <div className="text-muted small d-flex align-items-center gap-1">
                  <MapPinIcon width={14} height={14} /> {b.addressLine}, {b.city}
                </div>
                <div className="text-muted small d-flex align-items-center gap-1">
                  <CalendarIcon width={14} height={14} />
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
    </div>
  )
}
