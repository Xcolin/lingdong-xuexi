# 前端规范授权最终复验（2026-10-10）

仅检查并修改已授权前端业务文件。未操作真实数据库、服务或Maven；浏览器联调等待主任务启动最新JAR后安排。

## 当前验证

- 当前生成的规范目录、菜单导航、App准入、ConfiguredButton/Modal、角色与用户授权树：9文件57项通过。
- 任务附件、附件管理、原生Upload所属模板/导入流程、用户状态控件及配置按钮：7文件42项通过。
- 组织和菜单管理、只读拖拽、自定义审核角色及组织类型限制：2文件43项通过。
- TypeScript检查通过。上述测试分组有重叠，不能相加为总用例数。

## 附件与原生控件结论

Web没有TaskAttachment上传/下载命令；任务审核图片自动调用GET /attachments/{id}/content，真实权限为ATTACHMENT_READ。主任务将TASK_ASSIGNMENT_REVIEW的最小可选读取声明为ATTACHMENT_READ，明确DENY仍不派生该读取，且不阻断文本审核。

TaskAttachmentPreview现按有效ATTACHMENT_READ控制请求和显示。读取拒绝不发送403请求；撤权后隐藏图片并释放预览URL；晚到响应不恢复图片。无配置上下文仅为独立组件测试兼容。

模板和导入页面的原生Upload.beforeUpload返回false，只选择本地文件；实际提交分别使用IMPORT_EXPORT_TEMPLATE_MANAGE和IMPORT_JOB_CREATE，不需额外ATTACHMENT_UPLOAD。表单Switch、Checkbox、字段行删除等均只改本地表单；真正业务提交仍受规范动作控制。账号安全和匿名排行偏好属于后端认证/所有者规则控制的自助API，不作为角色授权树动作。

发现并修复组织Tree拖拽只看展示状态的缺口：draggable与handleTreeDrop都要求有效ORG_NODE_UPDATE及动作配置启用。菜单Tree管理能力同步检查MENU_MANAGE的动作配置。组织写按钮额外按传入有效动作隐藏，独立组件不再因无Provider显示未授权写入口。

组织变更记录此前虽过滤出条目，却仍被非SYS_ADMIN的强制tree activeKey/隐藏tab bar挡住；现由ORG_NODE_CHANGE_SUBMIT/REVIEW决定可见性。组织类型页签继续遵守用户明确的SYS_ADMIN限制。

## 审批边界

曾尝试新增通用ConfiguredAction.test.tsx，自动审批以本批禁止root/core写入为由拒绝；命令未执行，文件未创建。随后使用既有用户/角色状态控件回归验证ConfiguredAction，新增测试仅写在已授权业务模块中。
