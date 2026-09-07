# 灵动学习 V41 学生机构关系生命周期实施计划

> **执行说明：** 使用 `superpowers:executing-plans` 按任务顺序逐项实施，并用复选框（`- [ ]`）记录状态。

**目标：** 完成学生校内转班、机构关系停用、跨机构转出边界、不可变审计及 Web/uni-app 独立操作入口。

**架构：** 复用 `edu_student_organization` 作为当前关系事实源，新增不可变变更表记录完整事件。应用服务在学生行锁和单事务内校验组织范围、切换班级或停用入学关系；Web 与机构小程序通过 `BOTH` 权限和同一 API 获得一致规则。

**技术栈：** Spring Boot 3、JDK 17、Maven 4、MyBatis XML、Flyway、H2/MySQL 兼容 SQL、React/Ant Design、uni-app/Vue 3/TypeScript。

---

## 文件结构

- 新增 `V41__add_student_organization_lifecycle.sql`：变更审计表、功能开关、双端权限和角色授权。
- 新增 `StudentOrganizationLifecycleService`：集中执行查询、转班和转出事务。
- 新增关系视图、命令、变更领域记录与专用 Mapper：保持控制器、领域和 SQL 职责清晰。
- 扩展 `StudentOrganizationMapper`：锁定活动入学关系、停用指定子树班级和入学关系。
- 扩展 `StudentManagementController`：提供三个显式生命周期接口。
- 改造 Web `StudentClassAssignmentDrawer`：升级为学员关系管理入口。
- 新增 uni-app `organization-students` 页面和 API：使用独立机构会话。
- 同步 00-12 中文设计文档、README 和主开发进度。

## 任务 1：V41 迁移和持久化约束

- [x] 在 `FlywayMigrationTest` 先断言 V41、新审计表、`STUDENT_ORGANIZATION_RELATIONSHIP`、`STUDENT_ORGANIZATION_MANAGE`、`BOTH` 客户端及 19 位标识，运行测试确认红灯。
- [x] 新增 V41 迁移，所有主键使用显式 `BIGINT`，不使用自增；审计表只建查询索引和外键。
- [x] 重新运行迁移测试，确认 V1-V41 空库连续迁移通过。

## 任务 2：关系持久化和不可变审计

- [x] 在使用真实 MyBatis XML 的控制器集成测试中覆盖活动入学锁、活动班级查询、子树班级停用、入学停用、审计插入与倒序查询，并确认缺失接口红灯。
- [x] 新增 `StudentOrganizationChange`、`StudentOrganizationChangeType`、`StudentOrganizationChangeMapper` 和 XML。
- [x] 扩展 `StudentOrganizationMapper`，SQL 必须显式限定 `relation_type`、`status` 和组织路径。
- [x] 运行集成测试确认绿灯，不提供审计更新或删除方法。

## 任务 3：生命周期应用服务

- [x] 在应用服务集成路径覆盖首次分班、同校转班、同班幂等、跨校拒绝、无活动入学拒绝、转出级联停用、学生账号不变和审计原子性。
- [x] 新增命令与视图对象，所有标识在领域层使用 `Long`，Web 响应再转字符串。
- [x] 实现 `StudentOrganizationLifecycleService`；复用学生行锁、组织数据范围和功能开关，不读取或修改家庭私有数据。
- [x] 运行应用服务集成测试确认绿灯，并将原班级配置接口统一委托生命周期服务。

## 任务 4：HTTP、客户端权限和安全边界

- [x] 先在 `StudentManagementControllerTest` 增加接口成功、404、400、功能停用和 MINIAPP/BOTH 权限测试，运行确认红灯。
- [x] 新增请求响应类型并接入控制器；原因字段 1 至 200 字，转出请求必须携带活动入学组织。
- [x] 将旧 `PUT /students/{id}/class` 保留为兼容入口，新页面使用显式转班接口；两条路径共享生命周期服务。
- [x] 运行控制器和权限测试确认绿灯。

## 任务 5：Web 学员关系管理

- [x] 先扩展组织管理页面测试，断言机构管理员可打开学员关系抽屉并提交关系变更。
- [x] 扩展 `classAssignmentApi` 和抽屉，提供转班与转出分段操作、明确当前班级和错误反馈。
- [x] 接入公开能力，功能停用时隐藏入口；运行 Web 目标测试确认绿灯。

## 任务 6：uni-app 机构学员关系页

- [x] 先通过类型契约定义机构令牌下的学生、班级、转班和转出请求，确认页面引用缺失时类型检查失败。
- [x] 新增 `organization-students` 页面和 API；机构工作台仅在能力启用时显示入口。
- [x] 转出使用原生确认弹窗，提交失败保留选择和原因；成功后刷新列表。
- [x] 运行 uni-app 类型检查、H5 构建和微信小程序构建确认通过。

## 任务 7：中文文档、全量回归和复核

- [x] 同步 README、设计文档 00-12、主计划和当前实现核对，明确跨机构不迁移原账号、V41 边界及未完成注销。
- [x] 扫描 V1-V41 无自增主键、无定位地图调用、无未勾选 V41 任务和文档进度冲突。
- [x] 执行后端全量测试、Web 全量测试与构建、uni-app 类型检查及双端构建。
- [x] 复核跨租户权限、历史留存、功能停用直达拦截和 JavaScript 标识精度，修正重要问题后再次回归。
