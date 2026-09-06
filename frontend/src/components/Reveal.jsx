import useInView from '../hooks/useInView'

/**
 * Wraps children in a div that fades/slides into place once it scrolls
 * into the viewport. Thin wrapper over useInView + the existing
 * `.fade-in-up` CSS utility class already defined in theme.css, so no
 * new animation system is introduced -- just applied on scroll instead
 * of only on mount.
 *
 * `as` lets the wrapper render as a different element (e.g. "section").
 * `delay` (ms) staggers a group of Reveal children visually.
 */
export default function Reveal({ children, as: Tag = 'div', delay = 0, className = '', ...rest }) {
  const [ref, inView] = useInView({ threshold: 0.15 })

  return (
    <Tag
      ref={ref}
      className={`reveal ${inView ? 'reveal-visible' : ''} ${className}`.trim()}
      style={inView ? { transitionDelay: `${delay}ms` } : undefined}
      {...rest}
    >
      {children}
    </Tag>
  )
}
