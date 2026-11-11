# Tracelight — Business Requirements Document

**Version:** 1.0
**Date:** 3 October 2026
**Status:** Approved for build
**Owner:** Sufyan Abdulghani

---

## 1. The one-paragraph version

Fraud analysts at payment companies spend 20–40 minutes manually reconstructing
the story behind every flagged transaction before they can close it. Tracelight
is the AI fraud analyst that writes the case file for them. It scores
transactions, raises alerts, and then — on one click — investigates the case,
writes the narrative, names the fraud pattern, recommends an action, and files
everything to an immutable audit trail. The analyst reviews and signs instead of
writing from scratch. Thirty minutes becomes three.

---

## 2. The user

**Primary persona — Fraud Analyst "Maya".**
Works a queue at a mid-market payment service provider or digital bank. Handles
40–120 alerts a day. Measured on two numbers: cases closed per day, and false
positive rate. Lives in a spreadsheet and three browser tabs.

**Secondary persona — Head of Fraud / Risk "Daniel".**
Owns the fraud P&L. Needs to prove to the board and to regulators that decisions
are consistent, explainable and auditable. Buys the tool.

**Tertiary persona — Compliance / Audit "Sara".**
Never logs in daily. Logs in when a regulator asks why a specific customer was
blocked on a specific date. Needs an answer in minutes, not a data request.

---

## 3. The problem

Existing fraud platforms stop at the score. They tell Maya *that* a transaction
is risky and *which rule* fired. Everything after that is manual labour:

1. Pull the customer's transaction history.
2. Check the device and whether it is new.
3. Check velocity — how many transactions in the last hour.
4. Check geography against the customer's normal pattern.
5. Decide.
6. Write a justification that will survive an audit.

Steps 1–4 are data gathering a machine should do. Step 6 is writing a machine
should draft. Maya does all six by hand, 40–120 times a day, and the queue never
empties. When she is behind, she rubber-stamps — which is exactly when fraud
gets through and when good customers get blocked.

**The cost of this problem**

| Symptom                         | Business impact                                    |
| ------------------------------- | -------------------------------------------------- |
| 20–40 min average handling time | Analyst headcount scales linearly with volume       |
| Alert backlog                   | Fraud losses on cases reviewed too late             |
| Rubber-stamping under pressure  | False positives → churn of good customers           |
| Inconsistent written rationale  | Regulatory exposure, failed audits                  |

---

## 4. The solution — four core features

### F1. Triage queue
Every alert, ranked by severity, with the signals that fired visible before the
analyst clicks. Filter by status and severity. One click to open.

*Why it matters:* Maya decides what to work on in seconds instead of scrolling a
spreadsheet.

### F2. Case workspace
One screen holding the transaction, the customer's device, velocity and
geography signals, the triggered rules, the comment thread and the full history.
No tab switching.

*Why it matters:* Eliminates steps 1–4 of the manual process.

### F3. Case lifecycle
Create a case from an alert. Assign it. Comment on it. Move it
`OPEN → INVESTIGATING → RESOLVED → CLOSED` with a required resolution of
`FRAUD`, `FALSE_POSITIVE` or `MANUAL_REVIEW`.

*Why it matters:* Daniel gets consistent process and real throughput metrics.

### F4. Immutable audit trail
Every state change, every comment, every AI output is written to an append-only
record with actor, timestamp, old value and new value.

*Why it matters:* Sara answers the regulator from a screen instead of a data
request.

---

## 5. The AI feature that makes it special

**One button on the case screen: "Run AI Investigation."**

Three specialised agents run over the case evidence:

| Agent               | Output                                                               |
| ------------------- | -------------------------------------------------------------------- |
| **Investigation**   | `likelyFraud` verdict, confidence score, key indicators, written narrative |
| **Recommendation**  | One of `AUTO_APPROVE`, `AUTO_BLOCK`, `REQUEST_VERIFICATION`, `ESCALATE`, `MONITOR`, with reasoning |
| **Summary**         | Plain-language case summary for the audit record                     |

Three design decisions make this defensible rather than a demo trick:

1. **It is persisted, not ephemeral.** Every agent run is written to
   `ai_conversations` with its prompt, response, confidence, provider and
   latency. You can replay exactly what the model saw and said on any past
   decision. This is what makes it auditable under the EU AI Act.

2. **It degrades, it does not fail.** With `AI_PROVIDER=none` the agents fall
   back to deterministic rule-based reasoning. The product works without an LLM
   key, with no outage when the provider has one. Enterprise buyers test for
   this.

3. **The analyst signs, the AI drafts.** Tracelight never auto-closes a case.
   The recommendation is advisory. The human decision is the record. This is the
   difference between a tool a risk officer will buy and one legal will block.

**The one-line pitch:** *Every other fraud tool gives you a score. Tracelight
gives you the case file.*

---

## 6. Scope

### In scope — MVP 1

