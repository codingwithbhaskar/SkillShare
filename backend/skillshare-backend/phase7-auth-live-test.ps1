# Phase 7 (authentication) live test script - paste into PowerShell while
# the app is running. No env vars required beyond what Phase 5/6 already
# need (SKILLSHARE_DB_PASSWORD, optionally RAZORPAY_*) - JWT_SECRET is
# optional too (JwtService falls back to a random in-memory key if unset).
$base = "http://localhost:8080/api"
$rand = Get-Random -Maximum 999999

function Invoke-Json($method, $path, $body, $token) {
    $headers = @{}
    if ($token) { $headers["Authorization"] = "Bearer $token" }
    $params = @{ Uri = "$base$path"; Method = $method; Headers = $headers }
    if ($body) { $params.Body = ($body | ConvertTo-Json); $params.ContentType = "application/json" }
    try {
        return @{ ok = $true; data = Invoke-RestMethod @params }
    } catch {
        return @{ ok = $false; status = $_.Exception.Response.StatusCode; message = $_.ErrorDetails.Message }
    }
}

Write-Host "`n=== 1. Register a customer ===" -ForegroundColor Cyan
$custEmail = "customer.$rand@test.com"
$custReg = Invoke-Json POST "/auth/register" @{ role="customer"; fullName="Test Customer $rand"; email=$custEmail; phone="9000000001"; password="password123" }
$custReg.data | Format-List
$customerToken = $custReg.data.token

Write-Host "`n=== 2. Register a worker ===" -ForegroundColor Cyan
$workerEmail = "worker.$rand@test.com"
$workerReg = Invoke-Json POST "/auth/register" @{ role="worker"; fullName="Test Worker $rand"; email=$workerEmail; phone="9000000002"; password="password123" }
$workerReg.data | Format-List
$workerToken = $workerReg.data.token

Write-Host "`n=== 3. Register with the SAME email again (expect 409) ===" -ForegroundColor Cyan
$dup = Invoke-Json POST "/auth/register" @{ role="customer"; fullName="Dup"; email=$custEmail; phone=""; password="password123" }
Write-Host "$($dup.status) $($dup.message)"

Write-Host "`n=== 4. Register with a too-short password (expect 400) ===" -ForegroundColor Cyan
$badPw = Invoke-Json POST "/auth/register" @{ role="customer"; fullName="Bad"; email="short.$rand@test.com"; phone=""; password="short" }
Write-Host "$($badPw.status) $($badPw.message)"

Write-Host "`n=== 5. Login as the customer with correct password (expect 200 + token) ===" -ForegroundColor Cyan
$login = Invoke-Json POST "/auth/login" @{ email=$custEmail; password="password123" }
$login.data | Format-List

Write-Host "`n=== 6. Login with wrong password (expect 401) ===" -ForegroundColor Cyan
$badLogin = Invoke-Json POST "/auth/login" @{ email=$custEmail; password="wrongpassword" }
Write-Host "$($badLogin.status) $($badLogin.message)"

Write-Host "`n=== 7. Call a protected endpoint with NO token (expect 401) ===" -ForegroundColor Cyan
$noToken = Invoke-Json GET "/workers/1/stats" $null $null
Write-Host "$($noToken.status) $($noToken.message)"

Write-Host "`n=== 8. Call worker-dashboard endpoint with a CUSTOMER token (expect 403 - wrong role) ===" -ForegroundColor Cyan
$wrongRole = Invoke-Json GET "/workers/1/stats" $null $customerToken
Write-Host "$($wrongRole.status) $($wrongRole.message)"

Write-Host "`n=== 9. Call worker-dashboard endpoint with a WORKER token (expect 200) ===" -ForegroundColor Cyan
$rightRole = Invoke-Json GET "/workers/1/stats" $null $workerToken
if ($rightRole.ok) { $rightRole.data | Format-List } else { Write-Host "$($rightRole.status) $($rightRole.message)" -ForegroundColor Red }

Write-Host "`n=== 10. Create a booking with the customer token (expect 200 - any authenticated user allowed for now) ===" -ForegroundColor Cyan
$bookingBody = @{
    customerId = 1; serviceId = 1; skillIds = @(1); urgency = "normal"
    scheduledStart = (Get-Date).Date.AddDays(30).AddHours(11).ToString("yyyy-MM-ddTHH:mm:ss+05:30")
    scheduledEnd   = (Get-Date).Date.AddDays(30).AddHours(13).ToString("yyyy-MM-ddTHH:mm:ss+05:30")
    notes = "Phase 7 auth live test booking"; addressLine = "College Road, Nashik"
    city = "Nashik"; state = "Maharashtra"; pincode = "422005"; latitude = 19.9975; longitude = 73.7898
}
$booking = Invoke-Json POST "/bookings" $bookingBody $customerToken
if ($booking.ok) { $booking.data | Format-List } else { Write-Host "$($booking.status) $($booking.message)" -ForegroundColor Red }

Write-Host "`n=== Done. ===" -ForegroundColor Yellow
