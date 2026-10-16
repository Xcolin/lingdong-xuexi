# 项目长期约定

## 本地启动（Windows / Git Bash）

- Maven 必须走项目内包装脚本 `tools/mvn-run.sh`（详见下方坑），不要直接 `mvn`。
- 后端启动命令：

  ```bash
  cd lingdong-bansui-server
  ../../tools/mvn-run.sh -DskipTests spring-boot:run \
    -Dspring-boot.run.profiles=local \
    -Dspring-boot.run.arguments="--server.port=8080 --spring.config.additional-location=file:./src/main/resources/application-local.yml"
  ```

  `--server.port=8080` 不可省略：环境注入的 `SERVER__PORT` 会覆盖配置文件的端口。
- Web 启动：`lingdong-bansui-web` 下 `npm run dev`（Vite 5173，`/api` 代理 8080）。
- 健康探测：`GET http://127.0.0.1:8080/api/v1/health` → `{"status":"UP","db":true,"redis":true}`。

## 已知环境坑

- 全局 `MAVEN_HOME` 带反斜杠、`JAVA_HOME` 指向 temurin-18，都会让 Maven/Java 启动失败；`tools/mvn-run.sh` 已固定 JDK 17 并直接调用 classworlds Launcher。
- 全局 `JAVA_HOME` 会被 bash 会话带入，脚本内必须硬写 JDK 17 路径。

## 配置与密钥

- `lingdong-bansui-server/src/main/resources/application-local.yml` 为本地外置配置（已 gitignore），存放远端 DB/Redis 凭据与本地开发密钥。
- 必需密钥（缺任一项启动失败）：`lingdong.auth.student-code.keys.v1`（Base64 ≥32B）、`lingdong.student-import.credential-key`（Base64 恰好 32B）、`lingdong.export-job.source-hmac-secret`（≥32 字符）。
- 学生登录码密钥参与 HMAC 摘要，更换会导致远端库存量登录码失配，需重置。
