# Sentinel

An autonomous financial crime operations platform. A Spring Boot backend scores
transactions, detects scams and mule networks, verifies identities, monitors for
money laundering, and runs agents that investigate, decide, and document — with
humans supervising rather than grinding. A Next.js frontend is the workspace.

The positioning is deliberate: scoring is close to a commodity, and the cost sits
in investigation, not detection. Everything here is built around removing analyst
hours while keeping every decision explainable and replayable.

## Layout

| Path                 | What it is                                               |
| -------------------- | -------------------------------------------------------- |
| `backend/`           | Spring Boot 3.2 / Java 17 API, hexagonal modules          |
| `sentinel-frontend/` | Next.js 15 / React 19 analyst console                     |
| `docs/`              | Architecture and roadmap documents                        |
| `docker-compose.yml` | PostgreSQL, Redis, Kafka, and optional admin UIs          |

Backend modules follow `adapter.in.rest` → `application.service` → `domain.model`,
with persistence behind `adapter.out.persistence`.

## Running locally

PostgreSQL is the only hard dependency. Redis and Kafka are opt-in.

```bash
docker compose up -d postgres          # database only
cd backend && ./mvnw spring-boot:run   # API on :8081, Flyway migrates on boot
cd sentinel-frontend && npm install && npm run dev   # UI on :3000
```

### Sign-in accounts

The backend creates one account per role on every start, so a fresh database is
usable immediately. All four share the password `Sentinel@123`.

| Email                  | Role     | Can do                                                      |
| ---------------------- | -------- | ----------------------------------------------------------- |
| `admin@sentinel.ai`    | ADMIN    | Everything, including autonomy levels, rule promotion, API keys |
| `reviewer@sentinel.ai` | REVIEWER | Approve models, promote rules, file SARs                     |
| `analyst@sentinel.ai`  | ANALYST  | Investigate, triage, run sweeps                              |
| `auditor@sentinel.ai`  | AUDITOR  | Read-only                                                    |

Registration through `/api/v1/auth/register` only ever mints an ANALYST, which is
why these exist. Set `SENTINEL_DEMO_ACCOUNTS_ENABLED=false` for any deployment
that is not a local demo; the password itself is `SENTINEL_DEMO_PASSWORD`.

Every start also resets these four accounts' passwords, clears any lockout, and
removes any second factor enrolled against them. A shared demo account with an
authenticator nobody else holds is a locked door with no key.

## Security

| Control | Behaviour |
| --- | --- |
| Account lockout | Three failed sign-ins locks the account for 15 minutes; each further lockout doubles the wait, to a day |
| Password policy | 12 characters, mixed case, a number, a symbol, no breach-list entry, no reuse of the last 5 |
| Multi-factor | TOTP authenticator, SMS to a verified phone, and ten single-use recovery codes |
| Step-up | Role changes, API keys, model approvals, and rule promotion need a second factor used in the last 15 minutes |
| Sessions | One row per device, refresh tokens rotated on use; replaying a retired token ends every session for that user |
| Sign-out | Revokes the session and denies the access token by `jti`, so it stops working immediately |
| Verification | Email and phone codes, hashed at rest, attempt-capped, superseded when a new one is issued |
| Rate limits | Per-IP budgets on sign-in, registration, reset, and code requests, plus a global ceiling |
| Headers | CSP, HSTS over TLS, `nosniff`, `DENY` framing, no referrer, no store |
| Uploads | Profile images are decoded and re-encoded to PNG, which strips EXIF, appended data, and SVG markup |
| Audit | Every sign-in, lockout, factor change, and permission change lands in `security_events` with IP and user agent |

Verification codes are returned in API responses while
`SENTINEL_EXPOSE_CODES=true`, because the default sender writes to the log
rather than to an inbox. Turn it off anywhere real. The backend reports every
such setting at startup, and refuses to boot under the `prod` profile while any
of them is still in place.

With an empty database, sign in as any account and `POST /api/v1/demo/seed` to
generate transactions, alerts, and cases through the real scoring pipeline.

To enable the optional infrastructure:

```bash
docker compose up -d postgres redis zookeeper kafka
SENTINEL_REDIS_ENABLED=true SENTINEL_KAFKA_ENABLED=true ./mvnw spring-boot:run
```

## Configuration

Every secret is read from the environment and falls back to a development value.
Production deployments must set these explicitly.

