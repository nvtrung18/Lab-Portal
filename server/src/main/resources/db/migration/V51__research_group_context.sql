-- ============================================================
-- V51__research_group_context.sql
-- Introduce group_id column in milestones and migrate legacy NCKH records to group context
-- ============================================================

-- 1. Add group_id column to milestones table
ALTER TABLE milestones ADD COLUMN group_id BIGINT NULL;
ALTER TABLE milestones ADD CONSTRAINT fk_milestone_group FOREIGN KEY (group_id) REFERENCES research_groups (id);
ALTER TABLE milestones ADD INDEX idx_milestone_group (group_id);

-- 2. Migrate legacy milestones to their respective groups
UPDATE milestones m
JOIN projects p ON p.id = m.project_id
SET m.group_id = p.group_id
WHERE m.group_id IS NULL AND p.group_id IS NOT NULL;

UPDATE milestones m
SET m.group_id = (
    SELECT rg.id FROM research_groups rg
    WHERE rg.project_id = m.project_id
    LIMIT 1
)
WHERE m.group_id IS NULL
  AND (SELECT COUNT(*) FROM research_groups rg WHERE rg.project_id = m.project_id) = 1;

-- 3. Migrate legacy products to their respective groups
UPDATE products pdt
JOIN projects p ON p.id = pdt.project_id
SET pdt.group_id = p.group_id
WHERE pdt.group_id IS NULL AND p.group_id IS NOT NULL;

UPDATE products pdt
SET pdt.group_id = (
    SELECT rg.id FROM research_groups rg
    WHERE rg.project_id = pdt.project_id
    LIMIT 1
)
WHERE pdt.group_id IS NULL
  AND (SELECT COUNT(*) FROM research_groups rg WHERE rg.project_id = pdt.project_id) = 1;

-- 4. Migrate legacy evaluations to their respective groups
UPDATE evaluations e
JOIN projects p ON p.id = e.project_id
SET e.group_id = p.group_id
WHERE e.group_id IS NULL AND p.group_id IS NOT NULL;

UPDATE evaluations e
SET e.group_id = (
    SELECT rg.id FROM research_groups rg
    WHERE rg.project_id = e.project_id
    LIMIT 1
)
WHERE e.group_id IS NULL
  AND (SELECT COUNT(*) FROM research_groups rg WHERE rg.project_id = e.project_id) = 1;

-- 5. Migrate legacy research logs to their respective groups
UPDATE research_logs rl
JOIN projects p ON p.id = rl.project_id
SET rl.group_id = p.group_id
WHERE rl.group_id IS NULL AND p.group_id IS NOT NULL;

UPDATE research_logs rl
SET rl.group_id = (
    SELECT rg.id FROM research_groups rg
    WHERE rg.project_id = rl.project_id
    LIMIT 1
)
WHERE rl.group_id IS NULL
  AND (SELECT COUNT(*) FROM research_groups rg WHERE rg.project_id = rl.project_id) = 1;

-- 6. Migrate legacy reports to their respective groups
UPDATE reports r
JOIN projects p ON p.id = r.project_id
SET r.group_id = p.group_id
WHERE r.group_id IS NULL AND p.group_id IS NOT NULL;

UPDATE reports r
SET r.group_id = (
    SELECT rg.id FROM research_groups rg
    WHERE rg.project_id = r.project_id
    LIMIT 1
)
WHERE r.group_id IS NULL
  AND (SELECT COUNT(*) FROM research_groups rg WHERE rg.project_id = r.project_id) = 1;
