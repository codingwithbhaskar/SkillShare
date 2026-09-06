import { Link } from 'react-router-dom'

export default function NotFoundPage() {
  return (
    <div className="container text-center">
      <h1 className="h3">Page not found</h1>
      <p><Link to="/">Go home</Link></p>
    </div>
  )
}
