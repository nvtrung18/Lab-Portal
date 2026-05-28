-- UC20 dashboard stats aggregate indexes.
CREATE INDEX idx_reports_project_group_submitter_status
    ON reports (project_id, group_id, submitted_by_id, status);

CREATE INDEX idx_reports_milestone_submitter_status
    ON reports (milestone_id, submitted_by_id, status);

CREATE INDEX idx_products_project_group_submitter
    ON products (project_id, group_id, submitted_by_id);

CREATE INDEX idx_evaluations_project_group_student
    ON evaluations (project_id, group_id, student_id);

CREATE INDEX idx_group_members_group_user_active
    ON group_members (group_id, user_id, active, deleted);
