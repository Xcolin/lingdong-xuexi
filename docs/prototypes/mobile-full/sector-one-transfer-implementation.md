# 独立转学申请实施契约（2026-10-14）

## 当前施工状态（2026-10-14）

已实现未部署候选：V129申请表、V130独立菜单权限、提交/详情/审核/撤销与范围分页；V131材料规则及独立上传/下载接口已接入，回归执行中。下面的“拟接口/尚未实现”表述保留为最初设计记录，实际完成度以施工日志和测试结果为准，不能据候选代码推断真实服务已开放。

材料上传复用 ManagedAttachmentContentService，限制PDF/JPG/PNG及10MB，家庭/有效主监护/当前入学关系与专属CREATE权限先验。申请后的下载复核当前VIEW及对象范围、申请附件ID、有效文件业务关系、文件状态和模块分类，按附件下载、禁止缓存和MIME嗅探，不提供公开存储地址。已补DELETE草稿材料显式退役与关联/退役共用文件行锁，待隔离HTTP验证；沿用现有未设置期限的保留口径，不自行按日期删除文件。

仍未完成：机构代办、到期执行与权限变化挂起、真实隔离HTTP、04-03页面接入、微信原生对照。新申请提交不得立即调用离校。

## 初始设计记录

04-03 原型：目标类型/目标学校描述、必填原因200字、预计日期、最多3材料。纯领域实现位于 student/application/transfer；它没有 mapper、HTTP、上传或离校调用，提交不会更改学籍。日期以注入业务当天判定；当天可提交；理由按 Unicode 码点200上限；附件仅正数且不重复的文件ID。文件存在、上传完成和归属必须在应用层另验，ID校验不意味着上传可用。

状态：PENDING 可由原申请人撤销，源机构其他审核人批准/拒绝；APPROVED 到预计日期后执行成功成为 COMPLETED；终态不可再次审核/撤销/完成。批准不自动加入目标机构。失败保留 APPROVED 供任务重试，不提前标记完成。纯领域机构ID检查不能替代管理员有效性/权限检查。

## 已有代码核实及对接

- InstitutionEnrollmentService 使用 STUDENT_ENROLLMENT_INVITE_CREATE、STUDENT_ENROLLMENT_REQUEST_CREATE、STUDENT_ENROLLMENT_REVIEW；审核依 class scope，创建检查有效家庭、学生及监护关系。其入学审核权限仅代表入学，不能直接授予转学审核。
- StudentManagementAccessService 通过 PermissionDecisionService 按客户端判定授权；生命周期 StudentOrganizationLifecycleService 使用 STUDENT_ORGANIZATION_RELATIONSHIP 开关、OrganizationDataScopeService 与关系停用逻辑。未来执行器需复核当前源机构关系并在同事务调用可复用的机构范围离校逻辑，禁止借调HTTP actor或绕过数据范围。
- AttachmentFileApplicationService 已有 registerUpload / completeUpload / attachToBusiness 与文件关系；AttachmentContentStorage 和保留清理基础设施存在。尚未验证转学模块上传路由、规则配置、对象存储及客户端二进制上传链路，不能宣称材料可用。需要新模块关系类型、上传完成校验、上传人/家庭归属、读取源机构权限、事务绑定与清理保留策略。
- scheduling 包已有 AttachmentSchedulingConfiguration 等任务。新增任务应走同类配置和日志体系，但不能直接复用附件清理任务处理学籍。

## 后续实现边界

新增申请表含申请/学生/家庭/源机构/创建者、目标描述、原因、日期、状态、审核人与时间、执行时间、版本；附件用受控文件关系表。唯一活动申请约束按学生和源机构，状态 PENDING/APPROVED。申请人身份保存实际操作者，管理员代办另记主监护信息，不混淆自审判定。数据库与迁移号待主线分配。

建议新增 STUDENT_TRANSFER_REQUEST_CREATE、STUDENT_TRANSFER_REVIEW、STUDENT_TRANSFER_VIEW，明确客户端及机构/家庭范围授权，不能从菜单可见推断权限。管理员代办同时要求既有学员管理权限和源学校范围；家庭提交要求有效主监护和有效家庭关系。每次读取、撤销、审核、下载附件及执行重新验证范围，审核禁止申请创建者自审。

拟接口（尚未实现）：POST /api/student-transfer-applications，GET 同路径，POST /{id}/approve、reject、withdraw；提交不调用离校。审批/撤销采用版本条件更新，重复审批拒绝；到期任务逐条行锁/条件抢占，锁定源关系、执行离校与完成落库同一事务，异常回滚并记录安全日志；多实例重试无重复离校历史。目标入学继续现有邀请码与审核接口，无原权限迁移。

仍需集成验证：家庭和源学校越权、离校后审批失效、重复并发申请、附件越权/清理、任务回滚和幂等、旧Web离校兼容。当前仅写JUnit领域测试，遵循主线程统一Maven执行安排，尚未执行；无HTTP/数据库/部署完成声明。业务待确认事项：若批准后主监护变化，建议执行前挂起人工复核，不能静默按历史授权执行。
