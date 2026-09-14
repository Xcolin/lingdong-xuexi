# 业务审核绑定当前展示打卡

原审核通过接口允许空请求体，服务端仅锁定并审核最新打卡。两个窗口先后操作、期间学生被退回后重新提交时，旧窗口可能审核自己未看过的新内容，与既有“并发旧请求返回 409”要求不符。

## 契约修正

- `POST /api/v1/task-reviews/{assignmentId}/approve` 请求体必须包含 `expectedCheckInId`。
- `POST /api/v1/task-reviews/{assignmentId}/reject` 同样必须包含 `expectedCheckInId`，并保留必填 `reviewComment`。
- `expectedCheckInId` 取页面实际展示的 `latestCheckIn.id`，必须为 19 位正整数字符串且处于 Long 范围；JSON 数字、缺失、空值或非法值返回 `400 VALIDATION_ERROR`。
- 服务端锁定当前审核人的任务及最新待审打卡后比较标识，不一致返回 `409 STATE_CONFLICT`，不改变打卡、任务或积分。不得先重新拉取最新标识再自动重试旧审核决定。
- 前端冲突后刷新详情，用户重新查看并确认后才能操作。Web、机构小程序和家长小程序同步传递该字段。

这是有意收紧的接口兼容边界：旧客户端缺字段会收到 400，必须更新客户端；不保留缺省审核最新内容的绕过路径。既有行锁、动态权限和当前审核人范围校验保留，不新增迁移。

## 验证边界

`LearningTaskControllerTest.miniappParentReviewsOwnPendingTasksWithDynamicAccessChecks` 复用创建、发布、学生打卡流程，在真实认证过滤器下使用本地 H2 合成家长 MINIAPP 会话（包括当前协议接受记录）；不代表短信或微信登录验证。

修复前 `remaining-r04-review-version-red.log` 复现旧标识通过返回 200，而断言应为 409。测试进一步覆盖旧标识退回、合法最新标识通过、缺失与非法标识、待办清空；最终执行结果由主任务统一运行并记录。
