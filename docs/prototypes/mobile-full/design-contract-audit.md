# 设计原型与现有能力对照（2026-10-12）

本次为源码/契约静态核对，不代表全部运行场景验证。七组70屏是视觉提案，不能作为接口规范。实施只重排小程序，不修改Web、数据库、后端权限、状态机。

## 必须校正的原型差异

| 原型内容 | 处理结论 | 当前依据 |
|---|---|---|
| 伙伴手机密码登录、孩子邮箱密码登录 | 移除。伙伴微信，孩子账号登录码/扫码 | unified-login、student-login |
| 注册强制手机号、学校、区域 | 移除。默认邮箱验证；区域在入驻时填写 | public-registration、organization-onboarding.ts |
| 家庭区域必须同孩子学校 | 移除该提示，不增加不存在的绑定限制 | onboardingApi.family(name,idempotencyKey,regionId) |
| 可见范围可选择家庭+班级或整个学校 | 移除新字段。按现有目标、来源、数据隔离执行 | family-task.ts、managed-learning-task.ts |
| 基础积分可任意填5/10/20 | 不直接落地。创建接口没有basePoints输入 | ManagedTaskInput，基础积分是返回字段 |
| 创建仅标题日期积分 | 修正。保留difficultyLevel/durationMinutes/targets/reviewerUserId/recurrenceEnabled | ManagedTaskInput |
| 把任务日期解释为截止日期 | 修正为任务日期scheduledDate | 两种任务API |
| 简化编辑覆盖Web分类标签周期 | 禁止。保留未编辑的既有字段 | models/family-task-draft.ts draftInput |
| 新增课程视频、在线资料、培训计划/评价 | 移除额外功能 | 原始需求明确排除在线教学；现有组织工作台无此入口 |
| 组织切换只限同校 | 修正。组织切换按实际归属，转班/教师班级按同校规则 | onboardingApi.organizations |
| 创建成员必须其本人微信登录 | 移除。管理员创建账号复用已授权接口 | account-management、组织Web创建 |
| 邀请码只年月日 | 修正为yyyy-MM-dd HH:mm:ss | 已确认的精度要求 |
| 奖励学校统一发货、预计配送 | 移除未接入字段 | StudentRewardExchange，没有物流字段 |
| 积分兑换永不撤销 | 移除。依照兑换真实状态，不写未经核验保证 | PENDING_APPROVAL/PENDING_VERIFICATION/REJECTED/AUTO_REJECTED/EXPIRED/VERIFIED |
| 库存、商品商城分类、强制图片 | 未核对支持前不落地，保留当前奖励契约字段 | reward.ts、parent-reward.ts |
| 我的页面替换成多人关系首页 | 修正。账号设置为主，关系为独立二级入口 | parent-me、account-security |
| 学校培训申请实际变成课程报名 | 修正为邀请码关联学校或培训机构 | 现有入学关系流程 |
| 注销立即不可逆全删除 | 移除绝对提示，按现有申请审核生命周期 | parent-account-lifecycle.ts、student-account-cancellation.ts |
| 情绪音乐播放、徽章等新增内容 | 仅保留当前接口支持的暂停与复盘功能，不新增媒体服务 | learning-task.ts、growth-review.ts |
| 位置考勤、学生轨迹 | 不增加入口，不改变禁用开关 | 已确认业务边界 |
| 首页统计数字和曲线 | 只显示已有接口字段，示例数字不能补造统计接口 | workbench API，具体统计逐页核验 |

## 模块契约依据

### 账号与入驻

- `lingdong-bansui-miniapp/src/api/auth.ts`：`/auth/student-captchas`、`/auth/student-sessions/code`、`/auth/student-sessions/qr`、`/auth/student-qr-captchas`、`/auth/student-wechat-sessions`、`/auth/student-wechat-binding-codes`、`/auth/student-wechat-bindings`、`/auth/sessions/current`、`/auth/organization-sessions/password`、`/public/parent-auth-context`、`/auth/login-sms-codes`、`/auth/parent-sms-codes`、`/auth/parent-wechat-sessions`、`/auth/parent-wechat-bindings`、`/auth/sessions/sms`、`/auth/parent-sessions/password`、`/auth/parent-state`、`/auth/parent-agreement-acceptances`、`/parent-onboarding/completion`、`/auth/devices`、`/auth/security-events`、`/auth/devices/${sessionId}`、`/auth/devices/sign-out-all`、`/auth/security-events/${eventId}/read`、`/auth/security-events/read-all`
- `lingdong-bansui-miniapp/src/api/account-settings.ts`：`/auth/account-settings`、`/auth/password`
- `lingdong-bansui-miniapp/src/api/organization-onboarding.ts`：`/organization-onboarding${path}`、`/mobile-code`、`/mobile-verify`、`/context`、`/regions`、`/my-organizations`、`/applications`、`/applications/${encodeURIComponent(id)}`、`/families`、`/applications/${encodeURIComponent(item.id)}/resubmit`、`/applications/${encodeURIComponent(item.id)}/withdraw`

