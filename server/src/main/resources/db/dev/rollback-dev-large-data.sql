-- ============================================================
-- Rollback FE checkpoint day 35 large development dataset
-- Deletes feature data with an explicit D35 marker.
-- Shared accounts, labs, memberships and visible slot fixtures are retained.
-- time_slots do not have a seed marker column, so deleting them here could
-- remove a slot that already existed before the seed was run.
-- ============================================================

START TRANSACTION;

DELETE FROM bookings
WHERE purpose LIKE '[D35]%';

-- Deleting D35 projects cascades to attached groups, milestones and tasks.
DELETE FROM projects
WHERE code IN ('D35-AI-FACE', 'D35-AI-LAB', 'D35-ROBOT');

COMMIT;
