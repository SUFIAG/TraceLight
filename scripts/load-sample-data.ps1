# Uploads the sample CSV files in the order the behavioural rules need.
#
#   powershell -ExecutionPolicy Bypass -File scripts\load-sample-data.ps1

param(
    [string]$BaseUrl  = "http://localhost:8081",
    [string]$Email    = "admin@sentinel.ai",
    [string]$Password = "Sentinel@2026",
    [string]$DataDir  = (Join-Path (Split-Path $PSScriptRoot -Parent) "data"),
    [string[]]$Files  = @(
        "01_baseline_clean.csv",
        "02_card_testing_attack.csv",
        "03_account_takeover.csv",
        "04_impossible_travel.csv",
        "05_mixed_production_day.csv"
    )
)

$ErrorActionPreference = "Stop"

$login = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post -ContentType "application/json" `
            -Body (@{ email = $Email; password = $Password } | ConvertTo-Json) -TimeoutSec 30
$token = $login.data.accessToken
Write-Host "Authenticated as $Email`n" -ForegroundColor DarkGray

foreach ($name in $Files) {
    $path = Join-Path $DataDir $name
    if (-not (Test-Path $path)) { Write-Host "  SKIP $name (not found)" -ForegroundColor Yellow; continue }

    # Windows PowerShell 5.1 has no -Form, so the multipart body is built by hand.
    $boundary = [guid]::NewGuid().ToString()
    $body = "--$boundary`r`n" +
            "Content-Disposition: form-data; name=`"file`"; filename=`"$name`"`r`n" +
            "Content-Type: text/csv`r`n`r`n" +
            [System.IO.File]::ReadAllText($path) +
            "`r`n--$boundary--`r`n"

    try {
        $r = Invoke-RestMethod -Uri "$BaseUrl/api/v1/transactions/upload?analyze=true" -Method Post `
                -Headers @{ Authorization = "Bearer $token" } `
                -ContentType "multipart/form-data; boundary=$boundary" -Body $body -TimeoutSec 600
        $d = $r.data
        Write-Host ("  {0,-32} accepted={1,4}  rejected={2,3}  analyzed={3,4}" -f
                    $name, $d.totalProcessed, $d.totalFailed, $d.totalAnalyzed) -ForegroundColor Green
        if ($d.errors) { $d.errors | Select-Object -First 8 | ForEach-Object { Write-Host "       $_" -ForegroundColor DarkGray } }
    }
    catch {
        $detail = $_.Exception.Message
        if ($_.Exception.Response) {
            try { $detail = (New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())).ReadToEnd() } catch { }
        }
        Write-Host "  FAIL $name" -ForegroundColor Red
        Write-Host "       $detail" -ForegroundColor DarkGray
    }
}

Write-Host "`nDashboard after load:" -ForegroundColor Cyan
$dash = Invoke-RestMethod -Uri "$BaseUrl/api/v1/dashboard" -Headers @{ Authorization = "Bearer $token" } -TimeoutSec 60
$dash.data | Format-List
