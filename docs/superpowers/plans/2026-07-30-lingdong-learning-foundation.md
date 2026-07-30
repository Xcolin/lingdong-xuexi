# 灵动学习工程基础实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在当前目录建立可启动、可测试、可通过Flyway演进数据库的灵动学习后端基础工程，并为独立Web和小程序应用提供稳定的第一条公共接口。

**Architecture:** 后端采用Spring Boot模块化单体，所有业务模块未来位于同一应用中但按领域包隔离。数据库结构和基础数据只通过Flyway迁移；微信凭证只位于被Git忽略的本地配置，后端读取且不向客户端返回。

**Tech Stack:** Java 17, Spring Boot 3.4.5, Maven 3.9+, MyBatis XML, Flyway, MySQL 8, H2 test database, JUnit 5, Spring MockMvc.

---

## 先决条件

- Maven执行命令前设置 `JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot`。当前系统级 `JAVA_HOME` 指向不可启动的JDK 18，不能用于本项目构建。
- Docker当前不可用，因此基础迁移测试先使用H2的MySQL兼容模式；接入真实MySQL后补充真实MySQL迁移验证。

## 文件结构

- Create: `.gitignore` - 忽略本地密钥、构建产物和前端依赖目录。
- Create: `README.md` - 说明本地Java、MySQL、Flyway和启动方式。
- Create: `server/pom.xml` - 后端依赖与构建配置。
- Create: `server/src/main/java/com/lingdong/learning/LingdongLearningApplication.java` - Spring Boot入口。
- Create: `server/src/main/java/com/lingdong/learning/common/config/WxProperties.java` - 微信后端配置绑定。
- Create: `server/src/main/java/com/lingdong/learning/common/web/HealthController.java` - 公共健康检查接口。
- Create: `server/src/main/resources/application.yml` - 不含密钥的默认配置。
- Create: `server/src/main/resources/application-local.yml` - 仅本地使用的微信和数据库凭证，必须被忽略。
- Create: `server/src/main/resources/db/migration/V1__create_system_config.sql` - 第一条系统配置表迁移。
- Create: `server/src/test/java/com/lingdong/learning/HealthControllerTest.java` - 健康接口行为测试。
- Create: `server/src/test/java/com/lingdong/learning/FlywayMigrationTest.java` - 迁移结果测试。
- Create: `server/src/test/resources/application-test.yml` - H2 MySQL兼容测试配置。

### Task 1: 建立本地密钥与构建文件隔离

**Files:**
- Create: `.gitignore`
- Create: `README.md`

- [x] **Step 1: 创建忽略规则**

```gitignore
server/target/
server/src/main/resources/application-local.yml
web/node_modules/
web/dist/
miniapp/node_modules/
miniapp/dist/
.idea/
*.iml
```

- [x] **Step 2: 创建本地运行说明**

README必须明确Java 17路径、`JAVA_HOME`临时设置方式、MySQL 8连接配置位置、Flyway脚本目录和禁止提交`application-local.yml`的规则。

- [x] **Step 3: 验证密钥文件将被Git忽略**

Run: `git check-ignore -v server/src/main/resources/application-local.yml`

Expected: 输出`.gitignore`中对应规则。

### Task 2: 先写健康接口失败测试

**Files:**
- Create: `server/pom.xml`
- Create: `server/src/test/java/com/lingdong/learning/HealthControllerTest.java`
- Create: `server/src/test/resources/application-test.yml`

- [x] **Step 1: 创建Maven项目定义**

`server/pom.xml`使用Spring Boot 3.4.5父POM、Java 17，并声明`spring-boot-starter-web`、`spring-boot-starter-validation`、`mybatis-spring-boot-starter`、`flyway-core`、`flyway-mysql`、MySQL连接器、H2和`spring-boot-starter-test`。

- [x] **Step 2: 写入失败测试**

```java
package com.lingdong.learning;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsApplicationHealthWithoutSensitiveConfiguration() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.application").value("lingdong-learning"));
    }
}
```

`application-test.yml`使用`jdbc:h2:mem:lingdong_learning;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE`，并将Flyway迁移目录设为`classpath:db/migration`。

- [x] **Step 3: 运行测试并确认因应用入口和接口尚不存在而失败**

Run: `$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'; Set-Location server; mvn test -Dtest=HealthControllerTest`

Expected: FAIL，缺少`LingdongLearningApplication`或`/api/v1/health`。

### Task 3: 实现最小可启动后端与安全配置绑定

