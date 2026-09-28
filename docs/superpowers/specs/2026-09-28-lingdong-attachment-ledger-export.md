# R06 附件管理台账导出边界

依据统计设计第 4 节和 V54 附件管理设计第 4、6 节，复用已有安全元数据管理权限，不改变业务文件预览和下载权限。当前实现与验证记录见文末。

## 权限与范围

统计条目的“有业务权限”在本管理台账中沿用 V54 明确的安全元数据授权 `ATTACHMENT_FILE_LEDGER_READ`，并新增 `ATTACHMENT_FILE_LEDGER_EXPORT`；它不代表取得源附件内容权限，不通过导出结果提供预览、签名链接或源文件下载。保持现有元数据台账范围，不更改任务、模板、家庭报告等业务内容的对象授权。

账号启用、WEB 动态权限（DENY 优先）、DATA_EXPORT、IMPORT_EXPORT_TEMPLATE_MANAGEMENT、ATTACHMENT_SERVICE 均满足。新权限默认只给系统管理员，继续允许显式授予自定义运维角色；不照抄其他台账的内置角色限制。本人作业读取另需 EXPORT_JOB_READ，自定义运维账号仅因拥有两项附件台账权限取得作业列表的必要通路，不获得其他数据集创建/下载权限。沿既有当前账号/权限/功能检查执行创建、每页、终态、详情和下载，撤权时停止或回收未完成附件。

## 字段和筛选

类型 `ATTACHMENT_LEDGER`，模板 `ATTACHMENT_LEDGER_REPORT`。默认七列 NAME、MODULE_CODE、UPLOADER_NAME、CREATED_AT、FILE_CATEGORY、SIZE_BYTES、STATUS，沿通用列子集选择和公式注入转义。名称、模块、上传人取既有安全台账；时间明确为创建时间（created_at），类型明确为文件分类，大小为字节。上传人姓名沿导出统一姓名脱敏处理。

可选 attachmentModuleCode、attachmentUploaderId（19位字符串且在Long范围）、attachmentFileCategory；时间沿通用 startedAt/endedAt 创建时间闭区间。拒绝 studentId、其他数据集筛选和非白名单列。空白筛选归空，模块/分类沿旧台账规范化。无需全量用户枚举，上传人按字符串 ID 筛选。

另建有上界和游标的分页查询，不复用原列表 LIMIT 200。仅 SELECT 七列及内部游标 ID，不读取 storage_key、content_sha256、实际路径、关系历史或文件字节。当前台账权限是统一元数据读取范围，新增业务引用或角色不得作为源内容授权。

## 迁移与验收

已核对并新增 V83。不修改历史迁移，不新增业务表，不执行远程写入。新增类型约束、独立模板模块、新权限及最小默认授权，普通台账类型必须无 studentId、systemTaskId 且非敏感审批作业。

测试实际 XLSX 七列、姓名脱敏和公式转义、未上传/已退休等原文件状态、时间闭区间、205 行分页及上界；动态自定义运维权限和 DENY、非所有者、源内容权限不被放开、执行中及下载时撤权。Web 创建/筛选/下载入口与动态权限、迟到文件保护。完成专项、最终全量与构建、JAR 本地配置排除后才更新矩阵。

## 2026-09-28 验证记录

红灯 `server/target/remaining-r06-attachment-red.log` 确认缺少独立模板模块，新增迁移及链路后 `remaining-r06-attachment-green.log` 专项通过。最终 `remaining-r06-attachment-verified.log` package 退出 0：189 类 797 项，失败/错误/跳过均 0；全部 189 份报告本轮更新。实际工作簿覆盖七列、姓名脱敏、公式转义、205 行分页、迟到上界排除、创建时间闭区间、上传人字符串筛选及 AVAILABLE/RETIRED/UPLOADING 状态。动态自定义角色、DENY、撤权、非本人读取、源内容访问拒绝及执行中附件回收均通过。

`remaining-r06-attachment-artifact-check.json` 确认 V83 与新 Mapper 在最终 JAR 中，构建资源和 JAR 均无 application-local 配置。Web 专项 `r06-attachment-web-green.log` 54 项通过（新增五项先红），`r06-attachment-web-build-final.log` 类型检查及最终生产构建通过；全量 `r06-attachment-web-full.log` 为 45 文件 234 项通过，退出 0。本地闭环完成，计为第十一类数据集。

远程仍为 V77，V78 至 V83、独立默认模板和真实环境验收尚未部署；Maven 4 正式版要求保持，当前 Maven 3.9.14 验证不等同于发布准入。未修改源附件内容授权、未执行远程写入。