- Authentication, organisations, role-based access (`ADMIN`, `ANALYST`, `REVIEWER`, `AUDITOR`, `USER`)
- Transaction ingestion: single, bulk CSV, demo seed
- Rule engine: high amount, rapid velocity, geographic anomaly, off-hours, blacklisted merchant
- Risk scoring with `LOW` / `MEDIUM` / `HIGH` / `CRITICAL` bands
- Alert queue with severity and status
- Case management: create, assign, comment, status, history
- Behavioural signals: device trust, user profile, velocity
- AI investigation, recommendation, summary, alert explanation, pattern discovery
- Analytics: fraud trend, merchant risk, geographic risk, dashboard KPIs
- Immutable audit trail

### Out of scope — MVP 1

- Real-time sub-100ms inline scoring at PSP volume
- Customer-authored rule builder
- Multi-organisation admin console
- Scheduled report export
- SSO / SAML
- Mobile application

### Deferred infrastructure

Kafka event streaming and Redis L2 cache are implemented and opt-in behind
`SENTINEL_KAFKA_ENABLED` and `SENTINEL_REDIS_ENABLED`. They are not required to
run the product and are not demonstrated in MVP 1.

---

## 7. Success metrics

| Metric                        | Baseline        | Target        |
| ----------------------------- | --------------- | ------------- |
| Average case handling time    | 20–40 min       | Under 5 min   |
| Cases closed per analyst/day  | 15–25           | 60+           |
| False positive rate           | Customer's current | −20% relative |
| Time to answer an audit query | Days            | Under 2 min   |
| AI recommendation accepted    | n/a             | Over 70%      |

The first and last metrics are the ones to instrument in the product. They are
the demo.

---

## 8. Competitive position

| Competitor | Their strength         | Where Tracelight wins                             |
| ---------- | ---------------------- | -------------------------------------------------- |
| Sift       | Network data at scale  | They score. They do not write the case file.        |
| Forter     | Real-time decisioning  | Black box. No analyst-facing explanation layer.     |
| Sardine    | Behavioural biometrics | Signal provider, not a casework platform.           |
| Unit21     | Casework and workflow  | Closest. Their AI is bolt-on; ours is the workflow. |
| Feedzai    | Enterprise ML          | Long deployment, enterprise-only pricing.           |

**Do not sell "AI fraud detection."** Every vendor on that list says it and
buyers are deaf to the claim. Sell **analyst throughput and audit defensibility**.
Those are budgeted line items with a named owner.

---

## 9. Demo script — 7 minutes

Built for a CEO/CTO room. Every number on screen is real data from PostgreSQL.

1. **00:00 — Seed.** Click "Load demo data." Realistic transactions, alerts and
   cases appear. *"This is a live database, not a mockup."*
2. **01:00 — Dashboard.** KPIs, fraud trend, top-risk merchants, geographic
   heat. *"This is Daniel's Monday morning."*
3. **02:00 — Queue.** Open Alerts. Filter to `CRITICAL`. *"This is Maya's
   Monday morning. 60 of these today."*
4. **02:45 — Open an alert.** Triggered rules, risk score, the transaction.
   *"Today, this is where the 30 minutes of manual work starts."*
5. **03:30 — Escalate to case.** One click. *"Now watch."*
6. **04:00 — Run AI Investigation.** Narrative, indicators, confidence, a
   recommended action with reasoning. *"Three seconds. She reviews and signs."*
7. **05:30 — Resolve the case.** Pick `FRAUD`. Open the history tab. Every step,
   every actor, every AI output, timestamped. *"This is what you hand the
   regulator."*
8. **06:30 — Close.** *"Every other tool gives you a score. We give you the case
   file. Thirty minutes becomes three."*

---

## 10. Commercial model

**Pricing:** per seat per month for analyst licences, plus a volume tier on
transactions scored. Analyst seats are the value metric because the value
proposition is analyst throughput — the pricing should mirror the pitch.

**Land:** one fraud team, 5–10 seats, 30-day pilot with their own CSV data.
**Expand:** more seats, then the volume tier as they route live traffic.
**Proof point for the pilot:** average handling time before versus after,
measured inside the product.

---

## 11. Risks

| Risk                                           | Mitigation                                                              |
| ---------------------------------------------- | ----------------------------------------------------------------------- |
| LLM hallucinates a fraud narrative             | AI is advisory only. Confidence is shown. Every run is persisted and replayable. Human signs. |
| Buyer will not send transaction data to OpenAI | `AI_PROVIDER` is pluggable; deterministic fallback ships in the box; self-hosted model is a roadmap item. |
| "Sentinel" name collides with SentinelOne / Microsoft Sentinel | Renamed to Tracelight before go-to-market.               |
| Incumbent ships a case-writing agent           | Speed. Also depth — our audit trail and replay are a year of work, not a feature. |
| Single-tenant data leakage                     | Every repository query is scoped by `organizationId`; integration tests must prove isolation. |

---

## 12. Definition of done for MVP 1

- Every link in the navigation resolves to a working screen.
- Every screen reads live data from the API. No `Math.random()`, no hardcoded arrays.
- Every failure is visible to the user as a toast, not a console log.
- The full demo script runs start to finish against PostgreSQL without a manual database edit.
- `RuleEngine`, `RiskScoringService` and multi-tenant isolation have automated tests.
- The application builds clean: `mvnw verify` and `npm run build` both exit 0.
