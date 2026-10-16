-- Forward-only weekday recurrence extension. Existing DAILY plans remain unchanged.
ALTER TABLE learn_task
 ADD COLUMN recurrence_frequency VARCHAR(16) NOT NULL DEFAULT 'DAILY',
 ADD COLUMN recurrence_weekdays JSON NULL,
 ADD CONSTRAINT ck_learn_task_frequency_v127 CHECK (recurrence_frequency IN ('DAILY','WEEKLY','CUSTOM'));
ALTER TABLE learn_task_recurrence ADD COLUMN recurrence_weekdays JSON NULL;
-- V30 declared this exact name. Read INFORMATION_SCHEMA rather than guessing a generated name.
SET @frequency_check = (SELECT CONSTRAINT_NAME FROM information_schema.TABLE_CONSTRAINTS
 WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='learn_task_recurrence'
 AND CONSTRAINT_TYPE='CHECK' AND CONSTRAINT_NAME='ck_learn_task_recurrence_frequency');
SET @drop_frequency = IF(@frequency_check IS NULL, 'SELECT 1', CONCAT('ALTER TABLE learn_task_recurrence DROP CHECK `', @frequency_check, '`'));
PREPARE weekday_frequency_stmt FROM @drop_frequency;
EXECUTE weekday_frequency_stmt;
DEALLOCATE PREPARE weekday_frequency_stmt;
ALTER TABLE learn_task_recurrence ADD CONSTRAINT ck_learn_task_recurrence_frequency_v127 CHECK (frequency_type IN ('DAILY','WEEKLY','CUSTOM'));
