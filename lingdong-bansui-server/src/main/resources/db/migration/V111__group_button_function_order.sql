-- Keep each sub-function together; put core page operations before secondary dialogs.
CREATE TEMPORARY TABLE button_function_order(id BIGINT PRIMARY KEY,new_order INT NOT NULL);
INSERT INTO button_function_order(id,new_order)
SELECT id,ROW_NUMBER() OVER(PARTITION BY parent_id ORDER BY
 CASE WHEN function_name IN ('组织架构','菜单配置','数据字典','用户管理','角色管理','教师管理','考勤记录','学习任务','成长积分','成长复盘','奖励管理','数据导出','导入校验','导入导出模板','功能开关','系统任务','附件管理','接口服务','缓存操作','家长关系','学生登录凭证','工作台') THEN 0 ELSE 1 END,
 group_order,function_name,
 CASE
 WHEN action_name LIKE '%查询%' OR action_name LIKE '%查看%' OR action_name IN ('详情','导出历史') THEN 0
 WHEN action_name LIKE '%新增%' OR action_name LIKE '%新建%' OR action_name LIKE '%创建%' THEN 10
 WHEN action_name LIKE '%编辑%' OR action_name LIKE '%修改%' OR action_name LIKE '%纠错%' THEN 20
 WHEN action_name LIKE '%关联%' OR action_name LIKE '%绑定%' OR action_name LIKE '%授权%' OR action_name LIKE '%配置%' THEN 30
 WHEN action_name LIKE '%批准%' OR action_name LIKE '%通过%' OR action_name='同意' THEN 40
 WHEN action_name LIKE '%驳回%' THEN 45
 WHEN action_name LIKE '%删除%' OR action_name LIKE '%注销%' OR action_name LIKE '%停用%' OR action_name LIKE '%下线%' OR action_name LIKE '%撤销%' OR action_name LIKE '%解除%' THEN 90
 WHEN action_name LIKE '%保存%' OR action_name LIKE '%提交%' OR action_name LIKE '%确认%' OR action_name='确定' THEN 70
 ELSE 60 END,
 sort_order,id)*10
FROM (
 SELECT id,parent_id,name,sort_order,
 SUBSTRING_INDEX(name,' · ',1) AS function_name,
 SUBSTRING_INDEX(name,' · ',-1) AS action_name,
 MIN(sort_order) OVER(PARTITION BY parent_id,SUBSTRING_INDEX(name,' · ',1)) AS group_order
 FROM sys_menu WHERE type='BUTTON'
) grouped_buttons;
UPDATE sys_menu m JOIN button_function_order ordered ON ordered.id=m.id SET m.sort_order=ordered.new_order,m.version=m.version+1;
UPDATE sys_permission SET sort_order=(SELECT sort_order FROM sys_menu WHERE type='BUTTON' AND code=sys_permission.permission_code)
WHERE client_type='WEB' AND EXISTS(SELECT 1 FROM sys_menu WHERE type='BUTTON' AND code=sys_permission.permission_code);
DROP TEMPORARY TABLE button_function_order;
