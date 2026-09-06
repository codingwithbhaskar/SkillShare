import { useEffect, useRef, useState } from 'react'

// A handful of real-world "trades" photos so the homepage shows the kind
// of work SkillShare actually books — sourced from Unsplash (free to use
// under the Unsplash License, hotlinked from their CDN rather than
// bundled, so no binary assets ship with the app).
const SLIDES = [
  {
    src: 'https://images.unsplash.com/photo-1758101755915-462eddc23f57?auto=format&fit=crop&w=1600&q=70',
    label: 'Electrical work',
    text: 'Wiring, fixtures and panel repairs by verified electricians.',
  },
  {
    src: 'https://images.unsplash.com/photo-1749532125405-70950966b0e5?auto=format&fit=crop&w=1600&q=70',
    label: 'Plumbing',
    text: 'Leaks, fittings and installations, booked in minutes.',
  },
  {
    src: 'https://images.unsplash.com/photo-1758272421751-963195322eaa?auto=format&fit=crop&w=1600&q=70',
    label: 'Home cleaning',
    text: 'Deep cleans and regular upkeep, matched to your schedule.',
  },
  {
    src: 'https://images.unsplash.com/photo-1675191863404-e1db618d6655?auto=format&fit=crop&w=1600&q=70',
    label: 'Painting',
    text: 'Interior and exterior painting from rated local workers.',
  },
  {
    src: 'https://images.unsplash.com/photo-1779031242515-205111711b23?auto=format&fit=crop&w=1600&q=70',
    label: 'Carpentry',
    text: 'Custom woodwork and repairs, tracked from booking to job done.',
  },
]

const INTERVAL_MS = 4200

export default function SkillsSlider() {
  const [index, setIndex] = useState(0)
  const [paused, setPaused] = useState(false)
  const timerRef = useRef(null)

  useEffect(() => {
    if (paused) return undefined
    timerRef.current = setInterval(() => {
      setIndex((i) => (i + 1) % SLIDES.length)
    }, INTERVAL_MS)
    return () => clearInterval(timerRef.current)
  }, [paused])

  return (
    <div
      className="skills-slider mb-4"
      onMouseEnter={() => setPaused(true)}
      onMouseLeave={() => setPaused(false)}
    >
      {SLIDES.map((slide, i) => (
        <div
          key={slide.src}
          className={`skills-slide ${i === index ? 'is-active' : ''}`}
          aria-hidden={i !== index}
        >
          <img src={slide.src} alt={slide.label} loading={i === 0 ? 'eager' : 'lazy'} />
          <div className="skills-slide-caption">
            <span className="skills-slide-label">{slide.label}</span>
            <span className="skills-slide-text">{slide.text}</span>
          </div>
        </div>
      ))}

      <div className="skills-slider-dots">
        {SLIDES.map((slide, i) => (
          <button
            key={slide.src}
            type="button"
            className={`skills-slider-dot ${i === index ? 'is-active' : ''}`}
            aria-label={`Show ${slide.label}`}
            onClick={() => setIndex(i)}
          />
        ))}
      </div>
    </div>
  )
}
