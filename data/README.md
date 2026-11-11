# Sentinel sample data

Five CSV files, each one a scenario you can upload and watch the platform react to.
They are plain UTF-8, comma separated, with the header row the importer expects.

## Column contract

| Column                  | Required | Format                                   | Notes |
|-------------------------|----------|------------------------------------------|-------|
| `transactionExternalId` | yes      | any string, unique per organization      | Used as the idempotency key. Re-uploading the same file is a no-op, not a duplicate load. |
| `userId`                | yes      | any string                               | Your customer identifier. Scoped per organization. |
| `merchantId`            | yes      | any string                               | Matched against the merchant blacklist. |
| `amount`                | yes      | decimal, positive                        | Currency symbols and thousands separators are stripped. |
| `currency`              | yes      | ISO 4217, 3 letters                      | Validated against `java.util.Currency`. |
| `country`               | no       | ISO 3166-1 alpha-2, 2 letters            | Drives geographic and impossible-travel detection. |
| `deviceId`              | no       | any string                               | Builds the device trust graph. |
| `ipAddress`             | no       | IPv4                                     | Stored for investigation context. |
| `timestamp`             | yes      | ISO-8601, e.g. `2026-09-28T14:05:00Z`    | Must not be in the future. Also accepts `yyyy-MM-dd HH:mm:ss`, `dd/MM/yyyy HH:mm:ss` and `MM/dd/yyyy HH:mm:ss`, interpreted as UTC. |

## Upload

```bash
curl -X POST "http://localhost:8081/api/v1/transactions/upload?analyze=true" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@data/01_baseline_clean.csv"
```

`analyze=true` is the default and runs every accepted row through the live fraud
pipeline, producing risk scores, alerts and explanations. Pass `analyze=false` to
load history without scoring it — useful when you want to establish a baseline
first and only score what comes after.

**Upload order matters.** Behavioural rules read the history that precedes each
transaction, so load `01_baseline_clean.csv` before anything else. Loading an
attack file against an empty database still produces alerts, but the per-customer
deviation and geographic rules have no baseline to compare against and the scores
come out lower than they should.

## The files

| # | File | Rows | What it demonstrates |
|---|------|------|----------------------|
| 1 | `01_baseline_clean.csv` | 120 | Ordinary spending for 12 customers over three weeks. Establishes the behavioural baselines every other file is judged against. Expect almost everything APPROVED and a flat fraud trend. |
| 2 | `02_card_testing_attack.csv` | 34 | Three card-testing bursts: many micro-authorisations on one card in minutes from a single new device. Triggers `RAPID_TRANSACTION`. |
| 3 | `03_account_takeover.csv` | 22 | Four takeovers. A quiet low-value customer suddenly transacts at 50-500× their own average, from a new device, a new country, at 03:00. Triggers `AMOUNT_DEVIATION`, `GEOGRAPHIC_ANOMALY`, `OFF_HOURS`, `HIGH_AMOUNT`. |
| 4 | `04_impossible_travel.csv` | 18 | Nine card-present pairs where the same card appears in two countries faster than any aircraft allows. Triggers `IMPOSSIBLE_TRAVEL` at contribution 97 — the highest single signal in the engine. |
| 5 | `05_mixed_production_day.csv` | 160 | A realistic trading day: roughly 88% legitimate, the rest a mix of every attack type. Use this one for dashboards, trend charts and measuring the false-positive rate. |

## Expected results

After loading files 1-5 in order against a clean organization:

| Metric | Expected |
|--------|----------|
| Transactions | 354 |
| Flagged | 60-75 |
| Alerts | 60-75 |
| CRITICAL risk scores | 25+ |
| Distinct countries | 14 |

Numbers vary by a few rows because device trust and customer baselines change as
each file lands. The shape is what matters: a clean baseline, then three sharp
spikes, then a realistic mixed day.

## Resetting

```sql
-- Wipe transactional data for one organization, keep users and settings.
DELETE FROM case_history     WHERE case_id IN (SELECT id FROM fraud_cases WHERE organization_id = :org);
DELETE FROM case_comments    WHERE case_id IN (SELECT id FROM fraud_cases WHERE organization_id = :org);
DELETE FROM fraud_cases      WHERE organization_id = :org;
DELETE FROM explanations     WHERE alert_id IN (SELECT id FROM fraud_alerts WHERE organization_id = :org);
DELETE FROM fraud_alerts     WHERE organization_id = :org;
DELETE FROM risk_scores      WHERE transaction_id IN (SELECT id FROM transactions WHERE organization_id = :org);
DELETE FROM idempotency_keys WHERE transaction_id IN (SELECT id FROM transactions WHERE organization_id = :org);
DELETE FROM transactions     WHERE organization_id = :org;
```

Or use `scripts/reset-demo.ps1`, which does the same thing and then re-seeds.
