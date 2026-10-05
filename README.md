# 灵动伴随

灵动伴随是面向系统管理员、系统审核员、机构管理员、教师、家长和学生的自律成长管理系统。

## 工程结构

- `lingdong-bansui-server/`：Spring Boot 3 模块化单体后端。
- `lingdong-bansui-web/`：Ant Design Pro React 独立 Web 应用。
- `lingdong-bansui-miniapp/`：uni-app 独立小程序应用。
- `docs/`：业务需求、设计和实施计划。

## 设计基线

正式设计文档从 [docs/design/00-设计文档体系与需求追溯-V1.0.md](docs/design/00-设计文档体系与需求追溯-V1.0.md) 开始阅读。该目录包含 FSD、交互说明、HLD、数据库、Flyway、API、权限安全、第三方、统计、测试、部署和当前实现一致性核对。

全项目开发范围、依赖顺序、功能点状态和统一进度口径见 [灵动伴随完整开发实施计划](docs/superpowers/plans/2026-08-08-lingdong-learning-master-development.md)。专项计划只负责对应功能的施工步骤，不替代总计划。

Web 与小程序共享后端 OpenAPI 契约，不共享页面代码或私密配置。

当前本地代码与构建基线为 Flyway V1-V94；V36-V48 完成认证、关系、账号安全及组织班级基础，V49-V58 完成授权审计、通用配置、导入导出基础和机构学员开户，V59-V61 完成教师管理、双端任务及异常报备。V62 完成人工点名、请假结果、原子更正、历史动作和双端考勤台账，独立于地理开关，不包含请假审批或定位能力。V63-V67 完成导出载荷、成长复盘 PDF、周报订阅与投递队列；V68-V72 补齐匿名排行偏好与小程序家长端权限；V73-V75 完成功能开关管理与接口变更执行审计；V76-V86 依次补齐字典、模板、接口服务、缓存操作、系统任务、奖励兑换、异常报备、附件台账、学生任务报表、机构任务统计与考勤台账导出；V87-V89 建立 Web 菜单目录与按钮目录（编码即权限编码）；V90-V94 完成管理端权限与组织体系升级（组织树直接生效拖拽与行政区划、角色菜单树勾选式批量授权与批量授予用户、菜单按钮批量维护、纯 UI 按钮目录）。Web 保留复杂管理，uni-app 提供高频操作（含单班最多 100 人一次点名），两端独立；权限及数据范围由后端实时校验。

V94 验收基线通过后端 **209 个套件 872 项**（失败/错误/跳过均为 0，H2 迁移至 V94）、Web **54 个文件 290 项**、Web 类型检查与生产构建；uni-app 类型检查与 H5/微信构建沿用既有专项验证记录。本轮没有连接远程 MySQL、Redis、微信或共享测试/预生产/生产，浏览器模拟接口不等同实网联调，业务 UAT 与微信真机验收尚未完成；剩余收口计划见 OpenSpec 变更 `finish-remaining-delivery`，不采用旧加权分值描述进度。

## 本地后端运行

V94 收口状态：菜单管理页与角色权限页支持按钮批量维护、菜单树勾选式授权与行内快捷授权，菜单管理页另提供「菜单树（拖拽排序）」页签（拖拽调整同级顺序与层级挂载，与表格页签的上移/下移、移动抽屉并存），组织管理页支持直接生效拖拽与行政区划，编码即权限编码（246 处 `@RequirePermission` 运行时判定基线零改动）。微信真实授权/投递、短信真实发送与定位能力按既定决策暂缓，待项目开发完毕后补齐。后端最近全量基线 **209 套件 872 项**通过（日志见 `lingdong-bansui-server/target/` 与 `.local-verification/backend-full-test-final.log`）。V1-V94 本地迁移通过，业务主键表 95 个，后续迁移从 V95 开始。内部队列不等同送达，调度及订阅业务开关默认关闭。合成 PDF 验证文件位于 `lingdong-bansui-server/target/pdf-verification/`，不含真实学生数据。

本项目要求 JDK 17、Maven 4+、MySQL 8、Redis 和 Flyway。当前本地 Maven 3.9.14 仅用于兼容性验证，不能视为满足 Maven 4 要求。执行 Maven 命令前请在当前 PowerShell 会话中设置：

```powershell
$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'
Set-Location lingdong-bansui-server
mvn test
```

本地运行配置保留在 `lingdong-bansui-server/src/main/resources/application-local.yml`，但不再自动从 classpath 导入，也不进入构建资源或 JAR。在 server 目录启动时显式传入 `--spring.profiles.active=local --spring.config.additional-location=file:./src/main/resources/application-local.yml`。该文件包含本地凭证，禁止提交、打印或复制到前端工程。发布配置及检查见 [外置配置与发布检查](docs/deployment/外置配置与发布检查.md)。

## 数据库迁移

所有数据库结构、基础字典、权限初始化和受控数据修正必须通过 `lingdong-bansui-server/src/main/resources/db/migration/` 下的 Flyway 迁移脚本发布。禁止将手工改库作为常规发布方式。

开发、测试、预生产和生产环境都必须保留迁移版本记录。涉及历史数据回填或修复时，迁移脚本应包含明确说明并保留审计记录。