### 陪伴任务

- `lingdong-bansui-miniapp/src/api/family-task.ts`：`/learning-tasks/${encodeURIComponent(id)}`、`/auth/me`、`/learning-tasks?sourceType=FAMILY&page=${page}&pageSize=20`、`/learning-task-options/students?sourceType=FAMILY`、`/learning-tasks`
- `lingdong-bansui-miniapp/src/api/parent-task-review.ts`：`/task-reviews/${encodeURIComponent(id)}`、`/auth/me`、`/task-reviews?page=${page}&pageSize=20`
- `lingdong-bansui-miniapp/src/api/managed-learning-task.ts`：`/learning-tasks?page=${page}&pageSize=20`、`/learning-tasks/${encodeURIComponent(taskId)}`、`/learning-tasks`、`/learning-tasks/${encodeURIComponent(taskId)}/publish`、`/learning-task-options/organizations?sourceType=ORGANIZATION`、`/learning-tasks/${encodeURIComponent(taskId)}/progress?page=1&pageSize=100`、`/task-reviews?page=${page}&pageSize=${pageSize}`、`/task-reviews/${encodeURIComponent(assignmentId)}/approve`、`/task-reviews/${encodeURIComponent(assignmentId)}/reject`

### 组织教学

- `lingdong-bansui-miniapp/src/api/organization-workbench.ts`：`/organization-workbench/context`
- `lingdong-bansui-miniapp/src/api/organization-classes.ts`：`/classes/schools`、`/classes`、`/classes/grades`、`/classes/${classId}`、`/classes/${item.id}/${action}`、`/learning-task-options/teachers?classId=${encodeURIComponent(classId)}`、`/teachers/${teacherUserId}/classes/${classId}`
- `lingdong-bansui-miniapp/src/api/organization-students.ts`：`/students/organization-relationships`、`/students/organization-relationship-classes`、`/students/${studentId}/class-transfers`、`/students/${studentId}/organization-deactivations`
- `lingdong-bansui-miniapp/src/api/teacher-management.ts`：`/organization-teachers?${parameters.join(`、`/organization-teachers`、`/organization-teachers/${teacherUserId}/profile`、`/organization-teachers/${teacherUserId}/status`、`/organization-teachers/${teacherUserId}/password-resets`、`/teachers/${teacherUserId}/classes/${classOrganizationId}`
- `lingdong-bansui-miniapp/src/api/teacher-workbench.ts`：`/teacher-workbench/context`

### 人员关系考勤

- `lingdong-bansui-miniapp/src/api/parent-relationship.ts`：`/parent-relationships/students`、`/students/${studentId}/parent-relationships`、`/students/${studentId}/secondary-parent-invitations`、`/students/${studentId}/primary-transfer-invitations`、`/students/${studentId}/secondary-parent-relationships/${parentUserId}`、`/students/${studentId}/primary-parent-relationship`、`/parent-relationship-invitations/${invitationId}/acceptance`、`/parent-relationship-invitations/${invitationId}/rejection`
- `lingdong-bansui-miniapp/src/api/attendance.ts`：`/auth/me`、`/attendance-records?${query}`、`/attendance-records/class-options${operational ? `、`/attendance-records/roster?classOrganizationId=${encodeURIComponent(classOrganizationId)}&attendanceDate=${encodeURIComponent(attendanceDate)}`、`/attendance-records/batch`、`/attendance-records/${encodeURIComponent(id)}`
- `lingdong-bansui-miniapp/src/api/exception-report.ts`：`/exception-reports?page=${page}&pageSize=${pageSize}${status?`、`/exception-reports/student-options?classOrganizationId=${encodeURIComponent(classId)}`、`/exception-reports`、`/exception-reports/${encodeURIComponent(id)}`、`/exception-reports/${encodeURIComponent(id)}/handle`
- `lingdong-bansui-miniapp/src/api/student-account-cancellation.ts`：`/student-account-cancellations/candidates`、`/students/${studentId}/cancellations`

