-- MySQL needs a replacement task_id index before V29 removes the old unique index.
-- Preserve the task FK throughout a fresh schema installation.
CREATE INDEX idx_learn_task_assignment_task_fk ON learn_task_assignment(task_id);
