-- V26: Broker Account Reconciliation Scoping
-- Persists broker account provenance with fills, reconciliation runs, and mismatches.
-- Resolves historical cross-account false positive mismatches from September 12.

ALTER TABLE fills ADD COLUMN IF NOT EXISTS broker_account_id VARCHAR(128);
CREATE INDEX IF NOT EXISTS fills_broker_account_idx ON fills (broker_account_id);

ALTER TABLE reconciliation_runs ADD COLUMN IF NOT EXISTS broker_account_id VARCHAR(128);
CREATE INDEX IF NOT EXISTS reconciliation_runs_account_idx ON reconciliation_runs (broker_account_id);

ALTER TABLE reconciliation_mismatches ADD COLUMN IF NOT EXISTS broker_account_id VARCHAR(128);
CREATE INDEX IF NOT EXISTS reconciliation_mismatches_account_idx ON reconciliation_mismatches (broker_account_id);

-- Backfill provenance for existing fills
UPDATE fills
SET broker_account_id = 'LEGACY_ACCOUNT_SEPT12'
WHERE filled_at < '2026-10-01' AND broker_account_id IS NULL;

UPDATE fills
SET broker_account_id = 'PA36EX6RQWQT'
WHERE filled_at >= '2026-10-01' AND broker_account_id IS NULL;

-- Mark the 3 cross-account false positive critical mismatches from Cycle 18 as resolved
UPDATE reconciliation_mismatches
SET resolution_state = 'RESOLVED',
    resolved_at = NOW(),
    broker_account_id = 'LEGACY_ACCOUNT_SEPT12'
WHERE mismatch_type = 'FILL_MISSING_BROKER'
  AND resolution_state = 'UNRESOLVED'
  AND created_at < '2026-10-01 12:20:00';

-- Tag historical warning mismatch with legacy account provenance
UPDATE reconciliation_mismatches
SET broker_account_id = 'LEGACY_ACCOUNT_SEPT12'
WHERE mismatch_type = 'POSITION_PRICE_MISMATCH'
  AND created_at < '2026-10-01'
  AND broker_account_id IS NULL;
