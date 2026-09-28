# R06 导入导出模板台账

依据统计设计第 4 节，每行对应一个模板配置版本，只输出名称、类型、适用模块编码、版本、状态、更新时间六列。按类型、模块、状态和更新时间闭区间筛选，包含停用版本；不读取附件内容、存储键、内部标识或默认范围键，不将模板数量作为业务导入次数。

新增 `TEMPLATE_LEDGER`，复用原异步创建、领取、分页写入、附件下载及失败补偿。按模板主键捕获上界和游标，不沿用管理列表的 200 条上限，不声称历史时点快照。独立默认导出模板模块 `TEMPLATE_REPORT`，不替换其他数据集默认模板。

仅系统管理员且不兼具审核员身份可操作；要求原导出、模板、附件开关和 `IMPORT_EXPORT_TEMPLATE_READ/IMPORT_EXPORT_TEMPLATE_EXPORT` 动态权限，读取本人作业仍要求 `EXPORT_JOB_READ`。创建、执行、读取和下载复核当前权限。Web 权限关闭隐藏创建及历史内容入口。

V77 增加类型约束、权限和模板模块选项，不新增表或改写历史迁移。验证顺序为接口先红、实现、专项及全量 package、最终 JAR 排除本地配置、Web 测试及构建。仅使用本地测试数据库。

## 配置与接口

沿用 Web 的 `POST /api/v1/export-jobs` 和列表、详情、下载接口。`exportType=TEMPLATE_LEDGER`；可选 `templateType=IMPORT/EXPORT`、`templateModuleCode`、`templateStatus=ENABLED/DISABLED`，编码去空白后转大写；`startedAt/endedAt` 按模板更新时间闭区间过滤。禁止混用学生、权限事件或字典筛选；其他数据集禁止接受模板筛选。选项接口返回固定六列且学生列表为空。

在既有模板管理页上传 XLSX，类型选择导出，模块选择“导入导出模板台账”，启用并设为本模块默认模板。第一张工作表首行 A1:F1 依次配置以下字符串，不添加标题行或公式：

| A1 | B1 | C1 | D1 | E1 | F1 |
|---|---|---|---|---|---|
| `${TEMPLATE_NAME}` | `${TEMPLATE_TYPE}` | `${MODULE_CODE}` | `${VERSION}` | `${STATUS}` | `${UPDATED_AT}` |

实际结果替换为固定中文表头。无默认模板时明确报错，不借用其他模块模板。模板类型和状态输出中文，适用模块保留编码以与筛选及原记录一致。

## 验证记录

- 接口先红：`server/target/remaining-r06-template-red.log`，缺少 TEMPLATE_REPORT 模板模块。首次实现专项及首轮全量 package 已通过，日志为 `remaining-r06-template-green.log`、`remaining-r06-template-final.log`。
- 独立复核发现生成中撤权空档；`server/target/remaining-r06-template-revocation-red.log` 两项如期失败。修复后在 XLSX 每页读取前及保存附件后、提交成功前重新执行当前身份、权限、对象关系及开关校验，失败沿用原附件关系释放、内容清理与失败终态；PDF 保留既有复核。三类 20 项专项通过，日志 `remaining-r06-template-revocation-green.log`。
- 验证覆盖实际创建、领取、下载、六列工作簿内容、非法筛选与内部列拒绝、混合审核员拒绝、读取/导出权限撤销、功能关闭、超过管理页上限的 205 条分页、时间闭区间及捕获上界后新数据排除。
- Web 先红 `.local-verification/r06-template-web-red.log`；最终契约测试 `r06-template-web-contract-green.log` 8 项通过；类型检查和生产构建退出 0，日志 `r06-template-web-typecheck.log`、`r06-template-web-build.log`。构建保留既有分包体积提示。

真实环境仍需受控执行 V77、配置本模块默认模板并验收；本轮未执行远程数据库迁移。Maven 3.9.14 仅用于本地验证，不代表已满足 Maven 4 正式工具链要求。

最终全量：`server/target/remaining-r06-template-verified.log`，package 退出 0，188 类 755 项，失败/错误/跳过均 0。最终 JAR 包含 V77、新 Mapper，未包含 application-local.yml/yaml/properties；核验记录 `server/target/remaining-r06-template-artifact-check.json`。独立复核确认分页及成功前撤权处理闭合，游标、版本与附件补偿无新增问题。本项本地闭环完成，真实环境限制保留。
