# 灵动伴随 V38 家长关系生命周期实施计划

> **执行要求：** 使用 `superpowers:executing-plans` 按任务执行，全部步骤以复选框跟踪。严格 TDD，自动化默认只连接本地 H2、测试短信适配器和内存验证码存储。经用户单独授权后可对指定 MySQL、Redis 做只读连通性与版本核验；不得据此执行远程 Flyway 迁移、清库、刷新全库或连接未确认的生产环境。

**目标：** 完成副家长邀请确认、主副关系变更、自动晋升、监护权转移、待审核任务转交、副家长只读和 Web/uni-app 独立交互。

**架构：** 复用 `edu_parent_student` 保存当前关系，新增专用邀请表与不可变关系审计表。`ParentRelationshipApplicationService` 集中执行行锁、角色切换和邀请状态机，查询服务显式区分活动关系只读与活动主关系写权限。

**技术栈：** Spring Boot 3、JDK 17、MyBatis XML、Flyway、H2/MySQL 兼容 SQL、Redis 验证码端口、React、uni-app/Vue 3。

---

## 任务 1：V38 迁移与约束

**文件：**
- 新增：`server/src/main/resources/db/migration/V38__add_parent_relationship_lifecycle.sql`
- 修改：`server/src/test/java/com/lingdong/learning/FlywayMigrationTest.java`

- [x] 先增加失败断言：当前版本 V38、66 张主键表、关系范围键长度 32、邀请与日志表、状态检查、两个唯一约束、19 位基础数据和无 `AUTO_INCREMENT`。
- [x] 运行 `mvn -q -Dtest=FlywayMigrationTest test`，确认因 V38 不存在而失败。
- [x] 新增 V38：扩展关系范围键，创建邀请表和日志表，初始化 `PARENT_RELATIONSHIP_MANAGEMENT` 默认停用开关及关系管理权限。
- [x] 再次运行迁移测试并确认 V1-V38 空库连续迁移通过，不修改 V1-V37。

## 任务 2：关系领域与持久化

**文件：**
- 新增：`server/src/main/java/com/lingdong/learning/student/domain/ParentRelationship.java`
- 新增：`server/src/main/java/com/lingdong/learning/student/domain/ParentRelationshipRole.java`
- 新增：`server/src/main/java/com/lingdong/learning/student/domain/ParentRelationshipInvitation.java`
- 新增：`server/src/main/java/com/lingdong/learning/student/domain/ParentRelationshipInvitationType.java`
- 新增：`server/src/main/java/com/lingdong/learning/student/domain/ParentRelationshipInvitationStatus.java`
- 新增：`server/src/main/java/com/lingdong/learning/student/domain/ParentRelationshipChange.java`
- 新增：`server/src/main/java/com/lingdong/learning/student/infrastructure/persistence/ParentRelationshipInvitationMapper.java`
- 新增：`server/src/main/java/com/lingdong/learning/student/infrastructure/persistence/ParentRelationshipChangeMapper.java`
- 修改：`server/src/main/java/com/lingdong/learning/student/infrastructure/persistence/ParentStudentMapper.java`
- 新增：`server/src/main/resources/mapper/student/ParentRelationshipInvitationMapper.xml`
- 新增：`server/src/main/resources/mapper/student/ParentRelationshipChangeMapper.xml`
- 修改：`server/src/main/resources/mapper/student/ParentStudentMapper.xml`
- 新增测试：`server/src/test/java/com/lingdong/learning/student/application/ParentRelationshipPersistenceTest.java`

- [x] 先写真实 MyBatis 测试，覆盖锁定关系、插入/重新激活副关系、关闭范围键、最早副家长查询、待邀请唯一和审计只新增。
- [x] 运行目标测试，确认缺少领域对象和映射方法而失败。
- [x] 实现最小领域记录与 Mapper；所有标识使用 `Long`，API 层再转字符串。
- [x] 运行目标测试，确认最多一主一副、历史重绑和关闭键约束通过；主副交换在任务 4 状态机测试中覆盖。

## 任务 3：验证码与未注册家长建号

**文件：**
- 修改：`server/src/main/java/com/lingdong/learning/auth/application/ParentSmsPurpose.java`
- 修改：`server/src/main/java/com/lingdong/learning/auth/application/ParentPhoneAuthenticationService.java`
- 新增：`server/src/main/java/com/lingdong/learning/auth/application/ParentRelationshipMobileVerificationCommand.java`
- 修改测试：`server/src/test/java/com/lingdong/learning/auth/application/ParentPhoneAuthenticationServiceTest.java`

