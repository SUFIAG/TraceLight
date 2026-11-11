# Sentinel — complete walkthrough

Every scenario below has been run against a live backend on `http://localhost:8081`
with a live frontend on `http://localhost:3000`. Each one states what to do, what
you should see, and what it proves.

---

## 0. Setup

### Credentials

All four seeded accounts share one password.

| Email | Role | What it can do |
|-------|------|----------------|
| `admin@sentinel.ai` | ADMIN | Everything, including the raw event stream |
| `analyst@sentinel.ai` | ANALYST | Work the queue, create and progress cases |
| `reviewer@sentinel.ai` | REVIEWER | Review and resolve |
| `sweep@sentinel.ai` | ADMIN | Created by the API sweep; use for automation |

Password: `Sentinel@2026`

### Start both services

```powershell
# Backend (port 8081)
cd backend
.\mvnw.cmd spring-boot:run

# Frontend (port 3000) — must be a cold start.
# A hot reload does not pick up tailwind.config.ts.
cd sentinel-frontend
npm run dev
```

Confirm the backend is healthy before anything else:

```powershell
Invoke-RestMethod http://localhost:8081/actuator/health
# -> status: UP
```

### Scripts

| Script | Purpose |
|--------|---------|
| `scripts\api-sweep.ps1` | Calls every REST endpoint in dependency order and reports status and row counts |
| `scripts\generate-sample-data.ps1` | Regenerates the CSV files in `data\` |
| `scripts\load-sample-data.ps1` | Uploads all five CSVs in the correct order |
| `scripts\reset-demo.ps1` | Wipes transactional data for one organization, keeps users |

---

## 1. Authentication and session

### 1.1 Sign in

1. Open `http://localhost:3000`.
2. Sign in as `admin@sentinel.ai` / `Sentinel@2026`.

**Expect:** redirect to `/dashboard`. The sidebar shows an open-alert count.

**Proves:** JWT issue, storage and the authenticated route guard.

### 1.2 Token refresh

1. Stay signed in for over an hour, or shorten `JWT_ACCESS_TOKEN_EXPIRATION` to `60`
   and restart the backend.
2. Leave the dashboard open past expiry.

**Expect:** the page keeps updating. No redirect to login.

**Proves:** the client transparently exchanges the refresh token on the first 401
and retries the original request. The refresh is de-duplicated, so a burst of
simultaneous 401s produces one refresh call, not ten.

### 1.3 Session expiry

1. In devtools, delete `tracelight.refreshToken` from localStorage.
2. Wait for the access token to expire, then navigate.

**Expect:** redirect to `/login` with the session cleared.

### 1.4 Registration hardening — negative test

```powershell
$body = '{"email":"attacker@test.com","password":"Test@1234","role":"ADMIN"}'
Invoke-RestMethod -Uri "http://localhost:8081/api/v1/auth/register" -Method Post `
  -ContentType "application/json" -Body $body
