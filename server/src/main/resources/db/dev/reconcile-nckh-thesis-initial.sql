-- ONE-TIME reconciliation for the older progress-rich NCKH thesis demo fixture.
-- Manual MySQL 8 development use only. Never run against a database containing
-- real NCKH activity. Review the preflight counts and use mysql without --force:
-- any guard error aborts the client session and rolls back this transaction.
-- Scope: the three exact THESIS-NCKH-2026 project codes in the demo lab.
-- Users, lab, topics, projects, groups and group memberships are preserved.
-- Run the current seed-nckh-thesis.sql afterward to inspect its acceptance checks.
SET NAMES utf8mb4;

SET @nckh_lab := (SELECT id FROM laboratories WHERE lab_name = 'NCKH Thesis Demo 2026' LIMIT 1);
SET @nckh_project1 := (SELECT id FROM projects WHERE code = 'THESIS-NCKH-2026' LIMIT 1);
SET @nckh_project2 := (SELECT id FROM projects WHERE code = 'THESIS-NCKH-2026-B' LIMIT 1);
SET @nckh_project3 := (SELECT id FROM projects WHERE code = 'THESIS-NCKH-2026-C' LIMIT 1);
SET @nckh_manager := (SELECT id FROM users WHERE username = 'nckh_manager' LIMIT 1);
SET @nckh_leader := (SELECT id FROM users WHERE username = 'nckh_leader' LIMIT 1);
SET @nckh_member2 := (SELECT id FROM users WHERE username = 'nckh_member2' LIMIT 1);
-- Observed local import window; an added row created later fails the guard.
SET @nckh_import_start := '2026-09-26 10:37:32';
SET @nckh_import_end := '2026-09-26 10:37:33';

SELECT 'fixture_projects' AS item, COUNT(*) AS total FROM projects
WHERE id IN (@nckh_project1, @nckh_project2, @nckh_project3)
UNION ALL SELECT 'fixture_groups', COUNT(*) FROM research_groups
          WHERE project_id IN (@nckh_project1, @nckh_project2, @nckh_project3)
UNION ALL SELECT 'fixture_members', COUNT(*) FROM group_members
          WHERE group_id IN (SELECT id FROM research_groups
                             WHERE project_id IN (@nckh_project1, @nckh_project2, @nckh_project3))
UNION ALL SELECT 'milestones_to_remove', COUNT(*) FROM milestones
          WHERE project_id IN (@nckh_project1, @nckh_project2, @nckh_project3)
UNION ALL SELECT 'tasks_to_remove', COUNT(*) FROM tasks
          WHERE project_id IN (@nckh_project1, @nckh_project2, @nckh_project3)
UNION ALL SELECT 'reports_to_remove', COUNT(*) FROM reports
          WHERE project_id IN (@nckh_project1, @nckh_project2, @nckh_project3)
UNION ALL SELECT 'proposals_to_remove', COUNT(*) FROM research_task_proposal
          WHERE project_id IN (@nckh_project1, @nckh_project2, @nckh_project3)
UNION ALL SELECT 'research_logs_to_remove', COUNT(*) FROM research_logs
          WHERE project_id IN (@nckh_project1, @nckh_project2, @nckh_project3)
UNION ALL SELECT 'evaluations_to_remove', COUNT(*) FROM evaluations
          WHERE project_id IN (@nckh_project1, @nckh_project2, @nckh_project3)
UNION ALL SELECT 'products_to_remove', COUNT(*) FROM products
          WHERE project_id IN (@nckh_project1, @nckh_project2, @nckh_project3)
UNION ALL SELECT 'project_logs_to_remove', COUNT(*) FROM project_logs
          WHERE project_id IN (@nckh_project1, @nckh_project2, @nckh_project3)
UNION ALL SELECT 'task_comments_to_remove', COUNT(*) FROM task_comments
          WHERE task_id IN (SELECT id FROM tasks
                            WHERE project_id IN (@nckh_project1, @nckh_project2, @nckh_project3))
