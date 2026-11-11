# Wipes transactional data for one organization and leaves users, roles and
# organization settings intact.
#
#   powershell -ExecutionPolicy Bypass -File scripts\reset-demo.ps1

param(
    [string]$OrgId    = "550e8400-e29b-41d4-a716-446655440000",
    [string]$DbHost   = "localhost",
    [string]$Database = "sentinel",
    [string]$DbUser   = "postgres",
    [string]$DbPass   = "postgres"
)

$ErrorActionPreference = "Stop"
$env:PGPASSWORD = $DbPass

$psql = (Get-Command psql -ErrorAction SilentlyContinue).Source
if (-not $psql) {
    $psql = (Get-ChildItem "C:\Program Files\PostgreSQL\*\bin\psql.exe" -ErrorAction SilentlyContinue |
             Select-Object -First 1).FullName
}
if (-not $psql) { throw "psql not found on PATH or under C:\Program Files\PostgreSQL" }

# Child-table-first ordering so no foreign key is ever violated.
$sql = @"
BEGIN;
DELETE FROM case_history  WHERE case_id  IN (SELECT id FROM fraud_cases  WHERE organization_id = '$OrgId');
DELETE FROM case_comments WHERE case_id  IN (SELECT id FROM fraud_cases  WHERE organization_id = '$OrgId');
DELETE FROM fraud_cases   WHERE organization_id = '$OrgId';
-- These four tables carry no organization_id column, so they are cleared by
-- joining back to a row that does. See docs/KNOWN_GAPS.md: they should gain the
-- column before the platform serves a second tenant.
DELETE FROM ai_explanations WHERE alert_id IN (SELECT id FROM fraud_alerts WHERE organization_id = '$OrgId');
DELETE FROM agent_executions WHERE alert_id IN (SELECT id FROM fraud_alerts WHERE organization_id = '$OrgId')
                                OR case_id  IN (SELECT id FROM fraud_cases  WHERE organization_id = '$OrgId')
                                OR transaction_id IN (SELECT id FROM transactions WHERE organization_id = '$OrgId');
DELETE FROM ai_conversations WHERE case_id IN (SELECT id FROM fraud_cases WHERE organization_id = '$OrgId');
DELETE FROM feedback_events  WHERE alert_id IN (SELECT id FROM fraud_alerts WHERE organization_id = '$OrgId')
                                OR case_id  IN (SELECT id FROM fraud_cases  WHERE organization_id = '$OrgId');
DELETE FROM fraud_alerts  WHERE organization_id = '$OrgId';
DELETE FROM risk_scores   WHERE transaction_id IN (SELECT id FROM transactions WHERE organization_id = '$OrgId');
DELETE FROM idempotency_keys WHERE transaction_id IN (SELECT id FROM transactions WHERE organization_id = '$OrgId');
DELETE FROM devices       WHERE organization_id = '$OrgId';
DELETE FROM user_profiles WHERE organization_id = '$OrgId';
DELETE FROM discovered_patterns;
DELETE FROM merchant_risk_profiles WHERE organization_id = '$OrgId';
DELETE FROM daily_analytics    WHERE organization_id = '$OrgId';
DELETE FROM audit_trail   WHERE organization_id = '$OrgId';
DELETE FROM transactions  WHERE organization_id = '$OrgId';
COMMIT;
"@

& $psql -h $DbHost -U $DbUser -d $Database -v ON_ERROR_STOP=1 -c $sql
& $psql -h $DbHost -U $DbUser -d $Database -c @"
SELECT (SELECT count(*) FROM transactions WHERE organization_id = '$OrgId') AS transactions,
       (SELECT count(*) FROM fraud_alerts WHERE organization_id = '$OrgId') AS alerts,
       (SELECT count(*) FROM fraud_cases  WHERE organization_id = '$OrgId') AS cases;
"@
