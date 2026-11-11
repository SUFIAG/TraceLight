-- =================================================================
-- V8: Autonomous financial crime operations
--
-- Adds the scam, mule, graph, identity, AML, autonomy, strategy,
-- GenAI-defence, dispute, consortium, governance, friction,
-- integration, and quality-assurance domains.
--
-- Every table is organization-scoped except consortium_signals, which
-- is deliberately cross-tenant and holds only keyed hashes.
-- =================================================================

-- =================================================================
-- SCAM AND AUTHORIZED PUSH PAYMENT
-- =================================================================

CREATE TABLE IF NOT EXISTS scam_signals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    transaction_id UUID,
    user_id VARCHAR(255) NOT NULL,
    beneficiary_id VARCHAR(255),
    session_id VARCHAR(255),
    amount DECIMAL(15, 2),
    typology VARCHAR(40) NOT NULL,
    score INT NOT NULL,
    coaching_score INT NOT NULL DEFAULT 0,
    beneficiary_score INT NOT NULL DEFAULT 0,
    vulnerability_score INT NOT NULL DEFAULT 0,
    indicators JSONB NOT NULL DEFAULT '[]'::jsonb,
    action VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_scam_signals_org_created ON scam_signals(organization_id, created_at DESC);
CREATE INDEX idx_scam_signals_user ON scam_signals(organization_id, user_id);
CREATE INDEX idx_scam_signals_typology ON scam_signals(organization_id, typology);

COMMENT ON TABLE scam_signals IS
'Scam-in-progress assessments. Unlike transaction risk, these measure whether the customer is being coached.';

CREATE TABLE IF NOT EXISTS payment_holds (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    transaction_id UUID,
    user_id VARCHAR(255) NOT NULL,
    beneficiary_id VARCHAR(255),
    amount DECIMAL(15, 2),
    scam_signal_id UUID,
    status VARCHAR(40) NOT NULL,
    reason TEXT NOT NULL,
    release_at TIMESTAMP NOT NULL,
    resolved_at TIMESTAMP,
    resolved_by UUID,
    trusted_contact_notified BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_payment_holds_org_status ON payment_holds(organization_id, status);
CREATE INDEX idx_payment_holds_release ON payment_holds(status, release_at);

CREATE TABLE IF NOT EXISTS trusted_contacts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    user_id VARCHAR(255) NOT NULL,
    contact_name VARCHAR(255) NOT NULL,
    contact_channel VARCHAR(20) NOT NULL,
    contact_address VARCHAR(255) NOT NULL,
    relationship VARCHAR(100),
    notify_above_amount DECIMAL(15, 2),
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_trusted_contacts_user ON trusted_contacts(organization_id, user_id) WHERE active;

CREATE TABLE IF NOT EXISTS vulnerable_customers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    user_id VARCHAR(255) NOT NULL,
    basis VARCHAR(40) NOT NULL,
    evidence JSONB NOT NULL DEFAULT '[]'::jsonb,
    protection_level INT NOT NULL DEFAULT 1,
    active BOOLEAN NOT NULL DEFAULT true,
    review_due TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_vulnerable_user ON vulnerable_customers(organization_id, user_id) WHERE active;

-- =================================================================
-- MULE AND BENEFICIARY RISK
-- =================================================================

CREATE TABLE IF NOT EXISTS beneficiary_risk (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    beneficiary_id VARCHAR(255) NOT NULL,
    beneficiary_name VARCHAR(255),
    score INT NOT NULL DEFAULT 0,
    band VARCHAR(20) NOT NULL DEFAULT 'CLEAN',
    fan_in INT NOT NULL DEFAULT 0,
    credit_count INT NOT NULL DEFAULT 0,
    total_credited DECIMAL(18, 2) DEFAULT 0,
    small_credit_ratio DOUBLE PRECISION NOT NULL DEFAULT 0,
    activity_window_days INT NOT NULL DEFAULT 0,
    distinct_countries INT NOT NULL DEFAULT 0,
    externally_confirmed BOOLEAN NOT NULL DEFAULT false,
    reasons JSONB NOT NULL DEFAULT '[]'::jsonb,
    first_seen TIMESTAMP,
    last_seen TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(organization_id, beneficiary_id)
);

CREATE INDEX idx_beneficiary_risk_score ON beneficiary_risk(organization_id, score DESC);
CREATE INDEX idx_beneficiary_risk_band ON beneficiary_risk(organization_id, band);

COMMENT ON TABLE beneficiary_risk IS
'Risk held against receiving accounts. Scam money always lands somewhere, and the destination repeats across victims.';

-- =================================================================
-- GRAPH AND NETWORK INTELLIGENCE
-- =================================================================

CREATE TABLE IF NOT EXISTS entity_links (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    source_type VARCHAR(30) NOT NULL,
    source_id VARCHAR(255) NOT NULL,
    target_type VARCHAR(30) NOT NULL,
    target_id VARCHAR(255) NOT NULL,
    link_type VARCHAR(40) NOT NULL,
    via_value VARCHAR(255),
    strength DOUBLE PRECISION NOT NULL DEFAULT 0,
    observation_count INT NOT NULL DEFAULT 1,
    first_seen TIMESTAMP,
    last_seen TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(organization_id, source_id, target_id, link_type)
);

CREATE INDEX idx_entity_links_source ON entity_links(organization_id, source_id);
CREATE INDEX idx_entity_links_target ON entity_links(organization_id, target_id);
CREATE INDEX idx_entity_links_strength ON entity_links(organization_id, strength DESC);

CREATE TABLE IF NOT EXISTS fraud_rings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    ring_key VARCHAR(64) NOT NULL,
    label VARCHAR(500) NOT NULL,
    members JSONB NOT NULL DEFAULT '[]'::jsonb,
    link_types JSONB NOT NULL DEFAULT '[]'::jsonb,
    member_count INT NOT NULL DEFAULT 0,
    edge_count INT NOT NULL DEFAULT 0,
    density DOUBLE PRECISION NOT NULL DEFAULT 0,
    risk_score INT NOT NULL DEFAULT 0,
    total_exposure DECIMAL(18, 2) DEFAULT 0,
    status VARCHAR(30) NOT NULL DEFAULT 'DETECTED',
    detected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(organization_id, ring_key)
);