UNION ALL SELECT 'task_activity_to_remove', COUNT(*) FROM research_task_activity
          WHERE task_id IN (SELECT id FROM tasks
                            WHERE project_id IN (@nckh_project1, @nckh_project2, @nckh_project3))
UNION ALL SELECT 'report_comments_to_remove', COUNT(*) FROM comments c JOIN reports r ON r.id = c.report_id
          WHERE r.project_id IN (@nckh_project1, @nckh_project2, @nckh_project3)
UNION ALL SELECT 'fixture_actor_audit_rows', COUNT(*) FROM audit_logs
          WHERE actor_id IN (SELECT id FROM users WHERE username LIKE 'nckh%')
UNION ALL SELECT 'fixture_actor_system_audit_rows', COUNT(*) FROM system_audit_logs
          WHERE actor_id IN (SELECT id FROM users WHERE username LIKE 'nckh%');

-- A failing CHECK stops execution before any permanent row is changed.
-- These structural guards keep a fixture code collision or cross-project FK
-- from silently changing data outside the three intended projects.
DROP TEMPORARY TABLE IF EXISTS nckh_reconcile_guard;
CREATE TEMPORARY TABLE nckh_reconcile_guard (
    ok TINYINT NOT NULL,
    CONSTRAINT chk_nckh_reconcile_guard CHECK (ok = 1)
) ENGINE=InnoDB;
INSERT INTO nckh_reconcile_guard (ok)
SELECT CASE WHEN
    (SELECT COUNT(*) FROM laboratories WHERE lab_name = 'NCKH Thesis Demo 2026') = 1
    AND (SELECT COUNT(*) FROM projects
         WHERE code IN ('THESIS-NCKH-2026', 'THESIS-NCKH-2026-B', 'THESIS-NCKH-2026-C')) = 3
    AND (SELECT COUNT(*) FROM projects
         WHERE id IN (@nckh_project1, @nckh_project2, @nckh_project3)
           AND lab_id = @nckh_lab AND active = TRUE AND deleted = FALSE) = 3
    AND (SELECT COUNT(DISTINCT topic_id) FROM projects
         WHERE id IN (@nckh_project1, @nckh_project2, @nckh_project3)) = 3
    AND (SELECT COUNT(*) FROM research_topics
         WHERE id IN (SELECT topic_id FROM projects
                      WHERE id IN (@nckh_project1, @nckh_project2, @nckh_project3))
           AND lab_id = @nckh_lab AND active = TRUE AND deleted = FALSE) = 3
    AND (SELECT COUNT(*) FROM research_groups
         WHERE project_id IN (@nckh_project1, @nckh_project2, @nckh_project3)
           AND lab_id = @nckh_lab AND active = TRUE AND deleted = FALSE) = 3
    AND (SELECT COUNT(DISTINCT project_id) FROM research_groups
         WHERE project_id IN (@nckh_project1, @nckh_project2, @nckh_project3)) = 3
    AND (SELECT COUNT(*) FROM group_members
         WHERE group_id IN (SELECT id FROM research_groups
                            WHERE project_id IN (@nckh_project1, @nckh_project2, @nckh_project3))
           AND active = TRUE AND deleted = FALSE) = 12
    AND (SELECT COUNT(*) FROM memberships m JOIN users u ON u.id = m.user_id
         WHERE m.lab_id = @nckh_lab AND m.active = TRUE AND m.deleted = FALSE
           AND u.username IN ('nckh_member1', 'nckh_member2')) = 2
    -- Match old seed counts, owners, states and untouched timestamps.
    -- Every old seed INSERT set created_at and updated_at to the same NOW(6) value.
    AND (SELECT COUNT(*) FROM milestones
         WHERE project_id IN (@nckh_project1, @nckh_project2, @nckh_project3)) = 6
    AND (SELECT COUNT(*) FROM tasks
         WHERE project_id IN (@nckh_project1, @nckh_project2, @nckh_project3)) = 13
    AND (SELECT COUNT(*) FROM reports
         WHERE project_id IN (@nckh_project1, @nckh_project2, @nckh_project3)) = 0
    AND (SELECT COUNT(*) FROM research_task_proposal
         WHERE project_id IN (@nckh_project1, @nckh_project2, @nckh_project3)) = 1
    AND (SELECT COUNT(*) FROM research_logs
         WHERE project_id IN (@nckh_project1, @nckh_project2, @nckh_project3)) = 5
    AND (SELECT COUNT(*) FROM evaluations
         WHERE project_id IN (@nckh_project1, @nckh_project2, @nckh_project3)) = 3
    AND (SELECT COUNT(*) FROM products
         WHERE project_id IN (@nckh_project1, @nckh_project2, @nckh_project3)) = 1
    AND (SELECT COUNT(*) FROM project_logs
         WHERE project_id IN (@nckh_project1, @nckh_project2, @nckh_project3)) = 0
    AND (SELECT COUNT(*) FROM task_comments c JOIN tasks t ON t.id = c.task_id
         WHERE t.project_id IN (@nckh_project1, @nckh_project2, @nckh_project3)) = 1
    AND (SELECT COUNT(*) FROM research_task_activity a JOIN tasks t ON t.id = a.task_id
         WHERE t.project_id IN (@nckh_project1, @nckh_project2, @nckh_project3)) = 1
    AND (SELECT COUNT(*) FROM comments c JOIN reports r ON r.id = c.report_id
         WHERE r.project_id IN (@nckh_project1, @nckh_project2, @nckh_project3)) = 0
    AND (SELECT COUNT(*) FROM audit_logs
         WHERE actor_id IN (SELECT id FROM users WHERE username LIKE 'nckh%')) = 0
    AND (SELECT COUNT(*) FROM system_audit_logs
         WHERE actor_id IN (SELECT id FROM users WHERE username LIKE 'nckh%')) = 0
    AND (SELECT COUNT(*) FROM audit_logs
         WHERE (target_type = 'PROJECT' AND target_id IN (@nckh_project1, @nckh_project2, @nckh_project3))
            OR (target_type = 'GROUP' AND target_id IN
                (SELECT id FROM research_groups WHERE project_id IN (@nckh_project1, @nckh_project2, @nckh_project3)))
            OR (target_type = 'MILESTONE' AND target_id IN
                (SELECT id FROM milestones WHERE project_id IN (@nckh_project1, @nckh_project2, @nckh_project3)))
            OR (target_type = 'TASK' AND target_id IN
                (SELECT id FROM tasks WHERE project_id IN (@nckh_project1, @nckh_project2, @nckh_project3)))
            OR (target_type = 'PRODUCT' AND target_id IN
                (SELECT id FROM products WHERE project_id IN (@nckh_project1, @nckh_project2, @nckh_project3)))) = 0
    AND (SELECT COUNT(*) FROM milestones m JOIN research_groups g ON g.id = m.group_id
         JOIN group_members gm ON gm.group_id = g.id AND gm.user_id = m.assigned_to_student_id
         WHERE m.project_id IN (@nckh_project1, @nckh_project2, @nckh_project3)
           AND g.project_id = m.project_id AND m.created_by = @nckh_manager
           AND gm.active = TRUE AND gm.deleted = FALSE
           AND m.updated_at = m.created_at) = 6
    AND (SELECT COUNT(*) FROM milestones
         WHERE project_id IN (@nckh_project1, @nckh_project2, @nckh_project3)
           AND status = 'COMPLETED' AND progress_percent = 100) = 3
    AND (SELECT COUNT(*) FROM milestones
         WHERE project_id IN (@nckh_project1, @nckh_project2, @nckh_project3)
           AND status = 'IN_PROGRESS') = 3
    AND (SELECT COUNT(*) FROM tasks t JOIN research_groups g ON g.id = t.group_id
         JOIN group_members gm ON gm.group_id = g.id AND gm.user_id = t.assignee_id
         WHERE t.project_id IN (@nckh_project1, @nckh_project2, @nckh_project3)
           AND g.project_id = t.project_id
           AND t.created_by IN (@nckh_manager, g.leader_id)
           AND gm.active = TRUE AND gm.deleted = FALSE
           AND t.updated_at = t.created_at) = 13
    AND (SELECT COUNT(*) FROM tasks
         WHERE project_id IN (@nckh_project1, @nckh_project2, @nckh_project3)
           AND status = 'DONE' AND progress_percent = 100) = 6
    AND (SELECT COUNT(*) FROM tasks
         WHERE project_id IN (@nckh_project1, @nckh_project2, @nckh_project3)
           AND status = 'IN_PROGRESS') = 4
    AND (SELECT COUNT(*) FROM tasks
         WHERE project_id IN (@nckh_project1, @nckh_project2, @nckh_project3)
           AND status = 'TODO' AND progress_percent = 0) = 3
    AND (SELECT COUNT(*) FROM research_task_proposal p JOIN research_groups g ON g.id = p.group_id
         WHERE p.project_id = @nckh_project1 AND g.project_id = p.project_id
           AND p.proposed_by = @nckh_member2 AND p.status = 'PENDING'
           AND p.reviewed_by IS NULL AND p.updated_at = p.created_at) = 1
    AND (SELECT COUNT(*) FROM research_logs l JOIN research_groups g ON g.id = l.group_id
         JOIN group_members gm ON gm.group_id = g.id AND gm.user_id = l.author_id
         WHERE l.project_id IN (@nckh_project1, @nckh_project2, @nckh_project3)
           AND g.project_id = l.project_id AND gm.active = TRUE AND gm.deleted = FALSE
           AND l.updated_at = l.created_at) = 5
    AND (SELECT COUNT(*) FROM evaluations e JOIN research_groups g ON g.id = e.group_id
         JOIN group_members gm ON gm.group_id = g.id AND gm.user_id = e.student_id
         WHERE e.project_id = @nckh_project1 AND g.project_id = e.project_id
           AND e.reviewer_id = @nckh_manager AND gm.active = TRUE AND gm.deleted = FALSE
           AND e.updated_at = e.created_at) = 3
    AND (SELECT COUNT(*) FROM products p JOIN research_groups g ON g.id = p.group_id
         WHERE p.project_id = @nckh_project1 AND g.project_id = p.project_id
           AND p.submitted_by_id = @nckh_leader AND p.product_type = 'SOURCE_CODE'
           AND p.status = 'SUBMITTED' AND p.version = 1
           AND p.file_url IS NULL AND p.file_name IS NULL AND p.file_type IS NULL AND p.file_size IS NULL
           AND p.external_link = 'https://github.com/nvtrung18/Lab-Portal'
           AND p.updated_at = p.created_at) = 1
    AND (SELECT COUNT(*) FROM task_comments c JOIN tasks t ON t.id = c.task_id
         WHERE t.project_id = @nckh_project1 AND c.author_id = @nckh_manager
           AND c.updated_at = c.created_at) = 1
    AND (SELECT COUNT(*) FROM research_task_activity a JOIN tasks t ON t.id = a.task_id
         WHERE t.project_id = @nckh_project1 AND a.user_id = @nckh_leader
           AND a.action = 'STATUS_CHANGED' AND a.old_value = 'IN_PROGRESS'
           AND a.new_value = 'DONE' AND a.updated_at = a.created_at) = 1
    AND (SELECT COUNT(*) FROM (
         SELECT created_at FROM milestones WHERE project_id IN (@nckh_project1, @nckh_project2, @nckh_project3)
         UNION ALL SELECT created_at FROM tasks WHERE project_id IN (@nckh_project1, @nckh_project2, @nckh_project3)
         UNION ALL SELECT created_at FROM research_task_proposal WHERE project_id IN (@nckh_project1, @nckh_project2, @nckh_project3)
         UNION ALL SELECT created_at FROM research_logs WHERE project_id IN (@nckh_project1, @nckh_project2, @nckh_project3)
         UNION ALL SELECT created_at FROM evaluations WHERE project_id IN (@nckh_project1, @nckh_project2, @nckh_project3)
         UNION ALL SELECT created_at FROM products WHERE project_id IN (@nckh_project1, @nckh_project2, @nckh_project3)
         UNION ALL SELECT c.created_at FROM task_comments c JOIN tasks t ON t.id = c.task_id
                   WHERE t.project_id IN (@nckh_project1, @nckh_project2, @nckh_project3)
         UNION ALL SELECT a.created_at FROM research_task_activity a JOIN tasks t ON t.id = a.task_id
                   WHERE t.project_id IN (@nckh_project1, @nckh_project2, @nckh_project3)
         ) seed_rows
         WHERE seed_rows.created_at < @nckh_import_start
            OR seed_rows.created_at >= @nckh_import_end) = 0
    AND NOT EXISTS (
        SELECT 1 FROM tasks child JOIN tasks parent ON parent.id = child.parent_task_id
        WHERE parent.project_id IN (@nckh_project1, @nckh_project2, @nckh_project3)
    )
    AND NOT EXISTS (
        SELECT 1 FROM tasks t JOIN milestones m ON m.id = t.milestone_id
        WHERE m.project_id IN (@nckh_project1, @nckh_project2, @nckh_project3)
          AND (t.project_id IS NULL OR t.project_id NOT IN (@nckh_project1, @nckh_project2, @nckh_project3))
    )
    AND NOT EXISTS (
        SELECT 1 FROM reports r
        LEFT JOIN tasks t ON t.id = r.task_id
        JOIN milestones m ON m.id = r.milestone_id
        WHERE (m.project_id IN (@nckh_project1, @nckh_project2, @nckh_project3)
               OR t.project_id IN (@nckh_project1, @nckh_project2, @nckh_project3))
          AND (r.project_id IS NULL OR r.project_id NOT IN (@nckh_project1, @nckh_project2, @nckh_project3))
    )
    AND NOT EXISTS (
        SELECT 1 FROM research_logs l
        LEFT JOIN tasks t ON t.id = l.task_id
        LEFT JOIN milestones m ON m.id = l.milestone_id
        WHERE (m.project_id IN (@nckh_project1, @nckh_project2, @nckh_project3)
               OR t.project_id IN (@nckh_project1, @nckh_project2, @nckh_project3))
          AND l.project_id NOT IN (@nckh_project1, @nckh_project2, @nckh_project3)
    )
    AND NOT EXISTS (
        SELECT 1 FROM research_task_proposal p
        LEFT JOIN milestones m ON m.id = p.milestone_id
        WHERE m.project_id IN (@nckh_project1, @nckh_project2, @nckh_project3)
          AND p.project_id NOT IN (@nckh_project1, @nckh_project2, @nckh_project3)
    )
    THEN 1 ELSE 0 END;

