/** Existing business pages and independently configurable actions supported by this build. */
export const menuPageCatalog = [
  {
    "code": "dashboard",
    "name": "工作台",
    "route": "/dashboard",
    "icon": "LayoutDashboard"
  },
  {
    "code": "system-tasks",
    "name": "系统任务",
    "route": "/system-tasks",
    "icon": "ClipboardList"
  },
  {
    "code": "feature-management",
    "name": "功能开关",
    "route": "/feature-management",
    "icon": "Gauge"
  },
  {
    "code": "attendance-records",
    "name": "考勤台账",
    "route": "/attendance-records",
    "icon": "BookOpenCheck"
  },
  {
    "code": "learning-tasks",
    "name": "学习任务",
    "route": "/learning-tasks",
    "icon": "ClipboardList"
  },
  {
    "code": "exception-reports",
    "name": "异常报备",
    "route": "/exception-reports",
    "icon": "CircleAlert"
  },
  {
    "code": "growth-points",
    "name": "积分台账",
    "route": "/growth-points",
    "icon": "Coins"
  },
  {
    "code": "rewards",
    "name": "奖励管理",
    "route": "/rewards",
    "icon": "Gift"
  },
  {
    "code": "growth-reviews",
    "name": "成长复盘",
    "route": "/growth-reviews",
    "icon": "BookOpenCheck"
  },
  {
    "code": "anonymous-ranks",
    "name": "班级匿名排行",
    "route": "/anonymous-ranks",
    "icon": "Coins"
  },
  {
    "code": "rank-preferences",
    "name": "排行查看授权",
    "route": "/rank-preferences",
    "icon": "ShieldCheck"
  },
  {
    "code": "student-login",
    "name": "学生登录",
    "route": "/student-login",
    "icon": "QrCode"
  },
  {
    "code": "parent-relationships",
    "name": "家长关系",
    "route": "/parent-relationships",
    "icon": "UserRoundCog"
  },
  {
    "code": "teachers",
    "name": "教师管理",
    "route": "/teachers",
    "icon": "GraduationCap"
  },
  {
    "code": "users",
    "name": "用户管理",
    "route": "/users",
    "icon": "UsersRound"
  },
  {
    "code": "iam",
    "name": "角色与权限",
    "route": "/iam",
    "icon": "ShieldCheck"
  },
  {
    "code": "dictionaries",
    "name": "数据字典",
    "route": "/dictionaries",
    "icon": "Database"
  },
  {
    "code": "cache-management",
    "name": "缓存管理",
    "route": "/cache-management",
    "icon": "Gauge"
  },
  {
    "code": "interface-services",
    "name": "接口服务",
    "route": "/interface-services",
    "icon": "Cable"
  },
  {
    "code": "attachment-management",
    "name": "附件管理",
    "route": "/attachment-management",
    "icon": "Paperclip"
  },
  {
    "code": "import-export-templates",
    "name": "导入导出模板",
    "route": "/import-export-templates",
    "icon": "Files"
  },
  {
    "code": "import-jobs",
    "name": "导入校验作业",
    "route": "/import-jobs",
    "icon": "FileCheck2"
  },
  {
    "code": "export-jobs",
    "name": "数据导出中心",
    "route": "/export-jobs",
    "icon": "FileDown"
  },
  {
    "code": "organizations",
    "name": "组织管理",
    "route": "/organizations",
    "icon": "Network"
  },
  {
    "code": "menu-management",
    "name": "菜单管理",
    "route": "/menu-management",
    "icon": "ListTree"
  }
] as const;