CREATE INDEX idx_fraud_rings_score ON fraud_rings(organization_id, risk_score DESC);

-- =================================================================
-- IDENTITY, KYC AND KYB
-- =================================================================

CREATE TABLE IF NOT EXISTS identity_verifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    subject_id VARCHAR(255) NOT NULL,
    subject_name VARCHAR(255),
    type VARCHAR(40) NOT NULL,
    decision VARCHAR(40) NOT NULL,
    risk_score INT NOT NULL DEFAULT 0,
    document_score INT NOT NULL DEFAULT 0,
    liveness_score INT NOT NULL DEFAULT 0,
    deepfake_score INT NOT NULL DEFAULT 0,
    synthetic_score INT NOT NULL DEFAULT 0,
    footprint_score INT NOT NULL DEFAULT 0,
    telco_score INT NOT NULL DEFAULT 0,
    sanctions_hit BOOLEAN NOT NULL DEFAULT false,
    reasons JSONB NOT NULL DEFAULT '[]'::jsonb,
    imposed_limits JSONB DEFAULT '[]'::jsonb,
    reviewed_by UUID,
    next_review_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_identity_org_created ON identity_verifications(organization_id, created_at DESC);
CREATE INDEX idx_identity_subject ON identity_verifications(organization_id, subject_id);
CREATE INDEX idx_identity_review_due ON identity_verifications(next_review_at) WHERE next_review_at IS NOT NULL;

CREATE TABLE IF NOT EXISTS sanctions_list_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    list_type VARCHAR(30) NOT NULL,
    list_name VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    normalised_name VARCHAR(255) NOT NULL,
    date_of_birth VARCHAR(20),
    country VARCHAR(2),
    program VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT true
);

CREATE INDEX idx_sanctions_list_active ON sanctions_list_entries(active);

CREATE TABLE IF NOT EXISTS sanctions_hits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    subject_id VARCHAR(255) NOT NULL,
    verification_id UUID,
    list_type VARCHAR(30) NOT NULL,
    list_name VARCHAR(255) NOT NULL,
    matched_name VARCHAR(255) NOT NULL,
    match_score INT NOT NULL,
    matched_dob VARCHAR(20),
    matched_country VARCHAR(2),
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    dismissed_by_ai BOOLEAN NOT NULL DEFAULT false,
    dismissal_rationale TEXT,
    reviewed_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_sanctions_hits_status ON sanctions_hits(organization_id, status, match_score DESC);

