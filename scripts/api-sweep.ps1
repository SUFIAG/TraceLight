# Sentinel API sweep.
#
# Logs in, walks every REST endpoint in dependency order (seed -> transactions ->
# fraud -> alerts -> cases -> AI) and prints status plus row counts for each one.
# An endpoint that answers 200 with zero rows is reported separately from one that
# fails, because "compiles and returns 200" is not the same as "returns real data".
#
#   powershell -ExecutionPolicy Bypass -File scripts\api-sweep.ps1
#   powershell -ExecutionPolicy Bypass -File scripts\api-sweep.ps1 -Seed
#   powershell -ExecutionPolicy Bypass -File scripts\api-sweep.ps1 -Seed -Csv data\transactions_baseline.csv

param(
    [string]$BaseUrl  = "http://localhost:8081",
    [string]$Email    = "admin@sentinel.ai",
    [string]$Password = "Sentinel@2026",
    [switch]$Seed,
    [string]$Csv
)

$ErrorActionPreference = "Continue"
$script:Results = @()

function Invoke-Api {
    param([string]$Method, [string]$Path, $Body, [hashtable]$Headers)

    $params = @{ Uri = "$BaseUrl$Path"; Method = $Method; TimeoutSec = 120 }
    if ($Headers) { $params.Headers = $Headers }
    if ($Body) {
        $params.ContentType = "application/json"
        $params.Body = ($Body | ConvertTo-Json -Depth 10 -Compress)
    }

    try {
        $response = Invoke-RestMethod @params
        $payload  = if ($null -ne $response.data) { $response.data } else { $response }
        $count    = if ($payload -is [array])          { $payload.Count }
                    elseif ($payload.content -is [array]) { $payload.content.Count }
                    elseif ($null -ne $payload)           { 1 }
                    else                                  { 0 }

        $script:Results += [pscustomobject]@{ Endpoint = "$Method $Path"; Status = "OK"; Rows = $count }
        $colour = if ($count -gt 0) { "Green" } else { "Yellow" }
        Write-Host ("  OK   {0,-6} {1,-54} rows={2}" -f $Method, $Path, $count) -ForegroundColor $colour
        return $payload
    }
    catch {
        $code = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { "ERR" }
        $detail = $_.Exception.Message
        if ($_.Exception.Response) {
            try {
                $detail = (New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())).ReadToEnd()
            } catch { }
        }
        $script:Results += [pscustomobject]@{ Endpoint = "$Method $Path"; Status = "FAIL $code"; Rows = 0 }
        Write-Host ("  FAIL {0,-6} {1,-54} {2}" -f $Method, $Path, $code) -ForegroundColor Red
        Write-Host ("       {0}" -f $detail.Substring(0, [Math]::Min(260, $detail.Length))) -ForegroundColor DarkGray
        return $null
    }
}

function Upload-Csv {
    param([string]$File, [string]$Token)
    if (-not (Test-Path $File)) {
        Write-Host "  SKIP  CSV not found: $File" -ForegroundColor Yellow
        return $null
    }
    # Invoke-RestMethod on Windows PowerShell 5.1 has no -Form, so build the
    # multipart body by hand.
    $boundary = [guid]::NewGuid().ToString()
    $content  = [System.IO.File]::ReadAllText($File)
    $body = "--$boundary`r`n" +
            "Content-Disposition: form-data; name=`"file`"; filename=`"$(Split-Path $File -Leaf)`"`r`n" +
            "Content-Type: text/csv`r`n`r`n" +
            "$content`r`n--$boundary--`r`n"
    try {
        $r = Invoke-RestMethod -Uri "$BaseUrl/api/v1/transactions/upload" -Method Post `
                -Headers @{ Authorization = "Bearer $Token" } `
                -ContentType "multipart/form-data; boundary=$boundary" -Body $body -TimeoutSec 300
        $d = $r.data
        $script:Results += [pscustomobject]@{ Endpoint = "POST /api/v1/transactions/upload"; Status = "OK"; Rows = $d.totalProcessed }
        Write-Host ("  OK   POST   /api/v1/transactions/upload  processed={0} failed={1}" -f $d.totalProcessed, $d.totalFailed) -ForegroundColor Green
        if ($d.errors) { $d.errors | Select-Object -First 5 | ForEach-Object { Write-Host "       $_" -ForegroundColor DarkGray } }
        return $d
    } catch {
        $detail = $_.Exception.Message
        if ($_.Exception.Response) {
            try { $detail = (New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())).ReadToEnd() } catch { }
        }
        $script:Results += [pscustomobject]@{ Endpoint = "POST /api/v1/transactions/upload"; Status = "FAIL"; Rows = 0 }
        Write-Host "  FAIL POST   /api/v1/transactions/upload" -ForegroundColor Red
        Write-Host "       $detail" -ForegroundColor DarkGray
        return $null
    }
}

