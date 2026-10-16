# 陪伴端主布局数据缺口（2026-10-13）

对应 LAYOUT-01 首页、LAYOUT-03 成长、LAYOUT-04 我的。依据当前前端 API 声明核查；以下路径相对于 http.ts 配置的 API 根地址，未宣称已验证服务端返回。

| 原图区域 | 当前可用接口及字段 | 缺少的语义与建议契约 |
|---|---|---|
| 首页任务待审核 | GET /task-reviews，total、items.assignmentId/title/studentName/latestCheckIn.submittedAt | total 可直接使用；须保留权限过滤，失败显示未知，不能显示零 |
| 首页兑换待处理 | GET /parent-rewards/students 返回可管理孩子；GET /parent-reward-exchanges/students/{id}?page=… 返回分页 items.status、total | total 是所有状态总数，不能当待处理数；准确前端聚合需读取每个孩子全部分页，再计 PENDING_APPROVAL。建议增加经权限与组织范围过滤的汇总 pendingApprovalCount、pendingVerificationCount，并明确原图待处理口径 |
| 首页最新动态 | GET /learning-tasks?sourceType=FAMILY 返回 title/status/scheduledDate/durationMinutes；GET /task-reviews 返回待审打卡与 submittedAt | 任务定义 DRAFT/PUBLISHED 不代表孩子完成、兑换、打卡审核结果，也无完整事件时间。建议只读活动流 eventId/eventType/occurredAt/studentId/studentName/title/status/targetType/targetId，按时间分页且限定当前可访问组织和孩子；现页明确展示最新任务，未冒充最新动态 |
| 孩子选择头像与动态头像 | GET /growth-reviews/miniapp/students 返回 studentId/studentName；GET /students 返回 studentName 等；GET /auth/me 返回 displayName/username | 三个当前声明均无 avatarUrl。需返回真实头像及授权访问方式；未提供时保留通用图标，不能放原图人物照片 |
| 我的伙伴头像 | GET /auth/me 的 ChildIdentity 含 displayName、username、userId | 缺 avatarUrl；原图姓名使用真实 displayName/username，不写固定小林 |
| 本周成长概览 | GET /growth-reviews/miniapp/students/{studentId}?page=…：periodStart/periodEnd/completedCount/earnedPoints；详情 /{reviewId}：dailyTrends.completedCount/earnedPoints/taskTotalCount/trendDate | 可画真实周趋势。已生成历史周报不能自动称本周，需明确本周概览是否实时。详情 currently 使用完成比例而非原图绝对计数；真实零值不可画装饰高度。无周报时显示空态 |
| 近期成长回顾 | 周报详情 categories、supplements.content/supplementedAt | supplements 是周报补充内容，不等价于阅读进步/习惯提升/运动成就。建议 milestones id/title/description/category/occurredAt，来源必须真实记录；无数据使用空态 |
| 成长奖励累计奖牌 | 当前家长奖励列表 rewardName/requiredPoints/status；兑换记录 status/verifiedAt | 奖励商品与已核销兑换不等于奖牌成就。需独立可读 achievementSummary medalCount、recentAchievements，或经产品确认以真实已核销兑换展示并改正确标题；不能用商品数量假充奖牌 |

## 当前实现边界

三个页面继续使用独立家长令牌，异步返回校验账号和页面生命周期；菜单保留现有 capability 与 permission 检查。未增添静态示例数量、姓名、照片或伪造回顾。首页和周报仍有原图外的真实业务入口/统计，因此本轮只完成布局样式清理，尚未达到原图验收。后续接入汇总时应明确组织 scope（当前选择组织不可只影响标题），响应携带 scope 或由服务器按明确 organizationId 验证访问权。

微信原生截图、差异图、API 成功/失败与跨账号验证尚待主代理执行；不以 typecheck 代替视觉验收。

## 2026-10-14 组织概览入口迁移

新增 organization-overview 页面并注册，仅组织列表接口确认可进入的家庭/机构可作为当前组织；账号 token 和生命周期变化清除入口。原我的页孩子、成员、入学、关系/扫一扫、联系方式与账号、孩子微信绑定及负责人转交迁入概览，按原 permission/capability 条件展示。考勤和排行沿用既有权限条件。我的主页面保留四行：我的组织、账号与安全、登录设备、设置。账号与安全和设置进入现有账号设置；登录设备功能关闭时给出未开放提示，未绕过后端。负责人转交继续复用原组件实际 owner/options 检查。

本轮未生产写入。首次 typecheck 因 Node Zone Allocation 内存不足中止，直接 node --max-old-space-size=768 重试已通过；尚无新增原生截图证据。
