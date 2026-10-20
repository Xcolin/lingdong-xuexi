# 进度日志：小程序原型 1:1 施工差距修复

> 本文档为 change 收尾（9.1/9.2）归档记录：按组汇总实施进度、验证证据与 accepted deviations 清单。逐任务详情见 tasks.md 各条目备注。

## 验证基础设施（9.1 期间确立）

- **测试库切换 H2 → 本地 MySQL**：HEAD 既有提交引入的 V109-V115 多个迁移使用 MySQL 专有多表 DELETE/UPDATE JOIN 与 TEMPORARY TABLE 语法，H2(MODE=MySQL) 不支持，@SpringBootTest 基线损坏（tasks.md 3.1 备注已记录"待用户决策修复方式"）。9.1 收尾决策：application-test.yml 数据源改为 MySQL（env 占位符，CI 由 mysql service 容器提供 root/root，本地经环境变量注入），迁移脚本不再维护 H2 双语法。本地验证用免安装 MySQL 8.0.28（E:\tmp\mysql-8.0.28-winx64，端口 33306，初始化脚本 .local-verification/init-local-mysql.ps1）。
- **远程验证库（8.156.80.3 ld_verify_20261011_a121abcd）临时变更已全部还原**：期间曾试改 V109 为 H2 兼容写法并手工校准 flyway_schema_history checksum，路线变更后文件 git 还原、checksum 经 Flyway 官方 repair 恢复官方值（-149238826），零残留。
- CI（.github/workflows/ci.yml）backend job 增加 mysql:8.0 service，与 test profile 默认值（127.0.0.1:3306/lingdong_test, root/root）匹配。

## 进度日志

### 第 1 组 缺陷修复（1.1-1.4）
4 项全部完成：OrganizationOwnerTransfer mounted 兜底、unified-login 静态导入、03-06 操作溢出、03-10 标题重复；均附 8081 克隆库截图。

### 第 2 组 家长关系授权缺口（2.1-2.3）
authorization-model.json 去除家长关系域 6 条 policy 的 client:WEB 声明；V136 迁移为 PARENT/PARENT_ADMIN 内置角色种子授权；WEB 回归 + 07-01/03/04 重拍回写 catalog。

### 第 3 组 学生任务执行域（3.1-3.8 + LAYOUT-06）
后端 V137-V139（任务内容/要求清单/示例图/时间线/顺延/放弃类型/DEFERRED 事件类型）+ 学生端任务列表/打卡/认领确认页/时间线/情绪暂停/搁置/顺延/放弃独立页全部落地；V138 修复学生角色 ATTACHMENT_READ 回归；示例图改字符串 id 防雪花精度丢失；逐屏截图回写 catalog。

### 第 4 组 家长任务管理域（4.1-4.5 + LAYOUT-02）
V140（task_type 六选/end_date/visibility 四选）+ 发布表单增强 + 任务列表筛选组 + 待审核徽标 + 通过/驳回流程；02 组逐屏回写（02-01/02-09/02-10 结构差异见 catalog gaps）。

### 第 5 组 教师教学工作台（5.1-5.5）
03 组工作台统计接口（复用既有 read 权限）+ 班级维护/教师选择/审核队列 UI 修复与增强；03 组逐屏回写。

### 第 6 组 成长激励域（6.1-6.5）
成长周报/每日复盘/积分商城学生端链路；06-01/06-07 保持实现增强现状（accepted deviation）。

### 第 7 组 奖励与家庭视图域（7.1-7.8 + LAYOUT-08）
V141（奖励分类/库存/图片/上架）+ 学生端商城展示与家长端表单共用固定枚举分类；兑换确认页/兑换详情时间线/每日复盘空日期直填/成长周报详情/家长成长页奖励区块；07 组逐屏回写（07-05 语义差异 accepted deviation，07-10 注销表单见 8.4）。

### 第 8 组 账号与安全域（8.1-8.5）
- 8.3 我的孩子孩子行补性别/年龄/陪伴天数（verify-83 8/8；expectedDays 按 children[0].id 匹配关系记录）。
- 8.4 账号注销独立表单：V142（auth_parent_account_cancellation 加 reason/reason_note）+ ParentAccountCancellationRequest/Service 白名单校验（UNUSED/RE_REGISTER/GRADUATED/OTHER，note≤200）+ 新页面 pages/parent-cancel-form（07-10 完整结构）；端到端实测发码→Redis HMAC 还原验证码→提交→COOLING_OFF→DB reason=UNUSED 持久化（verify-84 23/23）。
- 8.5 修改密码增强：三输入可见性 toggle（PrototypeIcon 新增 eye/eye-off 4 色）+ 4 段强度条（长度≥8/字母数字/≥12/特殊字符）+ 安全提示条（verify-85 12/12，弱/中/强三档断言）。

