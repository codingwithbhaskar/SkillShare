export function Spinner({ label = 'Loading…' }) {
  return (
    <div className="d-flex align-items-center gap-2 text-muted py-4">
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"
           strokeLinecap="round" className="spin">
        <path d="M12 2a10 10 0 1 0 10 10" />
      </svg>
      <span>{label}</span>
    </div>
  )
}

export function SkeletonCard() {
  return (
    <div className="card p-3">
      <div className="skeleton mb-2" style={{ height: 16, width: '40%' }} />
      <div className="skeleton mb-2" style={{ height: 12, width: '70%' }} />
      <div className="skeleton" style={{ height: 12, width: '55%' }} />
    </div>
  )
}
