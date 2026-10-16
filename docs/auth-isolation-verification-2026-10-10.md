# 隔离授权验证 2026-10-10

目标：8081 / ld_verify_20261010_8b031c2a。主迁移119项至V118、联调V1–V5完成，initialization.completed=true。隔离服务长期exec session：98215。

| 账号 | 登录/认证/菜单 | 有效权限数 | 菜单页面集合与有效页面集合相等 |
|---|---|---:|---|
| admin | 200/200/200 | 105 | True |
| auditor | 200/200/200 | 33 | True |
| orgadmin | 200/200/200 | 65 | True |
| teacher01 | 200/200/200 | 18 | True |
| parent01 | 200/200/200 | 40 | True |
| parent02 | 200/200/200 | 30 | True |
| trainingadmin | 200/200/200 | 65 | True |
| teacher03 | 200/200/200 | 18 | True |

授权的代表读取接口 /organizations、/students、/classes、/feature-management/toggles 与各账号实际权限一致：有授权返回200，未授权返回403。parent01本人家庭成员读取200，跨家庭成员读取403；原学校与培训机构跨学校成员读取403，跨学校班级筛选200且返回0条。审核员提交功能变更403；系统管理员创建家庭奖励 403（验证真实学生奖励路径）。

未修改模型、生产代码、功能开关、真实库，也未执行Maven或repair。仅隔离登录创建验证会话，业务写请求均为空请求验证无授权拒绝。初次奖励负向检查误用不存在的集合根路径产生500，已纠正真实创建路径后重测；最终断言以修正结果为准。

脱敏逐项证据：.local-verification/auth-isolation-results.json。报告不包含密码或令牌。
