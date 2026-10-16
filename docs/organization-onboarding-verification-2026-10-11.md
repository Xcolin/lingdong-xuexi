# 组织入驻交付验证 2026-10-11

已部署真实后端 V120，管理端与 H5 开发服务运行在 5173 / 5174。此次仅执行 V119、V120 增量迁移，没有重建数据库或删除现有业务数据。两江新区及此前组织清理结果已通过真实组织树接口复核。

## 已实现

- 家庭自助创建、幂等重试、允许重名；事务内建立成员、PARENT_ADMIN 范围角色、管理员和负责人。
- 学校/培训机构申请、本人查看、撤回、修改重提；平台通过后创建组织及 ORG_ADMIN 范围授权。撤回旧任务终止，通过任务生效。
- 联系手机号使用独立验证码用途及持久验证记录；更换号码后旧证明不再适用。
- 保留账号原用户类型与已有角色，家庭身份档案单独建立。需要首次家庭引导时不自动接受协议，原机构业务不会因此被阻断。
- 负责人转交只选择本组织有效管理员；组织取消管理员、角色移除、停用及家长注销路径保护负责人和最后有效管理员。
- Web 我的组织及申请、系统任务审核；移动端我的组织、入驻表单、申请详情。已有家长邀请复用原链接接受流程，没有新增通用机构邀请码。
- 组织切换由服务端校验关系，范围只缩窄不扩大；匹配当前会话后发送组织上下文，切换清除旧页面缓存。家庭列表按真实学生成员关系过滤。

## 验证证据

- 最新 10 类后端专项共 47 项，失败/错误均为 0；日志 .local-verification/onboarding-release-tests.log。H2 仅使用 target108 避开历史 MySQL 专属迁移，数据库增量迁移另在 MySQL 验证。
- 最新 Web 权限与页面专项 7 项通过；此前页签与上下文组合测试通过。生产构建通过。
- 移动端 type-check、H5、微信小程序构建通过，最后一次家庭引导衔接补丁已重新构建。
- 隔离 MySQL ld_verify_20261010_1190abcd 恢复数据核对一致，V119/V120 迁移通过；所有流程测试数据仅写入该隔离库。手机号证明测试采用隔离虚拟数据，未发真实短信。
- 真实环境 admin 登录/me/菜单与 4 个入驻读取接口 HTTP200，组织树原变更保留，后端健康、Web/H5 HTTP200。
- 最新真实库加密备份 mysql-backup-onboarding-release-20261011：100表1083行；密钥继续使用现有 DPAPI 文件，不写入报告。

## 隔离 HTTP 断言

- family concurrent idempotency
- family owner and admin
- family duplicate name permitted
- foreign organization context rejected
- owned organization context works
- original parent role preserved
- owner removal rejected
- owner account disable rejected
- institution pending without organization
- non owner application rejected
- withdraw terminal
- withdrawn task cannot approve
- approved system task effective
- approved organization created
- duplicate approval rejected
- scoped organization role added
- institution context allowed
- foreign institution context rejected
- owner transfer to existing admin
- old owner cannot transfer again

## 验证边界

微信真机、真实短信送达未执行验收；沿用现有短信通道。历史完整测试套件中存在旧权限目录夹具不匹配，本次没有放宽权限规则来使这些夹具通过，也未宣称完整历史套件通过。无 Git 提交或远端发布。

入口：Web /my-organizations；平台审批在系统任务；移动端现有家长“我的”、家长首页和机构首页中的“我的组织”。本期不含认证、认领、解散或全平台机构搜索。