COMMENT ON COLUMN sanctions_hits.dismissed_by_ai IS
'Set when automated reasoning cleared the match on a hard discriminator. The rationale is always recorded.';

CREATE TABLE IF NOT EXISTS business_entities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    legal_name VARCHAR(255) NOT NULL,
    registration_number VARCHAR(255) NOT NULL,
    registration_country VARCHAR(2),
    incorporation_date TIMESTAMP,
    industry_code VARCHAR(20),
    registered_address VARCHAR(500),
    address_cohabitants INT NOT NULL DEFAULT 0,
    director_count INT NOT NULL DEFAULT 0,
    ownership_depth INT NOT NULL DEFAULT 0,
    shell_score INT NOT NULL DEFAULT 0,
    risk_score INT NOT NULL DEFAULT 0,
    decision VARCHAR(40) NOT NULL DEFAULT 'MANUAL_REVIEW',
    findings JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(organization_id, registration_number)
);

CREATE INDEX idx_business_entities_risk ON business_entities(organization_id, risk_score DESC);

CREATE TABLE IF NOT EXISTS ubo_nodes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    business_entity_id UUID NOT NULL REFERENCES business_entities(id) ON DELETE CASCADE,
    parent_node_id UUID,
    owner_type VARCHAR(20) NOT NULL,
    owner_name VARCHAR(255) NOT NULL,
    owner_country VARCHAR(2),
    ownership_percent DOUBLE PRECISION NOT NULL DEFAULT 0,
    effective_percent DOUBLE PRECISION NOT NULL DEFAULT 0,
    depth INT NOT NULL DEFAULT 1,
    is_ubo BOOLEAN NOT NULL DEFAULT false,
    sanctions_screened BOOLEAN NOT NULL DEFAULT false,
    pep_flag BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ubo_nodes_entity ON ubo_nodes(business_entity_id, depth);

-- =================================================================
-- AML AND REGULATORY REPORTING
-- =================================================================

CREATE TABLE IF NOT EXISTS aml_findings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    subject_id VARCHAR(255) NOT NULL,
    typology VARCHAR(40) NOT NULL,
    score INT NOT NULL DEFAULT 0,
    narrative TEXT NOT NULL,
    evidence JSONB NOT NULL DEFAULT '[]'::jsonb,
    transaction_ids JSONB DEFAULT '[]'::jsonb,
    total_value DECIMAL(18, 2),
    window_start TIMESTAMP,
    window_end TIMESTAMP,
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    case_id UUID,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_aml_findings_org_score ON aml_findings(organization_id, score DESC);
CREATE INDEX idx_aml_findings_subject ON aml_findings(organization_id, subject_id);
CREATE INDEX idx_aml_findings_dedupe ON aml_findings(organization_id, subject_id, typology, window_end DESC);

CREATE TABLE IF NOT EXISTS sar_filings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    reference_number VARCHAR(50) NOT NULL UNIQUE,
    subject_id VARCHAR(255) NOT NULL,
    subject_name VARCHAR(255),
    jurisdiction VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    narrative TEXT NOT NULL,
    finding_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    typologies JSONB NOT NULL DEFAULT '[]'::jsonb,
    total_value DECIMAL(18, 2),
    activity_start TIMESTAMP,
    activity_end TIMESTAMP,
    ai_drafted BOOLEAN NOT NULL DEFAULT false,
    drafted_at TIMESTAMP,
    approved_by UUID,
    approved_at TIMESTAMP,
    filed_at TIMESTAMP,
    deadline_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_sar_org_status ON sar_filings(organization_id, status, deadline_at);

COMMENT ON TABLE sar_filings IS
'Suspicious activity reports. Drafted automatically from findings; filing always requires a named human approver.';

-- =================================================================
-- AUTONOMY LAYER
-- =================================================================

CREATE TABLE IF NOT EXISTS autonomy_policies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    action_type VARCHAR(40) NOT NULL,
    level VARCHAR(30) NOT NULL,
    max_amount DECIMAL(15, 2),
    max_risk_score INT,
    min_confidence INT,
    requires_maker_checker BOOLEAN NOT NULL DEFAULT false,
    daily_action_cap INT,
    updated_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(organization_id, action_type)
);

COMMENT ON TABLE autonomy_policies IS
'What agents may do without a human, per action type. This is the table a risk committee signs off.';