# --------------------------------------------------------------------------- 1
Write-Host "`n=== 1. AUTH ===" -ForegroundColor Cyan
$login = Invoke-Api POST "/api/v1/auth/login" @{ email = $Email; password = $Password }
if (-not $login) { Write-Host "`nLogin failed. Nothing else can be checked." -ForegroundColor Red; exit 1 }
$token = $login.accessToken
$auth  = @{ Authorization = "Bearer $token" }

$me = Invoke-Api GET "/api/v1/auth/me" -Headers $auth
$members = Invoke-Api GET "/api/v1/auth/users" -Headers $auth
$otherUserId = $null
if ($members) { $otherUserId = ($members | Where-Object { $_.email -ne $Email } | Select-Object -First 1).id }

# --------------------------------------------------------------------------- 2
if ($Seed) {
    Write-Host "`n=== 2. DEMO SEED ===" -ForegroundColor Cyan
    $seed = Invoke-Api POST "/api/v1/demo/seed?force=true" -Headers $auth
    if ($seed) { Write-Host ("       " + ($seed | ConvertTo-Json -Depth 4 -Compress)) -ForegroundColor DarkGray }
}
if ($Csv) {
    Write-Host "`n=== 2b. CSV UPLOAD ===" -ForegroundColor Cyan
    Upload-Csv -File $Csv -Token $token | Out-Null
}

# --------------------------------------------------------------------------- 3
Write-Host "`n=== 3. DASHBOARD / ANALYTICS ===" -ForegroundColor Cyan
$dash = Invoke-Api GET "/api/v1/dashboard"                    -Headers $auth
if ($dash) { Write-Host ("       " + ($dash | ConvertTo-Json -Depth 3 -Compress)) -ForegroundColor DarkGray }
Invoke-Api GET "/api/v1/dashboard/summary"                    -Headers $auth | Out-Null
Invoke-Api GET "/api/v1/dashboard/fraud-trend?days=30"        -Headers $auth | Out-Null
Invoke-Api GET "/api/v1/dashboard/top-merchants?limit=10"     -Headers $auth | Out-Null
Invoke-Api GET "/api/v1/dashboard/geographic"                 -Headers $auth | Out-Null

# --------------------------------------------------------------------------- 4
Write-Host "`n=== 4. TRANSACTIONS ===" -ForegroundColor Cyan
$stamp = (Get-Date).ToUniversalTime().ToString("yyyyMMddHHmmss")
$ingested = Invoke-Api POST "/api/v1/transactions" -Headers $auth -Body @{
    transactionExternalId = "SWEEP-$stamp"
    userId                = "CUST-SWEEP-001"
    merchantId            = "MERCH-CRYPTO-OFFSHORE"
    amount                = 9850.00
    currency              = "USD"
    country               = "RU"
    deviceId              = "DEV-SWEEP-UNKNOWN"
    ipAddress             = "45.133.1.88"
    timestamp             = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ss") + "Z"
}
$txnPage = Invoke-Api GET "/api/v1/transactions?page=0&size=5" -Headers $auth
$txnId = if ($ingested) { $ingested.id } elseif ($txnPage.content.Count -gt 0) { $txnPage.content[0].id } else { $null }
if ($txnId) { Invoke-Api GET "/api/v1/transactions/$txnId" -Headers $auth | Out-Null }
Invoke-Api GET "/api/v1/transactions?page=0&size=5&status=FLAGGED" -Headers $auth | Out-Null

# --------------------------------------------------------------------------- 5
Write-Host "`n=== 5. FRAUD ANALYSIS ===" -ForegroundColor Cyan
if ($txnId) {
    Invoke-Api POST "/api/v1/fraud/analyze/$txnId"    -Headers $auth | Out-Null
    $risk = Invoke-Api GET "/api/v1/fraud/risk-score/$txnId" -Headers $auth
    if ($risk) { Write-Host ("       " + ($risk | ConvertTo-Json -Depth 3 -Compress)) -ForegroundColor DarkGray }
} else {
    Write-Host "  SKIP  no transaction id available" -ForegroundColor Yellow
}

# --------------------------------------------------------------------------- 6
Write-Host "`n=== 6. ALERTS ===" -ForegroundColor Cyan
$alerts  = Invoke-Api GET "/api/v1/alerts?page=0&size=5" -Headers $auth
$alertId = if ($alerts.content.Count -gt 0) { $alerts.content[0].id } else { $null }
if ($alertId) {
    Invoke-Api GET   "/api/v1/alerts/$alertId"        -Headers $auth | Out-Null
    Invoke-Api PATCH "/api/v1/alerts/$alertId/status" -Headers $auth -Body @{ status = "INVESTIGATING" } | Out-Null
}
Invoke-Api GET "/api/v1/alerts?page=0&size=5&status=OPEN" -Headers $auth | Out-Null

