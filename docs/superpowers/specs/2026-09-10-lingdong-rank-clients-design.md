# 匿名排行双端与撤回闭环

沿用已批准的排名口径、V68 偏好和 1/1/3 汇总，不重建业务。Web 与 uni-app 独立实现页面，共享后端契约，所有业务 ID 保持字符串。

## 客户端授权

V69 新增独立 `MINIAPP_ANONYMOUS_CLASS_RANK_READ` 操作权限，仅适用 MINIAPP，授予既有家长角色。V68 的 WEB 权限原样保留。服务端从认证会话取得实际客户端，分别检查对应权限；禁止通过请求参数伪装 WEB。身份检查仍要求有效账号、当前家长且非审核员，读取和开启每次检查功能及活动亲子/班级关系。

小程序入口读取能力摘要和 `/auth/me`，页面 onShow 再核对，失败关闭入口并清空数据；仅使用独立家长会话，不用学生或机构令牌。

## 查看与撤回

新增学生/班级选项接口限定当前家长关系；班级只返回启用且当前在班的记录。开启仍由原 PUT 接口携带期望版本，名次只输出 rank、points。

新增本人已开启偏好列表，只返回学生/班级字符串标识及版本，不连接姓名等历史资料，不返回排名。此查询及既有关闭操作不依赖已撤销的关系、查看权限或功能开关，仍要求有效本人家长身份。两端提供单独的“排行查看授权”撤回入口，功能关闭时隐藏排行入口但保留必要退出操作。撤回入口不允许开启或读取排名。

## 新增 HTTP 契约

- GET `/api/v1/anonymous-ranks/students`：当前家长关联孩子的 `studentId` 字符串与 `studentName`，不枚举无关学生。
- GET `/api/v1/anonymous-ranks/students/{studentId}/classes`：当前有效班级的 `classId` 字符串与 `className`。
- GET `/api/v1/anonymous-ranks/preferences`：本人已开启查看授权的 `studentId`、`classId` 字符串及 `version`，不带历史身份资料。无授权返回空数组。
- 原 GET 排行、GET/PUT 偏好路径保持不变。Web 使用 WEB 权限，小程序使用 MINIAPP 专用权限。`anonymousClassRankEnabled` 在两端公开能力中受同一全局开关控制，能力摘要不替代动态授权。
- Web 路由 `/anonymous-ranks` 与 `/rank-preferences`；小程序页面 `pages/anonymous-ranks/anonymous-ranks`，`withdraw=true` 为只撤回模式。排行入口隐藏不阻断必要退出入口。

## 验收证据与限制

偏好写入先校验会话形状，再锁定本人用户行，之后核验数据库身份及开启权限。偏好采用 `FOR UPDATE` 当前读，并禁用该查询缓存，避免 MySQL 可重复读下撤回请求读取并发开启前的旧快照并错误报告已关闭。旧版本遇到新开启状态返回版本冲突，用户刷新后可再次撤回。模拟回归先复现旧实现失败，再验证修复；代码复审通过，真实 MySQL 并发演练仍属于 R10。

验证两端权限互不替代、当前身份和组织关系逐次校验、本人偏好隔离、关系撤销和功能关闭后可撤回、字符串 ID、版本冲突、切换范围旧响应隔离。前端验证默认关闭、空态、并列排名、失败恢复、关闭清空以及桌面/移动视口；本地 H2 与合成浏览器不代表远程 MySQL 或微信真机验收。

2026-09-10：V69 后全量 package 174 套件 689 项通过，日志 `server/target/remaining-r03-final-full.log`。Web 全量 41 文件 171 项通过，另补空榜单及切换孩子旧响应两项专项，当前排行专项 6 项通过。Web typecheck/build、小程序 type-check/H5/mp-weixin 构建通过。双端合成浏览器脚本 `web/scripts/verify-anonymous-ranks.cjs`、`miniapp/scripts/verify-anonymous-ranks.cjs` 验证当前产物与失败关闭/撤回；截图在 `.local-verification/anonymous-rank-*.png`。未执行远程迁移、未开启真实环境功能，微信真机及真实关系数据 UAT 留在 R10。
