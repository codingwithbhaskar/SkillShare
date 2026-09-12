import { Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'
import { MailIcon, WrenchIcon } from './icons.jsx'

export default function Footer() {
  const { isAuthenticated, user } = useAuth()
  const year = new Date().getFullYear()

  return (
    <footer className="footer-skillshare mt-auto pt-5 pb-4">
      <div className="container">
        <div className="row g-4">
          <div className="col-12 col-md-4">
            <Link className="navbar-brand d-flex align-items-center gap-2 fw-bold text-white text-decoration-none" to="/">
              <WrenchIcon width={22} height={22} />
              SkillShare
            </Link>
            <p className="footer-tagline mt-2 mb-0">
              An on-demand worker marketplace with database-driven matching --
              candidates are ranked on distance, rating, skill fit, availability,
              price and workload balance, all computed in the database layer.
            </p>
          </div>

          <div className="col-6 col-md-2">
            <h6 className="footer-heading">Explore</h6>
            <ul className="list-unstyled footer-links">
              <li><Link to="/">Home</Link></li>
              {!isAuthenticated && (
                <>
                  <li><Link to="/login">Log in</Link></li>
                  <li><Link to="/register">Sign up</Link></li>
                </>
              )}
              {isAuthenticated && user?.role === 'customer' && (
                <li><Link to="/bookings">My Bookings</Link></li>
              )}
              {isAuthenticated && user?.role === 'worker' && (
                <>
                  <li><Link to="/worker">Worker Dashboard</Link></li>
                  <li><Link to="/worker/profile">My Profile</Link></li>
                </>
              )}
              {isAuthenticated && user?.role === 'admin' && (
                <li><Link to="/admin">Admin Panel</Link></li>
              )}
            </ul>
          </div>

          <div className="col-6 col-md-3">
            <h6 className="footer-heading">How matching works</h6>
            <ul className="list-unstyled footer-links">
              <li>Spatial search within a 30km radius</li>
              <li>Skill &amp; availability filtering</li>
              <li>Rating, price &amp; workload scoring</li>
              <li>Real-time slot locking, no double-booking</li>
            </ul>
          </div>

          <div className="col-12 col-md-3">
            <h6 className="footer-heading">Contact</h6>
            <ul className="list-unstyled footer-links">
              <li className="d-flex align-items-center gap-2">
                <MailIcon width={15} height={15} /> support@skillshare.local
              </li>
            </ul>
          </div>
        </div>

        <hr className="footer-divider" />

        <div className="d-flex flex-column flex-md-row justify-content-between align-items-center gap-2">
          <small className="footer-copyright mb-0">
            &copy; {year} SkillShare. All rights reserved.
          </small>
        </div>
      </div>
    </footer>
  )
}
