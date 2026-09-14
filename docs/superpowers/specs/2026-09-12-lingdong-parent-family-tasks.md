# 家长小程序家庭任务闭环（R04）

2026-09-13 本地闭环验证完成：混合审核员红灯 `server/target/remaining-r04-family-auditor-red.log` 后修复三处服务守卫，专项 `remaining-r04-family-green.log` 通过。全量 `remaining-r04-family-full.log` package 退出 0，183 个测试类、716 项，失败/错误/跳过均 0；最终 JAR 与 target/classes 的本地配置排除检查通过。本轮不新增迁移，未执行远程写入。

小程序类型检查、H5 和微信构建通过，日志 `.local-verification/r04-family-types.log`、`r04-family-h5.log`、`r04-family-weixin.log`。模型脚本 `test-family-task-draft.cjs` 和合成浏览器 `verify-family-tasks.cjs` 通过创建、编辑保留分类标签重复配置、失败保留输入、发布、撤权清空；家长周报与待审浏览器回归通过。浏览器首次失败为 uni-app 输入框不提供原生 placeholder 属性，改为选择实际 input 后通过，未据此修改业务逻辑。截图 `family-tasks-mini.png` 已检查。仍需真实微信环境与真实账号验收；R04 其他功能不因本页完成而自动验收。

复用 V60 已适用两端的任务创建、读取和发布权限。独立 uni-app 家长页只处理 FAMILY，不将家长角色混入教师/机构任务页；保持独立家长令牌、实际 MINIAPP 身份、动态角色权限和任务开关。系统审核员混合业务角色仍必须被服务端拒绝。

范围为本人任务分页、草稿详情、创建、编辑、发布确认及刷新。学生候选取现有 FAMILY 选项，只允许活动主家长孩子作为创建目标，无机构学生不要求班级。副家长选项说明不能创建；角色和关系在写入时由后端重新检查。已发布任务不编辑；发布失败显示真实错误并保留草稿，不自动重试。

新建字段沿用标题、难度、时长、计划日期、备注、学生目标。编辑保留原分类、标签、重复任务配置及全部学生目标；不通过简化移动表单擦除 Web 已设置的内容。来源组织和审核人不由家长指定，服务端固定家庭范围及本人审核人。模板库、昨日复制及奖励不在这一页新增实现。

既有接口：GET/POST `/learning-tasks`、GET/PUT `/learning-tasks/{id}`、POST `/learning-tasks/{id}/publish`、GET `/learning-task-options/students?sourceType=FAMILY`。列表使用 sourceType=FAMILY，所有 ID 保持字符串。页面恢复和操作前重查权限，离页使在途结果失效。

验证先复现混合审核员越界，修复后验证真实服务端合成 MINIAPP 会话的无机构主家长创建、PUT、发布，以及副家长、解绑、权限撤销、功能关闭拒绝。客户端核对独立令牌、隐藏入口、失败保留草稿及编辑保留隐藏字段。合成会话不代表真实短信或微信登录验收。
