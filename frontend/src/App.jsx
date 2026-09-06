import { Route, Routes } from 'react-router-dom'
import Navbar from './components/Navbar.jsx'
import Footer from './components/Footer.jsx'
import ProtectedRoute from './components/ProtectedRoute.jsx'
import HomePage from './pages/HomePage.jsx'
import LoginPage from './pages/LoginPage.jsx'
import RegisterPage from './pages/RegisterPage.jsx'
import BookingsListPage from './pages/BookingsListPage.jsx'
import NewBookingPage from './pages/NewBookingPage.jsx'
import BookingDetailPage from './pages/BookingDetailPage.jsx'
import WorkerDashboardPage from './pages/WorkerDashboardPage.jsx'
import WorkerProfilePage from './pages/WorkerProfilePage.jsx'
import AdminPage from './pages/AdminPage.jsx'
import AdminUsersPage from './pages/AdminUsersPage.jsx'
import AdminCatalogPage from './pages/AdminCatalogPage.jsx'
import AdminReportsPage from './pages/AdminReportsPage.jsx'
import AccountPage from './pages/AccountPage.jsx'
import NotFoundPage from './pages/NotFoundPage.jsx'

export default function App() {
  return (
    <div className="d-flex flex-column min-vh-100">
      <Navbar />
      <main className="flex-grow-1">
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />

        <Route element={<ProtectedRoute allowedRoles={['customer', 'admin']} />}>
          <Route path="/bookings" element={<BookingsListPage />} />
          <Route path="/bookings/new" element={<NewBookingPage />} />
        </Route>

        {/* Booking detail is shared by the customer who booked it and the
            worker it's assigned to — any authenticated user, same as the
            backend's current "no ownership check yet" scope
            (dev-status-and-next-steps.md's Phase 7 follow-up gap). */}
        <Route element={<ProtectedRoute />}>
          <Route path="/bookings/:bookingId" element={<BookingDetailPage />} />
          <Route path="/account" element={<AccountPage />} />
        </Route>

        <Route element={<ProtectedRoute allowedRoles={['worker', 'admin']} />}>
          <Route path="/worker" element={<WorkerDashboardPage />} />
          <Route path="/worker/profile" element={<WorkerProfilePage />} />
        </Route>

        <Route element={<ProtectedRoute allowedRoles={['admin']} />}>
          <Route path="/admin" element={<AdminPage />} />
          <Route path="/admin/users" element={<AdminUsersPage />} />
          <Route path="/admin/catalog" element={<AdminCatalogPage />} />
          <Route path="/admin/reports" element={<AdminReportsPage />} />
        </Route>

        <Route path="*" element={<NotFoundPage />} />
      </Routes>
      </main>
      <Footer />
    </div>
  )
}
