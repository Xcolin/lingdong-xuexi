-- V40：机构管理员小程序认证与工作台基础。
-- 基础数据标识使用 19 位雪花数字，不使用数据库自增列。

INSERT INTO sys_feature_toggle (
    id, feature_code, feature_name, scope_type, scope_key, status, built_in, description
) VALUES (
    1874244142494646548, 'ORGANIZATION_MINIAPP_AUTH', '机构管理员小程序认证',
    'GLOBAL', 'GLOBAL', 'ENABLED', 1,
    '控制机构管理员小程序登录入口、会话认证和工作台访问。'
);