| Variable                 | Default                      | Purpose                             |
| ------------------------ | ---------------------------- | ----------------------------------- |
| `DB_URL`                 | `jdbc:postgresql://localhost:5432/sentinel` | JDBC URL              |
| `DB_USERNAME`            | `postgres`                   | Database user                       |
| `DB_PASSWORD`            | `postgres`                   | Database password                   |
| `JWT_SECRET`             | development key              | HMAC signing key, 256 bits minimum  |
| `SENTINEL_REDIS_ENABLED` | `false`                      | Load the Redis L2 cache beans       |
| `SENTINEL_KAFKA_ENABLED` | `false`                      | Load the Kafka event-streaming beans|
| `AI_PROVIDER`            | `none`                       | `none` or `openai`                  |
| `OPENAI_API_KEY`         | empty                        | Required when `AI_PROVIDER=openai`  |
| `NEXT_PUBLIC_API_URL`    | `http://localhost:8081/api/v1` | Frontend API base URL             |
| `SENTINEL_DEMO_ACCOUNTS_ENABLED` | `true`               | Seed the four role accounts on start|
| `SENTINEL_DEMO_PASSWORD` | `Sentinel@123`               | Password for those accounts         |
| `SENTINEL_MAX_FAILED_ATTEMPTS` | `3`                    | Failures before a lockout           |
| `SENTINEL_LOCKOUT_MINUTES` | `15`                       | First lockout length, doubling after|
| `SENTINEL_MIN_PASSWORD_LENGTH` | `12`                   | Password floor                      |
| `SENTINEL_REQUIRE_EMAIL_VERIFICATION` | `false`         | Block sign-in until email is verified|
| `SENTINEL_EXPOSE_CODES` | `true`                      | Echo verification codes in responses|
| `SENTINEL_RATE_LIMIT_PER_MINUTE` | `600`                | Global per-IP request ceiling       |
| `SENTINEL_CORS_ORIGINS` | `http://localhost:3000`     | Exact allowed origins               |

With `AI_PROVIDER=none` the agents fall back to deterministic rule-based reasoning,
so the platform is fully functional without an LLM key.

## Capabilities

| Domain | Module | What it does |
| ------ | ------ | ------------ |
| Core detection | `fraud`, `featurestore` | Rule engine, weighted risk scoring with an evidence floor, point-in-time feature vectors |
| Scams (APP) | `scam` | Coaching detection, typology classification, cooling-off holds, trusted contacts, adapted protection |
| Mule networks | `mule`, `graph` | Beneficiary fan-in scoring, entity resolution, ring detection, link explorer |
| Identity | `identity` | Document forensics, liveness and injection attacks, synthetic identity, telco signals, KYB and beneficial ownership, list screening with automated false-positive reduction |
| Financial crime | `aml` | Behavioural typology monitoring and auto-drafted suspicious activity reports |
| Automation | `autonomy` | Triage agent, customer outreach agent, adversarial red-team agent, per-action autonomy policy |
| Strategy | `strategy` | Natural-language rule authoring, backtesting, shadow mode, champion/challenger, rule discovery, threshold tuning, retro analysis |
| GenAI defence | `genai` | Voice and video deepfake scoring, AI-generated document detection, Know Your Agent, brand impersonation |
| Disputes | `disputes` | First-party fraud scoring and automated representment |
| Consortium | `consortium` | Cross-institution signals shared as keyed hashes under a k-anonymity gate |
| Governance | `governance` | Model registry, drift (PSI), disparate impact, generated model documentation, LLM guardrails |
| Experience | `friction` | Dynamic step-up selection priced by abandonment cost |
| Platform | `integration`, `analytics` | Signed webhooks, API keys, connector catalogue, ROI dashboard, loss forecast |

Two principles run through all of it. Evidence is collected and scored
deterministically before any language model is consulted, so behaviour is
identical with `AI_PROVIDER=none`. And every autonomous action passes a policy
gate whose refusals are recorded — those records are the evidence for widening
or tightening autonomy later.

## Database

Flyway owns the schema; `ddl-auto` is `none`. Migrations live in
`backend/src/main/resources/db/migration` and run automatically at startup.
`V8__financial_crime_platform.sql` adds the scam, mule, graph, identity, AML,
autonomy, strategy, GenAI, dispute, consortium, governance, friction,
integration, and quality-assurance tables.
