# 灵动学习

灵动学习是面向系统管理员、系统审核员、机构管理员、教师、家长和学生的自律成长管理系统。

## 工程结构

- `server/`：Spring Boot 3 模块化单体后端。
- `web/`：Ant Design Pro React 独立 Web 应用。
- `miniapp/`：uni-app 独立小程序应用。
- `docs/`：业务需求、设计和实施计划。

## 设计基线

正式设计文档从 [docs/design/00-设计文档体系与需求追溯-V1.0.md](docs/design/00-设计文档体系与需求追溯-V1.0.md) 开始阅读。该目录包含 FSD、交互说明、HLD、数据库、Flyway、API、权限安全、第三方、统计、测试、部署和当前实现一致性核对。

全项目开发范围、依赖顺序、功能点状态和统一进度口径见 [灵动学习完整开发实施计划](docs/superpowers/plans/2026-08-08-lingdong-learning-master-development.md)。专项计划只负责对应功能的施工步骤，不替代总计划。

Web 与小程序共享后端 OpenAPI 契约，不共享页面代码或私密配置。

当前本地代码与构建基线为 Flyway V1-V61；V36-V48 已完成家长认证、关系与账号安全、机构和学生生命周期、组织节点及班级基础管理，V49-V58 完成授权审计、通用配置管理、导入导出基础和机构学员批量开户，V59 完成教师管理，V60 补齐机构和教师双端任务操作及审核连续性。V61 完成教师异常报备、机构管理员组织范围处理、不可变动作历史和本地消息事件出口。Web 保留组合筛选与复杂管理能力，uni-app 只包含高频单项操作；功能开关、动态 RBAC、组织范围、班级范围和对象范围均由后端实时校验。当前本地验收通过后端 154 个测试套件 568 项、Web 32 个测试文件 124 项、Web 类型检查与生产构建、uni-app 类型检查及 H5/微信小程序构建；真实短信、微信、Redis、MySQL、共享测试、预生产、生产和业务 UAT 尚未执行。按已验收子项计分，完整项目进度为 816/1000（81.6%），下一迁移从 V62 开始。

## 本地后端运行

本项目使用 JDK 17、Maven 3.9+、MySQL 8、Redis 和 Flyway。当前机器的系统 `JAVA_HOME` 指向一个不可启动的 JDK 18；执行 Maven 命令前请在当前 PowerShell 会话中设置：

```powershell
$env:JAVA_HOME='C:\Users\Administrator\.jdks\temurin-17.0.20'
Set-Location server
mvn test
```

本地运行配置位于 `server/src/main/resources/application-local.yml`，启动时使用 `--spring.profiles.active=local`。该文件包含本地数据库和第三方凭证，已被 Git 忽略，禁止提交、打印或复制到前端工程。

## 数据库迁移

所有数据库结构、基础字典、权限初始化和受控数据修正必须通过 `server/src/main/resources/db/migration/` 下的 Flyway 迁移脚本发布。禁止将手工改库作为常规发布方式。

开发、测试、预生产和生产环境都必须保留迁移版本记录。涉及历史数据回填或修复时，迁移脚本应包含明确说明并保留审计记录。
