# R06 已确认统计口径落实

2026-09-28 用户明确采用统计设计第 2.4 节四项规则。本次先闭环 R-001 和 R-003，R-002 活跃度、R-004 机构统计另按既定数据集推进，不把确认口径等同于代码完成。

## 修改范围

- 后端日、周、月复盘及每日趋势共用公式：已完成 /（总任务 - 进行中 - 免执行）；分母不大于零时为零。保留任务总数、免执行数等事实，不另行改变其他状态纳入规则。
- 修复事实 SQL 在无任务日期上 SUM 返回 NULL 的问题，零事实明确归零，使周、月复盘包含空日期时可生成。
- 继续使用不可变快照与事实指纹：新口径影响结果时生成新版本，旧快照及其完成率不覆盖；不自动回填远程历史数据。
- Web 每日趋势和小程序家长周报展示服务端完成率、积分、待优化数和暂停次数，不按完成数/总数自行重算，不合成综合评分。Web 保留现有趋势布局，小程序保留轻量文字摘要。

## 验证记录

`server/target/remaining-r06-metrics-red.log` 已复现原公式 0.3333 与确认口径 0.5000 不符，同时暴露无任务日期聚合空值错误。第一次绿灯保留该空值失败，完成 SQL 修复后 `remaining-r06-metrics-green-final.log` 36 项通过，覆盖生成、查询、批次、PDF 渲染及导出。

新增验证：周/月总览与每日趋势同口径、只有进行中和免执行时为零、旧 0.3333 快照仍保留而受控重算产生 0.5000 新版本、再次生成幂等。最终 `remaining-r06-metrics-verified.log` package 退出 0，189 类 791 项，失败/错误/跳过均 0；全部 189 份报告本轮更新，汇总 `remaining-r06-metrics-test-summary.json`。`check-release.ps1 -ArtifactOnly` 确认构建资源及 JAR 无本地配置。

小程序 `r06-metrics-miniapp-typecheck-final.log` 与 `r06-metrics-miniapp-build.log` 分别验证类型检查、微信小程序构建通过；首次类型检查发现每日趋势声明缺少后端已有的 pendingOptimizationCount，已补齐契约。尚不代表真实设备 UAT。

Web `.local-verification/r06-metrics-web-red.log` 新增三项红灯（历史率、全零、负积分），`r06-metrics-web-green.log` 10 项通过，`r06-metrics-web-build.log` 类型检查和生产构建通过。R-001 和 R-003 本地实现验收通过；R-002/R-004 尚待实现，真实设备与部署验收归 R10。源码迁移仍 V82，未新增迁移或远程写入。
