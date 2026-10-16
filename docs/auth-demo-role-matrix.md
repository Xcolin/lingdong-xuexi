# 规范联调种子角色矩阵

来源为授权模型和独立生成脚本 `tools/bootstrap-data/generate-canonical-role-grants.py`。执行脚本只生成 V3 与 JSON 矩阵，不连接数据库。根代理更新模型后应重新执行该脚本。

| 角色 | 身份与范围 | 授权意图 |
|---|---|---|
| SYS_ADMIN | PLATFORM / ALL | 系统配置、权限管理及教育管理；排除家长个人动作和个人模板 |
| SYS_AUDITOR | PLATFORM / ALL | 兼容只读和功能、缓存、接口、导出、组织变更审批；不授业务写和任务批阅 |
| ORG_ADMIN | ORGANIZATION / SCHOOL | 本机构组织成员、用户、班级、教师、学生、任务、考勤及导入导出 |
| TEACHER | ORGANIZATION / CLASS | 本班任务、考勤、异常处理、学生读取和有效对象登录二维码；无全校范围 |
| PARENT | FAMILY / SELF | 真实子女关系、成长、奖励、家长关系、个人任务及家庭模板 |
| PARENT_ADMIN | FAMILY / FAMILY | 家长业务及本家庭成员、管理员与用户管理；无跨家庭写权限 |
| STUDENT | STUDENT / SELF | Web不适用，无Web默认授权；学生本人业务保留原小程序独立授权 |
| ORG_MEMBER / PLATFORM_USER | 对应身份 / SELF | 仅工作台，无组织写权限 |

V3先清理旧 WEB/BOTH 角色效果再按矩阵生成。纯 MINIAPP 权限及其角色授权保留；BOTH 是同一授权行，无法分别存储两个客户端效果，属于共享项重建。候选数量见 JSON，实际写入还检查角色/权限启用、菜单及祖先启用、功能条件。动作、所属PAGE及递归requiredReads/impliedReads的条件共同参与运行检查。普通功能条件用 AND，各依赖自己的周期报告上下文条件遵循任一可用，不同依赖的条件组仍需同时满足。不会自动启用任何功能，未开启功能不会得到授权；以后启用功能须显式新增授权，不能假定联调种子自动补授。

V4仅追加基础联调关系，不修改 V1/V2。新增 student01/student02 与原两学生档案绑定，并保留有效学校、班级、家庭和监护关系；不注入伪造学生登录凭据，登录码需通过现有学生凭据管理接口真实签发。新增 trainingadmin 为培训机构 SCHOOL 范围，teacher03 为其单班 CLASS 范围；两者使用本地已有随机 hash 占位符，凭据别名分别对应 orgadmin 和 teacher02 的本地凭据，不在文档输出密码。新增无账号授权的对照学校/班级用于跨范围拒绝；原学校、培训机构、对照学校互不增加授权。原 teacher01/teacher02 增加自身班级成员关系以匹配实际 CLASS 范围。

这里没有执行 SQL 或 Maven；迁移语法、实际可用授权数量、登录及跨范围操作由根代理隔离重置演练验证。

学生Web资格只读证据：AuthenticationApplicationService.java:371-379 的密码Web仅允许PLATFORM/ORGANIZATION/真实启用家长；:342-360 的Web会话有效性同样不含STUDENT。organization-password（:179-191）实际建立MINIAPP机构会话，学生登录码/微信服务也只建立MINIAPP会话。建议Web角色目录对STUDENT标明“仅小程序，不适用Web授权”，本批不修改核心授权模型或认证入口。
