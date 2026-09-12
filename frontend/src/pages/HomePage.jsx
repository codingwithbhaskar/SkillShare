import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'
import { fetchServices } from '../api/catalog.js'
import Reveal from '../components/Reveal.jsx'
import SkillsSlider from '../components/SkillsSlider.jsx'
import CountUp from '../components/CountUp.jsx'
import {
  ArrowRightIcon, CalendarIcon, CheckCircleIcon, LayoutDashboardIcon, ListIcon,
  MapPinIcon, SearchIcon, ShieldCheckIcon, StarIcon, WrenchIcon,
} from '../components/icons.jsx'

const FEATURES = [
  { icon: ShieldCheckIcon, title: 'Verified workers', text: 'Every worker on SkillShare is screened and rated by real customers.' },
  { icon: CalendarIcon, title: 'Book in minutes', text: 'Pick a service, a time, and a location — we handle the matching.' },
  { icon: MapPinIcon, title: 'Track the job', text: 'See exactly where your worker is coming from, live on a map.' },
]

const STATS = [
  { value: 100, suffix: '+', label: 'Services offered, across every trade', decimals: 0 },
  { value: 30, suffix: 'km', label: 'Live search radius near you', decimals: 0 },
  { value: 6, suffix: '', label: 'Factors matched — distance, rating, price & more', decimals: 0 },
  { value: 100, suffix: '%', label: 'Slots protected from double-booking', decimals: 0 },
]

const STEPS = [
  { icon: SearchIcon, title: 'Choose a service', text: 'Tell us what you need and where — a haircut, a repair, a cleanup.' },
  { icon: CalendarIcon, title: 'Pick a time', text: 'Select an open slot. Availability is checked live, so nothing double-books.' },
  { icon: WrenchIcon, title: 'Get matched', text: 'Our engine ranks nearby workers on distance, rating, skill fit, price and workload.' },
  { icon: CheckCircleIcon, title: 'Job done', text: 'Track the booking, pay securely, and leave a rating when it is complete.' },
]

const FALLBACK_SERVICES = [
  { serviceId: 'fallback-1', serviceName: 'Electrical repair', category: 'Electrical', description: 'Wiring, fixtures, and fault diagnosis.' },
  { serviceId: 'fallback-2', serviceName: 'Plumbing', category: 'Plumbing', description: 'Leaks, fittings, and installations.' },
  { serviceId: 'fallback-3', serviceName: 'Home cleaning', category: 'Cleaning', description: 'Deep cleans and regular upkeep.' },
]

