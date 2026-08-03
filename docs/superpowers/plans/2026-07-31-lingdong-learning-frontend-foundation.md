# 灵动学习前端工程基础实施计划

> **执行要求**：按任务顺序实施。每一个行为变更先增加失败测试，再做最小实现；完成一个步骤后更新复选框。

**目标：**创建独立的 Web 管理端和 uni-app 小程序工程基础，并让 Web 首批页面可与已实现的认证、IAM 和组织管理 API 联调。

**架构：**`web/` 使用 Vite + React，开发时通过 Vite `/api` 代理访问 Spring Boot。`miniapp/` 是独立的 uni-app Vue 3 工程，只提供应用壳与 API 基础封装。两端不共享构建产物、路由或凭证存储。

**技术栈：**Node.js 24、npm、Vite、React、TypeScript、Ant Design、Ant Design Pro Components、Lucide React、React Router、Vitest、Testing Library、uni-app、Vue 3。

---

## 文件职责

| 文件或目录 | 职责 |
|---|---|
| `web/` | 独立 Web 管理端。 |
| `web/src/api/` | HTTP 客户端、认证会话和已实现 API 的类型化调用。 |
| `web/src/features/` | 登录、工作台、IAM 和组织管理页面。 |
| `web/src/test/` | Web 的 Vitest 测试和测试环境。 |
| `miniapp/` | 独立 uni-app 小程序应用壳。 |
| `.gitignore` | 忽略两端依赖和构建产物。 |
| `docs/design/12-当前实现一致性核对-V1.0.md` | 回填前端工程已创建的真实范围与未实现边界。 |

## 任务 1：初始化独立前端工程与依赖

- [x] **步骤 1：创建 Web 与小程序目录及构建测试的失败基线**

在 `web/` 创建最小 `package.json`，增加 `test` 脚本指向尚不存在的认证测试；在 `miniapp/` 创建最小 `package.json`，增加类型检查脚本。运行 Web 测试，预期因测试文件不存在而失败。

- [x] **步骤 2：安装 Web 和小程序依赖**

Web 安装 React、Vite、Ant Design、Ant Design Pro Components、Lucide React、React Router、Vitest、Testing Library 和 TypeScript；小程序安装 uni-app、Vue 3、Vite 与 Vue TypeScript 插件。所有依赖安装在各自工程目录，不在根目录创建共享 Node 工程。

- [x] **步骤 3：创建 Vite 和 uni-app 基础配置**

为 Web 配置开发端口、`/api` 到 `http://127.0.0.1:8080` 的代理、TypeScript 和 Vitest；为小程序配置 `pages.json`、`manifest.json`、Vue 启动文件和独立主题变量。

- [x] **步骤 4：验证基础构建可用**

运行 `npm run build` 和 `npm test`；运行小程序类型检查或构建。预期两个工程均不访问任何共享环境。

## 任务 2：实现 Web 认证会话与路由基础

- [x] **步骤 1：先写认证客户端失败测试**

在 `web/src/api/auth.test.ts` 覆盖：密码登录保存会话；认证请求收到一次 401 时调用刷新接口并重试；刷新失败时清空会话。测试使用 `fetch` 替身，不发送真实网络请求。

- [x] **步骤 2：运行认证测试并确认失败**

执行 `npm test -- auth.test.ts`。预期失败，因为 API 客户端与认证存储尚不存在。

- [x] **步骤 3：实现认证存储、HTTP 客户端和受保护路由**

实现 `sessionStorage` 会话存储、一次刷新锁、401 重试和登录跳转。创建 `/login`、`/dashboard`、`/iam`、`/organizations` 路由，受保护路由在没有会话时跳转登录页。

- [x] **步骤 4：验证认证测试通过**

重新执行步骤 2 的命令，预期所有认证行为通过。

## 任务 3：实现 Web 首批可操作页面

- [x] **步骤 1：先写组织管理页面失败测试**

在 `web/src/features/organizations/OrganizationPage.test.tsx` 覆盖：加载组织类型与组织树、创建组织类型、创建组织节点以及显示 403 错误。测试仅替代 API 模块，不替代页面状态。

- [x] **步骤 2：运行页面测试并确认失败**

执行 `npm test -- OrganizationPage.test.tsx`。预期失败，因为页面与组件尚不存在。

- [x] **步骤 3：实现登录、工作台、IAM 和组织管理页面**

实现平台账号登录、当前身份、设备会话下线、角色权限目录/创建/授权，以及组织类型/树查询和新增。所有操作显示服务端返回的成功或失败状态，不在前端假设系统管理员权限。

- [x] **步骤 4：验证页面测试和 Web 生产构建通过**

执行 `npm test` 和 `npm run build`。预期无 TypeScript 错误，且打包产物生成至 `web/dist/`。

## 任务 4：创建小程序应用壳并同步文档

- [x] **步骤 1：创建小程序首页和 API 基础封装**

创建 `miniapp/src/pages/index/index.vue`、`miniapp/src/api/http.ts`、页面注册、全局样式和项目配置。首页只体现应用当前状态，不提供微信登录、定位、任务或统计入口。

- [x] **步骤 2：执行小程序构建或类型检查**

执行小程序的 `npm run build:h5` 或项目定义的等价命令。预期构建成功，未配置任何第三方生产凭证。

- [x] **步骤 3：更新中文实现说明并完成最终验证**

更新 `docs/design/12-当前实现一致性核对-V1.0.md`，明确 Web 和小程序工程已创建、Web 首批 API 联调范围及小程序业务边界。执行 `git diff --check`、Web 测试、Web 构建、小程序构建和后端 `mvn test`；仅记录本地结果。

## 实施结果（2026-08-01）

- Web 已创建为独立 React 管理端。`npm test` 覆盖认证会话、刷新重试、组织类型创建、组织节点创建和权限拒绝；`npm run build` 已通过。
- 小程序已创建为独立 uni-app Vue 3 工程。`npm run type-check` 与 `npm run build:mp-weixin` 已通过，微信小程序产物位于 `miniapp/dist/build/mp-weixin/`。
- Web 登录页已在本地浏览器完成桌面和 `390×844` 视口检查，控制台无错误。未向任意共享测试、预生产或生产环境发起请求。
- Vite 生产构建仍会提示组件库公共分包超过 500 KB；当前已采用路由级懒加载，未使用会破坏组件库依赖边界的强制分包策略。后续在首批功能稳定后再结合真实访问频率进行包体分析。
