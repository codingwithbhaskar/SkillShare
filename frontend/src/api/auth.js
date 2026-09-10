import apiClient from './client.js'

/** Starts the password-reset flow. Always resolves (the backend returns
 *  202 whether or not the email is registered) unless the network fails. */
export async function requestPasswordReset(email) {
  await apiClient.post('/auth/forgot-password', { email })
}

/** Completes the flow with the token from the emailed link. */
export async function resetPassword(token, newPassword) {
  await apiClient.post('/auth/reset-password', { token, newPassword })
}