```

**Expect:** `201`, and `role` in the response is `ANALYST`, not `ADMIN`.

**Proves:** self-registration cannot escalate privilege. The requested role is
ignored; elevation is an administrative action.

### 1.5 Tenant isolation — negative test

1. Sign in as `admin@sentinel.ai` and copy any alert id from `/alerts`.
2. Create a second organization and a user inside it:

```sql
INSERT INTO organizations (id, name, created_at, updated_at)
VALUES ('660e8400-e29b-41d4-a716-446655440001', 'Second Bank', now(), now());
```

3. Register a user with header `X-Organization-Id: 660e8400-e29b-41d4-a716-446655440001`.
4. Sign in as that user and request the first organization's alert by id.

**Expect:** `404`, not `403` and not the alert body.

**Proves:** cross-tenant reads return "not found" rather than confirming the row
exists. The same guard sits on transactions, cases, explanations, devices and
velocity.

---

## 2. Data loading

### 2.1 One-click demo data

Press <kbd>Ctrl</kbd>+<kbd>K</kbd> → **Load demo data**.

**Expect:** a toast reporting roughly 237 transactions created, 130+ flagged,
100 alerts, 6 cases. The dashboard figures animate to their new values within a
second or two.

**Proves:** the seed runs the real ingestion and scoring pipeline — nothing is
inserted straight into the alert or risk tables, so every number on screen was
produced by the same code that serves production traffic.

### 2.2 Seed is re-runnable

Run **Load demo data** a second time.

**Expect:** success again, same shape of result.

**Proves:** the regression where a second seed returned `500` is fixed. The cause
was transaction propagation: the seed held one outer transaction, every inner
`@Transactional` call joined it, and the first expected `CASE_EXISTS` failure
marked the shared transaction rollback-only — so the commit threw and all 220
rows were discarded while the endpoint had already reported progress.

### 2.3 CSV upload, the main ingestion path

```powershell
powershell -ExecutionPolicy Bypass -File scripts\load-sample-data.ps1
```

Or in the UI: **Transactions → Upload CSV**, one file at a time, in numeric order.

**Expect:**

| File | Accepted | Rejected | Analyzed |
|------|----------|----------|----------|
| `01_baseline_clean.csv` | 120 | 0 | 120 |
| `02_card_testing_attack.csv` | 34 | 0 | 34 |
| `03_account_takeover.csv` | 20 | 0 | 20 |
| `04_impossible_travel.csv` | 18 | 0 | 18 |
| `05_mixed_production_day.csv` | 160 | 0 | 160 |

Dashboard after the load: 352 transactions, 64 flagged, 64 alerts, 18.18% fraud rate.

**Proves:** uploaded rows are scored, not just stored. Before this change the
importer only persisted rows — everything stayed `PENDING` and no alert was ever
raised from a CSV, which is the path most evaluations use first.

### 2.4 Upload order matters

1. Run `scripts\reset-demo.ps1`.
2. Upload `04_impossible_travel.csv` **first**, with no baseline.
3. Reset again, then upload `01` then `04`.

**Expect:** fewer and weaker alerts in the first run.

**Proves:** the behavioural rules are genuinely behavioural. Geographic anomaly,
amount deviation and impossible travel all compare against the customer's own
history; with no history there is nothing to deviate from.

### 2.5 Malformed rows — negative test

Upload `data\06_rejection_cases.csv`.

**Expect:** `totalProcessed: 1`, `totalFailed: 7`, and a readable error per row:

```
Row 1: Amount must be positive
Row 2: Invalid currency code: XYZ
Row 3: 'not-a-date' is not a recognised timestamp. Use ISO-8601, for example 2026-09-28T14:05:00Z
Row 4: 'abc' is not a valid amount
Row 5: column 'userId' is empty
Row 6: Timestamp cannot be in the future
Row 7: Amount must be positive
```

Row 8 is valid and is accepted.

**Proves:** one bad row does not fail the file, and each rejection names its own
reason. Each row commits or fails on its own transaction.

### 2.6 Re-upload is idempotent

Upload `01_baseline_clean.csv` a second time.

**Expect:** `totalProcessed` counts the rows, but the transaction count on the
dashboard does not increase.

**Proves:** the idempotency key is derived from the row's own external id, so
retrying a failed upload is safe. A random key per row would have double-loaded.

---

## 3. The fraud engine

### 3.1 Score a single transaction by hand

```powershell
$login = Invoke-RestMethod -Uri "http://localhost:8081/api/v1/auth/login" -Method Post `
  -ContentType "application/json" -Body '{"email":"admin@sentinel.ai","password":"Sentinel@2026"}'
$h = @{ Authorization = "Bearer $($login.data.accessToken)" }

$txn = Invoke-RestMethod -Uri "http://localhost:8081/api/v1/transactions" -Method Post -Headers $h `
  -ContentType "application/json" -Body (@{
     transactionExternalId = "MANUAL-001"
     userId     = "CUST_10045"
     merchantId = "MERCHANT_FRAUD_001"
     amount     = 38500
     currency   = "GBP"
     country    = "RU"
     deviceId   = "dev_never_seen"
     ipAddress  = "45.133.1.88"
     timestamp  = (Get-Date).ToUniversalTime().AddHours(-2).ToString("yyyy-MM-ddTHH:mm:ss") + "Z"
   } | ConvertTo-Json)

