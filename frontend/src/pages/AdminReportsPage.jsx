import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { fetchAdminReport } from '../api/admin.js'
import { extractErrorMessage } from '../api/client.js'
import { Spinner } from '../components/Spinner.jsx'
import { BarChartIcon } from '../components/icons.jsx'
import useDocumentTitle from '../hooks/useDocumentTitle.js'

function isoDateInput(date) {
  return date.toISOString().slice(0, 10)
}

const DEFAULT_TO = new Date()
const DEFAULT_FROM = new Date(DEFAULT_TO.getTime() - 30 * 24 * 60 * 60 * 1000)

export default function AdminReportsPage() {
  useDocumentTitle('Reports')
  const [fromInput, setFromInput] = useState(isoDateInput(DEFAULT_FROM))
  const [toInput, setToInput] = useState(isoDateInput(DEFAULT_TO))
  const [report, setReport] = useState(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const load = useCallback(async (fromStr, toStr) => {
    setLoading(true)
    setError('')
    try {
      const from = new Date(`${fromStr}T00:00:00`)
      // "to" is exclusive on the backend, so push it one day forward to
      // include every booking/payment that happened on the selected end date.
      const to = new Date(`${toStr}T00:00:00`)
      to.setDate(to.getDate() + 1)
      const data = await fetchAdminReport({ from, to })
      setReport(data)
    } catch (err) {
      setError(extractErrorMessage(err, 'Could not load this report.'))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load(fromInput, toInput)
    // Only run once on mount with the default range — subsequent loads
    // are triggered explicitly by the "Apply" button below.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  function handleApply(e) {
    e.preventDefault()
    load(fromInput, toInput)
  }

  return (
    <div className="container">
      <h1 className="h3 mb-1 fade-in-up d-flex align-items-center gap-2">
        <BarChartIcon /> Reports &amp; analytics
      </h1>
      <p className="text-muted">
        <Link to="/admin">← Back to dashboard</Link>
      </p>

      <form className="card p-3 mb-4 d-flex flex-row flex-wrap gap-2 align-items-end" onSubmit={handleApply}>
        <div>
          <label className="form-label small mb-1">From</label>
          <input type="date" className="form-control" value={fromInput} onChange={(e) => setFromInput(e.target.value)} />
        </div>
        <div>
          <label className="form-label small mb-1">To</label>
          <input type="date" className="form-control" value={toInput} onChange={(e) => setToInput(e.target.value)} />
        </div>
        <button type="submit" className="btn btn-primary" disabled={loading}>
          {loading ? 'Loading…' : 'Apply'}
        </button>
      </form>

      {error && <div className="alert alert-danger">{error}</div>}
      {!report && !error && <Spinner label="Loading report…" />}

      {report && (
        <>
          <div className="row g-3 mb-4 stagger">
            {[
              { label: 'Bookings in range', value: report.totalBookings },
              { label: 'Completed', value: report.completedBookings },
              { label: 'Cancelled', value: report.cancelledBookings },
              { label: 'Revenue collected', value: `₹${Number(report.totalRevenue).toFixed(2)}` },
            ].map(({ label, value }) => (
              <div className="col-6 col-md-3" key={label}>
                <div className="card stat-card p-3 text-center h-100">
                  <div className="text-muted small">{label}</div>
                  <div className="h4 mb-0">{value}</div>
                </div>
              </div>
            ))}
          </div>

          <div className="row g-4">
            <div className="col-md-6">
              <h2 className="h6 mb-3">Top workers (by completed bookings)</h2>
              {report.topWorkers.length === 0 && <p className="text-muted small">No completed bookings in this range.</p>}
              {report.topWorkers.length > 0 && (
                <table className="table table-sm align-middle">
                  <thead>
                    <tr>
                      <th>Worker</th>
                      <th>Completed</th>
                      <th>Avg rating</th>
                    </tr>
                  </thead>
                  <tbody>
                    {report.topWorkers.map((w) => (
                      <tr key={w.workerId}>
                        <td>{w.fullName}</td>
                        <td>{w.completedBookings}</td>
                        <td>{Number(w.avgRating) > 0 ? Number(w.avgRating).toFixed(1) : '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
            <div className="col-md-6">
              <h2 className="h6 mb-3">Top services (by completed bookings)</h2>
              {report.topServices.length === 0 && <p className="text-muted small">No completed bookings in this range.</p>}
              {report.topServices.length > 0 && (
                <table className="table table-sm align-middle">
                  <thead>
                    <tr>
                      <th>Service</th>
                      <th>Bookings</th>
                      <th>Revenue</th>
                    </tr>
                  </thead>
                  <tbody>
                    {report.topServices.map((s) => (
                      <tr key={s.serviceId}>
                        <td>{s.serviceName}</td>
                        <td>{s.bookingCount}</td>
                        <td>₹{Number(s.revenue).toFixed(2)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          </div>
        </>
      )}
    </div>
  )
}
