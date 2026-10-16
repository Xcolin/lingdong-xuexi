# 新增成长小主人：家庭归属兼容方案（等待业务选择）

现有证据：student/application/StudentApplicationService.java createStudent 的 organizationId==null 分支建立账号、积分账户和主监护关系，但未插入家庭组织成员；organizationId!=null 分支要求机构身份，不应拿家庭ID绕过。

建议兼容设计：POST /students 请求新增可选 familyOrganizationId，仅适用于家庭创建分支；原 organizationId 不改，两者不允许同时传。旧Web不传新字段，保持原行为。CreateStudentCommand及请求保留旧构造方式供测试兼容。

家庭ID必须为启用FAMILY，当前账号具备STUDENT_CREATE、实际家庭档案、活动角色，并在该家庭授权/归属范围内；不能仅凭前端ID写入其他家庭。事务内创建账号/档案/积分/主监护，再把新学生用户写入该家庭成员关系；任何冲突全部回滚。沿用现有生成的学生账号和初始登录码，不另建一套身份。

小程序入口按有效权限隐藏；选择当前归属家庭，不允许搜索范围外家庭。成功时仅本次响应展示登录码，不记录或回显到日志。刷新孩子候选及成员列表。首次登录和设备机制沿用既有实现。

待用户确认后再实施。验证必须覆盖旧Web原请求、本人家庭成功、跨家庭拒绝、停用家庭拒绝、数据库回滚、正确角色及监护关系；隔离库实际HTTP通过后部署。当前未修改任何后端代码。