START TRANSACTION;

-- NO ACTION task-activity and proposal FKs require deleting children first.
DELETE a FROM research_task_activity a JOIN tasks t ON t.id = a.task_id
WHERE t.project_id IN (@nckh_project1, @nckh_project2, @nckh_project3);
DELETE c FROM task_comments c JOIN tasks t ON t.id = c.task_id
WHERE t.project_id IN (@nckh_project1, @nckh_project2, @nckh_project3);
DELETE FROM research_task_proposal
WHERE project_id IN (@nckh_project1, @nckh_project2, @nckh_project3);
DELETE FROM reports
WHERE project_id IN (@nckh_project1, @nckh_project2, @nckh_project3);
DELETE FROM research_logs
WHERE project_id IN (@nckh_project1, @nckh_project2, @nckh_project3);
DELETE FROM evaluations
WHERE project_id IN (@nckh_project1, @nckh_project2, @nckh_project3);
DELETE FROM products
WHERE project_id IN (@nckh_project1, @nckh_project2, @nckh_project3);
DELETE FROM project_logs
WHERE project_id IN (@nckh_project1, @nckh_project2, @nckh_project3);
DELETE FROM tasks
WHERE project_id IN (@nckh_project1, @nckh_project2, @nckh_project3);
DELETE FROM milestones
WHERE project_id IN (@nckh_project1, @nckh_project2, @nckh_project3);

