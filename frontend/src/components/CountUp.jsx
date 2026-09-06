import { useEffect, useRef, useState } from 'react'
import useInView from '../hooks/useInView'

/**
 * Animates a number counting up from 0 to `value` once it scrolls into
 * view. Pure requestAnimationFrame, no new dependency. `suffix`/`prefix`
 * let callers add "%", "+", "km", etc. around the number.
 */
export default function CountUp({ value, duration = 1200, prefix = '', suffix = '', decimals = 0 }) {
  const [ref, inView] = useInView({ threshold: 0.4 })
  const [display, setDisplay] = useState(0)
  const started = useRef(false)

  useEffect(() => {
    if (!inView || started.current) return
    started.current = true

    const start = performance.now()
    const from = 0
    const to = value

    let frame
    const tick = (now) => {
      const elapsed = now - start
      const progress = Math.min(1, elapsed / duration)
      // ease-out cubic
      const eased = 1 - (1 - progress) ** 3
      setDisplay(from + (to - from) * eased)
      if (progress < 1) {
        frame = requestAnimationFrame(tick)
      } else {
        setDisplay(to)
      }
    }
    frame = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(frame)
  }, [inView, value, duration])

  return (
    <span ref={ref}>
      {prefix}
      {display.toFixed(decimals)}
      {suffix}
    </span>
  )
}
