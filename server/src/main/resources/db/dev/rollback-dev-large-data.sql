-- ============================================================
-- Rollback FE checkpoint day 35 large development dataset
-- Safely and sequentially deletes NCKH mock data with 'D35' code/purpose markers
-- avoiding foreign key constraint violations.
-- ============================================================

SET NAMES utf8mb4;

START TRANSACTION;

-- 1. Clean report comments
DELETE FROM comments 
WHERE report_id IN (
    SELECT r.id FROM reports r 
    JOIN projects p ON r.project_id = p.id 
    WHERE p.code LIKE 'D35-%'
);

-- 2. Clean research reports
DELETE FROM reports 
WHERE project_id IN (
    SELECT id FROM projects 
    WHERE code LIKE 'D35-%'
);

-- 3. Clean research products
DELETE FROM products 
WHERE project_id IN (
    SELECT id FROM projects 
    WHERE code LIKE 'D35-%'
);

-- 4. Clean student evaluations
DELETE FROM evaluations 
WHERE project_id IN (
    SELECT id FROM projects 
    WHERE code LIKE 'D35-%'
);

-- 5. Clean research logs (UC19)
DELETE FROM research_logs 
WHERE project_id IN (
    SELECT id FROM projects 
    WHERE code LIKE 'D35-%'
);

-- 6. Clean tasks
DELETE FROM tasks 
WHERE milestone_id IN (
    SELECT m.id FROM milestones m 
    JOIN projects p ON m.project_id = p.id 
    WHERE p.code LIKE 'D35-%'
);

-- 7. Clean milestones
DELETE FROM milestones 
WHERE project_id IN (
    SELECT id FROM projects 
    WHERE code LIKE 'D35-%'
);

-- 8. Clean group members
DELETE FROM group_members 
WHERE group_id IN (
    SELECT g.id FROM research_groups g 
    JOIN projects p ON g.project_id = p.id 
    WHERE p.code LIKE 'D35-%'
);

-- 9. Clean research groups
DELETE FROM research_groups 
WHERE project_id IN (
    SELECT id FROM projects 
    WHERE code LIKE 'D35-%'
);

-- 10. Clean research projects
DELETE FROM projects 
WHERE code LIKE 'D35-%';

-- 11. Clean bookings added for NCKH testing
DELETE FROM bookings 
WHERE purpose LIKE '[D35]%';

COMMIT;
