# 学生任务报表设计

范围：R06 独立 STUDENT_TASK_REPORT 数据集，六列学生、任务、来源、原始状态、净积分、最新打卡审核状态。Excel/PDF 均使用同一模板列与数据，模块 STUDENT_TASK_REPORT_EXPORT。本地源码迁移 V84；不执行远程迁移。

授权：拒绝任何 SYS_AUDITOR 混合身份；ORG_ADMIN > TEACHER > PARENT 单一分支。家长需活动绑定（主副均可读），且任务已发布或本人创建的家庭任务与 LEARNING_TASK_READ_MANAGED；教师和机构需 LEARNING_TASK_PROGRESS_READ；三者均需 STUDENT_TASK_REPORT_EXPORT。机构仅既有组织范围 ORGANIZATION 来源，教师仅本人 TEACHER 或已发布 ORGANIZATION，且每个实例学生须在本人活动班级。不得扩展原业务接口权限。

冻结：创建按筛选固化实例 ID 集及角色分支；使用独立 studentTaskMaxAssignments 配置（默认 50000）限制冻结集合；sheetMaxRows 仍仅控制 Excel 分表，超过限制拒绝并要求缩小范围，不截断。每页、完成前、查询下载复核全部冻结实例当前授权，任何撤权或记录删除整作业拒绝。新增授权与新实例不扩大旧集合。V84 将 scope_snapshot 扩为 MEDIUMTEXT，配置范围 1 至 50000，避免原 TEXT 64KiB 无法存储；ID 集仅内部快照，不进入 HTTP 响应。

事实：计划日期来自 assignment.scheduled_date；来源和原始状态来自实例；净积分按 growth_point_ledger.source_assignment_id 累计 TASK_REWARD/CORRECTION 的 amount（负纠错直接加）；审核来自最大 submission_no 的 checkin.status：SUBMITTED 待审核、APPROVED 已通过、REJECTED 已驳回，无记录未提交。纠错重开显示待审核，不查最后已通过记录。分页按实例 ID 单调推进，关系使用 EXISTS 避免重复。

筛选：可选 studentId、studentTaskSource、studentTaskStatus、startedAt/endedAt（计划自然日）；outputFormat 默认为 XLSX，PDF 仅本类型允许。学生选项与实例授权使用同一 SQL。

验证：真实 H2/MyBatis 验证主副家长、教师行级限制、机构范围与家庭排除、混合审核员、多角色、冻结新增授权、撤权、净积分和最新审核；接口校验跨类型筛选、真实文件格式、Excel/PDF 内容和附件补偿由统一测试执行。未完成验证前不声称通过。
