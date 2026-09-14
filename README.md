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

当前本地代码与构建基线为 Flyway V1-V62；V36-V48 完成认证、关系、账号安全及组织班级基础，V49-V58 完成授权审计、通用配置、导入导出基础和机构学员开户，V59-V61 完成教师管理、双端任务及异常报备。V62 完成人工点名、请假结果、原子更正、历史动作和双端考勤台账，独立于地理开关，不包含请假审批或定位能力。Web 保留复杂管理，uni-app 提供高频操作（含单班最多 100 人一次点名），两端独立；权限及数据范围由后端实时校验。

V62 验收基线通过后端 **157 个套件 580 项**、Web **34 个文件 140 项**、Web 类型检查与构建、uni-app 类型检查及 H5/微信构建；双端合成数据浏览器验收和定位扫描通过。按既定加权路线图为 **836/1000（83.6%）**，当前专项为 V63 成长复盘扩展。该分值不是上线准备度；本轮没有连接远程 MySQL、Redis、微信或共享测试/预生产/生产，浏览器模拟接口不等同实网联调，业务 UAT 与微信真机验收尚未完成。

## 本地后端运行

V63 开发中增量：复盘 PDF 创建、执行、附件、历史下载及 Web 操作已接入。周报订阅偏好、周一内部排程、单周去重、待发取消及过期处理，以及 Web 偏好开关、版本保护、失败恢复和跨孩子隔离已实现；微信真实授权/投递、小程序适用交互和匿名排行仍待完成。后端最近全量基线 **167 套件 659 项**通过，随后 HTTP 及页面能力增量按专项验证，不能混算为新全量结果。V1-V67 本地迁移通过，业务主键表 93 个，后续迁移从 V68 开始。内部队列不等同送达，调度及订阅业务开关默认关闭；V63 不提前计分。合成 PDF 验证文件位于 `server/target/pdf-verification/`，不含真实学生数据。

本项目要求 JDK 17、Maven 4+、MySQL 8、Redis 和 Flyway。当前本地 Maven 3.9.14 仅用于兼容性验证，不能视为满足 Maven 4 要求。执行 Maven 命令前请在当前 PowerShell 会话中设置：

```powershell
$env:JAVA_HOME='C:\Users\Administrator\.jdks\temurin-17.0.20'
Set-Location server
mvn test
```

本地运行配置保留在 `server/src/main/resources/application-local.yml`，但不再自动从 classpath 导入，也不进入构建资源或 JAR。在 server 目录启动时显式传入 `--spring.profiles.active=local --spring.config.additional-location=file:./src/main/resources/application-local.yml`。该文件包含本地凭证，禁止提交、打印或复制到前端工程。发布配置及检查见 [外置配置与发布检查](docs/deployment/外置配置与发布检查.md)。

## 数据库迁移

所有数据库结构、基础字典、权限初始化和受控数据修正必须通过 `server/src/main/resources/db/migration/` 下的 Flyway 迁移脚本发布。禁止将手工改库作为常规发布方式。

开发、测试、预生产和生产环境都必须保留迁移版本记录。涉及历史数据回填或修复时，迁移脚本应包含明确说明并保留审计记录。