- [x] 先写失败测试：`SECONDARY_PARENT_BIND` 与 `PRIMARY_PARENT_TRANSFER` 仅允许 `WEB/MINIAPP`，正确验证码可返回既有家长或原子创建未注册家长，停用/非家长账号中性失败，当前协议必须接受。
- [x] 抽取 V36/V37 共用的“验证码校验后查找或创建家长”私有流程，保留现有微信绑定行为不变。
- [x] 新增关系邀请专用公开方法，返回 `VerifiedParentAccount`，不创建关系和会话。
- [x] 回归 V36、V37 家长认证测试，确认用途隔离且旧验证码不可跨流程使用。

## 任务 4：邀请、解绑、晋升与监护权转移状态机

**文件：**
- 新增：`server/src/main/java/com/lingdong/learning/student/application/ParentRelationshipApplicationService.java`
- 新增：`server/src/main/java/com/lingdong/learning/student/application/CreateParentRelationshipInvitationCommand.java`
- 新增：`server/src/main/java/com/lingdong/learning/student/application/RespondParentRelationshipInvitationCommand.java`
- 新增：`server/src/main/java/com/lingdong/learning/student/application/ParentRelationshipView.java`
- 新增测试：`server/src/test/java/com/lingdong/learning/student/application/ParentRelationshipApplicationServiceTest.java`

- [x] 先写失败测试：仅活动主家长可邀请、目标非本人、一个活动副家长、每家长最多 10 名学生、邀请 5 分钟、接受/拒绝、验证码消费、未注册建号和重复响应。
- [x] 实现副家长邀请与响应，创建响应仅返回邀请标识、脱敏手机号和到期时间。
- [x] 先写失败测试：主家长解除副家长、副家长不能自解绑、主家长自行解绑后最早副家长晋升、无副家长进入无主状态。
- [x] 实现关系行锁、关闭键、角色晋升和不可变审计，任何关系不存在或越权按资源不存在处理。
- [x] 先写失败测试：监护权转移由原主家长发起、目标确认、原主降副、目标拒绝/过期不变、并发接受仅一条成功。
- [x] 实现主副范围键安全交换和数据库唯一冲突的中性错误映射。

## 任务 5：家庭待审核任务转交

**文件：**
- 修改：`server/src/main/java/com/lingdong/learning/learningtask/infrastructure/persistence/TaskReviewMapper.java`
- 修改：`server/src/main/resources/mapper/learningtask/TaskReviewMapper.xml`
- 新增：`server/src/main/java/com/lingdong/learning/student/application/ParentRelationshipTaskTransferService.java`
- 新增测试：`server/src/test/java/com/lingdong/learning/student/application/ParentRelationshipTaskTransferServiceTest.java`

- [x] 先写失败测试：自动晋升和监护权转移只处理目标学生、`FAMILY` 来源、`PENDING_REVIEW` 任务；机构/教师任务及已完成任务不变。
- [x] 锁定待转交任务，更新 `current_reviewer_id`，为每项写入 `learn_task_reviewer_transfer`，原因为固定中文“主家长关系变更”。
- [x] 将转交服务纳入关系事务；转交任一条失败时关系、邀请、审计和全部任务整体回滚。
- [x] 验证无新主家长时不修改审核人，但原家长因失去主关系无法访问或审核。

## 任务 6：HTTP API、安全白名单与功能开关

**文件：**
- 新增：`server/src/main/java/com/lingdong/learning/student/web/ParentRelationshipController.java`
- 新增：`server/src/main/java/com/lingdong/learning/student/web/ParentRelationshipInvitationRequest.java`
- 新增：`server/src/main/java/com/lingdong/learning/student/web/ParentRelationshipInvitationResponse.java`
- 新增：`server/src/main/java/com/lingdong/learning/student/web/RespondParentRelationshipInvitationRequest.java`
- 新增：`server/src/main/java/com/lingdong/learning/student/web/ParentRelationshipResponse.java`
- 修改：`server/src/main/java/com/lingdong/learning/common/security/SecurityConfiguration.java`
- 修改：`server/src/main/java/com/lingdong/learning/auth/web/AuthenticationExceptionHandler.java`
- 新增测试：`server/src/test/java/com/lingdong/learning/student/web/ParentRelationshipControllerTest.java`