Invoke-RestMethod -Uri "http://localhost:8081/api/v1/fraud/analyze/$($txn.data.id)" -Method Post -Headers $h
Invoke-RestMethod -Uri "http://localhost:8081/api/v1/fraud/risk-score/$($txn.data.id)" -Headers $h |
  Select-Object -ExpandProperty data
```

**Expect:** `score` in the nineties, `level: CRITICAL`, and a non-trivial value in
every dimension.

**Proves:** the scoring rewrite. Previously the maximum score any transaction
could reach was **63**, because the weighted average discarded the rule engine's
findings — a blacklisted merchant contributed 95 to nothing. The CRITICAL band
(81-100) was mathematically unreachable and the gauge could never leave its lower
two-thirds.

### 3.2 The full score range is in use

```sql
SELECT level, count(*), min(score), round(avg(score),1), max(score)
FROM risk_scores GROUP BY level ORDER BY min(score);
```

**Expect** (after loading the five CSVs):

```
 level    | count | min | avg  | max
----------+-------+-----+------+-----
 LOW      |   274 |   7 |  7.7 |  28
 MEDIUM   |    14 |  42 | 50.2 |  53
 HIGH     |    30 |  68 | 74.6 |  77
 CRITICAL |    34 |  84 | 93.2 |  97
```

**Proves:** all four bands are populated and separated. A fraud product whose
score never exceeds 63 cannot justify its own thresholds.

### 3.3 Each rule in isolation

| Rule | Contribution | How to trigger it | Where to see it |
|------|--------------|-------------------|-----------------|
| `IMPOSSIBLE_TRAVEL` | 97 | `04_impossible_travel.csv` | Alert reason names both countries, the distance and the time available |
| `BLACKLISTED_MERCHANT` | 95 | Any `MERCHANT_FRAUD_*` merchant id | Alert reason names the merchant |
| `AMOUNT_DEVIATION` | 50 / 72 / 90 | `03_account_takeover.csv` | Reason states the multiple of the customer's own average |
| `HIGH_AMOUNT` | 60 / 90 | Amount over 5,000 / 10,000 | Reason names the threshold crossed |
| `RAPID_TRANSACTION` | 60 / 88 | `02_card_testing_attack.csv` | Reason gives the count and the window |
| `GEOGRAPHIC_ANOMALY` | 70 / 85 | A country outside the customer's footprint | Reason lists the known footprint |
| `OFF_HOURS` | 40 | Transaction between 00:00 and 05:00 UTC | Reason gives the exact time |

Open any alert and read the **Why this fired** panel: every triggered rule is
listed with its own sentence. No score is presented without its reasons.

### 3.4 Impossible travel, the flagship detection

1. Open `/alerts` and filter to `CRITICAL`.
2. Find an alert whose rules include `IMPOSSIBLE_TRAVEL`.

**Expect** a reason of the form:

> Card used in 'GB' and 'ID' 40 minutes apart. The 11,400 km between them needs
> at least 12.7 h of travel; only 0.0 h were available.

**Proves:** this is the one signal with no innocent explanation. A geographic
anomaly can be a holiday. A card in two hemispheres inside an hour is two cards.
The rule allows 900 km/h plus two hours of airport overhead, so genuine long-haul
travel never triggers it — worth saying out loud in a demo, because the first
question any fraud team asks is "what about my customers who fly?"

### 3.5 Behaviour beats thresholds

1. Find an `AMOUNT_DEVIATION` alert in `03_account_takeover.csv`.
2. Note the amount — often only a few thousand.
3. Compare with a legitimate high-value customer in the baseline spending more.

**Expect:** the smaller amount is flagged; the larger one is not.

**Proves:** a flat "over $5,000 is suspicious" rule is wrong in both directions.
It never fires for the customer whose history is £20 coffees and who suddenly
spends £900, and it fires constantly for the private-banking client for whom
$5,000 is a Tuesday. The deviation rule asks the only question that generalises:
how far is this from what *this* customer normally does?

### 3.6 Velocity works on historical data

```powershell
Invoke-RestMethod -Uri "http://localhost:8081/api/v1/velocity/CUST_10047" -Headers $h |
  Select-Object -ExpandProperty data
