# 灵动伴随联调基础数据

显式执行的联调数据初始化，不属于应用的自动迁移目录。复用 V1–V86 的角色、权限、字典与功能开关，使用独立 `flyway_demo_history` 记录执行情况。请勿用于生产环境。

数据包括 7 个账号（admin、auditor、orgadmin、teacher01/02、parent01/02）、示例学校/年级/两个班级、教师任课、2 名虚拟学生、主家长关系、机构/班级关系、零余额积分账户与积分生命周期状态。学生账号及登录码须通过已有 `/students/{id}/credentials/initialize` 接口由家长或机构管理员初始化，不伪造认证摘要。

1. 使用 `tools/db-maintenance.ps1 -Mode backup` 备份真实库，再恢复到 `ld_verify_YYYYMMDD_8位十六进制` 隔离库。
2. 设置 `JAVA_HOME` 为 JDK 17；运行 `tools/bootstrap-data/run.ps1 -Schema <隔离库> -BackupDir <备份目录>`。凭据首次随机生成并保存在 Git 忽略的 `.local-verification/bansui-demo-credentials.properties`，不输出密码。
3. 在隔离库重复运行，确认 `demo.migrationsExecuted=0`；确认备份和演练结果后，以相同备份目录、凭据文件运行 `-Schema configured`。
4. 使用管理端登录与 `/auth/me` 验证角色权限，并通过家长/机构管理员初始化学生登录凭据。不向虚构人员发送短信、微信等消息。

固定标识范围为 `2190000000000010001` 至 `2190000000000100004`。首次执行遇到相同账号会停止，不覆盖现有账号或重置密码。成功后重复执行由 Flyway 跳过；不要删除历史表、修改已执行 SQL 或自动 repair 失败历史。

本地联调启动脚本 `.local-verification/start-real-env.ps1` 使用 `.local-verification/bansui-runtime-secrets.json` 中的持久密钥。应保留密钥和凭据文件，以免学生登录码或后续加密数据失效；它们不进入源码仓库。