export default function HomePage() {
  const { user, isAuthenticated } = useAuth()
  const [services, setServices] = useState([])
  const [servicesLoading, setServicesLoading] = useState(false)

  useEffect(() => {
    if (!isAuthenticated) return undefined
    let cancelled = false
    setServicesLoading(true)
    fetchServices()
      .then((data) => {
        if (!cancelled) setServices(Array.isArray(data) ? data.slice(0, 6) : [])
      })
      .catch(() => {
        // Anonymous-friendly homepage: if the catalogue call fails for any
        // reason, just fall back to the static illustrative list below
        // rather than showing an error on a marketing page.
      })
      .finally(() => {
        if (!cancelled) setServicesLoading(false)
      })
    return () => { cancelled = true }
  }, [isAuthenticated])

  const displayedServices = isAuthenticated && services.length > 0 ? services : FALLBACK_SERVICES

  return (
    <div className="container">
      <div className="hero-panel fade-in-up mb-4">
        <div className="row align-items-center position-relative">
          <div className="col-lg-8">
            <h1 className="display-6 fw-bold mb-3">
              {user ? `Welcome back, ${user.fullName}` : 'Skilled help, on your schedule'}
            </h1>
            <p className="lead mb-4">
              {user
                ? `You're signed in as a ${user.role}. Jump straight to what you need below.`
                : 'Book trusted electricians, plumbers, cleaners and more — matched to you by an intelligent allocation engine, not a random list.'}
            </p>
            <div className="d-flex flex-wrap gap-2">
              {user?.role === 'customer' && (
                <>
                  <Link to="/bookings/new" className="btn btn-primary btn-lg">
                    Book a service <ArrowRightIcon width={18} height={18} />
                  </Link>
                  <Link to="/bookings" className="btn btn-outline-light btn-lg">
                    <ListIcon width={18} height={18} /> My bookings
                  </Link>
                </>
              )}
              {user?.role === 'worker' && (
                <Link to="/worker" className="btn btn-primary btn-lg">
                  <LayoutDashboardIcon width={18} height={18} /> Go to your dashboard
                </Link>
              )}
              {!user && (
                <>
                  <Link to="/register" className="btn btn-primary btn-lg">
                    Get started <ArrowRightIcon width={18} height={18} />
                  </Link>
                  <Link to="/login" className="btn btn-outline-light btn-lg">Log in</Link>
                </>
              )}
            </div>
          </div>
        </div>
      </div>

      <SkillsSlider />

      <Reveal as="div" className="stat-strip mb-5">
        <div className="row g-3">
          {STATS.map((stat) => (
            <div className="col-6 col-md-3" key={stat.label}>
              <div className="stat-card">
                <div className="stat-value">
                  <CountUp value={stat.value} suffix={stat.suffix} decimals={stat.decimals} />
                </div>
                <div className="stat-label">{stat.label}</div>
              </div>
            </div>
          ))}
        </div>
      </Reveal>

      <Reveal as="section" className="mb-5">
        <h2 className="h4 text-center mb-4">How it works</h2>
        <div className="row g-4">
          {STEPS.map(({ icon: Icon, title, text }, i) => (
            <div className="col-6 col-md-3" key={title}>
              <div className="step-card">
                {i < STEPS.length - 1 && <div className="step-connector" />}
                <div className="step-number mx-auto">{i + 1}</div>
                <Icon width={20} height={20} className="text-primary mb-2" />
                <h3 className="h6">{title}</h3>
                <p className="text-muted small mb-0">{text}</p>
              </div>
            </div>
          ))}
        </div>
      </Reveal>

      <Reveal as="div" className="row g-3 stagger mb-5">
        {FEATURES.map(({ icon: Icon, title, text }) => (
          <div className="col-md-4" key={title}>
            <div className="card feature-card p-4 h-100">
              <div className="feature-icon mx-auto">
                <Icon width={26} height={26} />
              </div>
              <h3 className="h6">{title}</h3>
              <p className="text-muted small mb-0">{text}</p>
            </div>
          </div>
        ))}
      </Reveal>

      <Reveal as="section" className="mb-5">
        <div className="d-flex align-items-center justify-content-between mb-3 flex-wrap gap-2">
          <h2 className="h4 mb-0">Popular services</h2>
          {!isAuthenticated && (
            <Link to="/register" className="small">See the full catalogue &rarr;</Link>
          )}
        </div>
        {servicesLoading ? (
          <div className="row g-3">
            {[1, 2, 3].map((i) => (
              <div className="col-md-4" key={i}>
                <div className="skeleton" style={{ height: '110px', borderRadius: '1rem' }} />
              </div>
            ))}
          </div>
        ) : (
          <div className="row g-3 stagger">
            {displayedServices.map((s) => (
              <div className="col-md-4" key={s.serviceId}>
                <div className="card p-3 h-100">
                  <div className="d-flex align-items-center gap-2 mb-1">
                    <StarIcon width={16} height={16} className="text-warning" />
                    <span className="fw-semibold">{s.serviceName}</span>
                  </div>
                  {s.category && <div className="small text-muted mb-1">{s.category}</div>}
                  {s.description && <p className="small mb-0">{s.description}</p>}
                </div>
              </div>
            ))}
          </div>
        )}
        {!isAuthenticated && (
          <p className="small text-muted mt-2 mb-0">
            Showing a sample — sign in to see the live, full-priced catalogue.
          </p>
        )}
      </Reveal>
    </div>
  )
}