UPDATE projects SET status = 'DRAFT'
WHERE id IN (@nckh_project1, @nckh_project2, @nckh_project3);
UPDATE research_topics SET status = 'RECRUITING'
WHERE id IN (SELECT topic_id FROM projects
             WHERE id IN (@nckh_project1, @nckh_project2, @nckh_project3));
UPDATE research_groups SET status = 'ACTIVE'
WHERE project_id IN (@nckh_project1, @nckh_project2, @nckh_project3);
UPDATE memberships m JOIN users u ON u.id = m.user_id
SET m.role = CASE u.username
                 WHEN 'nckh_member1' THEN 'DEPUTY'
                 ELSE 'SECRETARY'
             END
WHERE m.lab_id = @nckh_lab AND m.active = TRUE AND m.deleted = FALSE
  AND u.username IN ('nckh_member1', 'nckh_member2');

-- A failing postcondition guard aborts the session and rolls back the changes.
INSERT INTO nckh_reconcile_guard (ok)
SELECT CASE WHEN
    (SELECT COUNT(*) FROM milestones WHERE project_id IN (@nckh_project1, @nckh_project2, @nckh_project3)) = 0
    AND (SELECT COUNT(*) FROM tasks WHERE project_id IN (@nckh_project1, @nckh_project2, @nckh_project3)) = 0
    AND (SELECT COUNT(*) FROM reports WHERE project_id IN (@nckh_project1, @nckh_project2, @nckh_project3)) = 0
    AND (SELECT COUNT(*) FROM research_task_proposal WHERE project_id IN (@nckh_project1, @nckh_project2, @nckh_project3)) = 0
    AND (SELECT COUNT(*) FROM research_logs WHERE project_id IN (@nckh_project1, @nckh_project2, @nckh_project3)) = 0
    AND (SELECT COUNT(*) FROM evaluations WHERE project_id IN (@nckh_project1, @nckh_project2, @nckh_project3)) = 0
    AND (SELECT COUNT(*) FROM products WHERE project_id IN (@nckh_project1, @nckh_project2, @nckh_project3)) = 0
    AND (SELECT COUNT(*) FROM project_logs WHERE project_id IN (@nckh_project1, @nckh_project2, @nckh_project3)) = 0
    AND (SELECT COUNT(*) FROM projects WHERE id IN (@nckh_project1, @nckh_project2, @nckh_project3) AND status = 'DRAFT') = 3
    AND (SELECT COUNT(*) FROM research_topics
         WHERE id IN (SELECT topic_id FROM projects
                      WHERE id IN (@nckh_project1, @nckh_project2, @nckh_project3))
           AND status = 'RECRUITING') = 3
    AND (SELECT COUNT(*) FROM memberships m JOIN users u ON u.id = m.user_id
         WHERE m.lab_id = @nckh_lab AND m.active = TRUE AND m.deleted = FALSE
           AND ((u.username = 'nckh_member1' AND m.role = 'DEPUTY')
             OR (u.username = 'nckh_member2' AND m.role = 'SECRETARY'))) = 2
    THEN 1 ELSE 0 END;

COMMIT;
DROP TEMPORARY TABLE nckh_reconcile_guard;
