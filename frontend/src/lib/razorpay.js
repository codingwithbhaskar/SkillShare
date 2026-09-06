// Loads Razorpay's Checkout.js once and caches the promise, mirroring the
// same script the Phase 6 throwaway payment-test.html page used, now
// wired into React instead of a standalone static page.
let scriptPromise = null

export function loadRazorpayCheckout() {
  if (scriptPromise) return scriptPromise
  scriptPromise = new Promise((resolve, reject) => {
    if (window.Razorpay) {
      resolve(window.Razorpay)
      return
    }
    const script = document.createElement('script')
    script.src = 'https://checkout.razorpay.com/v1/checkout.js'
    script.onload = () => resolve(window.Razorpay)
    script.onerror = () => reject(new Error('Could not load the Razorpay checkout script.'))
    document.body.appendChild(script)
  })
  return scriptPromise
}
