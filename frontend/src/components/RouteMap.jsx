import { useEffect, useRef } from 'react'
import { MapContainer, Marker, Popup, TileLayer, useMap } from 'react-leaflet'
import L from 'leaflet'
import 'leaflet-routing-machine'
import 'leaflet-routing-machine/dist/leaflet-routing-machine.css'
import '../lib/leafletIconFix.js'
import { MapPinIcon } from './icons.jsx'

// A small colored-dot marker (no emoji, per the design-system checklist)
// to visually tell "you" (the worker's live position) apart from the
// default blue pin used for the job address.
const workerDivIcon = L.divIcon({
  className: '',
  html: '<div style="width:16px;height:16px;border-radius:50%;background:#EA580C;border:2px solid #fff;box-shadow:0 0 0 1px #EA580C;"></div>',
  iconSize: [16, 16],
  iconAnchor: [8, 8],
})

function RouteLine({ from, to, onRouteFound }) {
  const map = useMap()
  const onRouteFoundRef = useRef(onRouteFound)
  onRouteFoundRef.current = onRouteFound

  useEffect(() => {
    if (!from || !to) return undefined

    const control = L.Routing.control({
      waypoints: [L.latLng(from.lat, from.lon), L.latLng(to.lat, to.lon)],
      router: L.Routing.osrmv1({ serviceUrl: 'https://router.project-osrm.org/route/v1' }),
      addWaypoints: false,
      draggableWaypoints: false,
      fitSelectedRoutes: true,
      show: false,
      createMarker: () => null, // markers are rendered separately as <Marker>
      lineOptions: { styles: [{ color: '#EA580C', weight: 5, opacity: 0.85 }] },
    })

    control.on('routesfound', (e) => {
      const summary = e.routes?.[0]?.summary
      if (summary && onRouteFoundRef.current) {
        onRouteFoundRef.current({
          distanceKm: summary.totalDistance / 1000,
          durationMin: summary.totalTime / 60,
        })
      }
    })

    control.on('routingerror', () => {
      if (onRouteFoundRef.current) onRouteFoundRef.current(null)
    })

    control.addTo(map)
    return () => {
      map.removeControl(control)
    }
  }, [map, from?.lat, from?.lon, to?.lat, to?.lon])

  return null
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
