# R06 数据字典台账导出

依据既有统计设计第 4 节，仅补字典台账 Excel 导出。每行是一条字典项，字段为类型编码、类型名称、项编码、项名称、排序、状态、更新时间；按类型编码和项状态筛选，可按项更新时间闭区间筛选。包含停用项以支持台账审计，不把字典类型停用解释为删除历史。

仅非审核员的系统管理员可创建、执行及下载，要求数据导出、模板、附件、字典管理开关以及 `DICTIONARY_READ/DICTIONARY_EXPORT` 动态权限；查询本人作业要求原 `EXPORT_JOB_READ`。每次执行及下载复核权限。复用原异步作业、分页 XLSX、附件与失败补偿，不新增同步导出器。

采用独立 `DICTIONARY_REPORT` 默认导出模板组，避免替换现有积分及权限审计的 `REPORT` 默认模板。字段占位符和表头来自固定白名单；无手机号、凭据、人员或附件存储字段。当前数据按主键上界和游标读取，更新时间为业务记录更新时间，不声称历史时点快照。

V76 仅扩展导出类型约束、权限及模板模块选项，不新增业务表、不修改已执行迁移。验收要求：真实创建、领取、生成 XLSX、本人下载、筛选一致、撤权及混合审核员拒绝、Web 创建入口与参数测试。

## 模板配置

通过原“导入导出模板”页面上传 `.xlsx`，模板类型选择导出、模块选择“数据字典台账”，启用并设为该模块默认模板。工作簿第一张表首行 A1:G1 依次填写以下七个字符串（不添加标题行或公式）：

| A1 | B1 | C1 | D1 | E1 | F1 | G1 |
|---|---|---|---|---|---|---|
| `${TYPE_CODE}` | `${TYPE_NAME}` | `${ITEM_CODE}` | `${ITEM_NAME}` | `${SORT_ORDER}` | `${STATUS}` | `${UPDATED_AT}` |

实际导出会使用固定中文表头。管理员进入“数据导出中心”，选择“数据字典台账”、填写筛选和原因后提交，在本人作业成功后下载。模块没有默认模板时明确报错，不使用其他数据集模板兜底。此配置由既有模板管理能力完成，不预置用户附件或改写已有默认模板。

## 本地验证

- 后端先红：`server/target/remaining-r06-dictionary-red.log`，当时缺少模板模块选项；Web 先红：`.local-verification/r06-dictionary-web-red.log`，当时无新建入口。
- 后端专项：`server/target/remaining-r06-dictionary-focused.log`，四类 18 项通过。包含真实创建/领取/生成/下载及工作簿内容、混合审核员拒绝、日期闭区间、停用记录、固定上界游标、撤权和关闭字典功能后创建/执行/下载拦截。
- Web：`.local-verification/r06-dictionary-web-green.log`，5 项通过；`.local-verification/r06-dictionary-typecheck.log` 类型检查通过。
- 最终 Web：`.local-verification/r06-dictionary-web-final.log` 6 项通过，增加权限/功能不可用时隐藏历史内容入口；`.local-verification/r06-dictionary-web-build-final.log` 类型检查及生产构建通过。
- 最终后端：`server/target/remaining-r06-dictionary-final.log` 全量测试及 package 退出 0，188 类 749 项，失败/错误/跳过均 0；最终 JAR 含 V76、新 Mapper 且无 `application-local.yml/yaml/properties`，核验记录 `server/target/remaining-r06-dictionary-artifact-check.json`。首轮全量的两个旧模板选项断言已按版本范围和新增模块修正，V76 另核验系统管理员唯一默认授权和模板选项。

验证使用本地测试数据库，不代表远程 MySQL 升级或真实用户验收；生产仍需受控应用 V76 并配置该模块默认模板。