**Files:**
- Create: `server/src/main/java/com/lingdong/learning/LingdongLearningApplication.java`
- Create: `server/src/main/java/com/lingdong/learning/common/config/WxProperties.java`
- Create: `server/src/main/java/com/lingdong/learning/common/web/HealthController.java`
- Create: `server/src/main/resources/application.yml`
- Create: `server/src/main/resources/application-local.yml`

- [x] **Step 1: 创建应用入口和微信配置类**

```java
@SpringBootApplication
@ConfigurationPropertiesScan
public class LingdongLearningApplication {
    public static void main(String[] args) {
        SpringApplication.run(LingdongLearningApplication.class, args);
    }
}
```

```java
@ConfigurationProperties(prefix = "wx")
public record WxProperties(String appId, String appSecret) {}
```

- [x] **Step 2: 实现不包含密钥的健康接口**

```java
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {
    @GetMapping
    public Map<String, String> health() {
        return Map.of("status", "UP", "application", "lingdong-learning");
    }
}
```

- [x] **Step 3: 写入默认和本地配置**

`application.yml`只定义应用名、MySQL连接变量占位符、MyBatis XML路径、Flyway路径和`wx`变量占位符，不默认激活任何本地配置。`application-local.yml`写入用户提供的微信凭证和本地MySQL连接参数；本地启动时通过`--spring.profiles.active=local`启用。该文件不得被Git跟踪或在命令输出中打印。

- [x] **Step 4: 运行健康接口测试并确认通过**

Run: `$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'; Set-Location server; mvn test -Dtest=HealthControllerTest`

Expected: PASS，`HealthControllerTest` 1项通过。

### Task 4: 先写Flyway迁移失败测试

**Files:**
- Create: `server/src/test/java/com/lingdong/learning/FlywayMigrationTest.java`

- [x] **Step 1: 写入迁移结果失败测试**

```java
package com.lingdong.learning;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class FlywayMigrationTest {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsSystemConfigurationTableThroughFlyway() {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_name = 'sys_config'",
                Integer.class);

        assertThat(count).isEqualTo(1);
    }
}
```

- [x] **Step 2: 运行迁移测试并确认因为迁移脚本缺失而失败**

Run: `$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'; Set-Location server; mvn test -Dtest=FlywayMigrationTest`

Expected: FAIL，查询`sys_config`表失败。

### Task 5: 实现第一条Flyway迁移并验证全量测试

**Files:**
- Create: `server/src/main/resources/db/migration/V1__create_system_config.sql`

- [x] **Step 1: 添加系统配置表迁移**

```sql
CREATE TABLE sys_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(128) NOT NULL,
    config_value VARCHAR(2048) NOT NULL,
    value_type VARCHAR(32) NOT NULL DEFAULT 'STRING',
    is_secret TINYINT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sys_config_key UNIQUE (config_key)
);
```

- [x] **Step 2: 运行迁移测试并确认通过**

Run: `$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'; Set-Location server; mvn test -Dtest=FlywayMigrationTest`

Expected: PASS，`sys_config`由Flyway创建。

- [x] **Step 3: 运行全部后端测试**

Run: `$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.13.11-hotspot'; Set-Location server; mvn test`

Expected: PASS，健康接口和Flyway迁移测试均通过。

### Task 6: 为后续独立前端和业务模块建立执行边界

**Files:**
- Modify: `README.md`
- Modify: `灵动学习-业务需求说明书-V1.0.md`

- [x] **Step 1: 在README列出独立应用目录约定**

后续创建`web/`作为Ant Design Pro React应用、`miniapp/`作为uni-app小程序应用；两者共享OpenAPI契约，不共用页面代码或密钥配置。

- [x] **Step 2: 在业务需求说明书补充工程约束**

明确Flyway为数据库变更唯一常规路径；地理考勤与轨迹默认停用，个人主体工具类目构建包不纳入定位采集和地图调用能力。

- [x] **Step 3: 核验当前基础范围**

Run: `git status --short`

Expected: 仅显示本计划列出的文档、后端基础工程和本地被忽略配置文件；不显示真实微信密钥。

## 后续独立实施计划

基础工程通过后，按以下顺序各自形成独立计划并实施：

1. 组织、用户、RBAC、数据权限、功能开关和系统审核。
2. 账号认证、学生账号、主副家长与机构关联。
3. 三来源任务、学生执行、审核转交、消息与附件。
4. 积分、奖励、成长复盘、报表和导出。
5. 学校机构、班级、教师、学员、考勤与异常报备。
6. 独立Web和uni-app应用、OpenAPI契约、端到端测试与发布运维。
