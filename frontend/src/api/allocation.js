import apiClient from './client.js'

export async function allocateBooking(bookingId) {
  const { data } = await apiClient.post(`/allocation/bookings/${bookingId}/allocate`)
  return data
}
