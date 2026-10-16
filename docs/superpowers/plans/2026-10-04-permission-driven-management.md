# 按权限编码访问管理端
用户已批准：自定义角色授权与内置角色授权具有相同菜单、页面、接口效力。移除Web管理访问中SYS_ADMIN/SYS_AUDITOR/ORG_ADMIN/TEACHER固定身份门槛，保留有效账号、WEB客户端、功能开关、显式拒绝、组织数据范围及业务关系和审核不能自审。
实施拆分：系统/IAM后端权限检查；教育/导出后端访问与数据范围；前端菜单页面按钮；独立复核和针对性回归；真实数据库现有ALL_ROLE_TEST/alltest验证，保留数据不改角色归属或密码。

验收结果：系统/IAM后端解除固定角色门槛改为权限编码判定（78项系统测试通过）；教育/导入导出后端按权限与真实组织范围授权，修正导入作业列表范围、教师班级元数据、混合审核加只读权限导出三处不一致；二维码Web签发精确权限化；前端菜单/页面/按钮按权限显示，自定义角色可直接访问/student-login等页面（PermissionRoutes等46项专项、87项相关回归与生产构建通过）。测试夹具（组织编码、注销候选机构状态）修复后业务复测4/4通过。后端打包重启（start-real-env.ps1，pwsh）后health UP、db=true、redis=true；verify-permission-runtime.ps1确认admin系统管理接口全200、无TEACHER_READ/LEARNING_TASK_READ_MANAGED的接口403、5173/5174均200。Codex额度中断后由ZCode收尾浏览器复核：admin登录5173，用户管理、角色与权限、菜单管理、组织管理、功能开关、系统任务、学生登录页正常加载，教育管理组仅显示学生登录，未授权菜单隐藏。截图.local-verification/permission-driven-web-final.png。未提交工作区，未提交真实授权变更。
