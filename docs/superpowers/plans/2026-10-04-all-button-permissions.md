# 全部按钮统一授权实施计划

使用superpowers:subagent-driven-development实施后端并独立审查，主代理负责前端和运行验收；不提交历史工作区改动。

Goal: 全部菜单按钮均有独立权限编码，可通过角色和用户树授权。
Architecture: 强制BUTTON权限同步，保留现有组件actionKey，新增V98迁移补齐节点与权限；current菜单逐按钮过滤。
Tech Stack: SpringBoot/MyBatis/Flyway、React/AntD/Vitest。

- [x] 后端红绿测试：false grantable创建/编辑/批量仍授权，actionKey兼容；V98全按钮权限补齐，current仅返回被授权按钮；保持业务权限。
- [x] 前端红绿测试：编辑历史按钮统一grantable=true，授权树全按钮可勾选，ConfiguredButton未授权隐藏。
- [x] 独立规格和质量审查，相关回归与构建。
- [x] 打包、真实数据库迁移、后端重启、浏览器截图及状态记录。
