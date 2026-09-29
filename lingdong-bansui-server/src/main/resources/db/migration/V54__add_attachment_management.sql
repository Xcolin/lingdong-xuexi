-- V54：附件统一管理的规则并发控制、功能开关和动态权限。
-- 不新增业务表；全部基础数据标识均为 19 位雪花数字。
ALTER TABLE sys_attachment_rule
    ADD COLUMN version_no BIGINT NOT NULL DEFAULT 0;

INSERT INTO sys_feature_toggle (
    id, feature_code, feature_name, scope_type, scope_key, status, built_in,
    description
) VALUES (
    1874244142494646603, 'ATTACHMENT_SERVICE', '附件服务',
    'GLOBAL', 'GLOBAL', 'ENABLED', 1,
    '统一控制附件规则、文件台账、上传、查看、预览、下载和删除能力。'
);

INSERT INTO sys_permission (
    id, permission_code, permission_name, resource_type, client_type,
    parent_id, sort_order, status, description
) VALUES
    (1874244142494646604, 'ATTACHMENT_RULE_READ', '查询附件规则',
     'OPERATION', 'WEB', NULL, 210, 'ENABLED', '查询附件规则及其允许的文件扩展名。'),
    (1874244142494646605, 'ATTACHMENT_RULE_MANAGE', '管理附件规则',
     'OPERATION', 'WEB', NULL, 220, 'ENABLED', '新增、编辑、启用和停用附件规则。'),
    (1874244142494646606, 'ATTACHMENT_FILE_LEDGER_READ', '查询附件文件台账',
     'OPERATION', 'WEB', NULL, 230, 'ENABLED', '查询安全文件元数据和业务关系，不授予文件内容读取权限。');

INSERT INTO sys_role_permission (id, role_id, permission_id, effect) VALUES
    (1874244142494646607, 1874244142494646273, 1874244142494646604, 'ALLOW'),
    (1874244142494646608, 1874244142494646273, 1874244142494646605, 'ALLOW'),
    (1874244142494646609, 1874244142494646273, 1874244142494646606, 'ALLOW');