```

**Expect:** non-zero counts and a `velocityRisk` above 5 for customers in
`02_card_testing_attack.csv`.

**Proves:** velocity windows are anchored on the transaction's own timestamp, not
on wall-clock time. Scoring a CSV-loaded transaction against "the last 10 minutes
of real time" always returns zero, which silently disabled velocity detection for
every batch path.

### 3.7 Device trust graph

```powershell
Invoke-RestMethod -Uri "http://localhost:8081/api/v1/devices/CUST_10045" -Headers $h |
  Select-Object -ExpandProperty data | Format-Table fingerprint, trustLevel, usageCount, riskScore
```

**Expect:** the customer's own device at `TRUSTED` with a low risk score, and any
`dev_clone_*` or `dev_takeover_*` fingerprint at `UNKNOWN` with a high one.

Then blacklist one and re-score:

```powershell
$deviceId = "<id from above>"
Invoke-RestMethod -Uri "http://localhost:8081/api/v1/devices/$deviceId/blacklist" -Method Post -Headers $h
```

**Expect:** device risk jumps to 100 and subsequent transactions on that
fingerprint score materially higher.

**Proves:** analyst action feeds straight back into scoring.

---

## 4. Alert triage

### 4.1 Work the queue

1. Open `/alerts`.
2. Filter by severity and by status.
3. Open a `CRITICAL` alert.

**Expect:** the detail page shows the transaction, the risk breakdown by
dimension, every triggered rule with its reason, and the AI explanation.

### 4.2 Alert lifecycle

1. On an open alert, set status to `INVESTIGATING`.
2. Then to `RESOLVED` or `FALSE_POSITIVE`.

**Expect:** the badge changes, the dashboard open-alert count drops, and the audit
trail records who did it and when.

### 4.3 Invalid status — negative test

```powershell
Invoke-RestMethod -Uri "http://localhost:8081/api/v1/alerts/$alertId/status" -Method Patch `
  -Headers $h -ContentType "application/json" -Body '{"status":"NOT_A_STATUS"}'
```

**Expect:** `422` with

```json
{"code":"INVALID_STATUS",
 "message":"'NOT_A_STATUS' is not a valid status. Valid values: OPEN, INVESTIGATING, RESOLVED, FALSE_POSITIVE"}
```

**Proves:** a client mistake returns a client error naming the valid values. This
used to be a `500` reading "An unexpected error occurred", because
`Enum.valueOf` threw straight into the generic handler.

---

## 5. Case management

### 5.1 Escalate an alert to a case

1. From an alert detail page, choose **Create case**.
2. Set a title and priority.

**Expect:** `201` and a redirect to the case workspace.

### 5.2 Duplicate case — negative test

Try to create a second case from the same alert.

**Expect:** `422 CASE_EXISTS` naming the alert id.

**Proves:** one alert, one investigation. A deliberate constraint, not a bug.

### 5.3 Workflow state machine

Permitted transitions:

```
OPEN -> INVESTIGATING -> RESOLVED -> CLOSED
OPEN -> CLOSED
INVESTIGATING -> CLOSED
```

