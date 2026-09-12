import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { fetchServices, fetchSkills } from '../api/catalog.js'
import { fetchMyWorkerProfile, updateMyWorkerProfile } from '../api/workers.js'
import { extractErrorMessage } from '../api/client.js'
import AddressMapPicker from '../components/AddressMapPicker.jsx'
import { Spinner } from '../components/Spinner.jsx'
import { CheckCircleIcon, MapPinIcon, UserIcon, WrenchIcon } from '../components/icons.jsx'
import useDocumentTitle from '../hooks/useDocumentTitle.js'

const DAY_LABELS = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday']

function timeToInputValue(t) {
  // Backend LocalTime comes back as "HH:mm:ss" - <input type="time">
  // wants "HH:mm".
  return t ? t.slice(0, 5) : ''
}

/**
 * Closes the "no self-service complete my profile" gap documented since
 * Phase 7 and flagged again in phase-8-frontend-in-progress.md: without
 * this page, a newly-registered worker has no location, no service
 * offerings, and no availability - all three are HARD filters in
 * fn_find_candidates, so they could never be allocated a single booking
 * no matter how the rest of the app looked. See
 * UpdateWorkerProfileRequest's backend javadoc for the same point.
 */
export default function WorkerProfilePage() {
  useDocumentTitle('My Worker Profile')
  const navigate = useNavigate()

  const [services, setServices] = useState([])
  const [skills, setSkills] = useState([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')

  const [bio, setBio] = useState('')
  const [experienceYears, setExperienceYears] = useState('')
  const [baseHourlyRate, setBaseHourlyRate] = useState('')

  const [addressLine, setAddressLine] = useState('')
  const [landmark, setLandmark] = useState('')
  const [city, setCity] = useState('')
  const [state, setState] = useState('')
  const [pincode, setPincode] = useState('')
  const [pin, setPin] = useState(null)

  const [skillIds, setSkillIds] = useState([])
  const [serviceRates, setServiceRates] = useState({}) // serviceId -> rate string
  const [availability, setAvailability] = useState(
    DAY_LABELS.map((_, dayOfWeek) => ({ dayOfWeek, enabled: false, startTime: '09:00', endTime: '18:00' })),
  )

  const [saveError, setSaveError] = useState('')
  const [saveSuccess, setSaveSuccess] = useState(false)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    let cancelled = false
    async function load() {
      try {
        const [svc, skl, profile] = await Promise.all([fetchServices(), fetchSkills(), fetchMyWorkerProfile()])
        if (cancelled) return
        setServices(svc)
        setSkills(skl)

        setBio(profile.bio || '')
        setExperienceYears(profile.experienceYears != null ? String(profile.experienceYears) : '')
        setBaseHourlyRate(profile.baseHourlyRate != null ? String(profile.baseHourlyRate) : '')
        setAddressLine(profile.addressLine || '')
        setLandmark('')
        setCity(profile.city || '')
        setState(profile.state || '')
        setPincode(profile.pincode || '')
        if (profile.latitude != null && profile.longitude != null) {
          setPin({ lat: Number(profile.latitude), lon: Number(profile.longitude) })
        }
        setSkillIds(profile.skillIds || [])
        if (profile.serviceRates?.length) {
          const rates = {}
          for (const r of profile.serviceRates) rates[r.serviceId] = String(r.hourlyRate)
          setServiceRates(rates)
        }
        if (profile.availability?.length) {
          setAvailability((prev) => prev.map((day) => {
            const match = profile.availability.find((a) => a.dayOfWeek === day.dayOfWeek)
            return match
              ? { ...day, enabled: true, startTime: timeToInputValue(match.startTime), endTime: timeToInputValue(match.endTime) }
              : day
          }))
        }
      } catch (err) {
        if (!cancelled) setLoadError(extractErrorMessage(err, 'Could not load your worker profile.'))
      } finally {
        if (!cancelled) setLoading(false)
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

  function updateRate(serviceId, value) {
    setServiceRates((prev) => ({ ...prev, [serviceId]: value }))
  }

  function updateDay(dayOfWeek, patch) {
    setAvailability((prev) => prev.map((d) => (d.dayOfWeek === dayOfWeek ? { ...d, ...patch } : d)))
  }

  async function handleSubmit(e) {
    e.preventDefault()
    setSaveError('')
    setSaveSuccess(false)

    const activeRates = Object.entries(serviceRates)
      .filter(([, rate]) => rate !== '' && rate != null)
      .map(([serviceId, rate]) => ({ serviceId: Number(serviceId), hourlyRate: Number(rate) }))

    const activeAvailability = availability
      .filter((d) => d.enabled)
      .map((d) => ({ dayOfWeek: d.dayOfWeek, startTime: `${d.startTime}:00`, endTime: `${d.endTime}:00` }))

    setSaving(true)
    try {
      await updateMyWorkerProfile({
        bio: bio.trim() || null,
        experienceYears: experienceYears !== '' ? Number(experienceYears) : null,
        baseHourlyRate: baseHourlyRate !== '' ? Number(baseHourlyRate) : null,
        addressLine: addressLine.trim() || null,
        landmark: landmark.trim() || null,
        city: city.trim() || null,
        state: state.trim() || null,
        pincode: pincode.trim() || null,
        latitude: pin?.lat ?? null,
        longitude: pin?.lon ?? null,
        skillIds,
        serviceRates: activeRates,
        availability: activeAvailability,
      })
      setSaveSuccess(true)
    } catch (err) {
      setSaveError(extractErrorMessage(err, 'Could not save your profile.'))
    } finally {
      setSaving(false)
    }
  }

  if (loading) {
    return (
      <div className="container">
        <Spinner label="Loading your profile…" />
      </div>
    )
  }

  return (
    <div className="container" style={{ maxWidth: 720 }}>
      <h1 className="h3 mb-4 d-flex align-items-center gap-2 fade-in-up">
        <UserIcon /> Complete your profile
      </h1>
      <p className="text-muted">
        A location, at least one service you offer (with your rate), and your weekly availability
        are all required before you can be matched to any booking — none of these were collected
        at sign-up.
      </p>

      {loadError && <div className="alert alert-danger fade-in-up">{loadError}</div>}
      {saveError && <div className="alert alert-danger fade-in-up">{saveError}</div>}
      {saveSuccess && (
        <div className="alert alert-success d-flex align-items-center gap-2 fade-in-up">
          <CheckCircleIcon width={18} height={18} /> Profile saved.
        </div>
      )}

      <form onSubmit={handleSubmit} className="card p-4 mb-3 fade-in-up">
        <h2 className="h6">About you</h2>
        <div className="mb-3">
          <label className="form-label" htmlFor="bio">Bio</label>
          <textarea id="bio" className="form-control" rows={2} value={bio} onChange={(e) => setBio(e.target.value)} />
        </div>
        <div className="row mb-3">
          <div className="col-md-6">
            <label className="form-label" htmlFor="experienceYears">Years of experience</label>
            <input
              id="experienceYears"
              type="number"
              min="0"
              className="form-control"
              value={experienceYears}
              onChange={(e) => setExperienceYears(e.target.value)}
            />
          </div>
          <div className="col-md-6">
            <label className="form-label" htmlFor="baseHourlyRate">Base hourly rate (₹, informational)</label>
            <input
              id="baseHourlyRate"
              type="number"
              min="0"
              step="0.01"
              className="form-control"
              value={baseHourlyRate}
              onChange={(e) => setBaseHourlyRate(e.target.value)}
            />
          </div>
        </div>

        <hr />
        <h2 className="h6 d-flex align-items-center gap-2"><MapPinIcon width={18} height={18} /> Your location</h2>
        <div className="mb-3">
          <label className="form-label" htmlFor="addressLine">Address</label>
          <input id="addressLine" className="form-control" value={addressLine} onChange={(e) => setAddressLine(e.target.value)} />
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
            <input id="city" className="form-control" value={city} onChange={(e) => setCity(e.target.value)} />
          </div>
          <div className="col-md-6">
            <label className="form-label" htmlFor="state">State</label>
            <input id="state" className="form-control" value={state} onChange={(e) => setState(e.target.value)} />
          </div>
        </div>
        <div className="mb-3">
          <span className="form-label d-block">Pin your base location</span>
          <AddressMapPicker value={pin} onChange={setPin} />
        </div>

        <hr />
        <h2 className="h6 d-flex align-items-center gap-2"><WrenchIcon width={18} height={18} /> Services you offer</h2>
        <div className="mb-3">
          {services.map((s) => (
            <div key={s.serviceId} className="row align-items-center mb-2">
              <div className="col-6">{s.serviceName}</div>
              <div className="col-6">
                <div className="input-group input-group-sm">
                  <span className="input-group-text">₹/hr</span>
                  <input
                    type="number"
                    min="0"
                    step="0.01"
                    className="form-control"
                    placeholder="Not offered"
                    value={serviceRates[s.serviceId] ?? ''}
                    onChange={(e) => updateRate(s.serviceId, e.target.value)}
                  />
                </div>
              </div>
            </div>
          ))}
          <p className="form-text mb-0">Leave a rate blank for any service you don't offer.</p>
        </div>

        <hr />
        <h2 className="h6">Skills</h2>
        <div className="mb-3 d-flex flex-wrap gap-2">
          {skills.map((s) => {
            const active = skillIds.includes(s.skillId)
            return (
              <button
                key={s.skillId}
                type="button"
                onClick={() => toggleSkill(s.skillId)}
                className={`badge border-0 ${active ? 'text-bg-primary' : 'text-bg-light border'}`}
                style={{ cursor: 'pointer', fontSize: '0.9rem', padding: '0.55rem 0.9rem' }}
                aria-pressed={active}
              >
                {s.skillName}
              </button>
            )
          })}
        </div>

        <hr />
        <h2 className="h6">Weekly availability</h2>
        <div className="mb-3">
          {availability.map((day) => (
            <div key={day.dayOfWeek} className="row align-items-center mb-2">
              <div className="col-4 col-md-3">
                <div className="form-check">
                  <input
                    className="form-check-input"
                    type="checkbox"
                    id={`day-${day.dayOfWeek}`}
                    checked={day.enabled}
                    onChange={(e) => updateDay(day.dayOfWeek, { enabled: e.target.checked })}
                  />
                  <label className="form-check-label" htmlFor={`day-${day.dayOfWeek}`}>
                    {DAY_LABELS[day.dayOfWeek]}
                  </label>
                </div>
              </div>
              {day.enabled && (
                <div className="col-8 col-md-6 d-flex gap-2 align-items-center">
                  <input
                    type="time"
                    className="form-control form-control-sm"
                    value={day.startTime}
                    onChange={(e) => updateDay(day.dayOfWeek, { startTime: e.target.value })}
                  />
                  <span className="text-muted">to</span>
                  <input
                    type="time"
                    className="form-control form-control-sm"
                    value={day.endTime}
                    onChange={(e) => updateDay(day.dayOfWeek, { endTime: e.target.value })}
                  />
                </div>
              )}
            </div>
          ))}
        </div>

        <div className="d-flex gap-2">
          <button type="submit" className="btn btn-primary" disabled={saving}>
            {saving ? 'Saving…' : <><CheckCircleIcon width={18} height={18} /> Save profile</>}
          </button>
          <button type="button" className="btn btn-outline-secondary" onClick={() => navigate('/worker')}>
            Back to dashboard
          </button>
        </div>
      </form>
    </div>
  )
}
