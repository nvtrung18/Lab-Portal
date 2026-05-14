-- ============================================================
-- V21__research_stats_indexes.sql
-- Indexes for project statistics queries
-- ============================================================

-- idx_reports_task_id already exists in V19__research_report.sql.
CREATE INDEX idx_tasks_status ON tasks (status);
