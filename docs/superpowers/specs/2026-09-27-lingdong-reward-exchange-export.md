# R06 奖励兑换报表实施边界

依据统计设计第 4 节，复用既有奖励兑换事实、活动主家长关系和异步导出作业，不新增兑换流程或统计公式。

## 数据与权限

类型 `REWARD_EXCHANGE_LEDGER`，独立模板模块 `REWARD_EXCHANGE_REPORT`。固定五列 `REWARD_NAME`（申请时奖励名称）、`REQUIRED_POINTS`（申请时所需积分）、`REQUESTED_AT`（申请时间）、`APPROVAL_STATUS`（审批状态）、`VERIFICATION_STATUS`（核销状态）。不包含奖励描述、驳回理由、内部身份或文件内容。文本沿用工作簿防公式处理。

审批状态：PENDING_APPROVAL 为待审批，PENDING_VERIFICATION 和 VERIFIED 为已通过，REJECTED 为已驳回，AUTO_REJECTED 为超时驳回；EXPIRED 若已有 reviewed_at 则已通过，否则未审批。核销状态：VERIFIED 为已核销，PENDING_VERIFICATION 为待核销，EXPIRED 为已过期，其余为未核销。保留原状态事实，不将过期推断为通过。

必须指定学生；可选 `rewardExchangeStatus` 为原六种兑换状态，申请时间闭区间。学生选项仅活动主家长关系。动态 PARENT 角色且不含 SYS_AUDITOR、活动主家长关系、`REWARD_EXCHANGE_EXPORT`、`REWARD_EXCHANGE_REVIEW_CHILD`、`REWARD_EXCHANGE` 开关，以及原导出/模板/附件开关同时满足。查询本人作业仍需 `EXPORT_JOB_READ`。创建、每页执行、成功终态、详情和下载均复核。不得放宽至副家长或机构教师。

## 实施与验收

新增迁移先核对最新编号，预期 V81，只增加权限、类型约束与模板模块。复用导出中心创建、筛选、详情和下载，动态撤权后清除旧内容、拒绝迟到下载。数据分页固定主键上界和游标，禁止混用其他数据集筛选。

先红后绿验证真实 XLSX 快照和各状态、主副家长和他人学生隔离、混合审核员、功能关闭/撤权、跨页及上界、非法筛选；完成全量 package、Web 类型检查与生产构建、最终 JAR 配置排除后记录证据。远程迁移和独立默认模板部署未完成，不以本地测试代替真实验收。

## 模板配置与验证记录

默认模板首行使用 `${REWARD_NAME}`、`${REQUIRED_POINTS}`、`${REQUESTED_AT}`、`${APPROVAL_STATUS}`、`${VERIFICATION_STATUS}`。通过现有模板管理上传至 `REWARD_EXCHANGE_REPORT` 并设为默认，不借用积分或系统任务模板。

Web 缺入口红灯 `.local-verification/r06-reward-web-red.log` 已复现；最终专项 `r06-reward-web-final.log` 共 46 项通过，覆盖创建、状态筛选、四项开关、角色和权限以及撤权后详情和迟到文件失效。类型检查/生产构建通过，日志 `r06-reward-web-build.log`。

后端红灯 `server/target/remaining-r06-reward-red.log` 确认原类型/模板缺失；首轮整合 `remaining-r06-reward-integration.log` 发现数字学生标识被自动转换、停用状态码断言与既有 409 契约不符、模板模块断言未更新。已修复字符串边界并保持原功能停用契约，详情范围文字也区分奖励与积分报表。四类专项 `remaining-r06-reward-green.log` 共 106 项通过，覆盖工作簿、权限/关系复核、205 条分页和上界、生成中撤权补偿及迁移。

2026-09-27 最终 `server/target/remaining-r06-reward-verified.log` package 退出 0；189 类、782 项通过，失败/错误/跳过均 0，全部报告为本轮更新。最终 JAR 包含 V81/新增 Mapper，target/classes 与 JAR 均无 application-local 配置，证据 `remaining-r06-reward-artifact-check.json`。本数据集本地闭环完成；远程 V77 未变，V78 至 V81 及默认模板尚未部署，Maven 4 发布要求和真实环境验收仍未完成。
