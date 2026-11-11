# Generates the sample CSV files in data\.
#
# Deterministic: the same seed always produces the same files, so a demo looks
# identical every time and expected results in data\README.md stay true.
#
#   powershell -ExecutionPolicy Bypass -File scripts\generate-sample-data.ps1

param(
    [string]$OutDir = (Join-Path (Split-Path $PSScriptRoot -Parent) "data"),
    # Anchor date. Every timestamp is generated before this, so nothing is ever
    # rejected as future-dated.
    [datetime]$AnchorUtc = [datetime]::new(2026, 10, 1, 0, 0, 0, [DateTimeKind]::Utc),
    [int]$Seed = 20261003
)

$ErrorActionPreference = "Stop"
New-Item -ItemType Directory -Force $OutDir | Out-Null
$rng = [System.Random]::new($Seed)

$HEADER = "transactionExternalId,userId,merchantId,amount,currency,country,deviceId,ipAddress,timestamp"

# --- customer population -----------------------------------------------------
# homeCountry drives the geographic baseline; avgSpend drives AMOUNT_DEVIATION.
$CUSTOMERS = @(
    @{ id = "CUST_10045"; country = "GB"; ccy = "GBP"; device = "dev_gb_4471"; avg =  45 }
    @{ id = "CUST_10046"; country = "GB"; ccy = "GBP"; device = "dev_gb_8820"; avg =  80 }
    @{ id = "CUST_10047"; country = "US"; ccy = "USD"; device = "dev_us_1193"; avg = 120 }
    @{ id = "CUST_10048"; country = "US"; ccy = "USD"; device = "dev_us_6627"; avg =  35 }
    @{ id = "CUST_10049"; country = "DE"; ccy = "EUR"; device = "dev_de_3310"; avg =  95 }
    @{ id = "CUST_10050"; country = "FR"; ccy = "EUR"; device = "dev_fr_7742"; avg =  60 }
    @{ id = "CUST_10051"; country = "AE"; ccy = "AED"; device = "dev_ae_2208"; avg = 220 }
    @{ id = "CUST_10052"; country = "SA"; ccy = "SAR"; device = "dev_sa_9915"; avg =  28 }
    @{ id = "CUST_10053"; country = "NL"; ccy = "EUR"; device = "dev_nl_5503"; avg =  70 }
    @{ id = "CUST_10054"; country = "ES"; ccy = "EUR"; device = "dev_es_1176"; avg =  52 }
    @{ id = "CUST_10055"; country = "US"; ccy = "USD"; device = "dev_us_4408"; avg = 150 }
    @{ id = "CUST_10056"; country = "GB"; ccy = "GBP"; device = "dev_gb_6691"; avg =  40 }
)

$MERCHANTS = @(
    "AMZN_RETAIL_01", "UBER_RIDES_02", "NETFLIX_SUB_03", "SHELL_FUEL_04",
    "TESCO_GROCERY_05", "APPLE_STORE_06", "BOOKING_TRAVEL_07", "STEAM_GAMES_08",
    "IKEA_HOME_09", "ZARA_FASHION_10", "SPOTIFY_SUB_11", "DELIVEROO_FOOD_12"
)

# Recognised by BlacklistedMerchantRule (contribution 95).
$BLACKLISTED = @(
    "MERCHANT_FRAUD_001", "MERCHANT_FRAUD_002", "MERCHANT_SUSPICIOUS_003",
    "MERCHANT_BLOCKED_004", "MERCHANT_RISK_005"
)

# Recognised by GeographicAnomalyRule as elevated risk (contribution 85 vs 70).
$HIGH_RISK_COUNTRIES = @("NG", "RU", "VN", "UA", "ID", "PK")

function New-Row {
    param($ExternalId, $UserId, $MerchantId, $Amount, $Currency, $Country, $DeviceId, $Ip, [datetime]$Ts)
    $stamp = $Ts.ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ss") + "Z"
    $money = [math]::Round($Amount, 2).ToString("0.00", [System.Globalization.CultureInfo]::InvariantCulture)
    "$ExternalId,$UserId,$MerchantId,$money,$Currency,$Country,$DeviceId,$Ip,$stamp"
}

