import apiClient from './client.js'

export async function createPaymentOrder(bookingId) {
  const { data } = await apiClient.post(`/payments/bookings/${bookingId}/order`)
  return data
}

export async function verifyPayment(bookingId, payload) {
  const { data } = await apiClient.post(`/payments/bookings/${bookingId}/verify`, payload)
  return data
}

export async function fetchPayment(bookingId) {
  const { data } = await apiClient.get(`/payments/bookings/${bookingId}`)
  return data
}
