import apiClient from './client.js'

export async function fetchMyWorkerProfile() {
  const { data } = await apiClient.get('/workers/me')
  return data
}

export async function updateMyWorkerProfile(payload) {
  const { data } = await apiClient.put('/workers/me', payload)
  return data
}

export async function fetchWorkerBookings(workerId) {
  const { data } = await apiClient.get(`/workers/${workerId}/bookings`)
  return data
}

export async function fetchWorkerReviews(workerId) {
  const { data } = await apiClient.get(`/workers/${workerId}/reviews`)
  return data
}

export async function fetchWorkerStats(workerId) {
  const { data } = await apiClient.get(`/workers/${workerId}/stats`)
  return data
}
