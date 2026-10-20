# 提案：小程序原型 1:1 施工差距修复（78 视图审计结论落地）

## Why

78 视图小程序原型 1:1 对照审计（docs/prototypes/mobile-full/all-screen-catalog.json，76 屏原生截图 + 2 屏定性）已完成并逐屏回写 gaps 结论。审计定性出 4 个实现缺陷、1 个授权模型缺口（家长关系域小程序端整体 403 不可用）以及分布在 02~07 视图域的"原型有/实现无"施工差距。本 change 将审计结论转化为可施工、可验收的修复任务，使小程序端向原型 1:1 对齐。

## What Changes

- **缺陷修复（P0）**：
  - organization-overview 页 OrganizationOwnerTransfer 组件晚挂载错过 onShow，"负责人管理"区块不渲染（04-06）——组件补 mounted 兜底 load。
  - unified-login 动态 `import('@/api/http')` 在编译产物中 ApiError=undefined，`e instanceof ApiError` 抛 TypeError，教师改密后二次登录卡死（03-03/01 组）——改静态导入。
  - 班级维护列表右侧操作按钮溢出屏幕边界（03-06）。
  - 教师选择标题重复渲染两行（03-10）。
- **授权模型缺口修复（P0）**：
  - authorization-model.json 中 PARENT_RELATIONSHIP_READ / SECONDARY_PARENT_INVITE_CREATE 条目 client 仅声明 WEB，小程序端（MINIAPP）判定不可用（07-01/03/04）。
  - 内置 PARENT / PARENT_ADMIN 角色对上述权限的 sys_role_permission 关联数为 0（连带 07-04 二维码弹层不可达）。
- **视图域施工差距（原型有/实现无）**：
  - 家长任务域（02 组 + LAYOUT-02）：状态 Tab 与筛选组、任务描述/类型六选/日期区间/重复频率三选、孩子卡与可见范围四选、任务详情独立视图（打点日历+打卡记录流）、审核列表状态 Tab 与缩略图、评语字段。
  - 学生任务执行域（05 组 + LAYOUT-06）：四快捷入口、任务描述段+目标要求 checklist+参考图、独立认领确认页、执行时间线、情绪暂停引导页、搁置原因收集、完成情况选择、顺延功能、放弃类型（含申请免执行）与原因输入。
  - 组织教学域（03 组）：工作台统计汇总与课程分类/数据概览入口、培训工作台专属模块、树形层级展开、节点表单学段学科字段、班级卡人数/班主任字段。
  - 组织运营域（04 组）：学员列表头像/学号/性别年龄、邀请码海报与有效期设置、管理员专属列表与角色徽标、审核三 Tab 计数。
  - 积分奖励复盘域（06 组 + LAYOUT-03）：奖励商城网格/分类/库存、兑换确认字段（对象/收货方式/预计发放时间/温馨提示）、兑换时间线可视化、奖励表单分类/库存/图片/上架 toggle、每日复盘直接可填、周报详情内容、家长成长页成长奖励区块。
  - 关系与账号设置域（07 组）：孩子卡性别/年龄/陪伴天数、注销表单（原因 radio/说明/条款 checkbox）、密码强度条与可见性 toggle 与安全提示。

## Capabilities

### New Capabilities

- `miniapp-family-task-management`: 家长端任务发布向导（描述/类型/日期区间/重复频率/可见范围）、任务列表状态视图、任务详情视图、打卡审核流的规格。
- `miniapp-student-task-execution`: 学生端任务执行全流程（入口、详情 checklist、认领确认、执行时间线、暂停/搁置引导、打卡、顺延、放弃）的规格。
- `miniapp-organization-teaching`: 教师工作台、组织层级、节点/班级/教师管理的规格。
- `miniapp-organization-operations`: 学员列表、转班转学、成员与管理员管理、邀请码、审核、考勤、报备的规格。
- `miniapp-growth-reward-review`: 积分、奖励商城、兑换、复盘与周报的规格。
- `miniapp-relationship-account-settings`: 家长关系（含小程序端授权可用性）、孩子管理、账号设置与生命周期的规格。

### Modified Capabilities

（无——既有 menu-button-management / organization-tree-administration / role-authorization 三个 capability 均面向 Web 管理端授权操作，与本 change 的需求层面不重叠。）

## Impact

- 小程序端 `lingdong-bansui-miniapp/src/pages/**`（02~07 组涉及页面）与公共组件。
- 后端 `lingdong-bansui-server`：
  - `src/main/resources/authorization-model.json`（action 条目 client 声明）。
  - 新增 Flyway 迁移（补内置 PARENT/PARENT_ADMIN 角色的 sys_role_permission 关联，仅新增不删除）。
  - 任务 DTO（描述/类型/日期区间/重复/可见范围）、工作台统计汇总接口、组织统计接口按需扩展。
- 数据库：Flyway 迁移新增角色权限关联行；任务/奖励相关表若缺字段（类型/可见范围/库存/分类）需迁移补列。
- 验收基线：docs/prototypes/mobile-full/all-screen-catalog.json 逐屏 gaps + reference 原型图；施工后逐屏重拍对照更新 implementationStatus。
