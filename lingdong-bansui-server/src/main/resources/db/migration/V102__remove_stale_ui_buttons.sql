-- 清理组件已不再引用的纯 UI 按钮配置（29 个）。
-- 背景：多轮交互改版后，以下按钮编码在前端动作目录中已无任何组件绑定：
--   1) 各页「刷新/重试」类按钮被去除（工作台、考勤台账、功能开关、积分台账、成长复盘、
--      奖励、系统任务、数据导出中心、学习任务顺延队列、匿名排行、排行授权）；
--   2) 菜单管理改拖拽排序与集中操作区后废弃的旧交互按钮（批量按钮/移动/添加一行/提交/确定）；
--   3) 角色与权限改级联勾选树与行内快捷授权后废弃的旧授权按钮（授权/批量授用户/保存授权/授予/授予角色）；
--   4) 系统任务改由工作台跳转业务页审核后废弃的旧按钮（刷新/批准/驳回）。
-- 这些按钮对任何角色均不可见，仅存在配置残留。处理顺序与 V100 相同：
-- 先删角色与用户授权，再删菜单按钮节点，最后删权限行；审计历史保留。
DELETE FROM sys_role_permission WHERE permission_id IN (SELECT id FROM sys_permission WHERE permission_code IN (
 'anonymous-ranks.anonymous-rank-page.1','anonymous-ranks.anonymous-rank-page.2',
 'attendance-records.attendance-page.2','dashboard.dashboard-page.2',
 'export-jobs.export-job-management-page.6','export-jobs.export-job-review-panel.1',
 'feature-management.feature-management-page.3','growth-points.growth-point-page.2',
 'growth-reviews.growth-review-export-history.2','growth-reviews.growth-review-page.1',
 'growth-reviews.growth-review-subscription-panel.1',
 'iam.iam-management-page.12','iam.iam-management-page.13','iam.iam-management-page.14','iam.iam-management-page.15','iam.iam-management-page.16',
 'learning-tasks.task-defer-queue.2',
 'menu-management.menu-management-page.10','menu-management.menu-management-page.11','menu-management.menu-management-page.12','menu-management.menu-management-page.13','menu-management.menu-management-page.14',
 'organizations.class-management-panel.3','organizations.organization-change-review-panel.3',
 'rank-preferences.anonymous-rank-withdrawal-page.1','rewards.reward-management-page.1',
 'system-tasks.system-task-workbench.1','system-tasks.system-task-workbench.6','system-tasks.system-task-workbench.7'
));
DELETE FROM sys_user_permission WHERE permission_id IN (SELECT id FROM sys_permission WHERE permission_code IN (
 'anonymous-ranks.anonymous-rank-page.1','anonymous-ranks.anonymous-rank-page.2',
 'attendance-records.attendance-page.2','dashboard.dashboard-page.2',
 'export-jobs.export-job-management-page.6','export-jobs.export-job-review-panel.1',
 'feature-management.feature-management-page.3','growth-points.growth-point-page.2',
 'growth-reviews.growth-review-export-history.2','growth-reviews.growth-review-page.1',
 'growth-reviews.growth-review-subscription-panel.1',
 'iam.iam-management-page.12','iam.iam-management-page.13','iam.iam-management-page.14','iam.iam-management-page.15','iam.iam-management-page.16',
 'learning-tasks.task-defer-queue.2',
 'menu-management.menu-management-page.10','menu-management.menu-management-page.11','menu-management.menu-management-page.12','menu-management.menu-management-page.13','menu-management.menu-management-page.14',
 'organizations.class-management-panel.3','organizations.organization-change-review-panel.3',
 'rank-preferences.anonymous-rank-withdrawal-page.1','rewards.reward-management-page.1',
 'system-tasks.system-task-workbench.1','system-tasks.system-task-workbench.6','system-tasks.system-task-workbench.7'
));
DELETE FROM sys_menu WHERE type = 'BUTTON' AND code IN (
 'anonymous-ranks.anonymous-rank-page.1','anonymous-ranks.anonymous-rank-page.2',
 'attendance-records.attendance-page.2','dashboard.dashboard-page.2',
 'export-jobs.export-job-management-page.6','export-jobs.export-job-review-panel.1',
 'feature-management.feature-management-page.3','growth-points.growth-point-page.2',
 'growth-reviews.growth-review-export-history.2','growth-reviews.growth-review-page.1',
 'growth-reviews.growth-review-subscription-panel.1',
 'iam.iam-management-page.12','iam.iam-management-page.13','iam.iam-management-page.14','iam.iam-management-page.15','iam.iam-management-page.16',
 'learning-tasks.task-defer-queue.2',
 'menu-management.menu-management-page.10','menu-management.menu-management-page.11','menu-management.menu-management-page.12','menu-management.menu-management-page.13','menu-management.menu-management-page.14',
 'organizations.class-management-panel.3','organizations.organization-change-review-panel.3',
 'rank-preferences.anonymous-rank-withdrawal-page.1','rewards.reward-management-page.1',
 'system-tasks.system-task-workbench.1','system-tasks.system-task-workbench.6','system-tasks.system-task-workbench.7'
);
DELETE FROM sys_permission WHERE permission_code IN (
 'anonymous-ranks.anonymous-rank-page.1','anonymous-ranks.anonymous-rank-page.2',
 'attendance-records.attendance-page.2','dashboard.dashboard-page.2',
 'export-jobs.export-job-management-page.6','export-jobs.export-job-review-panel.1',
 'feature-management.feature-management-page.3','growth-points.growth-point-page.2',
 'growth-reviews.growth-review-export-history.2','growth-reviews.growth-review-page.1',
 'growth-reviews.growth-review-subscription-panel.1',
 'iam.iam-management-page.12','iam.iam-management-page.13','iam.iam-management-page.14','iam.iam-management-page.15','iam.iam-management-page.16',
 'learning-tasks.task-defer-queue.2',
 'menu-management.menu-management-page.10','menu-management.menu-management-page.11','menu-management.menu-management-page.12','menu-management.menu-management-page.13','menu-management.menu-management-page.14',
 'organizations.class-management-panel.3','organizations.organization-change-review-panel.3',
 'rank-preferences.anonymous-rank-withdrawal-page.1','rewards.reward-management-page.1',
 'system-tasks.system-task-workbench.1','system-tasks.system-task-workbench.6','system-tasks.system-task-workbench.7'
);