CREATE TABLE IF NOT EXISTS agent_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    agent_name VARCHAR(40) NOT NULL,
    target_type VARCHAR(30) NOT NULL,
    target_id VARCHAR(255) NOT NULL,
    verdict VARCHAR(30) NOT NULL,
    confidence INT NOT NULL DEFAULT 0,
    proposed_action VARCHAR(40),
    action_executed BOOLEAN NOT NULL DEFAULT false,
    suppressed_reason VARCHAR(500),
    rationale TEXT NOT NULL,
    evidence JSONB NOT NULL DEFAULT '[]'::jsonb,
    duration_ms BIGINT NOT NULL DEFAULT 0,
    llm_assisted BOOLEAN NOT NULL DEFAULT false,
    human_agreed BOOLEAN,
    reviewed_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_agent_runs_org_created ON agent_runs(organization_id, created_at DESC);
CREATE INDEX idx_agent_runs_agent ON agent_runs(organization_id, agent_name);
CREATE INDEX idx_agent_runs_target ON agent_runs(organization_id, target_id);

COMMENT ON COLUMN agent_runs.suppressed_reason IS
'Why policy stopped an action the agent wanted to take. The evidence for widening or tightening autonomy.';

CREATE TABLE IF NOT EXISTS outreach_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    user_id VARCHAR(255) NOT NULL,
    alert_id UUID,
    transaction_id UUID,
    channel VARCHAR(20) NOT NULL,
    message TEXT NOT NULL,
    outcome VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    response_text TEXT,
    responded_at TIMESTAMP,
    expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_outreach_org_outcome ON outreach_attempts(organization_id, outcome);

CREATE TABLE IF NOT EXISTS attack_simulations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    scenario_key VARCHAR(60) NOT NULL,
    scenario_name VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    transactions_simulated INT NOT NULL DEFAULT 0,
    detected_count INT NOT NULL DEFAULT 0,
    detection_rate DOUBLE PRECISION NOT NULL DEFAULT 0,
    peak_score INT NOT NULL DEFAULT 0,
    outcome VARCHAR(20) NOT NULL,
    gaps JSONB NOT NULL DEFAULT '[]'::jsonb,
    recommendations JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_attack_sim_org ON attack_simulations(organization_id, created_at DESC);

-- =================================================================
-- STRATEGY: ANALYST-AUTHORED RULES
-- =================================================================

CREATE TABLE IF NOT EXISTS rule_definitions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    natural_language TEXT,
    definition JSONB NOT NULL DEFAULT '{}'::jsonb,
    risk_points INT NOT NULL DEFAULT 50,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    version INT NOT NULL DEFAULT 1,
    traffic_percent INT NOT NULL DEFAULT 0,
    hit_count BIGINT NOT NULL DEFAULT 0,
    true_positive_count BIGINT NOT NULL DEFAULT 0,
    false_positive_count BIGINT NOT NULL DEFAULT 0,
    created_by UUID,
    approved_by UUID,
    promoted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(organization_id, name, version)
);

CREATE INDEX idx_rule_definitions_status ON rule_definitions(organization_id, status);

COMMENT ON COLUMN rule_definitions.natural_language IS
'The sentence the author wrote. Six months later this answers "what was this meant to catch" better than the condition tree.';

CREATE TABLE IF NOT EXISTS rule_backtests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    rule_id UUID,
    rule_name VARCHAR(500) NOT NULL,
    transactions_evaluated INT NOT NULL DEFAULT 0,
    hit_count INT NOT NULL DEFAULT 0,
    true_positives INT NOT NULL DEFAULT 0,
    false_positives INT NOT NULL DEFAULT 0,
    false_negatives INT NOT NULL DEFAULT 0,
    precision_score DOUBLE PRECISION NOT NULL DEFAULT 0,
    recall_score DOUBLE PRECISION NOT NULL DEFAULT 0,
    value_caught DECIMAL(18, 2),
    value_disrupted DECIMAL(18, 2),
    monthly_review_hours DOUBLE PRECISION NOT NULL DEFAULT 0,
    window_days INT NOT NULL DEFAULT 90,
    sample_hits JSONB DEFAULT '[]'::jsonb,
    recommendation VARCHAR(30) NOT NULL,
    recommendation_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_rule_backtests_org ON rule_backtests(organization_id, created_at DESC);