- [x] 先写控制器失败测试，覆盖设计文档 8 个端点、字符串雪花标识、关系学生列表、公开接受/拒绝、认证管理接口、完整手机号脱敏、功能关闭和直达拒绝。
- [x] 实现 DTO 与 Controller；公开响应仅返回受控会话或状态，不返回验证码、密码、令牌摘要和内部范围键。
- [x] 仅将邀请接受/拒绝加入公开白名单，关系查询、创建和解绑必须持有有效家长会话。
- [x] 扫描日志和异常，确认手机号、验证码、密码、访问/刷新令牌及来源地址不外泄。

## 任务 7：副家长跨模块只读

**文件：**
- 修改：`server/src/main/java/com/lingdong/learning/learningtask/application/LearningTaskScopeService.java`
- 修改：`server/src/main/java/com/lingdong/learning/growthpoint/application/GrowthPointQueryService.java`
- 修改：`server/src/main/java/com/lingdong/learning/growthpoint/application/GrowthRewardService.java`
- 修改：`server/src/main/java/com/lingdong/learning/growthpoint/application/GrowthRewardExchangeService.java`
- 修改：`server/src/main/java/com/lingdong/learning/growthpoint/application/GrowthReviewQueryService.java`
- 修改对应测试类。

- [x] 先为任务、积分、奖励和复盘各增加副家长读取成功、无关家长 404、写操作拒绝的失败测试。
- [x] 将只读查询范围从活动主关系扩展到任一活动关系；学生选项同时返回关系角色供前端隐藏操作。
- [x] 保持任务创建/审核、纠错、奖励配置/处理、复盘补录、二维码签发和登录码重置只接受活动主关系。
- [x] 回归所有主家长用例，确保主家长行为和机构数据隔离不变。

## 任务 8：Web 家长关系管理

**文件：**
- 新增：`web/src/features/parent-relationships/api.ts`
- 新增：`web/src/features/parent-relationships/ParentRelationshipPage.tsx`
- 新增：`web/src/features/parent-relationships/ParentRelationshipPage.test.tsx`
- 修改：`web/src/app/App.tsx`
- 修改：`web/src/styles/index.css`

- [x] 先写组件测试：开关与家长角色共同控制入口，主家长可邀请/解绑/转移，副家长只读，危险操作二次确认，功能停用直达拒绝。
- [x] 实现学生选择、主副关系列表、脱敏手机号、邀请到期反馈和监护权转移。
- [x] 接受邀请页面支持未登录用户填写手机号、验证码、协议并建立 Web 会话；提交失败保留表单。
- [x] 执行 `npm test` 与 `npm run build`；组件使用响应式栅格、可换行操作区和稳定宽度表单控制窄屏布局。

## 任务 9：uni-app 家长关系管理

**文件：**
- 新增：`miniapp/src/api/parent-relationship.ts`
- 新增：`miniapp/src/pages/parent-relationships/parent-relationships.vue`
- 新增：`miniapp/src/pages/parent-relationship-invitation/parent-relationship-invitation.vue`
- 修改：`miniapp/src/pages.json`
- 修改：`miniapp/src/pages/parent-home/parent-home.vue`

- [x] 通过 TypeScript 契约和条件构建先固定关系 API、家长独立会话及副家长只读状态。
- [x] 实现主家长邀请/解绑/转移、副家长只读和未登录邀请确认；使用图标按钮、二次确认和稳定尺寸。
- [x] 功能关闭后隐藏入口，直达页重新读取能力并拒绝操作；不得清除或覆盖学生会话。
- [x] 执行 `npm run type-check`、`npm run build:h5`、`npm run build:mp-weixin` 并扫描两类产物。

## 任务 10：全量回归与中文文档

- [x] 执行后端目标测试和 `mvn -q test`，确认 76 个套件、274 项测试全通过并从空库连续迁移 V1-V38。
- [x] 执行 Web 全量 14 个文件、50 项测试与生产构建，以及 uni-app 类型检查、H5/微信小程序双目标构建。
- [x] 扫描 66 张主键表、自增语法、敏感信息、功能开关、端侧隔离和副家长写权限。
- [x] 更新 README、设计文档 00-12、主开发计划、V38 专项清单和整体进度；真实外部联调继续单列未完成。
