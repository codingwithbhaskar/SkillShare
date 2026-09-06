import { useEffect, useRef, useState } from 'react'

/**
 * Tiny IntersectionObserver wrapper — true once the element has scrolled
 * into the viewport, then stays true (no re-hide on scroll-away, so
 * animations don't replay awkwardly). Used to drive scroll-triggered
 * reveals/count-ups on the homepage without a new npm dependency.
 */
export default function useInView(options = { threshold: 0.2 }) {
  const ref = useRef(null)
  const [inView, setInView] = useState(false)

  useEffect(() => {
    const node = ref.current
    if (!node || inView) return undefined

    if (typeof IntersectionObserver === 'undefined') {
      // No IntersectionObserver support — fail open rather than hiding
      // content forever.
      setInView(true)
      return undefined
    }

    const observer = new IntersectionObserver(([entry]) => {
      if (entry.isIntersecting) {
        setInView(true)
        observer.disconnect()
      }
    }, options)

    observer.observe(node)
    return () => observer.disconnect()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [inView])

  return [ref, inView]
}