-- =================================================================
-- GENERATIVE-AI THREAT DEFENCE
-- =================================================================

CREATE TABLE IF NOT EXISTS media_analyses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    media_type VARCHAR(20) NOT NULL,
    subject_id VARCHAR(255),
    channel VARCHAR(30) NOT NULL,
    media_reference VARCHAR(500),
    synthetic_score INT NOT NULL DEFAULT 0,
    verdict VARCHAR(30) NOT NULL,
    indicators JSONB NOT NULL DEFAULT '[]'::jsonb,
    action VARCHAR(20) NOT NULL,
    reviewed_by UUID,
    human_verdict VARCHAR(30),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_media_org_created ON media_analyses(organization_id, created_at DESC);
CREATE INDEX idx_media_type ON media_analyses(organization_id, media_type);

COMMENT ON TABLE media_analyses IS
'Synthetic media assessments. Signal scores only: the platform never stores the biometric media itself.';

CREATE TABLE IF NOT EXISTS ai_agent_identities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    agent_key VARCHAR(255) NOT NULL,
    agent_name VARCHAR(255) NOT NULL,
    operator VARCHAR(255) NOT NULL,
    principal_user_id VARCHAR(255) NOT NULL,
    key_fingerprint VARCHAR(64) NOT NULL,
    scopes JSONB NOT NULL DEFAULT '[]'::jsonb,
    allowed_merchants JSONB DEFAULT '[]'::jsonb,
    per_transaction_limit DECIMAL(15, 2),
    daily_limit DECIMAL(15, 2),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    expires_at TIMESTAMP,
    transaction_count BIGINT NOT NULL DEFAULT 0,
    behaviour_drift_score INT NOT NULL DEFAULT 0,
    last_seen_at TIMESTAMP,
    revoked_reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(organization_id, agent_key)
);

CREATE INDEX idx_agent_identity_principal ON ai_agent_identities(organization_id, principal_user_id);

COMMENT ON TABLE ai_agent_identities IS
'Know Your Agent: which AI agents are authorised, by which customer, for what, and up to how much.';

CREATE TABLE IF NOT EXISTS ai_agent_activity (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    agent_identity_id UUID,
    agent_key VARCHAR(255) NOT NULL,
    principal_user_id VARCHAR(255),
    requested_scope VARCHAR(40) NOT NULL,
    merchant_id VARCHAR(255),
    amount DECIMAL(15, 2),
    decision VARCHAR(20) NOT NULL,
    reasons JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_agent_activity_key ON ai_agent_activity(organization_id, agent_key, created_at DESC);

CREATE TABLE IF NOT EXISTS phishing_domains (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    domain VARCHAR(255) NOT NULL,
    brand_term VARCHAR(255) NOT NULL,
    technique VARCHAR(30) NOT NULL,
    similarity_score INT NOT NULL DEFAULT 0,
    registered_at TIMESTAMP,
    resolves_to_ip VARCHAR(45),
    has_login_form BOOLEAN NOT NULL DEFAULT false,
    ssl_issued BOOLEAN NOT NULL DEFAULT false,
    status VARCHAR(30) NOT NULL DEFAULT 'DETECTED',
    evidence JSONB NOT NULL DEFAULT '[]'::jsonb,
    takedown_requested_at TIMESTAMP,
    resolved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(organization_id, domain)
);

CREATE INDEX idx_phishing_score ON phishing_domains(organization_id, similarity_score DESC);

-- =================================================================
-- DISPUTES AND CHARGEBACKS
-- =================================================================

CREATE TABLE IF NOT EXISTS disputes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    transaction_id UUID,
    external_reference VARCHAR(255),
    user_id VARCHAR(255) NOT NULL,
    merchant_id VARCHAR(255),
    network VARCHAR(20) NOT NULL,
    reason_code VARCHAR(20) NOT NULL,
    reason_description VARCHAR(500),
    amount DECIMAL(15, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    status VARCHAR(30) NOT NULL DEFAULT 'RECEIVED',
    first_party_fraud_score INT NOT NULL DEFAULT 0,
    win_probability INT NOT NULL DEFAULT 0,
    evidence JSONB NOT NULL DEFAULT '[]'::jsonb,
    representment_text TEXT,
    ai_drafted BOOLEAN NOT NULL DEFAULT false,
    respond_by TIMESTAMP,
    submitted_at TIMESTAMP,
    resolved_at TIMESTAMP,
    recovered_amount DECIMAL(15, 2),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_disputes_org_status ON disputes(organization_id, status, respond_by);
CREATE INDEX idx_disputes_user ON disputes(organization_id, user_id);

-- =================================================================
-- CONSORTIUM (CROSS-TENANT, HASHED ONLY)
-- =================================================================

CREATE TABLE IF NOT EXISTS consortium_signals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hashed_identifier VARCHAR(64) NOT NULL,
    identifier_type VARCHAR(20) NOT NULL,
    signal_type VARCHAR(40) NOT NULL,
    severity INT NOT NULL DEFAULT 0,
    contributor_count INT NOT NULL DEFAULT 0,
    sighting_count INT NOT NULL DEFAULT 0,
    first_seen TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(hashed_identifier, signal_type)
);

CREATE INDEX idx_consortium_lookup ON consortium_signals(hashed_identifier);
CREATE INDEX idx_consortium_shared ON consortium_signals(contributor_count, last_seen DESC);

COMMENT ON TABLE consortium_signals IS
'Cross-institution signals. Identifiers are HMAC-hashed under a platform pepper; a plain digest would be enumerable.';

CREATE TABLE IF NOT EXISTS consortium_contributions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    signal_id UUID NOT NULL REFERENCES consortium_signals(id) ON DELETE CASCADE,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    severity INT NOT NULL DEFAULT 0,
    report_count INT NOT NULL DEFAULT 1,
    last_reported_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(signal_id, organization_id)
);

