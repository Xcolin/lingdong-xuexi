-- 家长任务管理域原型对齐补列（78 视图审计 02 组 4.1）：
-- 任务类型六选、起止日期区间结束日、可见范围四选。
ALTER TABLE learn_task ADD COLUMN task_type VARCHAR(16) NULL;
ALTER TABLE learn_task
    ADD CONSTRAINT ck_learn_task_task_type
    CHECK (task_type IS NULL OR task_type IN ('STUDY', 'LIFE', 'SPORT', 'HOBBY', 'CHORE', 'OTHER'));

ALTER TABLE learn_task ADD COLUMN end_date DATE NULL;
ALTER TABLE learn_task
    ADD CONSTRAINT ck_learn_task_date_range
    CHECK (end_date IS NULL OR end_date >= scheduled_date);

ALTER TABLE learn_task ADD COLUMN visibility VARCHAR(32) NULL;
ALTER TABLE learn_task
    ADD CONSTRAINT ck_learn_task_visibility
    CHECK (visibility IS NULL OR visibility IN ('FAMILY_ONLY', 'FAMILY_CLASS_PARTNER', 'FAMILY_SCHOOL_PARTNER', 'CUSTOM'));
