# R06 异常报备台账实施边界

依据统计设计第 4 节，复用现有异常报备事实、班级选项及组织授权，不改变异常提交或处理流程。

## 范围和权限

类型 `EXCEPTION_REPORT_LEDGER`，独立模板模块 `EXCEPTION_REPORT_EXPORT`，导出权限 `EXCEPTION_REPORT_EXPORT`，另需 `EXCEPTION_REPORT_READ`、`STUDENT_EXCEPTION_REPORT` 及原导出/模板/附件开关，账号须启用。仅动态 TEACHER 或 ORG_ADMIN，任何含 SYS_AUDITOR 的身份拒绝。TEACHER+ORG_ADMIN 沿用机构分支；普通教师仅本人报备且当前仍绑定的班级。

复用 `ExceptionReportApplicationService.findClassOptions` 的当前有效班级决策（班级 status/effectiveStatus 均启用）；不扩大或重写历史异常列表行为。创建时固化所选授权班级 ID 集合及 teacherOnly 身份，上界和游标沿用原作业。未指定班级时固化全部当前有效班级，指定时只固化该班；无权班级直接拒绝。当前范围必须包含全部固化班级，且身份范围不变，才能执行每页、成功终态、读取详情及下载。教师解绑、班级停用、组织移出授权范围均拒绝旧文件；新授权不扩大已创建作业。

## 字段与契约

默认五列白名单（沿用通用列子集选择） `STUDENT_NAME`、`EXCEPTION_TYPE`、`REPORTER_NAME`、`STATUS`、`REPORTED_AT`。学生和报备教师姓名沿用 ExportMasking 首字加星号；类型与状态按现有枚举输出，时间取 reported_at。不导出账号、异常正文、处理意见、动作历史和组织内部路径，不推断心理风险；MENTAL_STATE 作为原合法类型保留。

可选 `exceptionClassId`（19 位字符串）、`exceptionType`（ATTENDANCE/LEARNING_STATUS/MENTAL_STATE）、`exceptionStatus`（SUBMITTED/HANDLED）；沿用通用报备时间闭区间。拒绝其他数据集筛选和 studentId。选项新增 `exceptionClasses: [{id,name}]`，只含当前有效授权班级，其他数据集返回空集合。

新增迁移已核对并使用 V82；授予教师/机构管理员导出权限及本人作业读取权限，不授予审批或家庭报表权限。新增记录使用19位雪花标识，不改已执行迁移，不连接远程。

## 验收

最小 Web 导出中心闭环；类型/班级/状态和时间筛选、动态撤权详情和迟到文件失效。后端验证实际工作簿五列与双姓名脱敏、教师他人报备隔离、机构范围、混合审核员、MENTAL_STATE、班级停用/解绑/角色变化、组织范围变化后旧文件拒绝、分页上界及执行补偿。最终全量、Web 构建和 JAR 配置排除通过后才计完成，部署与默认模板另记。

## 当前验证记录

2026-09-28 Web 新入口红灯 `.local-verification/r06-exception-web-red.log` 已复现；专项 `r06-exception-web-green.log` 共 49 项通过，类型检查和生产构建通过，日志 `r06-exception-web-build.log`。覆盖服务端班级选项、原类型/状态、字符串班级标识、角色和四项开关，以及权限上下文变化时清除详情和丢弃迟到文件。

后端 `server/target/remaining-r06-exception-red.log` 为缺少模块的明确红灯；首轮整合因机构测试尚未建立组织关联就授角色而失败，仅调整测试准备顺序。`remaining-r06-exception-green-final.log` 专项通过。最终 `remaining-r06-exception-verified.log` package 退出 0：189 类 788 项，失败、错误、跳过均为 0，189 份报告全部本轮更新。覆盖教师本人隔离、机构兼教师取机构范围、组织移出、班级停用/解绑/角色变化、混合审核员、非法筛选、205 行分页上界和新增授权不扩大，以及分页前和文件保存后撤权补偿。

Web 全量 `.local-verification/r06-exception-web-full.log` 为 45 文件 226 项通过。最终产物 `remaining-r06-exception-artifact-check.json` 确认 V82 和新 Mapper 已打入 JAR；`tools/check-release.ps1 -ArtifactOnly` 确认 classes/JAR 无 application-local 配置。本地闭环完成并计为第十类数据集，远程仍 V77，V78 至 V82、默认模板和真实环境验收尚未发布；Maven 4 正式版要求未降低。
