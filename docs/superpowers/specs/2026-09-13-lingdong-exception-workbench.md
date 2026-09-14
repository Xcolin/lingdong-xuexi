# 机构异常待处理工作台闭环（R04）

复用异常报备现有状态 SUBMITTED/HANDLED、组织数据范围和版本处理接口。机构首页展示服务端按 SUBMITTED 查询的 total，明确“待处理异常”，不把首屏条数当总数，不新增风险指标。进入已有异常页时带待处理筛选，支持真实分页、详情、处理说明和处理后刷新；保留教师原报备入口。

进入和操作前校验实际 MINIAPP、机构/教师当前角色、动态操作权限、组织认证及异常功能开关，系统审核员优先拒绝。数据范围仍由服务端决定，不以首页直接组织列表裁剪或扩大业务查询。离页、换身份与刷新使旧响应失效，失败状态不能伪装为空列表，处理使用当前显示版本，冲突后要求刷新。

先以合成浏览器复现首页无待处理总数，再验证 total 大于首屏条数、分页、详情、处理后刷新、失败重试和关闭。后端不重复新增状态机或迁移；核对发现原服务未明确排除混合审核员，新增回归先失败后修复三个角色/查询守卫。

2026-09-13 本地闭环验证完成：`.local-verification/r04-exception-summary-red.log` 为缺失总数的浏览器红灯；`server/target/remaining-r04-exception-auditor-red.log` 为混合审核员未抛拒绝的一项失败、无错误；`remaining-r04-exception-green.log` 包含服务、持久化和边界专项通过。全量 `remaining-r04-exception-full.log` package 退出 0，184 类 721 项、失败/错误/跳过均 0，最终 JAR 与 target/classes 的本地配置排除检查通过。

小程序最终类型检查、H5/微信构建通过，日志 `.local-verification/r04-exception-final-types.log`、`r04-exception-final-h5.log`、`r04-exception-final-weixin.log`；`r04-exception-final-browser.log` 验证 23 项服务端总数、失败重试、第二页、详情、携当前版本处理、列表和首页归零、功能关闭不查询；`r04-exception-review-regression.log` 验证原教师/机构待审流程未回退。未执行真实微信操作或远程迁移。R04 系统管理员/审核员等其他端入口仍未完成，不以本摘要代表整个工作台完工。
