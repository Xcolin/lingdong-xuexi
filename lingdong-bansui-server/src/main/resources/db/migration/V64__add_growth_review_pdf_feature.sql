-- 复盘 PDF 新导出独立开关；完整业务入口验收前默认停用。
-- 历史文件访问不由该开关撤权，仍检查当前权限、亲子关系和统一附件服务。
INSERT INTO sys_feature_toggle(id, feature_code, feature_name, scope_type, scope_key, status, built_in, description)
VALUES (1874244142494646688, 'GROWTH_REVIEW_PDF_EXPORT', '成长复盘PDF新导出', 'GLOBAL', 'GLOBAL', 'DISABLED', 1,
    '控制成长复盘PDF创建和后台生成；关闭后保留已生成文件的受控历史访问。');
