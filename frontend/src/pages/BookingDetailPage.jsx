import { useCallback, useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'
import {
  cancelBooking, completeBooking, fetchBooking, startBooking, submitReview,
} from '../api/bookings.js'
import { allocateBooking } from '../api/allocation.js'
import { createPaymentOrder, fetchPayment, verifyPayment } from '../api/payments.js'
import { fetchMyWorkerProfile } from '../api/workers.js'
import { extractErrorMessage } from '../api/client.js'
import { loadRazorpayCheckout } from '../lib/razorpay.js'
import RouteMap from '../components/RouteMap.jsx'
import { Spinner } from '../components/Spinner.jsx'
import {
  CalendarIcon, CheckCircleIcon, ClockIcon, CreditCardIcon, ListIcon,
  MapPinIcon, NavigationIcon, PhoneIcon, StarIcon, UserIcon, XCircleIcon,
} from '../components/icons.jsx'
import useDocumentTitle from '../hooks/useDocumentTitle.js'

const STATUS_BADGE = {
  pending: 'bg-secondary',
  confirmed: 'bg-primary',
  in_progress: 'bg-warning text-dark badge-pulse',
  completed: 'bg-success',
  cancelled: 'bg-danger',
}

function StarPicker({ value, onChange }) {
  const [hover, setHover] = useState(0)
  return (
    <div className="d-flex gap-1" role="radiogroup" aria-label="Rating">
      {[1, 2, 3, 4, 5].map((n) => (
        <button
          key={n}
          type="button"
          className="btn btn-sm p-0 border-0 bg-transparent"
          style={{ color: '#EA580C', cursor: 'pointer', lineHeight: 1 }}
          onMouseEnter={() => setHover(n)}
          onMouseLeave={() => setHover(0)}
          onClick={() => onChange(n)}
          aria-label={`${n} star${n > 1 ? 's' : ''}`}
        >
          <StarIcon width={26} height={26} style={{ fill: (hover || value) >= n ? 'currentColor' : 'none' }} />
        </button>
      ))}
    </div>
  )
}

export default function BookingDetailPage() {
  const { bookingId } = useParams()
  useDocumentTitle(`Booking #${bookingId}`)
  const { user } = useAuth()

  const [booking, setBooking] = useState(null)
  const [payment, setPayment] = useState(null)
  const [myWorkerId, setMyWorkerId] = useState(null)
  const [error, setError] = useState('')
  const [actionError, setActionError] = useState('')
  const [busy, setBusy] = useState(false);

  const [reviewRating, setReviewRating] = useState(5)
  const [reviewComment, setReviewComment] = useState('')
  const [reviewSubmitted, setReviewSubmitted] = useState(false)
  const [routeInfo, setRouteInfo] = useState(null)
  const [geoError, setGeoError] = useState('')
  const [workerPosition, setWorkerPosition] = useState(null)

  const reload = useCallback(async () => {
    try {
      // fetchPayment doesn't depend on fetchBooking's result (both only
      // need bookingId, already in hand from useParams) - run them
      // concurrently rather than one-after-another. On a cross-region
      // deployment (browser -> Render -> Neon) each round trip costs
      // real time, and this page was paying for two of them in serial
      // for no reason.
      const [bookingResult, paymentResult] = await Promise.allSettled([
        fetchBooking(bookingId),
        fetchPayment(bookingId),
      ])
      if (bookingResult.status === 'rejected') throw bookingResult.reason
      const b = bookingResult.value
      setBooking(b)
      // BookingResponse now carries `reviewed` (closing the dangling-
      // review-form gap from the Phase 8 full retest) - sync local state
      // from it on every load/reload, not just right after a submit, so
      // a fresh page load of an already-reviewed booking shows the
      // "Thanks for your review!" card immediately instead of the form.
      setReviewSubmitted(b.reviewed)
      // A rejected paymentResult just means no payment/order created yet
      // for this booking — not an error state.
      setPayment(paymentResult.status === 'fulfilled' ? paymentResult.value : null)
    } catch (err) {
      setError(extractErrorMessage(err, 'Could not load this booking.'))
    }
  }, [bookingId])

  useEffect(() => {
    reload()
  }, [reload])

  // Resolve the viewer's own workerId once, if they're a worker — used to
  // tell whether *this* booking is assigned to *them* (for the map's
  // one-time-geolocation "you" pin, Option B from
  // location-map-feature-design.md).
  useEffect(() => {
    if (user?.role !== 'worker') return
    fetchMyWorkerProfile().then((p) => setMyWorkerId(p.workerId)).catch(() => {})
  }, [user])

  const isAssignedWorker = booking && myWorkerId && booking.workerId === myWorkerId
  const isCustomerViewer = user?.role === 'customer'
  const isWorkerViewer = user?.role === 'worker'

  useEffect(() => {
    if (!isAssignedWorker) return
    if (!navigator.geolocation) {
      setGeoError('Your browser does not support geolocation.')
      return
    }
    navigator.geolocation.getCurrentPosition(
      (pos) => setWorkerPosition({ lat: pos.coords.latitude, lon: pos.coords.longitude }),
      () => setGeoError('Location permission was not granted — showing the job address only.'),
      { enableHighAccuracy: true, timeout: 8000 },
    )
  }, [isAssignedWorker])

  async function runAction(fn) {
    setActionError('')
    setBusy(true)
    try {
      await fn()
      await reload()
    } catch (err) {
      setActionError(extractErrorMessage(err, 'That action failed.'))
    } finally {
      setBusy(false)
    }
  }

  async function handlePay() {
    setActionError('')
    setBusy(true)
    try {
      const order = await createPaymentOrder(booking.bookingId)
      const Razorpay = await loadRazorpayCheckout()
      const rzp = new Razorpay({
        key: order.razorpayKeyId,
        amount: order.amountInPaise,
        currency: order.currency,
        order_id: order.razorpayOrderId,
        name: 'SkillShare',
        description: `Booking #${booking.bookingId}`,
        theme: { color: '#1E40AF' },
        handler: async (response) => {
          try {
            await verifyPayment(booking.bookingId, {
              razorpayOrderId: response.razorpay_order_id,
              razorpayPaymentId: response.razorpay_payment_id,
              razorpaySignature: response.razorpay_signature,
            })
            await reload()
          } catch (err) {
            setActionError(extractErrorMessage(err, 'Payment verification failed.'))
          }
        },
      })
      rzp.on('payment.failed', () => setActionError('Payment failed or was cancelled.'))
      rzp.open()
    } catch (err) {
      setActionError(extractErrorMessage(err, 'Could not start checkout.'))
    } finally {
      setBusy(false)
    }
  }

  async function handleAllocate() {
    setActionError('')
    setBusy(true)
    try {
      const result = await allocateBooking(booking.bookingId)
      await reload()
      if (!result.allocated) {
        setActionError('No eligible worker was found for this slot yet — try a different time, or check back once more workers are available.')
      }
    } catch (err) {
      setActionError(extractErrorMessage(err, 'Could not allocate a worker.'))
    } finally {
      setBusy(false)
    }
  }

  async function handleReviewSubmit(e) {
    e.preventDefault()
    setActionError('')
    setBusy(true)
    try {
      await submitReview(booking.bookingId, { rating: Number(reviewRating), comment: reviewComment })
      setReviewSubmitted(true)
      await reload()
    } catch (err) {
      const message = extractErrorMessage(err, 'That action failed.')
      // BookingResponse.reviewed (see reload() above) is the real fix for
      // the dangling-form gap now - this catch is just a defensive
      // backstop for the narrow race where two tabs/requests both submit
      // a review for the same booking around the same time.
      if (message.toLowerCase().includes('review already exists')) {
        setReviewSubmitted(true)
      } else {
        setActionError(message)
      }
    } finally {
      setBusy(false)
    }
  }

  if (error) {
    return (
      <div className="container">
        <div className="alert alert-danger fade-in-up">{error}</div>
      </div>
    )
  }

  if (!booking) {
    return (
      <div className="container">
        <Spinner label="Loading booking…" />
      </div>
    )
  }

  const jobPosition = booking.latitude != null && booking.longitude != null
    ? { lat: Number(booking.latitude), lon: Number(booking.longitude) }
    : null

  const isPaid = payment?.status === 'completed'

  return (
    <div className="container" style={{ maxWidth: 800 }}>
      <div className="d-flex justify-content-between align-items-start mb-3 fade-in-up">
        <h1 className="h3 mb-0 d-flex align-items-center gap-2">
          <ListIcon /> Booking #{booking.bookingId}
        </h1>
        <span className={`badge ${STATUS_BADGE[booking.status] || 'bg-secondary'} text-uppercase`}>
          {booking.status.replace('_', ' ')}
        </span>
      </div>

      {actionError && <div className="alert alert-danger fade-in-up">{actionError}</div>}

      <div className="card p-4 mb-3 fade-in-up">
        <dl className="row mb-0">
          <dt className="col-sm-4 d-flex align-items-center gap-2"><CalendarIcon width={18} height={18} /> Scheduled</dt>
          <dd className="col-sm-8">
            {new Date(booking.scheduledStart).toLocaleString()} – {new Date(booking.scheduledEnd).toLocaleTimeString()}
          </dd>

          <dt className="col-sm-4 d-flex align-items-center gap-2"><ClockIcon width={18} height={18} /> Urgency</dt>
          <dd className="col-sm-8 text-capitalize">{booking.urgency}</dd>

          <dt className="col-sm-4 d-flex align-items-center gap-2"><MapPinIcon width={18} height={18} /> Address</dt>
          <dd className="col-sm-8">{booking.addressLine}, {booking.city}</dd>

          <dt className="col-sm-4 d-flex align-items-center gap-2"><UserIcon width={18} height={18} /> Worker assigned</dt>
          <dd className="col-sm-8">{booking.worker ? booking.worker.fullName : 'Not yet allocated'}</dd>

          <dt className="col-sm-4 d-flex align-items-center gap-2"><CreditCardIcon width={18} height={18} /> Total amount</dt>
          <dd className="col-sm-8">{booking.totalAmount != null ? `₹${booking.totalAmount}` : '—'}</dd>

          {booking.notes && (
            <>
              <dt className="col-sm-4">Notes</dt>
              <dd className="col-sm-8">{booking.notes}</dd>
            </>
          )}

          {payment && (
            <>
              <dt className="col-sm-4 d-flex align-items-center gap-2">
                {isPaid ? <CheckCircleIcon width={18} height={18} /> : <CreditCardIcon width={18} height={18} />} Payment
              </dt>
              <dd className="col-sm-8 text-capitalize">{payment.status}{payment.gatewayPaymentId ? ` — ${payment.gatewayPaymentId}` : ''}</dd>
            </>
          )}
        </dl>
      </div>

      {isCustomerViewer && booking.worker && (
        <div className="card p-4 mb-3 fade-in-up">
          <h2 className="h6 d-flex align-items-center gap-2 mb-3"><UserIcon width={18} height={18} /> Your worker</h2>
          <div className="d-flex align-items-start gap-3">
            <div className="feature-icon"><UserIcon width={22} height={22} /></div>
            <div>
              <div className="fw-semibold">{booking.worker.fullName}</div>
              {booking.worker.avgRating != null && (
                <div className="text-muted small d-flex align-items-center gap-1">
                  <StarIcon width={14} height={14} style={{ fill: 'currentColor' }} />
                  {Number(booking.worker.avgRating).toFixed(1)}
                  {' '}({booking.worker.reviewCount} review{booking.worker.reviewCount === 1 ? '' : 's'})
                </div>
              )}
              {booking.worker.experienceYears != null && (
                <div className="text-muted small">
                  {booking.worker.experienceYears} year{booking.worker.experienceYears === 1 ? '' : 's'} of experience
                </div>
              )}
              {booking.worker.baseHourlyRate != null && (
                <div className="text-muted small">₹{booking.worker.baseHourlyRate}/hr base rate</div>
              )}
              {booking.worker.phone && (
                <div className="text-muted small d-flex align-items-center gap-1 mt-1">
                  <PhoneIcon width={14} height={14} /> {booking.worker.phone}
                </div>
              )}
              {booking.worker.bio && <p className="small mt-2 mb-0">{booking.worker.bio}</p>}
            </div>
          </div>
        </div>
      )}

      <div className="d-flex flex-wrap gap-2 mb-4 fade-in-up">
        {isCustomerViewer && booking.status === 'pending' && (
          <button className="btn btn-primary" disabled={busy} onClick={handleAllocate}>
            <NavigationIcon width={18} height={18} /> Find a worker
          </button>
        )}
        {isCustomerViewer && booking.status === 'confirmed' && !isPaid && (
          <button className="btn btn-primary" disabled={busy} onClick={handlePay}>
            <CreditCardIcon width={18} height={18} /> Pay now
          </button>
        )}
        {isCustomerViewer && (booking.status === 'pending' || booking.status === 'confirmed') && (
          <button className="btn btn-outline-secondary" disabled={busy} onClick={() => runAction(() => cancelBooking(booking.bookingId))}>
            <XCircleIcon width={18} height={18} /> Cancel booking
          </button>
        )}
        {isWorkerViewer && booking.status === 'confirmed' && (
          <button className="btn btn-primary" disabled={busy} onClick={() => runAction(() => startBooking(booking.bookingId))}>
            <ClockIcon width={18} height={18} /> Start job
          </button>
        )}
        {isWorkerViewer && booking.status === 'in_progress' && (
          <button className="btn btn-primary" disabled={busy} onClick={() => runAction(() => completeBooking(booking.bookingId))}>
            <CheckCircleIcon width={18} height={18} /> Mark complete
          </button>
        )}
      </div>

      {isCustomerViewer && booking.status === 'completed' && reviewSubmitted && (
        <div className="card p-4 mb-4 fade-in-up">
          <h2 className="h6 d-flex align-items-center gap-2 mb-0"><StarIcon width={18} height={18} /> Thanks for your review!</h2>
        </div>
      )}

      {isCustomerViewer && booking.status === 'completed' && !reviewSubmitted && (
        <div className="card p-4 mb-4 fade-in-up">
          <h2 className="h6 d-flex align-items-center gap-2"><StarIcon width={18} height={18} /> Leave a review</h2>
          <form onSubmit={handleReviewSubmit}>
            <div className="mb-3">
              <span className="form-label d-block">Rating</span>
              <StarPicker value={reviewRating} onChange={setReviewRating} />
            </div>
            <div className="mb-3">
              <label className="form-label" htmlFor="comment">Comment</label>
              <textarea id="comment" className="form-control" rows={2} value={reviewComment} onChange={(e) => setReviewComment(e.target.value)} />
            </div>
            <button type="submit" className="btn btn-primary" disabled={busy}>Submit review</button>
          </form>
        </div>
      )}

      <div className="card p-3 fade-in-up">
        <h2 className="h6 d-flex align-items-center gap-2"><MapPinIcon width={18} height={18} /> Location</h2>
        {geoError && <div className="alert alert-warning py-2">{geoError}</div>}
        {routeInfo && (
          <div className="alert alert-info py-2 d-flex align-items-center gap-2">
            <NavigationIcon width={18} height={18} />
            {routeInfo.distanceKm.toFixed(1)} km away — approx {Math.round(routeInfo.durationMin)} min
          </div>
        )}
        <RouteMap jobPosition={jobPosition} workerPosition={isAssignedWorker ? workerPosition : null} onRouteFound={setRouteInfo} />
        {isCustomerViewer && (
          <p className="text-muted small mt-2 mb-0">
            Live worker location isn't shown yet — that needs the worker's own registered
            location (not yet collected at signup) or in-app navigation sharing, neither of
            which is built. Only the job address is shown here for now.
          </p>
        )}
      </div>
    </div>
  )
}
