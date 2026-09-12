import { useState } from 'react'
import { MapContainer, Marker, TileLayer, useMapEvents } from 'react-leaflet'
import '../lib/leafletIconFix.js'
import { MapPinIcon, NavigationIcon } from './icons.jsx'

// Default center: Nashik, Maharashtra (matches this project's seed data /
// live-test scripts, so a fresh booking form starts somewhere sensible).
const DEFAULT_CENTER = [19.9975, 73.7898]

// LocationIQ (built on the same Nominatim data, so the response shape
// below - lat/lon as strings - didn't need to change) replaces calling
// nominatim.openstreetmap.org directly from the browser: Nominatim's own
// usage policy caps this at 1 request/second and expects self-hosting or
// a paid provider for anything beyond light, personal use - a live
// public site doing its own address search against it risks getting
// throttled. With VITE_LOCATIONIQ_API_KEY unset, this falls back to the
// direct Nominatim call (same graceful-degradation stance as every other
// optional integration in this project - search still works locally
// during dev, just without the dedicated key).
const LOCATIONIQ_API_KEY = import.meta.env.VITE_LOCATIONIQ_API_KEY

function ClickToPlacePin({ onPick }) {
  useMapEvents({
    click(e) {
      onPick({ lat: e.latlng.lat, lon: e.latlng.lng })
    },
  })
  return null
}

/**
 * Job-address picker for the booking-creation form.
 * location-map-feature-design.md's spec: geocode a typed address via
 * Nominatim, with click-to-place-a-pin as the fallback when geocoding
 * fails or misses. Both paths converge on the same onChange({lat, lon}).
 */
export default function AddressMapPicker({ value, onChange }) {
  const [searchText, setSearchText] = useState('')
  const [searching, setSearching] = useState(false)
  const [searchError, setSearchError] = useState('')

  const position = value ? [value.lat, value.lon] : null

  async function handleGeocode(e) {
    e.preventDefault()
    if (!searchText.trim()) return
    setSearching(true)
    setSearchError('')
    try {
      const url = LOCATIONIQ_API_KEY
        ? `https://us1.locationiq.com/v1/search?key=${LOCATIONIQ_API_KEY}&format=json&limit=1&q=${encodeURIComponent(searchText)}`
        : `https://nominatim.openstreetmap.org/search?format=json&limit=1&q=${encodeURIComponent(searchText)}`
      const res = await fetch(url)
      if (!res.ok) throw new Error('Geocoding service unavailable')
      const results = await res.json()
      if (!results.length) {
        setSearchError('No match found for that address — click the map to drop a pin manually instead.')
        return
      }
      onChange({ lat: parseFloat(results[0].lat), lon: parseFloat(results[0].lon) })
    } catch {
      setSearchError('Could not reach the geocoding service — click the map to drop a pin manually instead.')
    } finally {
      setSearching(false)
    }
  }

  return (
    <div>
      <div className="input-group mb-2">
        <input
          type="text"
          className="form-control"
          placeholder="Search an address (e.g. College Road, Nashik)"
          value={searchText}
          onChange={(e) => setSearchText(e.target.value)}
        />
        <button type="button" className="btn btn-secondary" onClick={handleGeocode} disabled={searching}>
          {searching ? <span className="spin d-inline-block"><NavigationIcon width={16} height={16} /></span> : <NavigationIcon width={16} height={16} />}
          {' '}{searching ? 'Searching…' : 'Find on map'}
        </button>
      </div>
      {searchError && <div className="alert alert-warning py-2">{searchError}</div>}
      <div style={{ height: 280, borderRadius: 'var(--bs-border-radius-lg)', overflow: 'hidden', border: '1px solid var(--color-border)', boxShadow: 'var(--shadow-sm)' }}>
        <MapContainer center={position || DEFAULT_CENTER} zoom={position ? 14 : 12} style={{ height: '100%', width: '100%' }}>
          <TileLayer
            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
            url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          />
          <ClickToPlacePin onPick={onChange} />
          {position && <Marker position={position} />}
        </MapContainer>
      </div>
      <div className="form-text d-flex align-items-center gap-1">
        <MapPinIcon width={14} height={14} />
        {position
          ? `Pin set at ${value.lat.toFixed(5)}, ${value.lon.toFixed(5)}. Click elsewhere on the map to move it.`
          : 'Search an address above, or click directly on the map to drop a pin.'}
      </div>
    </div>
  )
}
