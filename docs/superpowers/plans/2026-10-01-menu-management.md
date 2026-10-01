# 菜单管理实施计划

> 执行方式：superpowers:subagent-driven-development。按后端、前端管理页面、应用接入划分文件范围，根任务审查和集成。

**Goal:** 用数据库配置全部管理端菜单与按钮，并调整层级、状态、名称和顺序。
**Architecture:** 独立菜单目录复用现有权限；当前用户接口负责展示过滤；固定业务路由与动作绑定，公共按钮读取配置。
**Tech Stack:** Java 17、Spring Boot、MyBatis、Flyway、React、Ant Design、Vitest。

- [x] 后端：新增 menu/domain/application/persistence/web、mapper/menu、V87；先失败测试，再实现 CRUD、树校验、版本、同级排序与当前用户过滤；运行相关 Maven 测试。
- [x] 管理页面：新增 api/menus.ts、features/menus/MenuManagementPage.tsx/test；树表筛选、抽屉新增编辑、启停与上移下移、绑定权限和已有路由/动作选项；先失败测试，再实现并运行。
- [x] 接入：维护 route/action 目录，V88 初始化；App 加载当前菜单与树导航、页签名称映射；ConfiguredButton 控制现有按钮显示/名称/顺序，默认无provider兼容独立组件测试；补配置关闭及排序测试。
- [x] 审查：源码审查权限、循环、并发与按钮绑定边界，修复实测问题；运行 Web 全量/构建和后端相关检查。
- [x] 真实环境：迁移真实数据库、重启后端；浏览器验证菜单新增、排序、按钮隐藏及恢复，保存截图。

保留现有工作区改动，不提交历史文件，不输出数据库凭据。

验收证据：Web生产构建成功；后端10项测试通过、H2全部89迁移通过；Web全量269项中268通过，活跃度空态断言竞态改为waitFor重试后该文件4项全通过，无未处理错误。真实MySQL迁移至V89，health数据库与Redis为true；浏览器验证名称、顺序、按钮停用与恢复，截图保存在.local-verification/menu-management-final.png。目录新增由页面测试及后端集成测试覆盖；浏览器未额外创建临时目录。
