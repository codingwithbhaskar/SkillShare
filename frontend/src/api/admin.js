import apiClient from './client.js'

export async function fetchAdminStats() {
  const { data } = await apiClient.get('/admin/stats')
  return data
}

export async function fetchAdminUsers({ role, status, search } = {}) {
  const { data } = await apiClient.get('/admin/users', { params: { role, status, search } })
  return data
}

export async function updateUserStatus(userId, status) {
  const { data } = await apiClient.patch(`/admin/users/${userId}/status`, { status })
  return data
}

export async function fetchAdminBookings(status) {
  const { data } = await apiClient.get('/admin/bookings', { params: { status } })
  return data
}

export async function fetchAdminServices() {
  const { data } = await apiClient.get('/admin/services')
  return data
}

export async function createAdminService(payload) {
  const { data } = await apiClient.post('/admin/services', payload)
  return data
}

export async function updateAdminService(serviceId, payload) {
  const { data } = await apiClient.put(`/admin/services/${serviceId}`, payload)
  return data
}

export async function deleteAdminService(serviceId) {
  await apiClient.delete(`/admin/services/${serviceId}`)
}

export async function fetchAdminSkills() {
  const { data } = await apiClient.get('/admin/skills')
  return data
}

export async function createAdminSkill(payload) {
  const { data } = await apiClient.post('/admin/skills', payload)
  return data
}

export async function updateAdminSkill(skillId, payload) {
  const { data } = await apiClient.put(`/admin/skills/${skillId}`, payload)
  return data
}

export async function deleteAdminSkill(skillId) {
  await apiClient.delete(`/admin/skills/${skillId}`)
}

// from/to are Date objects (or undefined) — the backend parses
// ISO-8601 offset date-times (OffsetDateTime), so .toISOString() is
// exactly what's needed on the wire.
export async function fetchAdminReport({ from, to } = {}) {
  const { data } = await apiClient.get('/admin/reports', {
    params: {
      from: from ? from.toISOString() : undefined,
      to: to ? to.toISOString() : undefined,
    },
  })
  return data
}
