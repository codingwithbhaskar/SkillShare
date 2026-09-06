import apiClient from './client.js'

export async function createBooking(payload) {
  const { data } = await apiClient.post('/bookings', payload)
  return data
}

export async function fetchMyBookings() {
  const { data } = await apiClient.get('/bookings/me')
  return data
}

export async function fetchBooking(bookingId) {
  const { data } = await apiClient.get(`/bookings/${bookingId}`)
  return data
}

export async function cancelBooking(bookingId) {
  const { data } = await apiClient.post(`/bookings/${bookingId}/cancel`)
  return data
}

export async function startBooking(bookingId) {
  const { data } = await apiClient.post(`/bookings/${bookingId}/start`)
  return data
}

export async function completeBooking(bookingId) {
  const { data } = await apiClient.post(`/bookings/${bookingId}/complete`)
  return data
}

export async function submitReview(bookingId, { rating, comment }) {
  const { data } = await apiClient.post(`/bookings/${bookingId}/review`, { rating, comment })
  return data
}
