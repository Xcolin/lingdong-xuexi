# R06 接口服务台账导出

依据 `docs/design/09-统计口径与报表设计-V1.0.md` 第 4 节，继续现有数据集矩阵，不新增统计指标或接口调用能力。

## 范围与字段

新增 `INTERFACE_SERVICE_LEDGER`，复用原异步导出作业、模板、分页写入、附件下载和失败补偿。每行对应一个已生效登记的接口服务，包含启用和停用记录，不把待审核申请当作已生效服务。

固定白名单六列：名称 `SERVICE_NAME`、用途 `PURPOSE`、调用方 `CALLER_NAME`、授权范围 `AUTHORIZATION_SCOPE`、状态 `STATUS`、责任人 `OWNER_ID`。用途保留原枚举编码；范围展示原公开范围类型和非空范围值；状态中文；责任人沿原管理接口使用字符串标识，避免 Excel 数值精度损失。不读取或导出密钥、接口地址、存储信息、调用报文或额外个人字段。

## 契约与权限

- 可选 `interfaceCallerName`：输入最长 100 字符（含输入空白），查询前去首尾空白，按既有管理列表的大小写不敏感包含匹配。
- 可选 `interfaceStatus`：`ENABLED/DISABLED`；可选 `interfaceOwnerId`：有效的 19 位雪花字符串标识。
- `startedAt/endedAt`：按服务更新时间闭区间筛选；以主键捕获上界并分页，不套用管理列表数量上限，不声称历史时点快照。
- 拒绝学生、权限事件、字典、模板筛选混入本类型；其他类型拒绝接口服务筛选。
- 仅系统管理员，兼具审核员身份时拒绝。需要既有 `INTERFACE_SERVICE_READ` 和新增 `INTERFACE_SERVICE_EXPORT`，本人作业读取仍需 `EXPORT_JOB_READ`；沿用接口服务、导出、附件及模板开关。
- 创建、每页执行、成功终态、详情与下载复核当前权限。Web 功能停用或撤权隐藏对应入口、关闭已打开内容，旧异步选项响应不恢复权限已撤回的窗口。

## 实施与验证顺序

1. 按既有接口和实际工作簿测试先复现缺能力；接入适配器及 V78 权限、类型约束、模板模块（落盘前核对编号），不改已执行迁移。
2. Web 沿用数据导出中心，完成创建、筛选、详情和下载入口及权限撤回测试。
3. 后端专项、全量 package、最终 JAR 配置排除；Web 专项、类型检查、生产构建。所有必要验证结束后记录结果。
4. 本地完成不等于真实环境验收；V78 尚未应用到远程数据库前，部署状态仍为 V77。

## 默认模板配置

在原模板管理中为导出模块 `INTERFACE_REPORT` 配置启用的默认 XLSX 模板。第一张工作表首行 A1:F1 依次为 `${SERVICE_NAME}`、`${PURPOSE}`、`${CALLER_NAME}`、`${AUTHORIZATION_SCOPE}`、`${STATUS}`、`${OWNER_ID}`；不添加额外标题行或公式。生成结果替换为中文表头。缺少本模块默认模板必须明确报错，不借用其他数据集模板。

## 验证记录

2026-09-26 本地闭环完成，独立复核未发现阻断问题。

- 后端先红：`server/target/remaining-r06-interface-red.log`，缺少独立模板模块；实现后专项通过。固定样例验证实际六列 XLSX、字符串责任人、非法筛选和跨类型混用拒绝、混合审核员、动态撤权和停用，以及 205 行分页、时间闭区间和捕获上界。
- 最终后端 `server/target/remaining-r06-interface-final.log`：package 退出 0，189 类 763 项，失败/错误/跳过均 0；全部报告本轮更新。最终 JAR 包含 V78 和专用 Mapper，资源和产物均无 application-local 配置。
- Web 初次先红 `.local-verification/r06-interface-web-red.log`；下载等待中撤权问题由 `r06-interface-download-revoke-red.log` 复现后修复。最终 `r06-interface-web-verified.log` 覆盖页面及 App 权限，31 项通过；`r06-interface-web-build-verified.log` 类型检查及生产构建通过。此前全量 `r06-interface-web-full.log` 为 45 文件 205 项通过；末次撤权补丁采用上述受影响专项验证，不冒充又一次全量。
- 撤回接口导出或作业读取权限会清除详情；附件、模板、接口服务、导出开关共同限制入口；等待中的选项、详情及文件响应在撤权或卸载后失效。
- 本轮未执行远程迁移或配置真实默认模板。部署仍需 V78 及 `INTERFACE_REPORT` 默认模板，Maven 4 正式工具链和 R10 真实环境验收限制保留。现有远程 V77 的历史迁移未修改。
