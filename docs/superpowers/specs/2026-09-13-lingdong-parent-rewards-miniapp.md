# 家长小程序奖励配置与兑换处理（R04）

依据交互说明 4.2 家长端要求，补齐独立小程序家长页面，复用已有奖励及兑换状态机。保留学生本人申请端和 Web 页面；不重写积分扣减、72 小时审批时限、状态快照、版本锁及核销逻辑。

页面提供当前活动关系孩子选项，奖励与兑换分别分页显示服务端总数。主家长在动态操作权限及奖励开关允许时创建、编辑、上下架、删除奖励，以及同意、驳回、核销兑换；副家长仅查看。所有危险操作确认后再次验证当前权限，服务端重新校验活动主关系。系统审核员混合业务角色也不得读取或处理家庭奖励。

奖励表单沿用名称（30 字）、所需正整数积分、说明（200 字）、有效期和上下架状态。编辑保留原有效期，失败保留输入；发布或删除前说明实际影响。兑换详情使用已有名称、积分、说明快照与状态、审批时限；同意前明确扣分，驳回须填写原因，核销须确认已兑现。不将网络失败显示为操作成功，不自动重试写操作。

所有请求显式使用独立家长会话，HTTP ID 使用字符串；进入、恢复和操作前重验 MINIAPP 身份、当前角色权限与功能。切换孩子、离页或换会话使旧响应失效；权限或功能撤回清空旧内容。未授权时不请求奖励业务数据。

后端先补真实鉴权过滤链的合成 MINIAPP 会话契约，覆盖主/副家长、其他家庭、无机构学生、混合审核员、动态撤权、功能关闭及原状态机重复审批/核销；前端验证完整创建编辑和兑换处理流程。需要新增权限时仅新增迁移，当前最新 V70。离线 H2 和合成浏览器证据不能代替真实微信验收。

接口采用独立 `/api/v1/parent-rewards` 与 `/api/v1/parent-reward-exchanges`。孩子选项 GET `/parent-rewards/students` 接受两个小程序权限任一，返回 studentId/studentName/relationshipRole。奖励 GET/POST `/parent-rewards/students/{studentId}`、PUT/DELETE `/parent-rewards/{rewardId}`；兑换 GET `/parent-reward-exchanges/students/{studentId}`、POST `/{exchangeId}/approve|reject|verify`。请求体、分页与状态快照复用原业务定义。

V71 增加 `MINIAPP_REWARD_MANAGE_CHILD`、`MINIAPP_REWARD_EXCHANGE_REVIEW_CHILD` 并授予 PARENT；旧 Web 权限仍限定 WEB。两端均拒绝混合系统审核员，孩子选项不借用任务或周报权限。未新增业务表，下一迁移 V72。

当前状态：本地闭环已验证，真实微信环境验收仍待进行。

验证证据（2026-09-13）：新增路由红灯 `server/target/remaining-r04-parent-rewards-red.log` 预期 200 实际 404；混合审核员专项 `remaining-r04-parent-rewards-auditor-red.log` 两项失败、无错误，实际编译运行旧逻辑。该次测试期间生产修改存在交错，随后明确暂停修改并统一执行绿色验证。`remaining-r04-parent-rewards-green.log` 专项通过；`remaining-r04-parent-rewards-full.log` 全量 package 退出 0，183 类 720 项，失败/错误/跳过均 0。最终 JAR 和 target/classes 的本地配置排除检查通过，无远程写入或迁移执行。

小程序最终类型检查、H5 和微信构建通过，日志 `.local-verification/r04-parent-rewards-final-types.log`、`r04-parent-rewards-final-h5.log`、`r04-parent-rewards-final-weixin.log`。`test-parent-reward-access.cjs` 及 `verify-parent-rewards.cjs` 通过独立权限、创建编辑保留有效期、审批确认、核销、驳回、删除、副家长只读、功能关闭和混合审核员拦截；最终浏览器日志 `r04-parent-rewards-final-browser.log`。家庭任务浏览器回归通过。截图已查看，独立代码审查未发现阻断问题，并已补齐上架/下架影响确认文案。