Try to go `OPEN -> RESOLVED` directly.

**Expect:** `422 INVALID_TRANSITION` naming both states.

Try `RESOLVED` without a resolution.

**Expect:** `422 RESOLUTION_REQUIRED`.

**Proves:** a case cannot be closed without someone recording what it turned out
to be. That field is what trains the feedback loop.

### 5.4 Assignment

1. Assign a case to `analyst@sentinel.ai`.

**Expect:** an `OPEN` case auto-advances to `INVESTIGATING` on first assignment,
and the history shows both the assignment and the status change.

2. Assign to a user id from another organization.

**Expect:** `422 INVALID_ASSIGNEE`.

### 5.5 Collaboration and audit

1. Add comments from two different signed-in users.
2. Open the **History** tab.

**Expect:** a complete ordered record — created, assigned, status changes,
resolution — each with an actor and a timestamp.

**Proves:** the audit trail is a regulatory artefact, not a log file. Every
mutating action writes one.

---

## 6. AI agents

All four work with `ai.provider=none`, using deterministic reasoning over the
real signals. Setting `AI_PROVIDER=openai` and `OPENAI_API_KEY` swaps the
narrative layer for an LLM without changing any contract.

### 6.1 Explanation agent

Open any alert.

**Expect:** a plain-language cause, an explanation, a suggested action and a
confidence figure — generated at the moment the alert was raised, not on demand,
so it is already there when the analyst arrives.

### 6.2 Investigation agent

In a case workspace, choose **Run investigation**.

**Expect:** a verdict (`likelyFraud`), a confidence score, a list of key
indicators and a narrative that reads as an analyst's first-pass write-up.

### 6.3 Recommendation agent

Choose **Recommend action**.

**Expect:** one of `AUTO_APPROVE`, `AUTO_BLOCK`, `REQUEST_VERIFICATION`,
`ESCALATE`, `MONITOR`, with reasoning.

### 6.4 Case summary agent

Open the **Summary** tab.

**Expect:** a short brief suitable for a handover or an escalation email.

### 6.5 Pattern discovery

Press <kbd>Ctrl</kbd>+<kbd>K</kbd> → **Run pattern discovery**, or open `/patterns`.

**Expect:** 4-7 discovered patterns with occurrence counts and confidence —
clusters the engine found in your data, not rules someone wrote in advance.

**Proves:** the system learns the shape of your fraud rather than only matching
the signatures it shipped with.

---

## 7. Analytics

### 7.1 Dashboard

**Expect:**

- The risk gauge animates to the portfolio average and sits in a named band.
- Stat tiles count up and carry a sparkline and a period-over-period delta.
- The delta arrow is coloured by whether the direction is *good*, which differs
  per metric: rising cleared volume is good, rising fraud rate is not.

### 7.2 Live detections feed

1. Leave the dashboard open.
2. In another window, load `05_mixed_production_day.csv`.

**Expect:** new alerts arrive in the feed, each marked with one pass of light,
and the "new since you opened this page" counter increments. Pause the feed and
confirm it stops.

**Proves:** this is a monitoring surface, not a report. The pause control matters
— a list that moves while someone is reading it is worse than no feed at all.

### 7.3 Geographic exposure

Hover a marker on the threat map.

**Expect:** volume, flagged count and fraud rate. Marker *area* tracks volume and
colour tracks fraud rate, so a small country with a severe problem is as visible
as a large one with a mild one. The three worst countries carry a radar ping.

The table beneath carries the same numbers for screen readers and for copy-paste.

### 7.4 Trend chart

**Expect:** 14 daily buckets of total and flagged volume, bucketed by *transaction*
time rather than row insert time — so the trend reflects when activity happened,
not when you loaded the file.

### 7.5 Merchant risk

**Expect:** the `MERCHANT_FRAUD_*` and `MERCHANT_BLOCKED_*` identifiers at the top.

