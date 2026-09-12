import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'
import { fetchServices, fetchSkills } from '../api/catalog.js'
import { createBooking } from '../api/bookings.js'
import { extractErrorMessage } from '../api/client.js'
import AddressMapPicker from '../components/AddressMapPicker.jsx'
import { Spinner } from '../components/Spinner.jsx'
import {
  CalendarIcon, ClockIcon, MapPinIcon, PlusCircleIcon, WrenchIcon,
} from '../components/icons.jsx'
import useDocumentTitle from '../hooks/useDocumentTitle.js'

// datetime-local gives "YYYY-MM-DDTHH:mm" with no timezone. This app's
// seed data / live-test scripts all assume IST (+05:30) — see
// phase6-live-test.ps1 — so bookings created from the browser match the
// same convention rather than drifting to the server's own zone.
function toIsoWithIst(datetimeLocalValue) {
  if (!datetimeLocalValue) return null
  return `${datetimeLocalValue}:00+05:30`
}

export default function NewBookingPage() {
  useDocumentTitle('Book a Service')
  const { user } = useAuth()
  const navigate = useNavigate()

  const [services, setServices] = useState([])
  const [skills, setSkills] = useState([])
  const [loadingCatalog, setLoadingCatalog] = useState(true)
  const [catalogError, setCatalogError] = useState('')

  const [serviceId, setServiceId] = useState('')
  const [skillIds, setSkillIds] = useState([])
  const [urgency, setUrgency] = useState('normal')
  const [scheduledStart, setScheduledStart] = useState('')
  const [scheduledEnd, setScheduledEnd] = useState('')
  const [notes, setNotes] = useState('')
  const [addressLine, setAddressLine] = useState('')
  const [landmark, setLandmark] = useState('')
  const [city, setCity] = useState('')
  const [state, setState] = useState('')
  const [pincode, setPincode] = useState('')
  const [pin, setPin] = useState(null)

  const [submitError, setSubmitError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    let cancelled = false
    async function load() {
      try {
        const [svc, skl] = await Promise.all([fetchServices(), fetchSkills()])
        if (cancelled) return
        setServices(svc)
        setSkills(skl)
      } catch (err) {
        if (!cancelled) setCatalogError(extractErrorMessage(err, 'Could not load the service/skill catalogue.'))
      } finally {
        if (!cancelled) setLoadingCatalog(false)
      }
    }
    load()
    return () => {
      cancelled = true
    }
  }, [])

  function toggleSkill(id) {
    setSkillIds((prev) => (prev.includes(id) ? prev.filter((s) => s !== id) : [...prev, id]))
  }

  const selectedService = services.find((s) => String(s.serviceId) === String(serviceId))
  // Only offer skills relevant to the picked service's category (e.g.
  // choosing "Plumbing" shouldn't list "Ceiling Fan Installation") - a
  // skill with no category set (uncategorized, e.g. one an admin added
  // without picking one) still shows for every service rather than
  // silently disappearing. Nothing shows until a service is picked -
  // "skills needed" is meaningless without a service to attach them to.
  const relevantSkills = selectedService
    ? skills.filter((s) => !s.category || s.category === selectedService.category)
    : []

  function handleServiceChange(newServiceId) {
    setServiceId(newServiceId)
    // Drop any already-picked skill that's no longer relevant to the
    // newly-chosen service, rather than silently submitting a
    // skill/service combination the customer never actually intended.
    const newService = services.find((s) => String(s.serviceId) === String(newServiceId))
    setSkillIds((prev) =>
      prev.filter((id) => {
        const skill = skills.find((s) => s.skillId === id)
        return skill && (!skill.category || !newService || skill.category === newService.category)
      }),
    )
  }

  async function handleSubmit(e) {
    e.preventDefault()
    setSubmitError('')

    if (!pin) {
      setSubmitError('Set the job location on the map before submitting.')
      return
    }
    if (skillIds.length === 0) {
      setSubmitError('Select at least one skill.')
      return
    }

    setSubmitting(true)
    try {
      const booking = await createBooking({
        customerId: user.userId,
        serviceId: Number(serviceId),
        skillIds,
        urgency,
        scheduledStart: toIsoWithIst(scheduledStart),
        scheduledEnd: toIsoWithIst(scheduledEnd),
        notes,
        addressLine,
        landmark,
        city,
        state,
        pincode,
        latitude: pin.lat,
        longitude: pin.lon,
      })
      navigate(`/bookings/${booking.bookingId}`, { replace: true })
    } catch (err) {
      setSubmitError(extractErrorMessage(err, 'Could not create the booking.'))
    } finally {
      setSubmitting(false)
    }
  }

  if (loadingCatalog) {
    return (
      <div className="container">
        <Spinner label="Loading services…" />
      </div>
    )
  }

  return (
    <div className="container" style={{ maxWidth: 720 }}>
      <h1 className="h3 mb-4 d-flex align-items-center gap-2 fade-in-up">
        <PlusCircleIcon /> Book a service
      </h1>
      {catalogError && <div className="alert alert-danger fade-in-up">{catalogError}</div>}
      {submitError && <div className="alert alert-danger fade-in-up">{submitError}</div>}

      <form onSubmit={handleSubmit} className="card p-4 fade-in-up">
        <div className="mb-3">
          <label className="form-label d-flex align-items-center gap-2" htmlFor="service">
            <WrenchIcon width={18} height={18} /> Service
          </label>
          <select
            id="service"
            className="form-select"
            value={serviceId}
            onChange={(e) => handleServiceChange(e.target.value)}
            required
          >
            <option value="" disabled>Choose a service…</option>
            {services.map((s) => (
              <option key={s.serviceId} value={s.serviceId}>{s.serviceName}</option>
            ))}
          </select>
        </div>

        <div className="mb-3">
          <span className="form-label d-block">Skills needed</span>
          {!selectedService && (
            <p className="text-muted small mb-2">Choose a service above to see the skills relevant to it.</p>
          )}
          <div className="d-flex flex-wrap gap-2">
            {relevantSkills.map((s) => {
              const active = skillIds.includes(s.skillId)
              return (
                <button
                  key={s.skillId}
                  type="button"
                  onClick={() => toggleSkill(s.skillId)}
                  className={`badge border-0 ${active ? 'text-bg-primary' : 'text-bg-light border'}`}
                  style={{
                    cursor: 'pointer',
                    fontSize: '0.9rem',
                    padding: '0.55rem 0.9rem',
                    transition: 'transform 180ms ease, filter 180ms ease',
                  }}
                  aria-pressed={active}
                >
                  {s.skillName}
                </button>
              )
            })}
          </div>
        </div>

        <div className="row mb-3">
          <div className="col-md-6">
            <label className="form-label" htmlFor="urgency">Urgency</label>
            <select id="urgency" className="form-select" value={urgency} onChange={(e) => setUrgency(e.target.value)}>
              <option value="normal">Normal</option>
              <option value="urgent">Urgent</option>
            </select>
          </div>
        </div>

        <div className="row mb-3">
          <div className="col-md-6">
            <label className="form-label d-flex align-items-center gap-2" htmlFor="start">
              <CalendarIcon width={18} height={18} /> Start time
            </label>
            <input
              id="start"
              type="datetime-local"
              className="form-control"
              value={scheduledStart}
              onChange={(e) => setScheduledStart(e.target.value)}
              required
            />
          </div>
          <div className="col-md-6">
            <label className="form-label d-flex align-items-center gap-2" htmlFor="end">
              <ClockIcon width={18} height={18} /> End time
            </label>
            <input
              id="end"
              type="datetime-local"
              className="form-control"
              value={scheduledEnd}
              onChange={(e) => setScheduledEnd(e.target.value)}
              required
            />
          </div>
        </div>

        <div className="mb-3">
          <label className="form-label" htmlFor="notes">Notes</label>
          <textarea id="notes" className="form-control" rows={2} value={notes} onChange={(e) => setNotes(e.target.value)} />
        </div>

        <hr />
        <h2 className="h6 d-flex align-items-center gap-2"><MapPinIcon width={18} height={18} /> Job address</h2>

        <div className="mb-3">
          <label className="form-label" htmlFor="addressLine">Address</label>
          <input id="addressLine" className="form-control" value={addressLine} onChange={(e) => setAddressLine(e.target.value)} required />
        </div>
        <div className="row mb-3">
          <div className="col-md-6">
            <label className="form-label" htmlFor="landmark">Landmark</label>
            <input id="landmark" className="form-control" value={landmark} onChange={(e) => setLandmark(e.target.value)} />
          </div>
          <div className="col-md-3">
            <label className="form-label" htmlFor="pincode">Pincode</label>
            <input id="pincode" className="form-control" value={pincode} onChange={(e) => setPincode(e.target.value)} />
          </div>
        </div>
        <div className="row mb-3">
          <div className="col-md-6">
            <label className="form-label" htmlFor="city">City</label>
            <input id="city" className="form-control" value={city} onChange={(e) => setCity(e.target.value)} required />
          </div>
          <div className="col-md-6">
            <label className="form-label" htmlFor="state">State</label>
            <input id="state" className="form-control" value={state} onChange={(e) => setState(e.target.value)} required />
          </div>
        </div>

        <div className="mb-3">
          <span className="form-label d-block">Pin the exact location</span>
          <AddressMapPicker value={pin} onChange={setPin} />
        </div>

        <button type="submit" className="btn btn-primary w-100" disabled={submitting}>
          {submitting ? 'Booking…' : <><PlusCircleIcon width={18} height={18} /> Book this service</>}
        </button>
      </form>
    </div>
  )
}
