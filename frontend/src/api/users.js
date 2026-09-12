import apiClient from './client.js'

export async function changeMyPassword(currentPassword, newPassword) {
  await apiClient.post('/users/me/change-password', { currentPassword, newPassword })
}

export async function deactivateMyAccount() {
  await apiClient.post('/users/me/deactivate')
}
