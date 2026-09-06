import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'
import { LayoutDashboardIcon, ListIcon, LogOutIcon, ShieldCheckIcon, UserIcon, WrenchIcon } from './icons.jsx'

export default function Navbar() {
  const { isAuthenticated, user, logout } = useAuth()
  const navigate = useNavigate()

  function handleLogout() {
    logout()
    navigate('/login')
  }

  return (
    <nav className="navbar navbar-expand-lg navbar-dark navbar-skillshare mb-4">
      <div className="container">
        <Link className="navbar-brand d-flex align-items-center gap-2 fw-bold" to="/">
          <WrenchIcon width={22} height={22} />
          SkillShare
        </Link>
        <div className="d-flex ms-auto align-items-center gap-2 flex-wrap">
          {isAuthenticated ? (
            <>
              {user.role === 'customer' && (
                <Link className="nav-link text-light" to="/bookings">
                  <ListIcon width={16} height={16} /> My Bookings
                </Link>
              )}
              {user.role === 'worker' && (
                <Link className="nav-link text-light" to="/worker">
                  <LayoutDashboardIcon width={16} height={16} /> Worker Dashboard
                </Link>
              )}
              {user.role === 'worker' && (
                <Link className="nav-link text-light" to="/worker/profile">
                  <UserIcon width={16} height={16} /> My Profile
                </Link>
              )}
              {user.role === 'admin' && (
                <Link className="nav-link text-light" to="/admin">
                  <ShieldCheckIcon width={16} height={16} /> Admin
                </Link>
              )}
              <Link className="text-light small d-inline-flex align-items-center gap-1 ms-2 text-decoration-none" to="/account">
                <UserIcon width={15} height={15} />
                {user.fullName}{' '}
                <span className="badge bg-white text-primary text-uppercase">{user.role}</span>
              </Link>
              <button type="button" className="btn btn-outline-light btn-sm" onClick={handleLogout}>
                <LogOutIcon width={15} height={15} /> Log out
              </button>
            </>
          ) : (
            <>
              <Link className="nav-link text-light" to="/login">Log in</Link>
              <Link className="btn btn-primary btn-sm" to="/register">Sign up</Link>
            </>
          )}
        </div>
      </div>
    </nav>
  )
}
