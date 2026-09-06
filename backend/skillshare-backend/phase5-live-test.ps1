# Phase 5 live test script - run with the app already up (mvnw.cmd spring-boot:run).
$base = "http://localhost:8080/api"

Write-Host "`n=== 1. Create booking (customer 1, Electrical Repair, Wiring skill, Nashik job site) ===" -ForegroundColor Cyan
$body = @{
    customerId    = 1
    serviceId     = 1
    skillIds      = @(1)
    urgency       = "normal"
    scheduledStart= (Get-Date).Date.AddDays(1).AddHours(10).ToString("yyyy-MM-ddTHH:mm:ss+05:30")
    scheduledEnd  = (Get-Date).Date.AddDays(1).AddHours(12).ToString("yyyy-MM-ddTHH:mm:ss+05:30")
    notes         = "Phase 5 live test booking"
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

Write-Host "`n=== 2. Try /start while still pending (expect 409) ===" -ForegroundColor Cyan
try { Invoke-RestMethod -Uri "$base/bookings/$bookingId/start" -Method Post } catch { Write-Host $_.Exception.Response.StatusCode $_.ErrorDetails.Message }

Write-Host "`n=== 3. Try /review before completion (expect 409) ===" -ForegroundColor Cyan
try { Invoke-RestMethod -Uri "$base/bookings/$bookingId/review" -Method Post -Body (@{rating=5;comment="too early"} | ConvertTo-Json) -ContentType "application/json" } catch { Write-Host $_.Exception.Response.StatusCode $_.ErrorDetails.Message }

Write-Host "`n=== 4. Allocate (Phase 4 endpoint) ===" -ForegroundColor Cyan
$alloc = Invoke-RestMethod -Uri "$base/allocation/bookings/$bookingId/allocate" -Method Post
$alloc | Format-List
$workerId = $alloc.assignedWorkerId

Write-Host "`n=== 5. Start (confirmed -> in_progress) ===" -ForegroundColor Cyan
$started = Invoke-RestMethod -Uri "$base/bookings/$bookingId/start" -Method Post
$started | Format-List

Write-Host "`n=== 6. Complete (in_progress -> completed) ===" -ForegroundColor Cyan
$completed = Invoke-RestMethod -Uri "$base/bookings/$bookingId/complete" -Method Post
$completed | Format-List

Write-Host "`n=== 7. Submit review ===" -ForegroundColor Cyan
$review = Invoke-RestMethod -Uri "$base/bookings/$bookingId/review" -Method Post -Body (@{rating=5;comment="Great work, on time"} | ConvertTo-Json) -ContentType "application/json"
$review | Format-List

Write-Host "`n=== 8. Submit review again (expect 409 - already reviewed) ===" -ForegroundColor Cyan
try { Invoke-RestMethod -Uri "$base/bookings/$bookingId/review" -Method Post -Body (@{rating=3;comment="dup"} | ConvertTo-Json) -ContentType "application/json" } catch { Write-Host $_.Exception.Response.StatusCode $_.ErrorDetails.Message }

Write-Host "`n=== 9. Worker dashboard: bookings ===" -ForegroundColor Cyan
Invoke-RestMethod -Uri "$base/workers/$workerId/bookings" -Method Get | Format-Table

Write-Host "`n=== 10. Worker dashboard: reviews ===" -ForegroundColor Cyan
Invoke-RestMethod -Uri "$base/workers/$workerId/reviews" -Method Get | Format-Table

Write-Host "`n=== 11. Worker dashboard: stats ===" -ForegroundColor Cyan
Invoke-RestMethod -Uri "$base/workers/$workerId/stats" -Method Get | Format-List

Write-Host "`n=== 12. Create a second booking, then cancel it from pending ===" -ForegroundColor Cyan
$body2 = @{
    customerId    = 2
    serviceId     = 1
    skillIds      = @(1)
    urgency       = "urgent"
    scheduledStart= (Get-Date).Date.AddDays(2).AddHours(14).ToString("yyyy-MM-ddTHH:mm:ss+05:30")
    scheduledEnd  = (Get-Date).Date.AddDays(2).AddHours(15).ToString("yyyy-MM-ddTHH:mm:ss+05:30")
    notes         = "Phase 5 cancel test"
    addressLine   = "College Road, Nashik"
    city          = "Nashik"
    state         = "Maharashtra"
    pincode       = "422005"
    latitude      = 19.9975
    longitude     = 73.7898
} | ConvertTo-Json
$booking2 = Invoke-RestMethod -Uri "$base/bookings" -Method Post -Body $body2 -ContentType "application/json"
$cancelled = Invoke-RestMethod -Uri "$base/bookings/$($booking2.bookingId)/cancel" -Method Post
$cancelled | Format-List

Write-Host "`n=== 13. Try /start on the now-cancelled booking (expect 409) ===" -ForegroundColor Cyan
try { Invoke-RestMethod -Uri "$base/bookings/$($booking2.bookingId)/start" -Method Post } catch { Write-Host $_.Exception.Response.StatusCode $_.ErrorDetails.Message }

Write-Host "`n=== Done. Also check the spring-boot:run console for '[NOTIFICATION-DISPATCH] would send ...' lines appearing within ~30s of steps 1/4/5/6 above. ===" -ForegroundColor Yellow