export const menuActionCatalog = [
  {
    "code": "anonymous-ranks.anonymous-rank-page.1",
    "name": "刷新孩子列表",
    "route": "/anonymous-ranks",
    "source": "anonymous-ranks/AnonymousRankPage.tsx"
  },
  {
    "code": "anonymous-ranks.anonymous-rank-page.2",
    "name": "刷新排行",
    "route": "/anonymous-ranks",
    "source": "anonymous-ranks/AnonymousRankPage.tsx"
  },
  {
    "code": "rank-preferences.anonymous-rank-withdrawal-page.1",
    "name": "刷新授权",
    "route": "/rank-preferences",
    "source": "anonymous-ranks/AnonymousRankWithdrawalPage.tsx"
  },
  {
    "code": "rank-preferences.anonymous-rank-withdrawal-page.2",
    "name": "撤回授权",
    "route": "/rank-preferences",
    "source": "anonymous-ranks/AnonymousRankWithdrawalPage.tsx"
  },
  {
    "code": "attachment-management.attachment-management-page.1",
    "name": "查询规则",
    "route": "/attachment-management",
    "source": "attachments/AttachmentManagementPage.tsx"
  },
  {
    "code": "attachment-management.attachment-management-page.2",
    "name": "编辑",
    "route": "/attachment-management",
    "source": "attachments/AttachmentManagementPage.tsx"
  },
  {
    "code": "attachment-management.attachment-management-page.3",
    "name": "停用 / 启用",
    "route": "/attachment-management",
    "source": "attachments/AttachmentManagementPage.tsx"
  },
  {
    "code": "attachment-management.attachment-management-page.4",
    "name": "查询文件",
    "route": "/attachment-management",
    "source": "attachments/AttachmentManagementPage.tsx"
  },
  {
    "code": "attachment-management.attachment-management-page.5",
    "name": "查看关系",
    "route": "/attachment-management",
    "source": "attachments/AttachmentManagementPage.tsx"
  },
  {
    "code": "attachment-management.attachment-management-page.6",
    "name": "新增附件规则",
    "route": "/attachment-management",
    "source": "attachments/AttachmentManagementPage.tsx"
  },
  {
    "code": "attachment-management.attachment-management-page.7",
    "name": "重试",
    "route": "/attachment-management",
    "source": "attachments/AttachmentManagementPage.tsx"
  },
  {
    "code": "attachment-management.attachment-management-page.8",
    "name": "取消",
    "route": "/attachment-management",
    "source": "attachments/AttachmentManagementPage.tsx"
  },
  {
    "code": "attachment-management.attachment-management-page.9",
    "name": "保存规则",
    "route": "/attachment-management",
    "source": "attachments/AttachmentManagementPage.tsx"
  },
  {
    "code": "attendance-records.attendance-detail-drawer.1",
    "name": "重试",
    "route": "/attendance-records",
    "source": "attendance/AttendanceDetailDrawer.tsx"
  },
  {
    "code": "attendance-records.attendance-page.1",
    "name": "重试",
    "route": "/attendance-records",
    "source": "attendance/AttendancePage.tsx"
  },
  {
    "code": "attendance-records.attendance-page.2",
    "name": "刷新",
    "route": "/attendance-records",
    "source": "attendance/AttendancePage.tsx"
  },
  {
    "code": "attendance-records.attendance-page.3",
    "name": "班级点名",
    "route": "/attendance-records",
    "source": "attendance/AttendancePage.tsx"
  },
  {
    "code": "attendance-records.attendance-page.4",
    "name": "重试",
    "route": "/attendance-records",
    "source": "attendance/AttendancePage.tsx"
  },
  {
    "code": "attendance-records.attendance-page.5",
    "name": "查询",
    "route": "/attendance-records",
    "source": "attendance/AttendancePage.tsx"
  },
  {
    "code": "attendance-records.attendance-page.6",
    "name": "重置",
    "route": "/attendance-records",
    "source": "attendance/AttendancePage.tsx"
  },
  {
    "code": "attendance-records.attendance-page.7",
    "name": "详情",
    "route": "/attendance-records",
    "source": "attendance/AttendancePage.tsx"
  },
  {
    "code": "attendance-records.attendance-record-drawer.1",
    "name": "取消",
    "route": "/attendance-records",
    "source": "attendance/AttendanceRecordDrawer.tsx"
  },
  {
    "code": "attendance-records.attendance-record-drawer.2",
    "name": "提交考勤",
    "route": "/attendance-records",
    "source": "attendance/AttendanceRecordDrawer.tsx"
  },
  {
    "code": "attendance-records.attendance-record-drawer.3",
    "name": "重新加载名单",
    "route": "/attendance-records",
    "source": "attendance/AttendanceRecordDrawer.tsx"
  },
  {
    "code": "cache-management.cache-management-page.1",
    "name": "批准",
    "route": "/cache-management",
    "source": "cache/CacheManagementPage.tsx"
  },
  {
    "code": "cache-management.cache-management-page.2",
    "name": "驳回",
    "route": "/cache-management",
    "source": "cache/CacheManagementPage.tsx"
  },
  {
    "code": "cache-management.cache-management-page.3",
    "name": "执行缓存操作",
    "route": "/cache-management",
    "source": "cache/CacheManagementPage.tsx"
  },
  {
    "code": "cache-management.cache-management-page.4",
    "name": "提交高风险操作",
    "route": "/cache-management",
    "source": "cache/CacheManagementPage.tsx"
  },
  {
    "code": "cache-management.cache-management-page.5",
    "name": "重试",
    "route": "/cache-management",
    "source": "cache/CacheManagementPage.tsx"
  },
  {
    "code": "cache-management.cache-management-page.6",
    "name": "取消",
    "route": "/cache-management",
    "source": "cache/CacheManagementPage.tsx"
  },
  {
    "code": "cache-management.cache-management-page.7",
    "name": "提交保存",
    "route": "/cache-management",
    "source": "cache/CacheManagementPage.tsx"
  },
  {
    "code": "dashboard.dashboard-page.1",
    "name": "考勤台账",
    "route": "/dashboard",
    "source": "dashboard/DashboardPage.tsx"
  },
  {
    "code": "dashboard.dashboard-page.2",
    "name": "刷新",
    "route": "/dashboard",
    "source": "dashboard/DashboardPage.tsx"
  },
  {
    "code": "dashboard.dashboard-page.3",
    "name": "退出当前会话",
    "route": "/dashboard",
    "source": "dashboard/DashboardPage.tsx"
  },
  {
    "code": "dashboard.dashboard-page.4",
    "name": "标记已读",
    "route": "/dashboard",
    "source": "dashboard/DashboardPage.tsx"
  },
  {
    "code": "dashboard.dashboard-page.5",
    "name": "全部标记已读",
    "route": "/dashboard",
    "source": "dashboard/DashboardPage.tsx"
  },
  {
    "code": "dashboard.dashboard-page.6",
    "name": "下线",
    "route": "/dashboard",
    "source": "dashboard/DashboardPage.tsx"
  },
  {
    "code": "dashboard.dashboard-page.7",
    "name": "下线全部设备",
    "route": "/dashboard",
    "source": "dashboard/DashboardPage.tsx"
  },
  {
    "code": "dashboard.dashboard-task-reviews.1",
    "name": "重试",
    "route": "/dashboard",
    "source": "dashboard/DashboardTaskReviews.tsx"
  },
  {
    "code": "dashboard.organization-activity-trend.1",
    "name": "重试",
    "route": "/dashboard",
    "source": "dashboard/OrganizationActivityTrend.tsx"
  },
  {
    "code": "dashboard.parent-account-lifecycle-panel.1",
    "name": "更换手机号",
    "route": "/dashboard",
    "source": "dashboard/ParentAccountLifecyclePanel.tsx"
  },
  {
    "code": "dashboard.parent-account-lifecycle-panel.2",
    "name": "申请注销",
    "route": "/dashboard",
    "source": "dashboard/ParentAccountLifecyclePanel.tsx"
  },
  {
    "code": "dashboard.parent-account-lifecycle-panel.3",
    "name": "撤销注销申请",
    "route": "/dashboard",
    "source": "dashboard/ParentAccountLifecyclePanel.tsx"
  },
  {
    "code": "dashboard.parent-account-lifecycle-panel.4",
    "name": "发送当前手机号验证码",
    "route": "/dashboard",
    "source": "dashboard/ParentAccountLifecyclePanel.tsx"
  },
  {
    "code": "dashboard.parent-account-lifecycle-panel.5",
    "name": "验证当前手机号",
    "route": "/dashboard",
    "source": "dashboard/ParentAccountLifecyclePanel.tsx"
  },
  {
    "code": "dashboard.parent-account-lifecycle-panel.6",
    "name": "发送新手机号验证码",
    "route": "/dashboard",
    "source": "dashboard/ParentAccountLifecyclePanel.tsx"
  },
  {
    "code": "dashboard.parent-account-lifecycle-panel.7",
    "name": "确认更换手机号",
    "route": "/dashboard",
    "source": "dashboard/ParentAccountLifecyclePanel.tsx"
  },
  {
    "code": "dashboard.parent-account-lifecycle-panel.8",
    "name": "发送注销验证码",
    "route": "/dashboard",
    "source": "dashboard/ParentAccountLifecyclePanel.tsx"
  },
  {
    "code": "dashboard.parent-account-lifecycle-panel.9",
    "name": "提交注销申请",
    "route": "/dashboard",
    "source": "dashboard/ParentAccountLifecyclePanel.tsx"
  },
  {
    "code": "dictionaries.dictionary-management-page.1",
    "name": "新增类型",
    "route": "/dictionaries",
    "source": "dictionaries/DictionaryManagementPage.tsx"
  },
  {
    "code": "dictionaries.dictionary-management-page.2",
    "name": "新增字典项",
    "route": "/dictionaries",
    "source": "dictionaries/DictionaryManagementPage.tsx"
  },
  {
    "code": "dictionaries.dictionary-management-page.3",
    "name": "重试",
    "route": "/dictionaries",
    "source": "dictionaries/DictionaryManagementPage.tsx"
  },
  {
    "code": "dictionaries.dictionary-management-page.4",
    "name": "编辑字典类型",
    "route": "/dictionaries",
    "source": "dictionaries/DictionaryManagementPage.tsx"
  },
  {
    "code": "dictionaries.dictionary-management-page.5",
    "name": "编辑字典项",
    "route": "/dictionaries",
    "source": "dictionaries/DictionaryManagementPage.tsx"
  },
  {
    "code": "dictionaries.dictionary-management-page.6",
    "name": "取消",
    "route": "/dictionaries",
    "source": "dictionaries/DictionaryManagementPage.tsx"
  },
  {
    "code": "dictionaries.dictionary-management-page.7",
    "name": "保存",
    "route": "/dictionaries",
    "source": "dictionaries/DictionaryManagementPage.tsx"
  },
  {
    "code": "exception-reports.exception-report-page.1",
    "name": "新增报备",
    "route": "/exception-reports",
    "source": "exception-reports/ExceptionReportPage.tsx"
  },
  {
    "code": "exception-reports.exception-report-page.2",
    "name": "重试",
    "route": "/exception-reports",
    "source": "exception-reports/ExceptionReportPage.tsx"
  },
  {
    "code": "exception-reports.exception-report-page.3",
    "name": "查询",
    "route": "/exception-reports",
    "source": "exception-reports/ExceptionReportPage.tsx"
  },
  {
    "code": "exception-reports.exception-report-page.4",
    "name": "重置",
    "route": "/exception-reports",
    "source": "exception-reports/ExceptionReportPage.tsx"
  },
  {
    "code": "exception-reports.exception-report-page.5",
    "name": "详情",
    "route": "/exception-reports",
    "source": "exception-reports/ExceptionReportPage.tsx"
  },
  {
    "code": "exception-reports.exception-report-page.6",
    "name": "取消",
    "route": "/exception-reports",
    "source": "exception-reports/ExceptionReportPage.tsx"
  },
  {
    "code": "exception-reports.exception-report-page.7",
    "name": "确认处理",
    "route": "/exception-reports",
    "source": "exception-reports/ExceptionReportPage.tsx"
  },
  {
    "code": "exception-reports.exception-report-page.8",
    "name": "关闭",
    "route": "/exception-reports",
    "source": "exception-reports/ExceptionReportPage.tsx"
  },
  {
    "code": "export-jobs.export-job-management-page.1",
    "name": "新建导出",
    "route": "/export-jobs",
    "source": "export-jobs/ExportJobManagementPage.tsx"
  },
  {
    "code": "export-jobs.export-job-management-page.2",
    "name": "详情",
    "route": "/export-jobs",
    "source": "export-jobs/ExportJobManagementPage.tsx"
  },
  {
    "code": "export-jobs.export-job-management-page.3",
    "name": "下载",
    "route": "/export-jobs",
    "source": "export-jobs/ExportJobManagementPage.tsx"
  },
  {
    "code": "export-jobs.export-job-management-page.4",
    "name": "查询",
    "route": "/export-jobs",
    "source": "export-jobs/ExportJobManagementPage.tsx"
  },
  {
    "code": "export-jobs.export-job-management-page.5",
    "name": "重置",
    "route": "/export-jobs",
    "source": "export-jobs/ExportJobManagementPage.tsx"
  },
  {
    "code": "export-jobs.export-job-management-page.6",
    "name": "刷新作业",
    "route": "/export-jobs",
    "source": "export-jobs/ExportJobManagementPage.tsx"
  },
  {
    "code": "export-jobs.export-job-review-panel.1",
    "name": "刷新待审任务",
    "route": "/export-jobs",
    "source": "export-jobs/ExportJobReviewPanel.tsx"
  },
  {
    "code": "export-jobs.export-job-review-panel.2",
    "name": "批准",
    "route": "/export-jobs",
    "source": "export-jobs/ExportJobReviewPanel.tsx"
  },
  {
    "code": "export-jobs.export-job-review-panel.3",
    "name": "驳回",
    "route": "/export-jobs",
    "source": "export-jobs/ExportJobReviewPanel.tsx"
  },
  {
    "code": "feature-management.feature-management-page.1",
    "name": "查看记录",
    "route": "/feature-management",
    "source": "feature-management/FeatureManagementPage.tsx"
  },
  {
    "code": "feature-management.feature-management-page.2",
    "name": "重试",
    "route": "/feature-management",
    "source": "feature-management/FeatureManagementPage.tsx"
  },
  {
    "code": "feature-management.feature-management-page.3",
    "name": "刷新",
    "route": "/feature-management",
    "source": "feature-management/FeatureManagementPage.tsx"
  },
  {
    "code": "feature-management.feature-management-page.4",
    "name": "申请停用 / 申请启用",
    "route": "/feature-management",
    "source": "feature-management/FeatureManagementPage.tsx"
  },
  {
    "code": "feature-management.feature-management-page.5",
    "name": "批准",
    "route": "/feature-management",
    "source": "feature-management/FeatureManagementPage.tsx"
  },
  {
    "code": "feature-management.feature-management-page.6",
    "name": "驳回",
    "route": "/feature-management",
    "source": "feature-management/FeatureManagementPage.tsx"
  },
  {
    "code": "feature-management.feature-management-page.7",
    "name": "确认提交 / 下一步",
    "route": "/feature-management",
    "source": "feature-management/FeatureManagementPage.tsx"
  },
  {
    "code": "feature-management.feature-management-page.8",
    "name": "确认批准 / 确认驳回",
    "route": "/feature-management",
    "source": "feature-management/FeatureManagementPage.tsx"
  },
  {
    "code": "feature-management.feature-management-page.9",
    "name": "关闭",
    "route": "/feature-management",
    "source": "feature-management/FeatureManagementPage.tsx"
  },
  {
    "code": "growth-points.growth-point-page.1",
    "name": "导出",
    "route": "/growth-points",
    "source": "growth-points/GrowthPointPage.tsx"
  },
  {
    "code": "growth-points.growth-point-page.2",
    "name": "刷新积分",
    "route": "/growth-points",
    "source": "growth-points/GrowthPointPage.tsx"
  },
  {
    "code": "growth-points.growth-point-page.3",
    "name": "重试",
    "route": "/growth-points",
    "source": "growth-points/GrowthPointPage.tsx"
  },
  {
    "code": "growth-points.growth-point-page.4",
    "name": "纠错",
    "route": "/growth-points",
    "source": "growth-points/GrowthPointPage.tsx"
  },
  {
    "code": "growth-points.growth-point-page.5",
    "name": "取消",
    "route": "/growth-points",
    "source": "growth-points/GrowthPointPage.tsx"
  },
  {
    "code": "growth-points.growth-point-page.6",
    "name": "确认纠错",
    "route": "/growth-points",
    "source": "growth-points/GrowthPointPage.tsx"
  },
  {
    "code": "growth-reviews.create-growth-review-export.1",
    "name": "导出 PDF",
    "route": "/growth-reviews",
    "source": "growth-reviews/CreateGrowthReviewExport.tsx"
  },
  {
    "code": "growth-reviews.create-growth-review-export.2",
    "name": "重试模板",
    "route": "/growth-reviews",
    "source": "growth-reviews/CreateGrowthReviewExport.tsx"
  },
  {
    "code": "growth-reviews.create-growth-review-export.3",
    "name": "取消",
    "route": "/growth-reviews",
    "source": "growth-reviews/CreateGrowthReviewExport.tsx"
  },
  {
    "code": "growth-reviews.create-growth-review-export.4",
    "name": "提交导出",
    "route": "/growth-reviews",
    "source": "growth-reviews/CreateGrowthReviewExport.tsx"
  },
  {
    "code": "growth-reviews.growth-review-export-history.1",
    "name": "导出历史",
    "route": "/growth-reviews",
    "source": "growth-reviews/GrowthReviewExportHistory.tsx"
  },
  {
    "code": "growth-reviews.growth-review-export-history.2",
    "name": "刷新导出历史",
    "route": "/growth-reviews",
    "source": "growth-reviews/GrowthReviewExportHistory.tsx"
  },
  {
    "code": "growth-reviews.growth-review-export-history.3",
    "name": "详情",
    "route": "/growth-reviews",
    "source": "growth-reviews/GrowthReviewExportHistory.tsx"
  },
  {
    "code": "growth-reviews.growth-review-export-history.4",
    "name": "下载",
    "route": "/growth-reviews",
    "source": "growth-reviews/GrowthReviewExportHistory.tsx"
  },
  {
    "code": "growth-reviews.growth-review-page.1",
    "name": "刷新复盘",
    "route": "/growth-reviews",
    "source": "growth-reviews/GrowthReviewPage.tsx"
  },
  {
    "code": "growth-reviews.growth-review-page.2",
    "name": "补录复盘",
    "route": "/growth-reviews",
    "source": "growth-reviews/GrowthReviewPage.tsx"
  },
  {
    "code": "growth-reviews.growth-review-page.3",
    "name": "取消",
    "route": "/growth-reviews",
    "source": "growth-reviews/GrowthReviewPage.tsx"
  },
  {
    "code": "growth-reviews.growth-review-page.4",
    "name": "确认追加",
    "route": "/growth-reviews",
    "source": "growth-reviews/GrowthReviewPage.tsx"
  },
  {
    "code": "growth-reviews.growth-review-subscription-panel.1",
    "name": "刷新订阅状态",
    "route": "/growth-reviews",
    "source": "growth-reviews/GrowthReviewSubscriptionPanel.tsx"
  },
  {
    "code": "iam.iam-audit-panel.1",
    "name": "查询审计日志",
    "route": "/iam",
    "source": "iam/IamAuditPanel.tsx"
  },
  {
    "code": "iam.iam-audit-panel.2",
    "name": "重置",
    "route": "/iam",
    "source": "iam/IamAuditPanel.tsx"
  },
  {
    "code": "iam.iam-management-page.1",
    "name": "新增角色",
    "route": "/iam",
    "source": "iam/IamManagementPage.tsx"
  },
  {
    "code": "iam.iam-management-page.2",
    "name": "新增权限",
    "route": "/iam",
    "source": "iam/IamManagementPage.tsx"
  },
  {
    "code": "iam.iam-management-page.3",
    "name": "配置角色权限",
    "route": "/iam",
    "source": "iam/IamManagementPage.tsx"
  },
  {
    "code": "iam.iam-management-page.4",
    "name": "配置用户权限",
    "route": "/iam",
    "source": "iam/IamManagementPage.tsx"
  },
  {
    "code": "iam.iam-management-page.5",
    "name": "重试",
    "route": "/iam",
    "source": "iam/IamManagementPage.tsx"
  },
  {
    "code": "iam.iam-management-page.6",
    "name": "取消",
    "route": "/iam",
    "source": "iam/IamManagementPage.tsx"
  },
  {
    "code": "iam.iam-management-page.7",
    "name": "创建角色",
    "route": "/iam",
    "source": "iam/IamManagementPage.tsx"
  },
  {
    "code": "iam.iam-management-page.8",
    "name": "取消",
    "route": "/iam",
    "source": "iam/IamManagementPage.tsx"
  },
  {
    "code": "iam.iam-management-page.9",
    "name": "创建权限",
    "route": "/iam",
    "source": "iam/IamManagementPage.tsx"
  },
  {
    "code": "iam.iam-management-page.10",
    "name": "提交保存",
    "route": "/iam",
    "source": "iam/IamManagementPage.tsx"
  },
  {
    "code": "iam.iam-management-page.11",
    "name": "撤销",
    "route": "/iam",
    "source": "iam/IamManagementPage.tsx"
  },
  {
    "code": "import-export-templates.import-export-template-management-page.1",
    "name": "下载",
    "route": "/import-export-templates",
    "source": "import-export-templates/ImportExportTemplateManagementPage.tsx"
  },
  {
    "code": "import-export-templates.import-export-template-management-page.2",
    "name": "字段映射",
    "route": "/import-export-templates",
    "source": "import-export-templates/ImportExportTemplateManagementPage.tsx"
  },
  {
    "code": "import-export-templates.import-export-template-management-page.3",
    "name": "设为默认",
    "route": "/import-export-templates",
    "source": "import-export-templates/ImportExportTemplateManagementPage.tsx"
  },
  {
    "code": "import-export-templates.import-export-template-management-page.4",
    "name": "停用 / 启用",
    "route": "/import-export-templates",
    "source": "import-export-templates/ImportExportTemplateManagementPage.tsx"
  },
  {
    "code": "import-export-templates.import-export-template-management-page.5",
    "name": "新增模板版本",
    "route": "/import-export-templates",
    "source": "import-export-templates/ImportExportTemplateManagementPage.tsx"
  },
  {
    "code": "import-export-templates.import-export-template-management-page.6",
    "name": "重试",
    "route": "/import-export-templates",
    "source": "import-export-templates/ImportExportTemplateManagementPage.tsx"
  },
  {
    "code": "import-export-templates.import-export-template-management-page.7",
    "name": "查询",
    "route": "/import-export-templates",
    "source": "import-export-templates/ImportExportTemplateManagementPage.tsx"
  },
  {
    "code": "import-export-templates.import-export-template-management-page.8",
    "name": "重置",
    "route": "/import-export-templates",
    "source": "import-export-templates/ImportExportTemplateManagementPage.tsx"
  },
  {
    "code": "import-export-templates.import-export-template-management-page.9",
    "name": "选择模板文件",
    "route": "/import-export-templates",
    "source": "import-export-templates/ImportExportTemplateManagementPage.tsx"
  },
  {
    "code": "import-export-templates.import-export-template-management-page.10",
    "name": "保存字段映射",
    "route": "/import-export-templates",
    "source": "import-export-templates/ImportExportTemplateManagementPage.tsx"
  },
  {
    "code": "import-export-templates.import-export-template-management-page.11",
    "name": "上移字段",
    "route": "/import-export-templates",
    "source": "import-export-templates/ImportExportTemplateManagementPage.tsx"
  },
  {
    "code": "import-export-templates.import-export-template-management-page.12",
    "name": "下移字段",
    "route": "/import-export-templates",
    "source": "import-export-templates/ImportExportTemplateManagementPage.tsx"
  },
  {
    "code": "import-export-templates.import-export-template-management-page.13",
    "name": "删除字段",
    "route": "/import-export-templates",
    "source": "import-export-templates/ImportExportTemplateManagementPage.tsx"
  },
  {
    "code": "import-export-templates.import-export-template-management-page.14",
    "name": "新增字段",
    "route": "/import-export-templates",
    "source": "import-export-templates/ImportExportTemplateManagementPage.tsx"
  },
  {
    "code": "import-jobs.import-job-management-page.1",
    "name": "详情",
    "route": "/import-jobs",
    "source": "import-jobs/ImportJobManagementPage.tsx"
  },
  {
    "code": "import-jobs.import-job-management-page.2",
    "name": "下载错误",
    "route": "/import-jobs",
    "source": "import-jobs/ImportJobManagementPage.tsx"
  },
  {
    "code": "import-jobs.import-job-management-page.3",
    "name": "新建校验作业",
    "route": "/import-jobs",
    "source": "import-jobs/ImportJobManagementPage.tsx"
  },
  {
    "code": "import-jobs.import-job-management-page.4",
    "name": "重试",
    "route": "/import-jobs",
    "source": "import-jobs/ImportJobManagementPage.tsx"
  },
  {
    "code": "import-jobs.import-job-management-page.5",
    "name": "查询",
    "route": "/import-jobs",
    "source": "import-jobs/ImportJobManagementPage.tsx"
  },
  {
    "code": "import-jobs.import-job-management-page.6",
    "name": "重置",
    "route": "/import-jobs",
    "source": "import-jobs/ImportJobManagementPage.tsx"
  },
  {
    "code": "import-jobs.import-job-management-page.7",
    "name": "选择文件",
    "route": "/import-jobs",
    "source": "import-jobs/ImportJobManagementPage.tsx"
  },
  {
    "code": "import-jobs.import-job-management-page.8",
    "name": "执行学员导入",
    "route": "/import-jobs",
    "source": "import-jobs/ImportJobManagementPage.tsx"
  },
  {
    "code": "import-jobs.import-job-management-page.9",
    "name": "重试失败行",
    "route": "/import-jobs",
    "source": "import-jobs/ImportJobManagementPage.tsx"
  },
  {
    "code": "import-jobs.import-job-management-page.10",
    "name": "下载初始凭证",
    "route": "/import-jobs",
    "source": "import-jobs/ImportJobManagementPage.tsx"
  },
  {
    "code": "interface-services.interface-service-management-page.1",
    "name": "查询",
    "route": "/interface-services",
    "source": "interface-services/InterfaceServiceManagementPage.tsx"
  },
  {
    "code": "interface-services.interface-service-management-page.2",
    "name": "调整授权",
    "route": "/interface-services",
    "source": "interface-services/InterfaceServiceManagementPage.tsx"
  },
  {
    "code": "interface-services.interface-service-management-page.3",
    "name": "停用 / 启用",
    "route": "/interface-services",
    "source": "interface-services/InterfaceServiceManagementPage.tsx"
  },
  {
    "code": "interface-services.interface-service-management-page.4",
    "name": "批准",
    "route": "/interface-services",
    "source": "interface-services/InterfaceServiceManagementPage.tsx"
  },
  {
    "code": "interface-services.interface-service-management-page.5",
    "name": "驳回",
    "route": "/interface-services",
    "source": "interface-services/InterfaceServiceManagementPage.tsx"
  },
  {
    "code": "interface-services.interface-service-management-page.6",
    "name": "登记接口服务",
    "route": "/interface-services",
    "source": "interface-services/InterfaceServiceManagementPage.tsx"
  },
  {
    "code": "interface-services.interface-service-management-page.7",
    "name": "重试",
    "route": "/interface-services",
    "source": "interface-services/InterfaceServiceManagementPage.tsx"
  },
  {
    "code": "interface-services.interface-service-management-page.8",
    "name": "取消",
    "route": "/interface-services",
    "source": "interface-services/InterfaceServiceManagementPage.tsx"
  },
  {
    "code": "interface-services.interface-service-management-page.9",
    "name": "提交保存",
    "route": "/interface-services",
    "source": "interface-services/InterfaceServiceManagementPage.tsx"
  },
  {
    "code": "learning-tasks.learning-task-editor-drawer.1",
    "name": "保存为个人模板",
    "route": "/learning-tasks",
    "source": "learning-tasks/LearningTaskEditorDrawer.tsx"
  },
  {
    "code": "learning-tasks.learning-task-editor-drawer.2",
    "name": "保存草稿",
    "route": "/learning-tasks",
    "source": "learning-tasks/LearningTaskEditorDrawer.tsx"
  },
  {
    "code": "learning-tasks.learning-task-editor-drawer.3",
    "name": "添加目标",
    "route": "/learning-tasks",
    "source": "learning-tasks/LearningTaskEditorDrawer.tsx"
  },
  {
    "code": "learning-tasks.learning-task-editor-drawer.4",
    "name": "删除目标",
    "route": "/learning-tasks",
    "source": "learning-tasks/LearningTaskEditorDrawer.tsx"
  },
  {
    "code": "learning-tasks.learning-task-management-page.1",
    "name": "复制昨日任务",
    "route": "/learning-tasks",
    "source": "learning-tasks/LearningTaskManagementPage.tsx"
  },
  {
    "code": "learning-tasks.learning-task-management-page.2",
    "name": "任务模板",
    "route": "/learning-tasks",
    "source": "learning-tasks/LearningTaskManagementPage.tsx"
  },
  {
    "code": "learning-tasks.learning-task-management-page.3",
    "name": "批量发布",
    "route": "/learning-tasks",
    "source": "learning-tasks/LearningTaskManagementPage.tsx"
  },
  {
    "code": "learning-tasks.learning-task-management-page.4",
    "name": "新建任务",
    "route": "/learning-tasks",
    "source": "learning-tasks/LearningTaskManagementPage.tsx"
  },
  {
    "code": "learning-tasks.learning-task-management-page.5",
    "name": "重试",
    "route": "/learning-tasks",
    "source": "learning-tasks/LearningTaskManagementPage.tsx"
  },
  {
    "code": "learning-tasks.learning-task-management-page.6",
    "name": "查询",
    "route": "/learning-tasks",
    "source": "learning-tasks/LearningTaskManagementPage.tsx"
  },
  {
    "code": "learning-tasks.draft.edit",
    "name": "编辑草稿",
    "route": "/learning-tasks",
    "source": "learning-tasks/LearningTaskManagementPage.tsx"
  },
  {
    "code": "learning-tasks.publish",
    "name": "发布任务",
    "route": "/learning-tasks",
    "source": "learning-tasks/LearningTaskManagementPage.tsx"
  },
  {
    "code": "learning-tasks.progress",
    "name": "学生进度",
    "route": "/learning-tasks",
    "source": "learning-tasks/LearningTaskManagementPage.tsx"
  },
  {
    "code": "learning-tasks.recurrence.stop",
    "name": "停止每日固定任务",
    "route": "/learning-tasks",
    "source": "learning-tasks/LearningTaskManagementPage.tsx"
  },
  {
    "code": "learning-tasks.previous-day-task-copy-modal.1",
    "name": "关闭",
    "route": "/learning-tasks",
    "source": "learning-tasks/PreviousDayTaskCopyModal.tsx"
  },
  {
    "code": "learning-tasks.previous-day-task-copy-modal.2",
    "name": "确认复制",
    "route": "/learning-tasks",
    "source": "learning-tasks/PreviousDayTaskCopyModal.tsx"
  },
  {
    "code": "learning-tasks.previous-day-task-copy-modal.3",
    "name": "重试",
    "route": "/learning-tasks",
    "source": "learning-tasks/PreviousDayTaskCopyModal.tsx"
  },
  {
    "code": "learning-tasks.task-defer-queue.1",
    "name": "重试",
    "route": "/learning-tasks",
    "source": "learning-tasks/TaskDeferQueue.tsx"
  },
  {
    "code": "learning-tasks.task-defer-queue.2",
    "name": "刷新",
    "route": "/learning-tasks",
    "source": "learning-tasks/TaskDeferQueue.tsx"
  },
  {
    "code": "learning-tasks.task-defer-queue.3",
    "name": "顺延",
    "route": "/learning-tasks",
    "source": "learning-tasks/TaskDeferQueue.tsx"
  },
  {
    "code": "learning-tasks.task-review-drawer.1",
    "name": "刷新详情",
    "route": "/learning-tasks",
    "source": "learning-tasks/TaskReviewDrawer.tsx"
  },
  {
    "code": "learning-tasks.task-review-drawer.2",
    "name": "审核通过并发放积分",
    "route": "/learning-tasks",
    "source": "learning-tasks/TaskReviewDrawer.tsx"
  },
  {
    "code": "learning-tasks.task-review-drawer.3",
    "name": "驳回打卡",
    "route": "/learning-tasks",
    "source": "learning-tasks/TaskReviewDrawer.tsx"
  },
  {
    "code": "learning-tasks.task-review-drawer.4",
    "name": "确认转交",
    "route": "/learning-tasks",
    "source": "learning-tasks/TaskReviewDrawer.tsx"
  },
  {
    "code": "learning-tasks.task-review-queue.1",
    "name": "重试",
    "route": "/learning-tasks",
    "source": "learning-tasks/TaskReviewQueue.tsx"
  },
  {
    "code": "learning-tasks.task-review-queue.2",
    "name": "查看审核",
    "route": "/learning-tasks",
    "source": "learning-tasks/TaskReviewQueue.tsx"
  },
  {
    "code": "learning-tasks.task-template-editor-modal.1",
    "name": "取消",
    "route": "/learning-tasks",
    "source": "learning-tasks/TaskTemplateEditorModal.tsx"
  },
  {
    "code": "learning-tasks.task-template-editor-modal.2",
    "name": "保存",
    "route": "/learning-tasks",
    "source": "learning-tasks/TaskTemplateEditorModal.tsx"
  },
  {
    "code": "learning-tasks.template.select",
    "name": "选用模板",
    "route": "/learning-tasks",
    "source": "learning-tasks/TaskTemplateLibraryModal.tsx"
  },
  {
    "code": "learning-tasks.template.edit",
    "name": "编辑个人模板",
    "route": "/learning-tasks",
    "source": "learning-tasks/TaskTemplateLibraryModal.tsx"
  },
  {
    "code": "learning-tasks.template.up",
    "name": "上移",
    "route": "/learning-tasks",
    "source": "learning-tasks/TaskTemplateLibraryModal.tsx"
  },
  {
    "code": "learning-tasks.template.down",
    "name": "下移",
    "route": "/learning-tasks",
    "source": "learning-tasks/TaskTemplateLibraryModal.tsx"
  },
  {
    "code": "learning-tasks.template.delete",
    "name": "删除个人模板",
    "route": "/learning-tasks",
    "source": "learning-tasks/TaskTemplateLibraryModal.tsx"
  },
  {
    "code": "learning-tasks.task-template-library-modal.1",
    "name": "关闭",
    "route": "/learning-tasks",
    "source": "learning-tasks/TaskTemplateLibraryModal.tsx"
  },
  {
    "code": "learning-tasks.task-template-library-modal.2",
    "name": "新建个人模板",
    "route": "/learning-tasks",
    "source": "learning-tasks/TaskTemplateLibraryModal.tsx"
  },
  {
    "code": "menu-management.menu-management-page.1",
    "name": "重试",
    "route": "/menu-management",
    "source": "menus/MenuManagementPage.tsx"
  },
  {
    "code": "menu-management.menu-management-page.2",
    "name": "新增菜单",
    "route": "/menu-management",
    "source": "menus/MenuManagementPage.tsx"
  },
  {
    "code": "menu-management.menu-management-page.3",
    "name": "查询",
    "route": "/menu-management",
    "source": "menus/MenuManagementPage.tsx"
  },
  {
    "code": "menu-management.menu-management-page.4",
    "name": "重置",
    "route": "/menu-management",
    "source": "menus/MenuManagementPage.tsx"
  },
  {
    "code": "menu-management.menu-management-page.5",
    "name": "编辑",
    "route": "/menu-management",
    "source": "menus/MenuManagementPage.tsx"
  },
  {
    "code": "menu-management.menu-management-page.6",
    "name": "上移",
    "route": "/menu-management",
    "source": "menus/MenuManagementPage.tsx"
  },
  {
    "code": "menu-management.menu-management-page.7",
    "name": "下移",
    "route": "/menu-management",
    "source": "menus/MenuManagementPage.tsx"
  },
  {
    "code": "menu-management.menu-management-page.8",
    "name": "停用 / 启用",
    "route": "/menu-management",
    "source": "menus/MenuManagementPage.tsx"
  },
  {
    "code": "menu-management.menu-management-page.9",
    "name": "保存",
    "route": "/menu-management",
    "source": "menus/MenuManagementPage.tsx"
  },
  {
    "code": "organizations.class-management-panel.1",
    "name": "编辑",
    "route": "/organizations",
    "source": "organizations/ClassManagementPanel.tsx"
  },
  {
    "code": "organizations.class-management-panel.2",
    "name": "停用 / 启用",
    "route": "/organizations",
    "source": "organizations/ClassManagementPanel.tsx"
  },
  {
    "code": "organizations.class-management-panel.3",
    "name": "刷新",
    "route": "/organizations",
    "source": "organizations/ClassManagementPanel.tsx"
  },
  {
    "code": "organizations.class-management-panel.4",
    "name": "新增班级",
    "route": "/organizations",
    "source": "organizations/ClassManagementPanel.tsx"
  },
  {
    "code": "organizations.class-management-panel.5",
    "name": "重试",
    "route": "/organizations",
    "source": "organizations/ClassManagementPanel.tsx"
  },
  {
    "code": "organizations.class-management-panel.6",
    "name": "取消",
    "route": "/organizations",
    "source": "organizations/ClassManagementPanel.tsx"
  },
  {
    "code": "organizations.class-management-panel.7",
    "name": "保存修改 / 创建班级",
    "route": "/organizations",
    "source": "organizations/ClassManagementPanel.tsx"
  },
  {
    "code": "organizations.organization-change-review-panel.1",
    "name": "批准",
    "route": "/organizations",
    "source": "organizations/OrganizationChangeReviewPanel.tsx"
  },
  {
    "code": "organizations.organization-change-review-panel.2",
    "name": "驳回",
    "route": "/organizations",
    "source": "organizations/OrganizationChangeReviewPanel.tsx"
  },
  {
    "code": "organizations.organization-change-review-panel.3",
    "name": "刷新",
    "route": "/organizations",
    "source": "organizations/OrganizationChangeReviewPanel.tsx"
  },
  {
    "code": "organizations.organization-change-review-panel.4",
    "name": "取消",
    "route": "/organizations",
    "source": "organizations/OrganizationChangeReviewPanel.tsx"
  },
  {
    "code": "organizations.organization-change-review-panel.5",
    "name": "确认批准 / 确认驳回",
    "route": "/organizations",
    "source": "organizations/OrganizationChangeReviewPanel.tsx"
  },
  {
    "code": "organizations.organization-management-page.1",
    "name": "新增组织类型",
    "route": "/organizations",
    "source": "organizations/OrganizationManagementPage.tsx"
  },
  {
    "code": "organizations.organization-management-page.2",
    "name": "新增组织节点",
    "route": "/organizations",
    "source": "organizations/OrganizationManagementPage.tsx"
  },
  {
    "code": "organizations.organization-management-page.3",
    "name": "重试",
    "route": "/organizations",
    "source": "organizations/OrganizationManagementPage.tsx"
  },
  {
    "code": "organizations.organization-management-page.4",
    "name": "编辑",
    "route": "/organizations",
    "source": "organizations/OrganizationManagementPage.tsx"
  },
  {
    "code": "organizations.organization-management-page.5",
    "name": "重新启用",
    "route": "/organizations",
    "source": "organizations/OrganizationManagementPage.tsx"
  },
  {
    "code": "organizations.organization-management-page.6",
    "name": "申请停用",
    "route": "/organizations",
    "source": "organizations/OrganizationManagementPage.tsx"
  },
  {
    "code": "organizations.organization-management-page.7",
    "name": "申请移动",
    "route": "/organizations",
    "source": "organizations/OrganizationManagementPage.tsx"
  },
  {
    "code": "organizations.organization-management-page.8",
    "name": "申请删除",
    "route": "/organizations",
    "source": "organizations/OrganizationManagementPage.tsx"
  },
  {
    "code": "organizations.organization-management-page.9",
    "name": "取消",
    "route": "/organizations",
    "source": "organizations/OrganizationManagementPage.tsx"
  },
  {
    "code": "organizations.organization-management-page.10",
    "name": "提交审核",
    "route": "/organizations",
    "source": "organizations/OrganizationManagementPage.tsx"
  },
  {
    "code": "organizations.organization-management-page.11",
    "name": "学员关系",
    "route": "/organizations",
    "source": "organizations/OrganizationManagementPage.tsx"
  },
  {
    "code": "organizations.organization-management-page.12",
    "name": "家长换号核验",
    "route": "/organizations",
    "source": "organizations/OrganizationManagementPage.tsx"
  },
  {
    "code": "organizations.organization-management-page.13",
    "name": "学生账号注销",
    "route": "/organizations",
    "source": "organizations/OrganizationManagementPage.tsx"
  },
  {
    "code": "organizations.organization-management-page.14",
    "name": "配置教师班级",
    "route": "/organizations",
    "source": "organizations/OrganizationManagementPage.tsx"
  },
  {
    "code": "organizations.organization-management-page.15",
    "name": "取消",
    "route": "/organizations",
    "source": "organizations/OrganizationManagementPage.tsx"
  },
  {
    "code": "organizations.organization-management-page.16",
    "name": "创建类型",
    "route": "/organizations",
    "source": "organizations/OrganizationManagementPage.tsx"
  },
  {
    "code": "organizations.organization-management-page.17",
    "name": "取消",
    "route": "/organizations",
    "source": "organizations/OrganizationManagementPage.tsx"
  },
  {
    "code": "organizations.organization-management-page.18",
    "name": "创建节点",
    "route": "/organizations",
    "source": "organizations/OrganizationManagementPage.tsx"
  },
  {
    "code": "organizations.organization-node-editor-drawer.1",
    "name": "取消",
    "route": "/organizations",
    "source": "organizations/OrganizationNodeEditorDrawer.tsx"
  },
  {
    "code": "organizations.organization-node-editor-drawer.2",
    "name": "保存",
    "route": "/organizations",
    "source": "organizations/OrganizationNodeEditorDrawer.tsx"
  },
  {
    "code": "organizations.parent-mobile-manual-recovery-drawer.1",
    "name": "发送验证码",
    "route": "/organizations",
    "source": "organizations/ParentMobileManualRecoveryDrawer.tsx"
  },
  {
    "code": "organizations.parent-mobile-manual-recovery-drawer.2",
    "name": "取消",
    "route": "/organizations",
    "source": "organizations/ParentMobileManualRecoveryDrawer.tsx"
  },
  {
    "code": "organizations.parent-mobile-manual-recovery-drawer.3",
    "name": "确认换绑",
    "route": "/organizations",
    "source": "organizations/ParentMobileManualRecoveryDrawer.tsx"
  },
  {
    "code": "organizations.student-account-cancellation-drawer.1",
    "name": "取消",
    "route": "/organizations",
    "source": "organizations/StudentAccountCancellationDrawer.tsx"
  },
  {
    "code": "organizations.student-account-cancellation-drawer.2",
    "name": "注销学生账号",
    "route": "/organizations",
    "source": "organizations/StudentAccountCancellationDrawer.tsx"
  },
  {
    "code": "organizations.student-class-assignment-drawer.1",
    "name": "取消",
    "route": "/organizations",
    "source": "organizations/StudentClassAssignmentDrawer.tsx"
  },
  {
    "code": "organizations.student-class-assignment-drawer.2",
    "name": "确认配置",
    "route": "/organizations",
    "source": "organizations/StudentClassAssignmentDrawer.tsx"
  },
  {
    "code": "organizations.student-organization-lifecycle-drawer.1",
    "name": "取消",
    "route": "/organizations",
    "source": "organizations/StudentOrganizationLifecycleDrawer.tsx"
  },
  {
    "code": "organizations.student-organization-lifecycle-drawer.2",
    "name": "确认转班 / 确认转出",
    "route": "/organizations",
    "source": "organizations/StudentOrganizationLifecycleDrawer.tsx"
  },
  {
    "code": "organizations.teacher-class-assignment-drawer.1",
    "name": "取消",
    "route": "/organizations",
    "source": "organizations/TeacherClassAssignmentDrawer.tsx"
  },
  {
    "code": "organizations.teacher-class-assignment-drawer.2",
    "name": "解除绑定",
    "route": "/organizations",
    "source": "organizations/TeacherClassAssignmentDrawer.tsx"
  },
  {
    "code": "organizations.teacher-class-assignment-drawer.3",
    "name": "确认绑定",
    "route": "/organizations",
    "source": "organizations/TeacherClassAssignmentDrawer.tsx"
  },
  {
    "code": "parent-relationships.parent-relationship-page.1",
    "name": "邀请副家长",
    "route": "/parent-relationships",
    "source": "parent-relationships/ParentRelationshipPage.tsx"
  },
  {
    "code": "parent-relationships.parent-relationship-page.2",
    "name": "转移监护权",
    "route": "/parent-relationships",
    "source": "parent-relationships/ParentRelationshipPage.tsx"
  },
  {
    "code": "parent-relationships.parent-relationship-page.3",
    "name": "解除我的主家长关系",
    "route": "/parent-relationships",
    "source": "parent-relationships/ParentRelationshipPage.tsx"
  },
  {
    "code": "parent-relationships.parent-relationship-page.4",
    "name": "解除副家长",
    "route": "/parent-relationships",
    "source": "parent-relationships/ParentRelationshipPage.tsx"
  },
  {
    "code": "rewards.reward-management-page.1",
    "name": "刷新奖励数据",
    "route": "/rewards",
    "source": "rewards/RewardManagementPage.tsx"
  },
  {
    "code": "rewards.reward-management-page.2",
    "name": "新建奖励",
    "route": "/rewards",
    "source": "rewards/RewardManagementPage.tsx"
  },
  {
    "code": "rewards.reward-management-page.3",
    "name": "重试",
    "route": "/rewards",
    "source": "rewards/RewardManagementPage.tsx"
  },
  {
    "code": "rewards.reward-management-page.4",
    "name": "编辑奖励",
    "route": "/rewards",
    "source": "rewards/RewardManagementPage.tsx"
  },
  {
    "code": "rewards.reward-management-page.5",
    "name": "下架 / 上架",
    "route": "/rewards",
    "source": "rewards/RewardManagementPage.tsx"
  },
  {
    "code": "rewards.reward-management-page.6",
    "name": "删除奖励",
    "route": "/rewards",
    "source": "rewards/RewardManagementPage.tsx"
  },
  {
    "code": "rewards.reward-management-page.7",
    "name": "同意",
    "route": "/rewards",
    "source": "rewards/RewardManagementPage.tsx"
  },
  {
    "code": "rewards.reward-management-page.8",
    "name": "驳回",
    "route": "/rewards",
    "source": "rewards/RewardManagementPage.tsx"
  },
  {
    "code": "rewards.reward-management-page.9",
    "name": "确认核销",
    "route": "/rewards",
    "source": "rewards/RewardManagementPage.tsx"
  },
  {
    "code": "rewards.reward-management-page.10",
    "name": "取消",
    "route": "/rewards",
    "source": "rewards/RewardManagementPage.tsx"
  },
  {
    "code": "rewards.reward-management-page.11",
    "name": "保存奖励",
    "route": "/rewards",
    "source": "rewards/RewardManagementPage.tsx"
  },
  {
    "code": "rewards.reward-management-page.12",
    "name": "取消",
    "route": "/rewards",
    "source": "rewards/RewardManagementPage.tsx"
  },
  {
    "code": "rewards.reward-management-page.13",
    "name": "确认驳回",
    "route": "/rewards",
    "source": "rewards/RewardManagementPage.tsx"
  },
  {
    "code": "student-login.student-login-management-page.1",
    "name": "重试",
    "route": "/student-login",
    "source": "student-login/StudentLoginManagementPage.tsx"
  },
  {
    "code": "student-login.student-login-management-page.2",
    "name": "查询",
    "route": "/student-login",
    "source": "student-login/StudentLoginManagementPage.tsx"
  },
  {
    "code": "student-login.student-login-management-page.3",
    "name": "生成",
    "route": "/student-login",
    "source": "student-login/StudentLoginManagementPage.tsx"
  },
  {
    "code": "student-login.student-login-management-page.4",
    "name": "解绑",
    "route": "/student-login",
    "source": "student-login/StudentLoginManagementPage.tsx"
  },
  {
    "code": "student-login.student-login-management-page.5",
    "name": "关闭",
    "route": "/student-login",
    "source": "student-login/StudentLoginManagementPage.tsx"
  },
  {
    "code": "student-login.student-login-management-page.6",
    "name": "刷新二维码",
    "route": "/student-login",
    "source": "student-login/StudentLoginManagementPage.tsx"
  },
  {
    "code": "system-tasks.system-task-workbench.1",
    "name": "刷新",
    "route": "/system-tasks",
    "source": "system-tasks/SystemTaskWorkbench.tsx"
  },
  {
    "code": "system-tasks.system-task-workbench.2",
    "name": "重试",
    "route": "/system-tasks",
    "source": "system-tasks/SystemTaskWorkbench.tsx"
  },
  {
    "code": "system-tasks.system-task-workbench.3",
    "name": "查看详情",
    "route": "/system-tasks",
    "source": "system-tasks/SystemTaskWorkbench.tsx"
  },
  {
    "code": "system-tasks.system-task-workbench.4",
    "name": "关闭",
    "route": "/system-tasks",
    "source": "system-tasks/SystemTaskWorkbench.tsx"
  },
  {
    "code": "system-tasks.system-task-workbench.5",
    "name": "前往领域处理页",
    "route": "/system-tasks",
    "source": "system-tasks/SystemTaskWorkbench.tsx"
  },
  {
    "code": "teachers.teacher-management-page.1",
    "name": "批量操作",
    "route": "/teachers",
    "source": "teachers/TeacherManagementPage.tsx"
  },
  {
    "code": "teachers.teacher-management-page.2",
    "name": "新增教师",
    "route": "/teachers",
    "source": "teachers/TeacherManagementPage.tsx"
  },
  {
    "code": "teachers.teacher-management-page.3",
    "name": "重试",
    "route": "/teachers",
    "source": "teachers/TeacherManagementPage.tsx"
  },
  {
    "code": "teachers.teacher-management-page.4",
    "name": "查询",
    "route": "/teachers",
    "source": "teachers/TeacherManagementPage.tsx"
  },
  {
    "code": "teachers.profile.edit",
    "name": "编辑资料",
    "route": "/teachers",
    "source": "teachers/TeacherManagementPage.tsx"
  },
  {
    "code": "teachers.password.reset",
    "name": "重置密码",
    "route": "/teachers",
    "source": "teachers/TeacherManagementPage.tsx"
  },
  {
    "code": "teachers.classes.configure",
    "name": "班级范围",
    "route": "/teachers",
    "source": "teachers/TeacherManagementPage.tsx"
  },
  {
    "code": "teachers.teacher-management-page.5",
    "name": "保存",
    "route": "/teachers",
    "source": "teachers/TeacherManagementPage.tsx"
  },
  {
    "code": "teachers.teacher-management-page.8",
    "name": "取消",
    "route": "/teachers",
    "source": "teachers/TeacherManagementPage.tsx"
  },
  {
    "code": "teachers.teacher-management-page.9",
    "name": "提交保存",
    "route": "/teachers",
    "source": "teachers/TeacherManagementPage.tsx"
  },
  {
    "code": "users.user-management-page.1",
    "name": "新增用户",
    "route": "/users",
    "source": "users/UserManagementPage.tsx"
  },
  {
    "code": "users.user-management-page.2",
    "name": "重试",
    "route": "/users",
    "source": "users/UserManagementPage.tsx"
  },
  {
    "code": "users.user-management-page.3",
    "name": "查询",
    "route": "/users",
    "source": "users/UserManagementPage.tsx"
  },
  {
    "code": "users.user-management-page.4",
    "name": "取消",
    "route": "/users",
    "source": "users/UserManagementPage.tsx"
  },
  {
    "code": "users.user-management-page.5",
    "name": "创建用户",
    "route": "/users",
    "source": "users/UserManagementPage.tsx"
  },
  {
    "code": "users.status.enabled",
    "name": "启用账号",
    "route": "/users",
    "source": "users/*"
  },
  {
    "code": "users.status.disabled",
    "name": "停用账号",
    "route": "/users",
    "source": "users/*"
  },
  {
    "code": "users.status.locked",
    "name": "锁定账号",
    "route": "/users",
    "source": "users/*"
  },
  {
    "code": "teachers.status.enabled",
    "name": "启用账号",
    "route": "/teachers",
    "source": "teachers/*"
  },
  {
    "code": "teachers.status.disabled",
    "name": "停用账号",
    "route": "/teachers",
    "source": "teachers/*"
  },
  {
    "code": "teachers.status.locked",
    "name": "锁定账号",
    "route": "/teachers",
    "source": "teachers/*"
  },
  {
    "code": "exception-reports.exception-report-page.modal.1.ok",
    "name": "确定",
    "route": "/exception-reports",
    "source": "exception-reports/ExceptionReportPage.tsx"
  },
  {
    "code": "exception-reports.exception-report-page.modal.1.cancel",
    "name": "取消",
    "route": "/exception-reports",
    "source": "exception-reports/ExceptionReportPage.tsx"
  },
  {
    "code": "export-jobs.create-export-job-modal.modal.1.ok",
    "name": "提交导出",
    "route": "/export-jobs",
    "source": "export-jobs/CreateExportJobModal.tsx"
  },
  {
    "code": "export-jobs.create-export-job-modal.modal.1.cancel",
    "name": "取消",
    "route": "/export-jobs",
    "source": "export-jobs/CreateExportJobModal.tsx"
  },
  {
    "code": "export-jobs.export-job-review-panel.modal.1.ok",
    "name": "确定",
    "route": "/export-jobs",
    "source": "export-jobs/ExportJobReviewPanel.tsx"
  },
  {
    "code": "export-jobs.export-job-review-panel.modal.1.cancel",
    "name": "取消",
    "route": "/export-jobs",
    "source": "export-jobs/ExportJobReviewPanel.tsx"
  },
  {
    "code": "learning-tasks.batch-publish-result-modal.modal.1.ok",
    "name": "关闭",
    "route": "/learning-tasks",
    "source": "learning-tasks/BatchPublishResultModal.tsx"
  },
  {
    "code": "learning-tasks.batch-publish-result-modal.modal.1.cancel",
    "name": "取消",
    "route": "/learning-tasks",
    "source": "learning-tasks/BatchPublishResultModal.tsx"
  },
  {
    "code": "learning-tasks.learning-task-management-page.confirm.1.ok",
    "name": "确认发布",
    "route": "/learning-tasks",
    "source": "learning-tasks/LearningTaskManagementPage.tsx"
  },
  {
    "code": "learning-tasks.learning-task-management-page.confirm.1.cancel",
    "name": "取消",
    "route": "/learning-tasks",
    "source": "learning-tasks/LearningTaskManagementPage.tsx"
  },
  {
    "code": "learning-tasks.learning-task-management-page.confirm.2.ok",
    "name": "确认发布",
    "route": "/learning-tasks",
    "source": "learning-tasks/LearningTaskManagementPage.tsx"
  },
  {
    "code": "learning-tasks.learning-task-management-page.confirm.2.cancel",
    "name": "取消",
    "route": "/learning-tasks",
    "source": "learning-tasks/LearningTaskManagementPage.tsx"
  },
  {
    "code": "learning-tasks.learning-task-management-page.confirm.3.ok",
    "name": "确认停止",
    "route": "/learning-tasks",
    "source": "learning-tasks/LearningTaskManagementPage.tsx"
  },
  {
    "code": "learning-tasks.learning-task-management-page.confirm.3.cancel",
    "name": "取消",
    "route": "/learning-tasks",
    "source": "learning-tasks/LearningTaskManagementPage.tsx"
  },
  {
    "code": "learning-tasks.task-defer-queue.modal.1.ok",
    "name": "确认顺延",
    "route": "/learning-tasks",
    "source": "learning-tasks/TaskDeferQueue.tsx"
  },
  {
    "code": "learning-tasks.task-defer-queue.modal.1.cancel",
    "name": "取消",
    "route": "/learning-tasks",
    "source": "learning-tasks/TaskDeferQueue.tsx"
  },
  {
    "code": "learning-tasks.task-review-drawer.confirm.1.ok",
    "name": "确认驳回",
    "route": "/learning-tasks",
    "source": "learning-tasks/TaskReviewDrawer.tsx"
  },
  {
    "code": "learning-tasks.task-review-drawer.confirm.1.cancel",
    "name": "取消",
    "route": "/learning-tasks",
    "source": "learning-tasks/TaskReviewDrawer.tsx"
  },
  {
    "code": "learning-tasks.task-review-drawer.confirm.2.ok",
    "name": "确认通过",
    "route": "/learning-tasks",
    "source": "learning-tasks/TaskReviewDrawer.tsx"
  },
  {
    "code": "learning-tasks.task-review-drawer.confirm.2.cancel",
    "name": "取消",
    "route": "/learning-tasks",
    "source": "learning-tasks/TaskReviewDrawer.tsx"
  },
  {
    "code": "learning-tasks.task-template-library-modal.confirm.1.ok",
    "name": "确认删除",
    "route": "/learning-tasks",
    "source": "learning-tasks/TaskTemplateLibraryModal.tsx"
  },
  {
    "code": "learning-tasks.task-template-library-modal.confirm.1.cancel",
    "name": "取消",
    "route": "/learning-tasks",
    "source": "learning-tasks/TaskTemplateLibraryModal.tsx"
  },
  {
    "code": "organizations.class-management-panel.confirm.1.ok",
    "name": "确定",
    "route": "/organizations",
    "source": "organizations/ClassManagementPanel.tsx"
  },
  {
    "code": "organizations.class-management-panel.confirm.1.cancel",
    "name": "取消",
    "route": "/organizations",
    "source": "organizations/ClassManagementPanel.tsx"
  },
  {
    "code": "organizations.organization-management-page.confirm.1.ok",
    "name": "确认启用",
    "route": "/organizations",
    "source": "organizations/OrganizationManagementPage.tsx"
  },
  {
    "code": "organizations.organization-management-page.confirm.1.cancel",
    "name": "取消",
    "route": "/organizations",
    "source": "organizations/OrganizationManagementPage.tsx"
  },
  {
    "code": "organizations.student-account-cancellation-drawer.confirm.1.ok",
    "name": "确认注销",
    "route": "/organizations",
    "source": "organizations/StudentAccountCancellationDrawer.tsx"
  },
  {
    "code": "organizations.student-account-cancellation-drawer.confirm.1.cancel",
    "name": "取消",
    "route": "/organizations",
    "source": "organizations/StudentAccountCancellationDrawer.tsx"
  },
  {
    "code": "organizations.student-organization-lifecycle-drawer.confirm.1.ok",
    "name": "确认转出",
    "route": "/organizations",
    "source": "organizations/StudentOrganizationLifecycleDrawer.tsx"
  },
  {
    "code": "organizations.student-organization-lifecycle-drawer.confirm.1.cancel",
    "name": "取消",
    "route": "/organizations",
    "source": "organizations/StudentOrganizationLifecycleDrawer.tsx"
  },
  {
    "code": "parent-relationships.parent-relationship-page.confirm.1.ok",
    "name": "确认解除",
    "route": "/parent-relationships",
    "source": "parent-relationships/ParentRelationshipPage.tsx"
  },
  {
    "code": "parent-relationships.parent-relationship-page.confirm.1.cancel",
    "name": "取消",
    "route": "/parent-relationships",
    "source": "parent-relationships/ParentRelationshipPage.tsx"
  },
  {
    "code": "parent-relationships.parent-relationship-page.confirm.2.ok",
    "name": "确认解除",
    "route": "/parent-relationships",
    "source": "parent-relationships/ParentRelationshipPage.tsx"
  },
  {
    "code": "parent-relationships.parent-relationship-page.confirm.2.cancel",
    "name": "取消",
    "route": "/parent-relationships",
    "source": "parent-relationships/ParentRelationshipPage.tsx"
  },
  {
    "code": "parent-relationships.parent-relationship-page.modal.1.ok",
    "name": "发送邀请",
    "route": "/parent-relationships",
    "source": "parent-relationships/ParentRelationshipPage.tsx"
  },
  {
    "code": "parent-relationships.parent-relationship-page.modal.1.cancel",
    "name": "取消",
    "route": "/parent-relationships",
    "source": "parent-relationships/ParentRelationshipPage.tsx"
  },
  {
    "code": "student-login.student-login-management-page.confirm.1.ok",
    "name": "确认解绑",
    "route": "/student-login",
    "source": "student-login/StudentLoginManagementPage.tsx"
  },
  {
    "code": "student-login.student-login-management-page.confirm.1.cancel",
    "name": "取消",
    "route": "/student-login",
    "source": "student-login/StudentLoginManagementPage.tsx"
  },
  {
    "code": "import-export-templates.import-export-template-management-page.modal.1.ok",
    "name": "保存模板",
    "route": "/import-export-templates",
    "source": "import-export-templates/ImportExportTemplateManagementPage.tsx"
  },
  {
    "code": "import-export-templates.import-export-template-management-page.modal.1.cancel",
    "name": "取消",
    "route": "/import-export-templates",
    "source": "import-export-templates/ImportExportTemplateManagementPage.tsx"
  },
  {
    "code": "import-jobs.import-job-management-page.confirm.1.ok",
    "name": "确认下载",
    "route": "/import-jobs",
    "source": "import-jobs/ImportJobManagementPage.tsx"
  },
  {
    "code": "import-jobs.import-job-management-page.confirm.1.cancel",
    "name": "取消",
    "route": "/import-jobs",
    "source": "import-jobs/ImportJobManagementPage.tsx"
  },
  {
    "code": "import-jobs.import-job-management-page.modal.1.ok",
    "name": "提交校验",
    "route": "/import-jobs",
    "source": "import-jobs/ImportJobManagementPage.tsx"
  },
  {
    "code": "import-jobs.import-job-management-page.modal.1.cancel",
    "name": "取消",
    "route": "/import-jobs",
    "source": "import-jobs/ImportJobManagementPage.tsx"
  },
  {
    "code": "import-jobs.import-job-management-page.modal.2.ok",
    "name": "确认执行",
    "route": "/import-jobs",
    "source": "import-jobs/ImportJobManagementPage.tsx"
  },
  {
    "code": "import-jobs.import-job-management-page.modal.2.cancel",
    "name": "取消",
    "route": "/import-jobs",
    "source": "import-jobs/ImportJobManagementPage.tsx"
  }
] as const;
