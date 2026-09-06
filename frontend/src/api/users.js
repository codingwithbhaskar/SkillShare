import apiClient from './client.js'

export async function deactivateMyAccount() {
  await apiClient.post('/users/me/deactivate')
}
