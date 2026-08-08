-- V31：为统一附件元数据补充登记模块、文件分类和内容摘要，并开放任务图片打卡基础规则。
ALTER TABLE sys_file
    ADD COLUMN module_code VARCHAR(64) NOT NULL DEFAULT 'LEGACY';

ALTER TABLE sys_file
    ADD COLUMN file_category VARCHAR(64) NOT NULL DEFAULT 'UNCLASSIFIED';

ALTER TABLE sys_file
    ADD COLUMN content_sha256 VARCHAR(64) NULL;

CREATE INDEX idx_sys_file_module_category_status
    ON sys_file (module_code, file_category, status);

-- 图片打卡允许不填写文字，应用层继续保证文字与图片至少提交一项。
ALTER TABLE learn_task_checkin
    MODIFY COLUMN content VARCHAR(1000) NULL;

INSERT INTO sys_attachment_rule (
    id, module_code, file_category, rule_name, max_file_size_bytes,
    max_batch_count, preview_enabled, download_scope, status
) VALUES (
    1874244142494646500, 'LEARNING_TASK_CHECKIN', 'IMAGE', '任务打卡图片',
    10485760, 9, 1, 'BUSINESS_AUTHORIZED', 'ENABLED'
);

INSERT INTO sys_attachment_rule_extension (id, rule_id, extension) VALUES
    (1874244142494646501, 1874244142494646500, 'jpg'),
    (1874244142494646502, 1874244142494646500, 'jpeg'),
    (1874244142494646503, 1874244142494646500, 'png');

INSERT INTO sys_permission (
    id, permission_code, permission_name, resource_type, client_type,
    parent_id, sort_order, status, description
) VALUES
    (1874244142494646504, 'ATTACHMENT_UPLOAD', '上传附件',
        'OPERATION', 'MINIAPP', NULL, 155, 'ENABLED',
        '学生在小程序通过统一附件服务上传本人任务打卡图片。'),
    (1874244142494646505, 'ATTACHMENT_READ', '读取业务附件',
        'OPERATION', 'BOTH', NULL, 156, 'ENABLED',
        '业务参与者按对象数据范围读取附件元数据和受控内容。');

INSERT INTO sys_role_permission (id, role_id, permission_id) VALUES
    (1874244142494646506, 1874244142494646278, 1874244142494646504),
    (1874244142494646507, 1874244142494646278, 1874244142494646505),
    (1874244142494646508, 1874244142494646277, 1874244142494646505),
    (1874244142494646509, 1874244142494646276, 1874244142494646505),
    (1874244142494646510, 1874244142494646275, 1874244142494646505);
