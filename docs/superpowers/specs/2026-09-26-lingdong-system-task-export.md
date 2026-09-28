# R06 系统任务审批台账实施边界

依据统计设计第 4 节和 `SystemTaskQueryService` 当前五领域查询，复用系统任务审计事实，不新增审批接口或业务载荷导出。

## 权限范围

系统管理员仅本人任务；审核员（含混合身份）只读取已提交、非草稿且当前领域开关和审核权限可见的任务。领域范围严格复用现有查询服务：全局开关、组织变更、缓存、接口服务、敏感导出；不导出尚未落地的任务类型。

新增导出权限只允许生成台账，不授予审批权。原 SYSTEM_TASK_READ、导出/模板/附件开关和领域权限仍需满足。创建时固化管理员/审核员身份范围及可见领域集合；执行每页、成功终态和下载时重新求取当前权限。身份范围改变或原集合中任一领域失去访问权限时拒绝旧文件访问，不能仅重查列表而允许下载旧的宽范围文件。

## 数据与筛选

固定列为任务类型、发起人标识、审批人标识、状态、创建时间、审批意见；标识保持字符串，尚无审批人/意见时为空。意见来自原任务公开审计字段，经既有工作簿文本防公式处理；不包含任务业务载荷、范围快照、密钥或附件内容。

类型、状态和创建时间闭区间筛选。创建时捕获主键上界，按游标分页；读取 SQL 使用创建时的受控领域集合及当前复核后的身份范围，用户输入不能指定申请人来绕过本人限制。默认模板独立，不借用其他数据集模板。

## 模板与接口

导出类型为 `SYSTEM_TASK_LEDGER`，模板模块为 `SYSTEM_TASK_REPORT`；管理员通过现有模板管理上传并设置独立默认模板。首行使用 `${TASK_TYPE}`、`${SUBMITTER_ID}`、`${REVIEWER_ID}`、`${STATUS}`、`${CREATED_AT}`、`${REVIEW_COMMENT}`。任务类型和状态保留原枚举值，时间格式为 `yyyy-MM-dd HH:mm:ss`；不可使用其他模块模板代替。

请求增加 `systemTaskType`、`systemTaskStatus`；选项增加 `systemTaskTypes`，由后端当前可见范围产生。Web 创建、详情、下载沿用导出中心，权限或角色上下文变化时清除旧详情、重取选项并丢弃迟到下载结果。V80 只增加类型约束、模板模块、`SYSTEM_TASK_EXPORT` 及审核员本人作业读取授权，不增加业务表或审批能力。

## 验证记录

API 红灯 `server/target/remaining-r06-system-task-red.log` 确认原类型不存在。整合专项 `remaining-r06-system-task-green.log` 中新台账工作簿、范围及撤权用例通过，但整轮因两个旧迁移/模板选项断言失败，不记为绿灯；断言已按新增授权和模块修订，保持禁止越权的断言。

Web 最新组件 22 项、App 权限 19 项通过，日志分别为 `.local-verification/r06-system-task-web-reviewed.log`、`r06-system-task-app-reviewed.log`；类型检查和生产构建通过，日志 `r06-system-task-web-build.log`。包括总体导出权限仍存在但领域范围改变时不保存迟到文件。

2026-09-27 后端最终全量 `server/target/remaining-r06-system-task-verified.log` package 退出 0，189 类 776 项，失败/错误/跳过均 0，全部 Surefire XML 均为本轮更新。覆盖实际 XLSX 六列、公式防护、管理员本人/他人、审核员草稿和未提交隔离、混合身份、角色变化、领域停用/撤权、205 行分页和固化上界、生成中撤权及附件补偿。最终 JAR 包含 V80 和新 Mapper；`tools/check-release.ps1 -ArtifactOnly` 确认构建资源及 JAR 均无本地配置，记录 `server/target/remaining-r06-system-task-artifact-check.json`。

此数据集本地闭环完成。远程仍为 V77，V78 至 V80 及默认模板未部署，真实环境验收另归 R10；本地 Maven 3 运行不替代 Maven 4 发布要求。
