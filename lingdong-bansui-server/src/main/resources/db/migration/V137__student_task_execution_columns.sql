-- 学生任务执行域原型对齐补列（78 视图审计 05 组）：
-- 任务内容描述、要求清单、参考示例图；暂停调节方式与搁置原因说明；打卡完成情况。
ALTER TABLE learn_task ADD COLUMN description VARCHAR(500) NULL;
ALTER TABLE learn_task ADD COLUMN requirement_items VARCHAR(1000) NULL;
ALTER TABLE learn_task ADD COLUMN example_file_ids VARCHAR(500) NULL;

ALTER TABLE learn_task_pause ADD COLUMN adjust_method VARCHAR(32) NULL;
ALTER TABLE learn_task_pause ADD COLUMN reason_note VARCHAR(50) NULL;

ALTER TABLE learn_task_checkin ADD COLUMN completion VARCHAR(16) NULL;
ALTER TABLE learn_task_checkin
    ADD CONSTRAINT ck_task_checkin_completion
    CHECK (completion IS NULL OR completion IN ('FULL', 'PARTIAL'));
