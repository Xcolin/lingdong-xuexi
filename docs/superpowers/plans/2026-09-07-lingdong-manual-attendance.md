# V62 人工考勤实施计划

目标：完成已确认的人工考勤双端闭环，按功能权限和对象关系隔离数据，使用 V62 迁移及 19 位雪花主键。

架构：独立 attendance 模块，MyBatis XML 完成范围过滤；应用服务负责原子批量写入、校验、版本和不可变历史。Web 与 uni-app 共用 REST 契约，独立页面。

## 1. 设计校正与接口契约

- [x] 用户已确认方案一，按当前目录继续实施。
- [x] 明确历史查询不要求班级启用，写入要求班级及学生关系当前有效；禁止跨未来日期写入。
- [x] 默认教师按活动教师班级关系访问；机构管理员及获授权自定义角色按通用组织范围访问；家长、学生仅本人关系只读；系统审核员禁止进入。
- [x] 唯一性是班级、学生、日期组合，并非学生跨机构每天唯一。

统一契约：GET /api/v1/attendance-records 参数 classOrganizationId、studentId、keyword、status、dateFrom、dateTo、page、pageSize，日期可同时省略（最近30天），响应 {items,page,pageSize,total}。
记录字段 {id,studentId,studentName,classOrganizationId,className,attendanceDate,status,checkinTime,checkoutTime,source,recordedBy,recorderName,versionNo,createdAt,updatedAt}，所有 ID 字符串，versionNo 数字，时间 HH:mm:ss 可空，日期 yyyy-MM-dd。
GET /class-options?operational=true 返回 [{classOrganizationId,className}]；默认包含可查询历史的班级；operational=true 限有记录权限且有效班级。
GET /roster?classOrganizationId=&attendanceDate= 返回 [{studentId,studentName,record:记录或null}]；POST /batch 请求 {classOrganizationId,attendanceDate,items:[{studentId,status,checkinTime,checkoutTime,versionNo}]}，首次 versionNo=null，响应记录数组。
GET /{id} 返回 {record,actions:[{id,actionType,operatorUserId,operatorName,beforeStatus,afterStatus,beforeCheckinTime,afterCheckinTime,beforeCheckoutTime,afterCheckoutTime,createdAt}]}。

## 2. 数据库与后端

- [ ] 新建 domain/AttendanceStatus.java、AttendanceRecord.java 与 application/AttendanceEntry.java，验证未来日期、重复学生、状态时间组合、秒精度和版本冲突。
- [ ] 新建 V62__add_manual_attendance.sql：两张事实/动作表，唯一约束、外键、检查约束、开关和两项权限及六条角色授权。
- [ ] 新建 infrastructure/persistence/AttendanceMapper.java 与 mapper/attendance/AttendanceMapper.xml：SQL 层范围与分页、名单和不可变历史。
- [ ] 新建 application/AttendanceService.java、AttendanceAccessService.java；复用权限服务、组织数据范围、雪花 ID、Clock。班级锁序列化同班写入；重复同内容不新增历史；异内容必须匹配版本；全批次事务回滚。
- [ ] 新建 web/AttendanceController.java 与契约响应；公开能力增加 attendanceManagementEnabled；组织删除引用纳入考勤。
- [ ] 测试 AttendanceRulesTest、AttendancePersistenceTest、AttendanceControllerTest：规则、真实 SQL 四类身份/自定义角色、越权、批量回滚、版本、幂等、开关与动态权限。执行 mvn -q -DforkCount=0 -Dtest=AttendanceRulesTest,AttendancePersistenceTest,AttendanceControllerTest test，预期全部通过。

## 3. Web

- [ ] 新建 web/src/features/attendance/api.ts、AttendancePage.tsx 和测试；修改 App.tsx、capability.ts、DashboardPage.tsx（按现有入口模式）。列表、筛选、点名和详情完整实现；操作依据权限，直达重查开关；雪花 ID 保持字符串。
- [ ] npm run test -- --run、npm run type-check、npm run build，通过后记录证据。

## 4. uni-app

- [ ] 新建 miniapp/src/api/attendance.ts、pages/attendance/attendance.vue；注册独立路由，能力字段与相应角色首页接入；列表、点名、详情与只读身份裁剪。
- [ ] npm run type-check、npm run build:h5、npm run build:mp-weixin；检查源码和构建无定位 API。

## 5. 集成收口

- [ ] 全量后端回归与 Flyway/组织引用计数；审阅设计符合性及权限/并发代码。
- [ ] 更新受影响的 BRD、设计、README 和总计划；未验证的外部环境、地理考勤与统计不得计为完成。
- [ ] 浏览器检查桌面与移动布局和操作；git diff --check；记录结果与剩余限制。