function Get-Ip { param($Prefix = $null)
    if ($Prefix) { return "$Prefix.$($rng.Next(1,254))" }
    "$($rng.Next(1,223)).$($rng.Next(0,256)).$($rng.Next(0,256)).$($rng.Next(1,254))"
}

function Get-Amount { param([double]$Mean, [double]$Spread = 0.6)
    # Log-normal-ish: most values near the mean, occasional larger ones, never <= 0.
    $factor = 1.0 + ($rng.NextDouble() - 0.45) * 2 * $Spread
    [math]::Max(1.00, [math]::Round($Mean * $factor, 2))
}

function Save-Csv { param($Name, [string[]]$Rows)
    $path = Join-Path $OutDir $Name
    $content = (@($HEADER) + $Rows) -join "`r`n"
    [System.IO.File]::WriteAllText($path, $content + "`r`n", (New-Object System.Text.UTF8Encoding($false)))
    Write-Host ("  {0,-34} {1,4} rows" -f $Name, $Rows.Count) -ForegroundColor Green
}

# =============================================================================
# 1. Baseline. Ordinary spending, home country, known device, business hours.
# =============================================================================
$rows = @()
$n = 0
foreach ($c in $CUSTOMERS) {
    for ($i = 0; $i -lt 10; $i++) {
        $day = $rng.Next(2, 22)                       # 21 days of history
        $hour = $rng.Next(9, 20)                      # business hours only
        $ts = $AnchorUtc.AddDays(-$day).AddHours($hour - $AnchorUtc.Hour).AddMinutes($rng.Next(0, 60))
        $rows += New-Row "BASE-$('{0:D5}' -f $n)" $c.id $MERCHANTS[$rng.Next(0, $MERCHANTS.Count)] `
                         (Get-Amount $c.avg) $c.ccy $c.country $c.device (Get-Ip) $ts
        $n++
    }
}
Save-Csv "01_baseline_clean.csv" ($rows | Sort-Object { $_.Split(',')[8] })

# =============================================================================
# 2. Card testing. Micro-authorisations, one new device, minutes apart.
#    Triggers RAPID_TRANSACTION (>= 5 in 10 minutes) and the micro-amount band.
# =============================================================================
$rows = @()
$n = 0
$victims = @($CUSTOMERS[2], $CUSTOMERS[5], $CUSTOMERS[9])
$burstSizes = @(12, 11, 11)
for ($v = 0; $v -lt $victims.Count; $v++) {
    $c = $victims[$v]
    $device = "dev_bot_net_$($v + 1)"
    $ipPrefix = @("185.220.101", "45.154.98", "193.189.100")[$v]
    $start = $AnchorUtc.AddDays(-(3 + $v)).AddHours(2).AddMinutes($rng.Next(0, 50))
    for ($i = 0; $i -lt $burstSizes[$v]; $i++) {
        $ts = $start.AddSeconds(38 * $i)              # ~12 in 8 minutes
        $amount = [math]::Round(0.80 + ($rng.NextDouble() * 3.2), 2)
        $rows += New-Row "CARDTEST-$('{0:D4}' -f $n)" $c.id $MERCHANTS[$i % $MERCHANTS.Count] `
                         $amount $c.ccy $c.country $device (Get-Ip $ipPrefix) $ts
        $n++
    }
}
Save-Csv "02_card_testing_attack.csv" $rows