COMMENT ON TABLE consortium_contributions IS
'Attribution, held apart from the signal so a lookup never reveals who reported what.';

-- =================================================================
-- MODEL GOVERNANCE
-- =================================================================

CREATE TABLE IF NOT EXISTS model_registry (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    model_name VARCHAR(255) NOT NULL,
    model_version VARCHAR(50) NOT NULL,
    model_type VARCHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DEVELOPMENT',
    risk_tier VARCHAR(20) NOT NULL DEFAULT 'HIGH',
    purpose TEXT,
    owner_name VARCHAR(255) NOT NULL,
    training_data TEXT,
    metrics JSONB DEFAULT '{}'::jsonb,
    limitations JSONB DEFAULT '[]'::jsonb,
    input_features JSONB DEFAULT '[]'::jsonb,
    documentation TEXT,
    validated_by VARCHAR(255),
    approved_by UUID,
    approved_at TIMESTAMP,
    next_review_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(organization_id, model_name, model_version)
);

CREATE TABLE IF NOT EXISTS drift_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    model_name VARCHAR(255) NOT NULL,
    metric VARCHAR(40) NOT NULL,
    psi DOUBLE PRECISION NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    baseline_count INT NOT NULL DEFAULT 0,
    current_count INT NOT NULL DEFAULT 0,
    baseline_start TIMESTAMP,
    current_start TIMESTAMP,
    buckets JSONB DEFAULT '[]'::jsonb,
    interpretation TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_drift_org_created ON drift_reports(organization_id, created_at DESC);

CREATE TABLE IF NOT EXISTS fairness_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    model_name VARCHAR(255) NOT NULL,
    attribute VARCHAR(40) NOT NULL,
    groups JSONB NOT NULL DEFAULT '[]'::jsonb,
    disparity_ratio DOUBLE PRECISION NOT NULL DEFAULT 1,
    verdict VARCHAR(20) NOT NULL,
    groups_tested INT NOT NULL DEFAULT 0,
    sample_size INT NOT NULL DEFAULT 0,
    interpretation TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_fairness_org_created ON fairness_reports(organization_id, created_at DESC);

-- =================================================================
-- FRICTION ORCHESTRATION
-- =================================================================

CREATE TABLE IF NOT EXISTS friction_policies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    channel VARCHAR(20) NOT NULL DEFAULT 'ANY',
    min_score INT NOT NULL DEFAULT 0,
    max_score INT NOT NULL DEFAULT 100,
    min_amount DECIMAL(15, 2),
    method VARCHAR(30) NOT NULL,
    vulnerable_only BOOLEAN NOT NULL DEFAULT false,
    skip_if_trusted_device BOOLEAN NOT NULL DEFAULT false,
    priority INT NOT NULL DEFAULT 100,
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_friction_policies_priority ON friction_policies(organization_id, priority) WHERE enabled;

