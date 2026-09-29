# R06 报表数据集缺口核对

依据：`docs/design/09-统计口径与报表设计-V1.0.md` 第 4 节；当前 `ExportJobType`、`ExportAdapterRegistry` 及 `exportjob/application/adapter` 源码。核对日期：2026-09-20。此表不把已有查询页面或模板维护计为报表导出完成。

| 既定报表 | 当前导出执行能力 | 下一实施边界 |
|---|---|---|
| 系统任务审批台账 | 新增 `SYSTEM_TASK_LEDGER`，本地闭环与全量回归通过 | 原五领域范围、身份快照与撤权复核；V80 和默认模板待部署 |
| 权限变更日志 | 已有 `IAM_CHANGE_AUDIT` | 保留敏感审批和既有字段脱敏；不重复开发 |
| 附件管理台账 | 新增 `ATTACHMENT_LEDGER`，本地闭环与全量回归通过 | V54 动态安全元数据权限及独立导出权，不赋源内容权；V83 和默认模板待部署 |
| 缓存操作日志 | 新增 `CACHE_OPERATION_LOG`，本地闭环验证通过 | 六列审计事实，待处理执行人留空，会话清除明确强制退出；V79和默认模板待部署 |
| 数据字典台账 | 新增 `DICTIONARY_LEDGER`，本地专项闭环通过 | 固定七列、类型/项状态/更新时间筛选；默认模板配置及真实环境验收待发布 |
| 导入导出模板台账 | 新增 `TEMPLATE_LEDGER`，本地闭环及全量回归通过 | 固定六列、类型/模块/状态/更新时间筛选；默认模板配置及真实环境验收待发布 |
| 接口服务台账 | 新增 `INTERFACE_SERVICE_LEDGER`，本地闭环与全量回归通过 | 六列元数据及调用方/状态/责任人/时间筛选；V78 和默认模板待部署 |
| 学生任务报表 | 缺数据集适配器 | 家长按亲子关系；教师/机构仅机构公开来源 |
| 家庭复盘报告 | 已有 `GROWTH_REVIEW_PDF` | 复用单份/区间及简洁/详细能力，真实环境验收归 R10 |
| 积分明细报表 | 已有 `GROWTH_POINT_LEDGER` | 核对其现有角色及来源范围；不直接放宽到机构/教师 |
| 奖励兑换报表 | 新增 `REWARD_EXCHANGE_LEDGER`，本地闭环与全量回归通过 | 活动主家长、申请快照与五列事实；V81 和默认模板待部署 |
| 机构任务统计 | 新增 `ORGANIZATION_TASK_STATISTICS`，本地闭环与全量回归通过 | 仅机构管理员；R-001 完成率与已完成实例净积分平均，失效实例不计入；按来源组织聚合、班级筛选、冻结撤权；V85 和默认模板待部署 |
| 考勤统计报表 | 新增 `ATTENDANCE_LEDGER`，本地闭环与全量回归通过 | 四身份沿用考勤台账口径；家庭必选学生、教师/机构可选冻结班级；V86 和默认模板待部署 |
| 异常报备台账 | 新增 `EXCEPTION_REPORT_LEDGER`，本地闭环与全量回归通过 | 教师本人/机构授权班级、身份冻结与动态撤权、双姓名脱敏；V82 和默认模板待部署 |

初始核对为三个已实现导出类型，2026-09-20 新增字典及模板台账，截至 2026-09-28，接口服务台账、缓存日志、系统任务审批台账、奖励兑换报表、异常报备及附件管理台账也完成本地闭环，当前十一个类型。2026-09-29 机构任务统计实现后为十二个类型，同日考勤台账实现后为十三个类型。其余两类不能凭列表页面、通用 XLSX 写入器或模板记录声明可导出。各新增类型仍需固定列、筛选和脱敏，接入原异步作业、下载前复核及最小 Web 入口后验收。字典和模板台账证据分别见 `2026-09-20-lingdong-dictionary-export.md`、`2026-09-20-lingdong-template-ledger-export.md`；系统任务证据见 `2026-09-26-lingdong-system-task-export.md`。

2026-09-28 用户已确认 R-001 至 R-004，见统计设计第 2.4 节；不再列为待业务确认，但相关公式、活跃度、趋势和机构统计仍需逐项实施验收。

业务导入当前学员开户与通用文件校验的能力应分别核验；本表仅核对报表数据集，不代表其他业务导入已完成。

接口服务台账验收证据见 `2026-09-22-lingdong-interface-ledger-export.md`；远程仍为已验证 V77，不能将本地 V78 测试当作已发布。

异常报备最新证据见 `2026-09-27-lingdong-exception-report-export.md`：后端 189 类 788 项，Web 全量 226 项，最终 JAR 检查通过；远程仍 V77，V78 至 V82 未发布。