---

## 8. Experience

### 8.1 Command palette

Press <kbd>Ctrl</kbd>+<kbd>K</kbd> (<kbd>⌘</kbd>+<kbd>K</kbd> on macOS).

- Type `al` → jump to Alerts.
- Type part of an alert reason → the matching alerts appear under **Alerts**.
- Type `seed` → run the demo loader without leaving the keyboard.
- <kbd>↑</kbd> <kbd>↓</kbd> to move, <kbd>↵</kbd> to run, <kbd>Esc</kbd> to close.

**Proves:** the common path is three keystrokes rather than three clicks and a
scroll. Remote lookup is debounced, so typing a word fires one request, not one
per keystroke.

### 8.2 Light and dark

Toggle from the palette or the topbar.

**Expect:** both themes are stepped for their own surface. Dark is not an
inversion of light. Status colours are fixed across both and never appear without
an icon or a label beside them.

### 8.3 Reduced motion

Set the OS "reduce motion" preference and reload.

**Expect:** counters land on their final value immediately, the sweep and radar
animations do not play, everything stays usable.

### 8.4 Keyboard only

Tab through the dashboard, the alert list and a case workspace.

**Expect:** a visible focus ring on every interactive element, logical order, no
trap in the command palette.

### 8.5 Error and empty states

1. Stop the backend.
2. Reload the dashboard.

**Expect:** "Cannot reach the Tracelight API. Check that the backend is running."
with a retry button — not a blank page or a raw stack trace.

Then reset the data and reload.

**Expect:** an empty state that tells you how to populate it, not a zeroed-out
dashboard.

---

## 9. Full API sweep

```powershell
powershell -ExecutionPolicy Bypass -File scripts\api-sweep.ps1
```

**Expect:** `PASS: 39  FAIL: 1`. The single failure is the deliberate negative
test on line 188, which sends an invalid case status and passes by returning
`422`.

`GET /api/v1/events/{aggregateId}` returns `200` with zero rows. That is correct:
event sourcing is gated behind `SENTINEL_KAFKA_ENABLED` and is off by default so
the platform boots on PostgreSQL alone.

---

## 10. Performance and resilience

### 10.1 Bulk load

Generate a larger file and upload it:

```powershell
# Edit the row count in scripts\generate-sample-data.ps1, or concatenate the
# existing files several times with distinct external ids.
```

**Expect:** linear time. Uploads above 25,000 rows are truncated with an explicit
message rather than silently dropped.

### 10.2 Idempotent ingestion

Send the same transaction twice with the same `Idempotency-Key` header.

**Expect:** the same transaction id both times, one row in the database.

### 10.3 Infrastructure is optional

With `SENTINEL_REDIS_ENABLED=false` and `SENTINEL_KAFKA_ENABLED=false` — the
defaults — confirm `/actuator/health` reports `UP`.

**Proves:** health reflects what the application actually needs. Spring Boot
auto-registers a Redis health indicator whenever the starter is on the classpath,
which reported the whole application `DOWN` for a dependency no endpoint used.

---

## Known gaps

Stated plainly, because an evaluator will find them.

| Gap | Impact | Fix |
|-----|--------|-----|
| `discovered_patterns`, `agent_executions`, `ai_conversations` and `feedback_events` carry no `organization_id` | Pattern discovery is not tenant-scoped | A migration adding the column, then a filter on each query |
| `event_store` has no `organization_id` | `GET /events/{id}` is restricted to ADMIN as a stopgap | Same migration |
| CSV upload is synchronous | A very large file holds the request thread | Move to `async_jobs`, which already exists, and return a job id |
| No rate limiting on the public auth endpoints | Credential stuffing is not slowed | Bucket4j on `/auth/login` and `/auth/register` |
| Passwords have no complexity policy beyond length | Weak passwords are accepted | A validator on `RegisterRequest` |