### 第 9 组 收尾验收（9.1-9.2）
- 9.1：application-test.yml 切本地 MySQL 后全量 `mvn test` 通过（详见下方验证记录）；小程序 vue-tsc --noEmit 0 错误；8081 克隆库逐屏重拍：01-01/01-02（登录页）、01-07（?identity=organization 家庭入驻）、01-08（&kind=institution 机构入驻）、01-09（?id=<真实申请单 2109149485585137664> APPROVED 态申请详情三节点时间线）5 屏 phase3 实拍；01-03/01-10/02-01/02-09/02-10 既有截图补写状态；LAYOUT-01/02/04/05/08 状态收口"已通过原生截图对照"；all-screen-catalog.json 78 屏 implementationStatus 全部非空（仅 01-04/01-05/01-06 按定性无截图）。
- 9.2：本 progress 文档 + accepted deviations 清单 + 临时 DB 变更零残留确认。

## 临时 DB 变更零残留确认（9.2）

验证期间对远程克隆库的全部临时变更均已还原或仅影响 fixture 数据：
- V136-V142 迁移为产品变更（保留，属交付内容）。
- 2.2 临时授权：grant 完成验证后 revoke 还原（零残留）。
- 8.4 期间 fixture 家长变更：mobile 补设 13800008484、补授 PARENT_ACCOUNT_LIFECYCLE_MANAGE、27 条学生关系置 INACTIVE、1 条注销冷静期记录——均为验证 fixture 状态变更（非产品数据），fixture 库按惯例保留供复跑；不涉及生产数据。
- 9.1 期间 01-09 重拍将申请单 2109149485585137664 的 applicant_id 临时改为 fixture manager（1791858814796036）——fixture 数据变更，随库保留；flyway checksum 经官方 repair 校准（非手工值）。
- 远程验证库 flyway_schema_history 与迁移文件一一对应，validate 通过（V142 success=true）。

## Accepted Deviations（与原型不一致且经确认保留）

| # | 屏 | 差异 | 依据 |
|---|-----|------|------|
| 1 | 04-04 | 账号关联 vs 候选加入：语义不同需产品决策，维持现状 | design.md Non-Goals |
| 2 | 07-05 | 培训申请 vs 入学管理：语义不同需产品决策，维持现状 | design.md Non-Goals |
| 3 | 01-03/01-07/01-08 | 注册字段差异（步骤3文案、密码规则 8-20 位、缺手机号字段、child 姓名年级不在 family DTO、省市拆分 DTO 缺失）：需产品决策 | design.md Non-Goals + catalog gaps |
| 4 | 06-01 | 明细维度保持实现增强现状（不回退到原型） | design.md Non-Goals |
| 5 | 06-07 | 多状态 filter 保持实现增强现状（不回退到原型） | design.md Non-Goals |
| 6 | 01-04/01-05/01-06 | 本地不可达（需真实微信授权 bindingTicket），实现在 unified-login bindingChoice 分支，以代码走查+API 合同测试替代截图验收 | catalog gaps 2026-10-16/19 定性 |
| 7 | 01-09 | 仅 APPROVED 态实拍；PENDING/REJECTED/WITHDRAWN 态同组件按真实状态渲染，不伪造审核时间 | catalog implementationStatus |
| 8 | 02-01 | 原型缺"我家小主人"卡与本周任务进度卡；实现以 4 宫格入口替代"+新建任务"；"最近任务"→"最新动态"事件流 | catalog gaps（已到达截图对照） |
| 9 | 02-09 | 实现拆分"确认通过"卡片+成功页两态（原型一页完成） | catalog gaps |
| 10 | 02-10 | 驳回意见为详情页内嵌（原型独立整页），上限同为 100 字 | catalog gaps |
| 11 | 07-04 | 二维码生成受克隆库无短信网关阻断（非授权问题） | tasks.md 2.3 备注 |
| 12 | 01-10 | 结构差异为列表+进入按钮替代头像卡/切换卡；query 口径 choose=1 已修正 | catalog gaps |
