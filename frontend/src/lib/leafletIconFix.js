// Leaflet's default marker icon uses relative image URLs that break under
// Vite's bundling (the images never resolve, so pins render invisible).
// Standard fix: import the actual asset URLs and reset L.Icon.Default's
// options to point at them. Imported once, at app startup.
import L from 'leaflet'
import markerIcon2x from 'leaflet/dist/images/marker-icon-2x.png'
import markerIcon from 'leaflet/dist/images/marker-icon.png'
import markerShadow from 'leaflet/dist/images/marker-shadow.png'

delete L.Icon.Default.prototype._getIconUrl

L.Icon.Default.mergeOptions({
  iconRetinaUrl: markerIcon2x,
  iconUrl: markerIcon,
  shadowUrl: markerShadow,
})
