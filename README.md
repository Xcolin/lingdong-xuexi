# 灵动学习

灵动学习是面向系统管理员、系统审核员、机构管理员、教师、家长和学生的自律成长管理系统。

## 工程结构

- `server/`：Spring Boot 3 模块化单体后端。
- `web/`：后续创建的 Ant Design Pro React 独立 Web 应用。
- `miniapp/`：后续创建的 uni-app 独立小程序应用。
- `docs/`：业务需求、设计和实施计划。

## 设计基线

正式设计文档从 [docs/design/00-设计文档体系与需求追溯-V1.0.md](docs/design/00-设计文档体系与需求追溯-V1.0.md) 开始阅读。该目录包含 FSD、交互说明、HLD、数据库、Flyway、API、权限安全、第三方、统计、测试、部署和当前实现一致性核对。

Web 与小程序共享后端 OpenAPI 契约，不共享页面代码或私密配置。

## 本地后端运行

本项目使用 JDK 17、Maven 3.9+、MySQL 8、Redis 和 Flyway。当前机器的系统 `JAVA_HOME` 指向一个不可启动的 JDK 18；执行 Maven 命令前请在当前 PowerShell 会话中设置：

```powershell
$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'
Set-Location server
mvn test
```

本地运行配置位于 `server/src/main/resources/application-local.yml`，启动时使用 `--spring.profiles.active=local`。该文件包含本地数据库和第三方凭证，已被 Git 忽略，禁止提交、打印或复制到前端工程。

## 数据库迁移

所有数据库结构、基础字典、权限初始化和受控数据修正必须通过 `server/src/main/resources/db/migration/` 下的 Flyway 迁移脚本发布。禁止将手工改库作为常规发布方式。

开发、测试、预生产和生产环境都必须保留迁移版本记录。涉及历史数据回填或修复时，迁移脚本应包含明确说明并保留审计记录。