2026-09-29 机构任务统计证据见 `.local-verification/r08-org-stat-red.log`（红灯）与 `r08-org-stat-green.log`（专项 3/3 转绿）；全量 809 项 0 失败 0 跳过见 `r08-org-stat-package.log`，JAR 产物检查通过见 `r08-org-stat-jar-check.log`；Web 类型检查与生产构建见 `r08-org-stat-web-build.log`，Web 全量 242 项见 `r08-org-stat-web-tests.log`。口径固化见统计设计第 2.5 节。

2026-09-29 考勤台账证据见 `.local-verification/r09-att-ledger-red.log`（红灯）与 `r09-att-ledger-green.log`（专项 3/3 转绿，覆盖真实文件、撤权与跨数据集筛选拒绝）；exportjob 模块回归 149 项见 `r09-att-ledger-regression.log`，全量 package 与 JAR 产物检查通过见 `r09-att-ledger-package.log`；Web 类型检查与生产构建见 `r09-att-ledger-web-build.log`，Web 全量 45 文件 243 项见 `r09-att-ledger-web-tests.log`。口径固化见统计设计第 2.6 节。

2026-09-29 学员活跃度（R-002）与机构统计转班核对（R-004）证据见 `.local-verification/r10-act-trend-red.log`（红灯 404）与 `r10-act-trend-green.log`（ActivityTrendApiIntegrationTest 3/3 转绿：按日聚合与授权范围、身份与日期拒绝、转班后 source_organization_id 不变且导出内容按来源班级归属）；exportjob 全模块回归 0 失败见 `r10-exportjob-regression.log`，全量 package 通过。活跃度聚合口径固化见统计设计第 2.7 节；期间修复机构统计适配器完成率分母为 0 时 Map.of 空 value 抛 NPE 的边界（改为允许空单元格）。

2026-09-29 学员批量导入验收证据见 `.local-verification/r11-stu-import-green.log`（StudentImportApiIntegrationTest 3/3：真实 XLSX 全链路开户与数据库事实核对、一次性凭证下载与重复下载 409、格式/逐行/表头重复错误、越权与范围外班级 403、未校验作业与重复执行 409、开关禁用致全行失败与 REQUIRES_NEW 行级事务边界、恢复后失败重试转绿）；importjob + studentimport 模块回归 41 项 0 失败见 `r11-stu-import-regression.log`；Web 端 ImportJobManagementPage 学员导入执行与凭证状态页面测试 4 项通过，页面与数据库事实一致以固定业务样例核对。

附件最新证据见 `2026-09-28-lingdong-attachment-ledger-export.md`：后端 189 类 797 项、Web 45 文件 234 项和最终构建、JAR 检查通过。2026-09-29 考勤台账新增 V86 后当前下一迁移 V87；远程仍 V77，V78 至 V86 未发布。

2026-09-29 附件生命周期（4.1/4.2）证据见 `.local-verification/r14-attachment-storage-tests.log`（附件模块 15 项：LocalAttachmentContentStorageTest 新增 keepsContentReadableAcrossRestartWhenRootIsStable 重启可读用例——同根目录重建实例后内容仍可读）与 `r14-attachment-lifecycle-tests.log`（附件模块 28 项全绿，含新增 AttachmentRetentionCleanupServiceTest 4 项）。4.1：application.yml 附件根默认值由 `${java.io.tmpdir}` 改为 `${user.dir}/data/attachments` 稳定目录（ATTACHMENT_LOCAL_ROOT 指向持久卷），与 export-job.temp-directory（可丢弃临时产物）明确区分，`.gitignore` 增加 `server/data/`；云存储不接入，保留 AttachmentContentStorage 端口。4.2：既有保留口径（BRD 删除仅解除业务可见关系、已归档业务附件保留可追溯、V54 未关联临时文件上传人软删、导出失效退役+内容清理、无附件专属期限）基础上，新增 AttachmentRetentionCleanupService + AttachmentSchedulingConfiguration（`lingdong.attachment.cleanup.*`）：不按时间清理、只对 RETIRED 残留物理内容幂等重试收敛（findRetiredBatch + deleteIfExists 语义）；UPLOADING 滞留孤儿无期限依据不自动清理，待业务确认保留周期后另立专项；测试 profile 调度关闭。部署要求见 `docs/deployment/外置配置与发布检查.md`。

2026-09-29 附件安全验证（4.3）证据见 `.local-verification/r15-attachment-security-tests.log`（TaskAttachmentApplicationServiceTest 8/8：新增越权预览/下载统一 404 不泄露存在性且不触发内容读取、当前审核人可读、RETIRED 销毁后即使上传人也拒绝、销毁链 markRetired + 内容清理后访问拒绝）与 `r15-attachment-regression.log`（附件模块 31 项 0 失败）、`r15-attachment-package.log`（全量 831 项 0 失败 0 跳过 + JAR 构建成功）。路径穿越（`../outside` 拒绝）、伪装类型拒绝、规则大小/格式限制、文件与关系台账审计事实由既有用例覆盖。阶段 2 四项外部决策经用户二次确认：保持 Maven 4 门槛、短信保持通道未配置、微信周报待真机联调（归 7.2）、定位功能保持关闭并从剩余范围移除（7.3 明列）。