# --------------------------------------------------------------------------- 7
Write-Host "`n=== 7. CASES ===" -ForegroundColor Cyan
$caseId = $null
if ($alertId) {
    $created = Invoke-Api POST "/api/v1/cases" -Headers $auth -Body @{
        alertId = $alertId; title = "Sweep case $stamp"; priority = "HIGH"
    }
    if ($created) { $caseId = $created.id }
}
$cases = Invoke-Api GET "/api/v1/cases?page=0&size=5" -Headers $auth
if (-not $caseId -and $cases.content.Count -gt 0) { $caseId = $cases.content[0].id }
if ($caseId) {
    Invoke-Api GET   "/api/v1/cases/$caseId"           -Headers $auth | Out-Null
    Invoke-Api POST  "/api/v1/cases/$caseId/comments"  -Headers $auth -Body @{ content = "Automated sweep comment." } | Out-Null
    Invoke-Api GET   "/api/v1/cases/$caseId/comments"  -Headers $auth | Out-Null
    if ($otherUserId) {
        Invoke-Api PATCH "/api/v1/cases/$caseId/assign" -Headers $auth -Body @{ assigneeId = $otherUserId } | Out-Null
    }
    # Deliberately invalid: IN_PROGRESS is not a CaseStatus. Expect 422 with the
    # list of valid values, not 500.
    Write-Host "       (next call is a negative test: 422 is the pass condition)" -ForegroundColor DarkGray
    Invoke-Api PATCH "/api/v1/cases/$caseId/status"   -Headers $auth -Body @{ status = "IN_PROGRESS" } | Out-Null
    Invoke-Api PATCH "/api/v1/cases/$caseId/status"   -Headers $auth -Body @{ status = "RESOLVED"; resolution = "FRAUD" } | Out-Null
    Invoke-Api GET   "/api/v1/cases/$caseId/history"  -Headers $auth | Out-Null
}

# --------------------------------------------------------------------------- 8
Write-Host "`n=== 8. BEHAVIORAL ===" -ForegroundColor Cyan
$customer = "CUST-SWEEP-001"
if ($txnPage.content.Count -gt 0 -and $txnPage.content[0].userId) { $customer = $txnPage.content[0].userId }
Invoke-Api GET  "/api/v1/velocity/$customer"          -Headers $auth | Out-Null
Invoke-Api POST "/api/v1/profiles/$customer/analyze"  -Headers $auth | Out-Null
Invoke-Api GET  "/api/v1/profiles/$customer"          -Headers $auth | Out-Null
$devices = Invoke-Api GET "/api/v1/devices/$customer" -Headers $auth
if ($devices -and $devices.Count -gt 0) {
    Invoke-Api POST "/api/v1/devices/$($devices[0].id)/blacklist" -Headers $auth | Out-Null
}

# --------------------------------------------------------------------------- 9
Write-Host "`n=== 9. AI AGENTS ===" -ForegroundColor Cyan
Invoke-Api POST "/api/v1/patterns/analyze" -Headers $auth | Out-Null
Invoke-Api GET  "/api/v1/patterns"         -Headers $auth | Out-Null
if ($alertId) { Invoke-Api GET "/api/v1/explanations/alert/$alertId" -Headers $auth | Out-Null }
if ($caseId) {
    Invoke-Api POST "/api/v1/cases/$caseId/investigate"  -Headers $auth | Out-Null
    Invoke-Api GET  "/api/v1/cases/$caseId/investigation" -Headers $auth | Out-Null
    Invoke-Api POST "/api/v1/cases/$caseId/recommend"    -Headers $auth | Out-Null
    Invoke-Api GET  "/api/v1/cases/$caseId/summary"      -Headers $auth | Out-Null
}

# -------------------------------------------------------------------------- 10
Write-Host "`n=== 10. EVENT STORE (ADMIN ONLY) ===" -ForegroundColor Cyan
if ($txnId) { Invoke-Api GET "/api/v1/events/$txnId" -Headers $auth | Out-Null }

# -------------------------------------------------------------------------- 11
Write-Host "`n=== 11. TOKEN REFRESH ===" -ForegroundColor Cyan
Invoke-Api POST "/api/v1/auth/refresh" @{ refreshToken = $login.refreshToken } | Out-Null

# --------------------------------------------------------------------------- summary
Write-Host "`n=== SUMMARY ===" -ForegroundColor Cyan
$script:Results | Format-Table -AutoSize
$ok    = ($script:Results | Where-Object { $_.Status -eq "OK" }).Count
$fail  = ($script:Results | Where-Object { $_.Status -ne "OK" }).Count
$empty = ($script:Results | Where-Object { $_.Status -eq "OK" -and $_.Rows -eq 0 }).Count
Write-Host "PASS: $ok    FAIL: $fail    200-but-empty: $empty" -ForegroundColor Yellow
if ($fail -gt 0) {
    Write-Host "`nFailures:" -ForegroundColor Red
    $script:Results | Where-Object { $_.Status -ne "OK" } | Format-Table -AutoSize
}
