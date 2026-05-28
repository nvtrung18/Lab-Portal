-- UC18 student-level research evaluation.
ALTER TABLE evaluations
    ADD COLUMN group_id BIGINT NULL AFTER project_id,
    ADD COLUMN student_id BIGINT NULL AFTER group_id,
    ADD COLUMN attendance_score DECIMAL(4,2) NULL AFTER reviewer_id,
    ADD COLUMN task_score DECIMAL(4,2) NULL AFTER attendance_score,
    ADD COLUMN report_score DECIMAL(4,2) NULL AFTER task_score,
    ADD COLUMN product_score DECIMAL(4,2) NULL AFTER report_score,
    ADD COLUMN attitude_score DECIMAL(4,2) NULL AFTER product_score;

UPDATE evaluations
SET
    student_id = COALESCE(student_id, reviewer_id),
    score = LEAST(score, 10.00),
    attendance_score = COALESCE(attendance_score, LEAST(score, 10.00)),
    task_score = COALESCE(task_score, LEAST(score, 10.00)),
    report_score = COALESCE(report_score, LEAST(score, 10.00)),
    product_score = COALESCE(product_score, LEAST(score, 10.00)),
    attitude_score = COALESCE(attitude_score, LEAST(score, 10.00));

ALTER TABLE evaluations
    MODIFY COLUMN student_id BIGINT NOT NULL,
    MODIFY COLUMN attendance_score DECIMAL(4,2) NOT NULL,
    MODIFY COLUMN task_score DECIMAL(4,2) NOT NULL,
    MODIFY COLUMN report_score DECIMAL(4,2) NOT NULL,
    MODIFY COLUMN product_score DECIMAL(4,2) NOT NULL,
    MODIFY COLUMN attitude_score DECIMAL(4,2) NOT NULL,
    ADD CONSTRAINT fk_evaluation_group FOREIGN KEY (group_id) REFERENCES research_groups (id),
    ADD CONSTRAINT fk_evaluation_student FOREIGN KEY (student_id) REFERENCES users (id),
    ADD INDEX idx_evaluations_student_id (student_id),
    ADD INDEX idx_evaluations_project_student (project_id, student_id);
