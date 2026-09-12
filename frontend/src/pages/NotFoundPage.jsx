import { Link } from 'react-router-dom'
import { ArrowRightIcon, SearchIcon } from '../components/icons.jsx'
import useDocumentTitle from '../hooks/useDocumentTitle.js'

export default function NotFoundPage() {
  useDocumentTitle('Page not found')

  return (
    <div className="container text-center py-5 fade-in-up">
      <div className="feature-icon mx-auto mb-4">
        <SearchIcon width={26} height={26} />
      </div>
      <h1 className="h3 mb-2">Page not found</h1>
      <p className="text-muted mb-4">
        The page you're looking for doesn't exist, or may have moved.
      </p>
      <Link to="/" className="btn btn-primary">
        Back to home <ArrowRightIcon width={16} height={16} />
      </Link>
    </div>
  )
}