### 小主人任务

- `lingdong-bansui-miniapp/src/api/learning-task.ts`：`/task-assignments?${queryString(filters)}`、`/task-assignments/${encodeURIComponent(id)}`、`/task-assignments/${encodeURIComponent(id)}/${action}`
- `lingdong-bansui-miniapp/src/api/attachment.ts`：`/attachments/uploads`、`/attachments/${encodeURIComponent(fileId)}`

### 积分奖励复盘

- `lingdong-bansui-miniapp/src/api/growth-point.ts`：`/growth-points/me/account`、`/growth-points/me/ledgers?page=${page}&pageSize=${pageSize}`
- `lingdong-bansui-miniapp/src/api/reward.ts`：`/rewards/me/summary`、`/rewards/me?page=${page}&pageSize=100`、`/reward-exchanges`、`/reward-exchanges/me?page=${page}&pageSize=100`
- `lingdong-bansui-miniapp/src/api/parent-reward.ts`：`/auth/me`、`/parent-rewards/students`、`/parent-rewards/students/${id(studentId)}?page=${page}&pageSize=20`、`/parent-reward-exchanges/students/${id(studentId)}?page=${page}&pageSize=20`、`/parent-rewards/${id(rewardId)}`、`/parent-rewards/students/${id(studentId)}`、`/parent-reward-exchanges/${id(exchangeId)}/${action}`
- `lingdong-bansui-miniapp/src/api/growth-review.ts`：`/growth-reviews/me?periodType=DAY&page=1&pageSize=20`、`/growth-reviews/me/${reviewId}`、`/growth-reviews/me/${reviewId}/supplements`
- `lingdong-bansui-miniapp/src/api/parent-weekly-review.ts`：`/growth-reviews/miniapp/students`、`/auth/me`
- `lingdong-bansui-miniapp/src/api/anonymous-rank.ts`：`/anonymous-ranks/students`、`/auth/me`、`/anonymous-ranks/preferences`

### 账号安全

- `lingdong-bansui-miniapp/src/api/auth.ts`：`/auth/student-captchas`、`/auth/student-sessions/code`、`/auth/student-sessions/qr`、`/auth/student-qr-captchas`、`/auth/student-wechat-sessions`、`/auth/student-wechat-binding-codes`、`/auth/student-wechat-bindings`、`/auth/sessions/current`、`/auth/organization-sessions/password`、`/public/parent-auth-context`、`/auth/login-sms-codes`、`/auth/parent-sms-codes`、`/auth/parent-wechat-sessions`、`/auth/parent-wechat-bindings`、`/auth/sessions/sms`、`/auth/parent-sessions/password`、`/auth/parent-state`、`/auth/parent-agreement-acceptances`、`/parent-onboarding/completion`、`/auth/devices`、`/auth/security-events`、`/auth/devices/${sessionId}`、`/auth/devices/sign-out-all`、`/auth/security-events/${eventId}/read`、`/auth/security-events/read-all`
- `lingdong-bansui-miniapp/src/api/account-settings.ts`：`/auth/account-settings`、`/auth/password`
- `lingdong-bansui-miniapp/src/api/parent-account-lifecycle.ts`：`/auth/parent-account-lifecycle`、`/auth/parent-mobile-change/current-codes`、`/auth/parent-mobile-change-tickets`、`/auth/parent-mobile-change/new-codes`、`/auth/parent-mobile-changes`、`/auth/parent-account-cancellation-codes`、`/auth/parent-account-cancellations`、`/auth/parent-account-cancellations/current`
- `lingdong-bansui-miniapp/src/api/student-wechat-binding.ts`：`/student-wechat-bindings`、`/students/${studentId}/wechat-unbindings`

## 后端约束

路由证据见backend-route-evidence.json。页面可拆分，API不随视觉拆分。角色身份与组织范围、功能开关和实际操作许可均保持；未授权或禁用入口隐藏，接口仍自行校验。任务审核保留expectedCheckInId并发保护。转交负责人、注销、重置等操作保留现有确认及审计。

## 后续验收

每模块修改前记录请求参数与响应字段；修改后验证同一业务相同请求，不把展示名称改成后端角色编码。Web已有草稿经小程序编辑不能丢字段。覆盖正常、空数据、无权限、禁用、错误、重复点击；必须人工看375×667和390×844布局。真实微信授权仍需开发者工具或真机测试，不以模拟接口代替。