CREATE TABLE IF NOT EXISTS friction_decisions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    user_id VARCHAR(255) NOT NULL,
    transaction_id UUID,
    channel VARCHAR(20) NOT NULL DEFAULT 'ANY',
    risk_score INT NOT NULL DEFAULT 0,
    amount DECIMAL(15, 2),
    method VARCHAR(30) NOT NULL,
    policy_id UUID,
    policy_name VARCHAR(255),
    reasons JSONB NOT NULL DEFAULT '[]'::jsonb,
    outcome VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_friction_decisions_org ON friction_decisions(organization_id, created_at DESC);

-- =================================================================
-- INTEGRATION SURFACE
-- =================================================================

CREATE TABLE IF NOT EXISTS webhook_subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    target_url VARCHAR(1000) NOT NULL,
    events JSONB NOT NULL DEFAULT '[]'::jsonb,
    secret_hash VARCHAR(64) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    failure_count INT NOT NULL DEFAULT 0,
    disabled_reason VARCHAR(500),
    last_delivery_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS webhook_deliveries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    subscription_id UUID NOT NULL REFERENCES webhook_subscriptions(id) ON DELETE CASCADE,
    event_type VARCHAR(60) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    response_code INT,
    error_message VARCHAR(1000),
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP,
    delivered_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_webhook_deliveries_pending ON webhook_deliveries(status, next_attempt_at);
CREATE INDEX idx_webhook_deliveries_org ON webhook_deliveries(organization_id, created_at DESC);

CREATE TABLE IF NOT EXISTS api_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    key_prefix VARCHAR(16) NOT NULL,
    key_hash VARCHAR(64) NOT NULL,
    scopes JSONB NOT NULL DEFAULT '[]'::jsonb,
    rate_limit_per_minute INT NOT NULL DEFAULT 600,
    active BOOLEAN NOT NULL DEFAULT true,
    last_used_at TIMESTAMP,
    expires_at TIMESTAMP,
    created_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_api_keys_hash ON api_keys(key_hash);

-- =================================================================
-- QUALITY ASSURANCE
-- =================================================================

