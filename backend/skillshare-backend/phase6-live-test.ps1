# Phase 6 live test script — paste into PowerShell while the app is running.
# Requires RAZORPAY_KEY_ID / RAZORPAY_KEY_SECRET to be set in THIS terminal
# before you started `mvnw.cmd spring-boot:run` (env vars are read at boot).
$base = "http://localhost:8080/api"

Write-Host "`n=== 1. Create booking (customer 1, Electrical Repair, Wiring skill, Nashik job site) ===" -ForegroundColor Cyan
$body = @{
    customerId    = 1
    serviceId     = 1
    skillIds      = @(1)
    urgency       = "normal"
    scheduledStart= (Get-Date).Date.AddDays(9).AddHours(16).ToString("yyyy-MM-ddTHH:mm:ss+05:30")
    scheduledEnd  = (Get-Date).Date.AddDays(9).AddHours(18).ToString("yyyy-MM-ddTHH:mm:ss+05:30")
    notes         = "Phase 6 live payment test booking"
    addressLine   = "College Road, Nashik"
    landmark      = "near HPT College"
    city          = "Nashik"
    state         = "Maharashtra"
    pincode       = "422005"
    latitude      = 19.9975
    longitude     = 73.7898
} | ConvertTo-Json
$booking = Invoke-RestMethod -Uri "$base/bookings" -Method Post -Body $body -ContentType "application/json"
$booking | Format-List
$bookingId = $booking.bookingId

Write-Host "`n=== 2. GET payment before any order exists (expect 409 - no payment yet) ===" -ForegroundColor Cyan
try { Invoke-RestMethod -Uri "$base/payments/bookings/$bookingId" -Method Get } catch { Write-Host $_.Exception.Response.StatusCode $_.ErrorDetails.Message }

Write-Host "`n=== 3. Try creating an order before allocation (expect 400/409 - no total_amount yet) ===" -ForegroundColor Cyan
try { Invoke-RestMethod -Uri "$base/payments/bookings/$bookingId/order" -Method Post } catch { Write-Host $_.Exception.Response.StatusCode $_.ErrorDetails.Message }

Write-Host "`n=== 4. Allocate (Phase 4 endpoint) - this sets total_amount ===" -ForegroundColor Cyan
$alloc = Invoke-RestMethod -Uri "$base/allocation/bookings/$bookingId/allocate" -Method Post
$alloc | Format-List

Write-Host "`n=== 5. Create a REAL Razorpay order (the actual live check) ===" -ForegroundColor Cyan
$order = Invoke-RestMethod -Uri "$base/payments/bookings/$bookingId/order" -Method Post
$order | Format-List
Write-Host "If gatewayOrderId above starts with 'order_', a real Razorpay sandbox order was created successfully." -ForegroundColor Yellow

Write-Host "`n=== 6. Create order again (expect the SAME order returned - idempotency) ===" -ForegroundColor Cyan
$order2 = Invoke-RestMethod -Uri "$base/payments/bookings/$bookingId/order" -Method Post
$order2 | Format-List
if ($order.gatewayOrderId -eq $order2.gatewayOrderId) { Write-Host "MATCH - idempotent, no duplicate order created." -ForegroundColor Green } else { Write-Host "MISMATCH - unexpected new order created!" -ForegroundColor Red }

Write-Host "`n=== 7. GET payment now that an order exists ===" -ForegroundColor Cyan
Invoke-RestMethod -Uri "$base/payments/bookings/$bookingId" -Method Get | Format-List

Write-Host "`n=== 8. Verify with a deliberately WRONG signature (expect 400 - signature check works) ===" -ForegroundColor Cyan
$badVerify = @{
    razorpayOrderId   = $order.gatewayOrderId
    razorpayPaymentId = "pay_fake123"
    razorpaySignature = "0000000000000000000000000000000000000000000000000000000000000000"
} | ConvertTo-Json
try { Invoke-RestMethod -Uri "$base/payments/bookings/$bookingId/verify" -Method Post -Body $badVerify -ContentType "application/json" } catch { Write-Host $_.Exception.Response.StatusCode $_.ErrorDetails.Message }

Write-Host "`n=== 9. Order creation on a nonexistent booking (expect 404) ===" -ForegroundColor Cyan
try { Invoke-RestMethod -Uri "$base/payments/bookings/999999/order" -Method Post } catch { Write-Host $_.Exception.Response.StatusCode $_.ErrorDetails.Message }

Write-Host "`n=== Done. Check the spring-boot:run console for any Razorpay-related errors. ===" -ForegroundColor Yellow
Write-Host "=== Also log into the Razorpay Dashboard (Test Mode) -> Transactions -> Orders and confirm booking $bookingId's order appears there. ===" -ForegroundColor Yellow
