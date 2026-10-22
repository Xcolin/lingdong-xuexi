-- Preserve event microseconds: rounding a fresh ledger into the next second
-- incorrectly makes the valid correction window appear to start in the future.
-- Existing event values and the future-event safety check remain unchanged.
ALTER TABLE growth_point_ledger MODIFY COLUMN occurred_at TIMESTAMP(6) NOT NULL;