CREATE TABLE IF NOT EXISTS qa_samples (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    subject_type VARCHAR(20) NOT NULL,
    subject_id UUID NOT NULL,
    decided_by_type VARCHAR(10) NOT NULL,
    decided_by UUID,
    original_decision VARCHAR(60),
    verdict VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    findings JSONB DEFAULT '[]'::jsonb,
    reviewer_notes TEXT,
    reviewed_by UUID,
    reviewed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_qa_samples_org ON qa_samples(organization_id, created_at DESC);

COMMENT ON TABLE qa_samples IS
'Sampled decisions, human and agent alike. Reviewing only the agents leaves no baseline to judge them against.';

-- =================================================================
-- SEED: SCREENING LIST
-- Illustrative entries so name screening and false-positive
-- reduction are demonstrable without a vendor feed.
-- =================================================================

INSERT INTO sanctions_list_entries (list_type, list_name, full_name, normalised_name, date_of_birth, country, program, active)
VALUES
    ('SANCTIONS', 'OFAC SDN', 'Viktor Petrov', 'petrov viktor', '1962-04-11', 'RU', 'UKRAINE-EO13662', true),
    ('SANCTIONS', 'OFAC SDN', 'Ahmed Hassan Al-Masri', 'ahmed al masri hassan', '1975-09-02', 'SY', 'SDGT', true),
    ('SANCTIONS', 'EU Consolidated', 'Chen Wei Lin', 'chen lin wei', '1980-01-23', 'KP', 'DPRK', true),
    ('SANCTIONS', 'UK HMT', 'Dmitri Sokolov', 'dmitri sokolov', '1971-07-30', 'RU', 'RUSSIA', true),
    ('SANCTIONS', 'UN Consolidated', 'Mohammed Ali', 'ali mohammed', '1962-03-15', 'AF', 'TALIBAN', true),
    ('PEP', 'Global PEP', 'Isabella Moreno', 'isabella moreno', '1968-11-05', 'VE', 'HEAD_OF_STATE_FAMILY', true),
    ('PEP', 'Global PEP', 'John Smith', 'john smith', '1955-02-18', 'GB', 'SENIOR_OFFICIAL', true),
    ('PEP', 'Global PEP', 'Olusegun Adeyemi', 'adeyemi olusegun', '1964-06-21', 'NG', 'MINISTER', true),
    ('ADVERSE_MEDIA', 'Media Screening', 'Marcus Webb', 'marcus webb', '1979-12-01', 'US', 'FRAUD_CONVICTION', true),
    ('ADVERSE_MEDIA', 'Media Screening', 'Elena Kovac', 'elena kovac', '1983-05-14', 'RS', 'MONEY_LAUNDERING', true),
    ('WATCHLIST', 'Internal Watchlist', 'Daniel Okafor', 'daniel okafor', '1990-08-08', 'NG', 'MULE_NETWORK', true),
    ('WATCHLIST', 'Internal Watchlist', 'Sophia Rossi', 'rossi sophia', '1987-10-27', 'IT', 'CARD_FRAUD', true);

-- =================================================================
-- SEED: FRICTION LADDER FOR THE DEMO ORGANIZATION
-- A sensible default so the platform is never silently open.
-- =================================================================

INSERT INTO friction_policies
    (organization_id, name, channel, min_score, max_score, min_amount, method,
     vulnerable_only, skip_if_trusted_device, priority, enabled)
VALUES
    ('550e8400-e29b-41d4-a716-446655440000', 'Vulnerable customer protection', 'ANY', 40, 100, NULL,
     'CALLBACK', true, false, 10, true),
    ('550e8400-e29b-41d4-a716-446655440000', 'Critical risk', 'ANY', 90, 100, NULL,
     'CALLBACK', false, false, 20, true),
    ('550e8400-e29b-41d4-a716-446655440000', 'High risk', 'ANY', 75, 89, NULL,
     'BIOMETRIC', false, false, 30, true),
    ('550e8400-e29b-41d4-a716-446655440000', 'High value payment', 'ANY', 50, 100, 10000,
     'PUSH_APPROVAL', false, false, 40, true),
    ('550e8400-e29b-41d4-a716-446655440000', 'Medium risk', 'ANY', 55, 74, NULL,
     'PUSH_APPROVAL', false, true, 50, true),
    ('550e8400-e29b-41d4-a716-446655440000', 'Low risk observation', 'ANY', 35, 54, NULL,
     'PASSIVE_BIOMETRIC', false, true, 60, true),
    ('550e8400-e29b-41d4-a716-446655440000', 'Trusted traffic', 'ANY', 0, 34, NULL,
     'NONE', false, false, 90, true);

-- =================================================================
-- SEED: AUTONOMY POLICY FOR THE DEMO ORGANIZATION
-- Conservative by design: agents may close and escalate, never freeze.
-- =================================================================

INSERT INTO autonomy_policies
    (organization_id, action_type, level, max_amount, max_risk_score, min_confidence,
     requires_maker_checker, daily_action_cap)
VALUES
    ('550e8400-e29b-41d4-a716-446655440000', 'CLOSE_ALERT', 'ACT_ON_LOW_RISK', 5000, 70, 80, false, 500),
    ('550e8400-e29b-41d4-a716-446655440000', 'ESCALATE_ALERT', 'FULL_AUTONOMY', NULL, NULL, 50, false, NULL),
    ('550e8400-e29b-41d4-a716-446655440000', 'SUMMARISE_CASE', 'FULL_AUTONOMY', NULL, NULL, 40, false, NULL),
    ('550e8400-e29b-41d4-a716-446655440000', 'CONTACT_CUSTOMER', 'ACT_WITHIN_LIMITS', 25000, 90, 70, false, 200),
    ('550e8400-e29b-41d4-a716-446655440000', 'DRAFT_SAR', 'ACT_WITHIN_LIMITS', NULL, NULL, 75, false, 50),
    ('550e8400-e29b-41d4-a716-446655440000', 'FILE_SAR', 'OFF', NULL, NULL, 100, true, NULL),
    ('550e8400-e29b-41d4-a716-446655440000', 'FREEZE_ACCOUNT', 'SUGGEST_ONLY', NULL, NULL, 95, true, NULL),
    ('550e8400-e29b-41d4-a716-446655440000', 'BLOCK_BENEFICIARY', 'SUGGEST_ONLY', NULL, NULL, 90, true, NULL),
    ('550e8400-e29b-41d4-a716-446655440000', 'DEPLOY_RULE', 'SUGGEST_ONLY', NULL, NULL, 95, true, NULL);
