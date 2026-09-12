import { useEffect, useRef, useState } from 'react'
import { MapContainer, Marker, Polyline, Popup, TileLayer, useMap } from 'react-leaflet'
import L from 'leaflet'
import '../lib/leafletIconFix.js'
import { MapPinIcon } from './icons.jsx'

// Real hosted routing API (free tier: 2,000 requests/day, no card) -
// replaces leaflet-routing-machine's default router.project-osrm.org,
// OSRM's own public DEMO server which their docs explicitly say isn't
// for production use and which was the actual cause of "the map doesn't
// work" reports: no uptime guarantee, gets rate-limited under real
// traffic, and fails silently (no route line, no distance/time, no error
// shown). With VITE_ORS_API_KEY unset, this degrades the same way every
// other optional integration in this project does: no route line, no
// crash - see AddressMapPicker.jsx's LocationIQ fallback for the same
// pattern on the geocoding side.
const ORS_API_KEY = import.meta.env.VITE_ORS_API_KEY

// A small colored-dot marker (no emoji, per the design-system checklist)
// to visually tell "you" (the worker's live position) apart from the
// default blue pin used for the job address.
const workerDivIcon = L.divIcon({
  className: '',
  html: '<div style="width:16px;height:16px;border-radius:50%;background:#EA580C;border:2px solid #fff;box-shadow:0 0 0 1px #EA580C;"></div>',
  iconSize: [16, 16],
  iconAnchor: [8, 8],
})

/** Fits the map to the drawn route once it's known - replaces leaflet-
 *  routing-machine's built-in fitSelectedRoutes option, which doesn't
 *  exist now that routing is a plain fetch + Polyline. */
function FitToRoute({ positions }) {
  const map = useMap()
  useEffect(() => {
    if (positions && positions.length > 1) {
      map.fitBounds(L.latLngBounds(positions), { padding: [24, 24] })
    }
  }, [map, positions])
  return null
}

function RouteLine({ from, to, onRouteFound }) {
  const [positions, setPositions] = useState(null)
  const onRouteFoundRef = useRef(onRouteFound)
  onRouteFoundRef.current = onRouteFound

  useEffect(() => {
    setPositions(null)
    if (!from || !to) return undefined

    if (!ORS_API_KEY) {
      onRouteFoundRef.current?.(null)
      return undefined
    }

    let cancelled = false
    fetch('https://api.openrouteservice.org/v2/directions/driving-car/geojson', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: ORS_API_KEY },
      body: JSON.stringify({ coordinates: [[from.lon, from.lat], [to.lon, to.lat]] }),
    })
      .then((res) => {
        if (!res.ok) throw new Error('routing request failed')
        return res.json()
      })
      .then((geojson) => {
        if (cancelled) return
        const feature = geojson.features?.[0]
        if (!feature) throw new Error('no route returned')
        // GeoJSON is [lon, lat]; Leaflet wants [lat, lon].
        setPositions(feature.geometry.coordinates.map(([lon, lat]) => [lat, lon]))
        const summary = feature.properties?.summary
        if (summary) {
          onRouteFoundRef.current?.({
            distanceKm: summary.distance / 1000,
            durationMin: summary.duration / 60,
          })
        }
      })
      .catch(() => {
        if (!cancelled) onRouteFoundRef.current?.(null)
      })
    return () => {
      cancelled = true
    }
  }, [from?.lat, from?.lon, to?.lat, to?.lon])

  if (!positions) return null
  return (
    <>
      <Polyline positions={positions} pathOptions={{ color: '#EA580C', weight: 5, opacity: 0.85 }} />
      <FitToRoute positions={positions} />
    </>
  )
}

/**
 * Booking-detail map: the job's address pin always; the viewer's own live
 * position (via one-time browser geolocation, Option B from
 * location-map-feature-design.md) and a drawn route only when
 * `workerPosition` is supplied — the caller is responsible for deciding
 * whose browser gets asked for geolocation (only the assigned worker
 * viewing their own job, not the customer).
 */
export default function RouteMap({ jobPosition, workerPosition, onRouteFound }) {
  if (!jobPosition) {
    return (
      <div className="alert alert-secondary mb-0 d-flex align-items-center gap-2">
        <MapPinIcon width={18} height={18} /> No location on file for this booking.
      </div>
    )
  }
  const center = [jobPosition.lat, jobPosition.lon]

  return (
    <div className="fade-in-up" style={{ height: 320, borderRadius: 'var(--bs-border-radius-lg)', overflow: 'hidden', border: '1px solid var(--color-border)', boxShadow: 'var(--shadow-sm)' }}>
      <MapContainer center={center} zoom={13} style={{ height: '100%', width: '100%' }}>
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />
        <Marker position={center}>
          <Popup>Job location</Popup>
        </Marker>
        {workerPosition && (
          <Marker position={[workerPosition.lat, workerPosition.lon]} icon={workerDivIcon}>
            <Popup>You</Popup>
          </Marker>
        )}
        {workerPosition && <RouteLine from={workerPosition} to={jobPosition} onRouteFound={onRouteFound} />}
      </MapContainer>
    </div>
  )
}