# =============================================================================
# 3. Account takeover. A quiet customer, then a large foreign off-hours purchase
#    from a device never seen before. Triggers AMOUNT_DEVIATION + GEOGRAPHIC +
#    OFF_HOURS + HIGH_AMOUNT, and usually BLACKLISTED_MERCHANT.
# =============================================================================
$rows = @()
$n = 0
$victims = @($CUSTOMERS[7], $CUSTOMERS[3], $CUSTOMERS[0], $CUSTOMERS[11])
for ($v = 0; $v -lt $victims.Count; $v++) {
    $c = $victims[$v]
    # A few more baseline rows so the deviation ratio is computed from real history.
    for ($i = 0; $i -lt 3; $i++) {
        $ts = $AnchorUtc.AddDays(-(10 - $i)).AddHours($rng.Next(10, 18))
        $rows += New-Row "ATO-BASE-$('{0:D4}' -f $n)" $c.id $MERCHANTS[$rng.Next(0, $MERCHANTS.Count)] `
                         (Get-Amount $c.avg 0.2) $c.ccy $c.country $c.device (Get-Ip) $ts
        $n++
    }
    # The takeover itself: 60-400x this customer's own average.
    $multiple = $rng.Next(60, 400)
    $ts = $AnchorUtc.AddDays(-(2 + $v)).AddHours($rng.Next(0, 5)).AddMinutes($rng.Next(0, 60))
    $rows += New-Row "ATO-HIT-$('{0:D4}' -f $n)" $c.id $BLACKLISTED[$rng.Next(0, $BLACKLISTED.Count)] `
                     ($c.avg * $multiple) $c.ccy $HIGH_RISK_COUNTRIES[$rng.Next(0, $HIGH_RISK_COUNTRIES.Count)] `
                     "dev_takeover_$($v + 1)" (Get-Ip "45.133.1") $ts
    $n++
    # Immediate follow-up drain, same session.
    $rows += New-Row "ATO-DRAIN-$('{0:D4}' -f $n)" $c.id $BLACKLISTED[$rng.Next(0, $BLACKLISTED.Count)] `
                     ($c.avg * [math]::Max(20, $multiple / 2)) $c.ccy $HIGH_RISK_COUNTRIES[$rng.Next(0, $HIGH_RISK_COUNTRIES.Count)] `
                     "dev_takeover_$($v + 1)" (Get-Ip "45.133.1") $ts.AddMinutes(7)
    $n++
}
Save-Csv "03_account_takeover.csv" ($rows | Sort-Object { $_.Split(',')[8] })

# =============================================================================
# 4. Impossible travel. Same card, two countries, a gap no aircraft can close.
#    The rule allows 900 km/h plus 2 hours of overhead, so these must be tight.
# =============================================================================
$rows = @()
$n = 0
$pairs = @(
    @{ from = "GB"; to = "ID"; minutes =  40 },
    @{ from = "US"; to = "RU"; minutes =  55 },
    @{ from = "DE"; to = "BR"; minutes =  35 },
    @{ from = "AE"; to = "US"; minutes =  70 },
    @{ from = "FR"; to = "VN"; minutes =  45 },
    @{ from = "NL"; to = "AU"; minutes =  60 },
    @{ from = "ES"; to = "JP"; minutes =  50 },
    @{ from = "SA"; to = "CA"; minutes =  65 },
    @{ from = "GB"; to = "NG"; minutes =  30 }
)
for ($p = 0; $p -lt $pairs.Count; $p++) {
    $c = $CUSTOMERS[$p]
    $pair = $pairs[$p]
    $first = $AnchorUtc.AddDays(-(4 + ($p % 6))).AddHours($rng.Next(8, 16)).AddMinutes($rng.Next(0, 60))

    # Leg 1: normal purchase at home, known device.
    $rows += New-Row "TRAVEL-$('{0:D4}' -f $n)-A" $c.id $MERCHANTS[$rng.Next(0, $MERCHANTS.Count)] `
                     (Get-Amount $c.avg 0.3) $c.ccy $pair.from $c.device (Get-Ip) $first
    $n++
    # Leg 2: the same card, far away, minutes later, on a device never seen.
    $rows += New-Row "TRAVEL-$('{0:D4}' -f $n)-B" $c.id $BLACKLISTED[$rng.Next(0, $BLACKLISTED.Count)] `
                     (Get-Amount ($c.avg * 40) 0.4) $c.ccy $pair.to "dev_clone_$($p + 1)" (Get-Ip "103.28.148") `
                     $first.AddMinutes($pair.minutes)
    $n++
}
Save-Csv "04_impossible_travel.csv" ($rows | Sort-Object { $_.Split(',')[8] })

# =============================================================================
# 5. Mixed production day. Roughly 88% legitimate. Use for dashboards, the trend
#    chart and measuring how much noise the engine produces on normal traffic.
# =============================================================================
$rows = @()
$n = 0
for ($i = 0; $i -lt 160; $i++) {
    $c = $CUSTOMERS[$rng.Next(0, $CUSTOMERS.Count)]
    $roll = $rng.Next(0, 100)
    $day = $rng.Next(1, 8)

    if ($roll -lt 88) {
        # Legitimate.
        $ts = $AnchorUtc.AddDays(-$day).AddHours($rng.Next(8, 22)).AddMinutes($rng.Next(0, 60))
        $rows += New-Row "MIX-$('{0:D5}' -f $n)" $c.id $MERCHANTS[$rng.Next(0, $MERCHANTS.Count)] `
                         (Get-Amount $c.avg) $c.ccy $c.country $c.device (Get-Ip) $ts
    }
    elseif ($roll -lt 93) {
        # High-value foreign purchase, new device.
        $ts = $AnchorUtc.AddDays(-$day).AddHours($rng.Next(6, 23)).AddMinutes($rng.Next(0, 60))
        $rows += New-Row "MIX-$('{0:D5}' -f $n)" $c.id $MERCHANTS[$rng.Next(0, $MERCHANTS.Count)] `
                         (Get-Amount 7400 0.4) $c.ccy $HIGH_RISK_COUNTRIES[$rng.Next(0, $HIGH_RISK_COUNTRIES.Count)] `
                         "dev_unknown_$($rng.Next(1000, 9999))" (Get-Ip) $ts
    }
    elseif ($roll -lt 97) {
        # Blacklisted merchant, off-hours.
        $ts = $AnchorUtc.AddDays(-$day).AddHours($rng.Next(0, 5)).AddMinutes($rng.Next(0, 60))
        $rows += New-Row "MIX-$('{0:D5}' -f $n)" $c.id $BLACKLISTED[$rng.Next(0, $BLACKLISTED.Count)] `
                         (Get-Amount 1900 0.7) $c.ccy $c.country $c.device (Get-Ip) $ts
    }
    else {
        # Critical: very large, foreign, off-hours, blacklisted, new device.
        $ts = $AnchorUtc.AddDays(-$day).AddHours($rng.Next(0, 4)).AddMinutes($rng.Next(0, 60))
        $rows += New-Row "MIX-$('{0:D5}' -f $n)" $c.id $BLACKLISTED[$rng.Next(0, $BLACKLISTED.Count)] `
                         (Get-Amount 21000 0.5) $c.ccy $HIGH_RISK_COUNTRIES[$rng.Next(0, $HIGH_RISK_COUNTRIES.Count)] `
                         "dev_unknown_$($rng.Next(1000, 9999))" (Get-Ip "45.133.1") $ts
    }
    $n++
}
Save-Csv "05_mixed_production_day.csv" ($rows | Sort-Object { $_.Split(',')[8] })

# =============================================================================
# 6. Rejection cases. Every row is invalid in a different, specific way. Upload
#    this to prove the importer reports per-row errors instead of failing whole.
# =============================================================================
$bad = @(
    "BAD-001,CUST_10045,AMZN_RETAIL_01,-50.00,GBP,GB,dev_gb_4471,81.2.69.1,2026-09-20T10:00:00Z"
    "BAD-002,CUST_10045,AMZN_RETAIL_01,25.00,XYZ,GB,dev_gb_4471,81.2.69.2,2026-09-20T10:01:00Z"
    "BAD-003,CUST_10045,AMZN_RETAIL_01,25.00,GBP,GB,dev_gb_4471,81.2.69.3,not-a-date"
    "BAD-004,CUST_10045,AMZN_RETAIL_01,abc,GBP,GB,dev_gb_4471,81.2.69.4,2026-09-20T10:03:00Z"
    "BAD-005,,AMZN_RETAIL_01,25.00,GBP,GB,dev_gb_4471,81.2.69.5,2026-09-20T10:04:00Z"
    "BAD-006,CUST_10045,AMZN_RETAIL_01,25.00,GBP,GB,dev_gb_4471,81.2.69.6,2099-01-01T10:00:00Z"
    "BAD-007,CUST_10045,AMZN_RETAIL_01,0,GBP,GB,dev_gb_4471,81.2.69.7,2026-09-20T10:06:00Z"
    "GOOD-008,CUST_10045,AMZN_RETAIL_01,31.50,GBP,GB,dev_gb_4471,81.2.69.8,2026-09-20T10:07:00Z"
)
Save-Csv "06_rejection_cases.csv" $bad

Write-Host "`nWritten to $OutDir" -ForegroundColor Cyan
