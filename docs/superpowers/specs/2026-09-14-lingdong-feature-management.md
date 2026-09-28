# R07 全局功能开关管理闭环

依据：剩余开发计划 R07、FSD-SYS-01/04。复用现有 `FeatureToggleChangeService` 和系统任务审批，不创建直接生效接口。本专项只涉及 Web 全局配置管理，小程序业务继续通过既有能力接口读取结果。

## 实施边界

- Web 动态权限分为 `FEATURE_TOGGLE_READ`、`FEATURE_TOGGLE_MANAGE`、`FEATURE_TOGGLE_REVIEW`；管理员读取并提交本人申请，审核员读取已提交申请并审批。兼任管理员的审核员仍不得提交，不能自审。
- 管理页面展示全局开关、本人变更或审核历史、待审队列及持久化前后状态。管理权限自身不增加可使系统无法恢复开关的总开关。
- 提交必须带当前版本、目标状态、标题、说明及全局影响确认；服务器复核当前版本并固化原状态。审批按版本比较并更新，冲突不覆盖较新决定，保持待审并要求重新发起。
- 新增迁移 V73，不修改既有迁移。旧申请无原状态或版本时显示未知并拒绝执行，不用当前状态伪造历史。
- 定位与轨迹两个开关保持关闭，在创建申请和执行历史申请两处禁止启用；不开放新定位能力。
- 停用机构小程序认证继续撤销机构小程序会话。功能判定目前直接读取数据库，不声明不存在的开关缓存刷新成功。
- 标识及版本通过 HTTP 使用字符串；分页数量使用数字。所有验证使用本地合成数据，不对远程数据库迁移或写入。

## 接口约定

接口前缀 `/api/v1/feature-management`：

| 方法与路径 | 用途 |
|---|---|
| GET `/toggles` | 全局开关和当前版本 |
| GET `/changes` | 当前身份可见历史，支持页码、页大小及任务状态 |
| POST `/review-submissions` | 事务内创建并提交申请 |
| GET `/review-queue` | 审核员待审分页 |
| POST `/review-queue/{taskId}/approve` | 审批并执行领域变更 |
| POST `/review-queue/{taskId}/reject` | 必填意见驳回，不改变开关 |

系统任务工作台补充全局开关类型及领域入口。页面恢复焦点、提交和审批前重新读取当前权限；撤权时清空旧列表和弹窗，审批成功刷新能力与菜单。

## 验证记录

- 定位边界红灯：`server/target/remaining-r07-location-red.log`，四项均因未拒绝新申请或历史审批而失败。
- 定位边界与原有审批服务专项：`server/target/remaining-r07-location-green.log`，六项通过；旧审批用例改用学生登录停用，不再启用定位，并通过事务回滚隔离测试状态。
- 管理接口已使用 MyBatis XML 查询，复用原领域事务；后端专项 `server/target/remaining-r07-feature-special.log` 退出 0，覆盖新接口、原审批与定位边界及原系统任务查询。审批回滚用例改用独立 HTTP 请求事务，隔离 H2 数据库，避免在尚未结束的外层测试事务内错误断言。
- Web 专项 `.local-verification/r07-web-special.log`：2 文件 15 项通过，覆盖确认提交、字符串版本、角色边界、未知历史、撤权旧响应失效、冲突刷新及系统任务领域跳转。
- 合成浏览器 `web/scripts/verify-feature-management.cjs` 完成申请至审批生效、定位禁启和焦点撤权流程，日志 `.local-verification/r07-feature-browser.log`；截图 `.local-verification/feature-management-web.png` 已检查。浏览器使用合成 API，不代表真实后端联调。
- 后端完整回归与 package：`server/target/remaining-r07-feature-full.log` 退出 0，187 个测试类、735 项测试，失败/错误/跳过均为 0。包含新接口六项、定位边界四项，以及原服务和全项目回归。`tools/check-release.ps1 -ArtifactOnly` 确认最终 JAR 与 target/classes 不含本地配置。
- 最终合成浏览器复核 `.local-verification/r07-feature-browser-final.log` 通过。恢复已停止的本地 Vite 服务后运行，不依赖真实供应商或远程数据。
- 菜单撤权回归先在 `.local-verification/r07-web-menu-revoke-red.log` 复现，再修复为按请求世代同步最新权限到 App，清空列表并移除路由入口；`.local-verification/r07-web-special-final.log` 的 15 项专项和最终浏览器复核通过。
- Web 首轮全量 `r07-web-full.log` 捕获菜单撤权用例；第二轮 `r07-web-full-final.log` 虽有 194 项断言通过，但原任务模板测试的静态通知在环境销毁后触发 `window is not defined`，退出 1，不能计为全量通过。已仅在 `TaskTemplateLibraryModal.test.tsx` 补静态消息与确认弹窗清理，专项 `.local-verification/r07-web-notification-cleanup.log` 三项通过；未改模板业务实现、未屏蔽未处理错误。
- 审批在途与焦点刷新并发用例在 `.local-verification/r07-web-inflight-red.log` 复现；修复后仍发现旧闭包会以第一页覆盖第二页，最终改为写完成后调用最新分页状态的刷新函数。`.local-verification/r07-web-inflight-page-green.log` 九项通过，卸载和撤权隔离保留。
- 缓存边界收口后，后端完整回归 `server/target/remaining-r07-cache-full.log`：187 类、736 项，失败/错误/跳过均为 0；Web `.local-verification/r07-cache-web-full.log`：45 文件、195 项通过，生产构建 `.local-verification/r07-cache-web-build.log` 通过。最终 JAR 本地配置排除再次通过。
- Web 全量与生产构建：并发分页最终修复后重新执行中，尚未验收。

## 剩余限制

本专项尚未完成，不计为 R07 完成。系统任务其他领域的完整载荷和执行失败审计、接口服务调用边界仍按 R07 后续条目推进。缓存真实操作范围已于 2026-09-15 收口，真实 Redis 环境仍归 R10 验收。Maven 4 正式工具链及真实环境验收继续受原有约束。
