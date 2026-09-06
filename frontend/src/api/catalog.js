import apiClient from './client.js'

export async function fetchServices() {
  const { data } = await apiClient.get('/catalog/services')
  return data
}

export async function fetchSkills() {
  const { data } = await apiClient.get('/catalog/skills')
  return data
}
