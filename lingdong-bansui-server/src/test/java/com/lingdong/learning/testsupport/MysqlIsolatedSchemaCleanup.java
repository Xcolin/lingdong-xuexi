package com.lingdong.learning.testsupport;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 注册 JVM 退出时清理 @DynamicPropertySource 创建的按 context 隔离 MySQL schema。
 * 这些 schema 名含 UUID 前缀且无其他清理途径，跨轮残留会污染
 * FlywayMigrationTest 等基于 information_schema 的全库不变量断言（同名表被重复计数）。
 */
public final class MysqlIsolatedSchemaCleanup {
    private static final CopyOnWriteArraySet<String> SCHEMAS = new CopyOnWriteArraySet<>();
    private static volatile boolean hookRegistered;

    private MysqlIsolatedSchemaCleanup() {
    }

    /** 在 @DynamicPropertySource 中调用；JVM 退出时 drop 注册的完整隔离 schema。 */
    public static void register(String schemaName) {
        dropSql(schemaName);
        SCHEMAS.add(schemaName);
        if (!hookRegistered) {
            synchronized (MysqlIsolatedSchemaCleanup.class) {
                if (!hookRegistered) {
                    hookRegistered = true;
                    Runtime.getRuntime().addShutdownHook(new Thread(MysqlIsolatedSchemaCleanup::dropAll));
                }
            }
        }
    }

    private static void dropAll() {
        String url = "jdbc:mysql://" + env("LINGDONG_TEST_DB_HOST", "127.0.0.1") + ":"
                + env("LINGDONG_TEST_DB_PORT", "3306")
                + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        try (Connection connection = DriverManager.getConnection(url,
                env("LINGDONG_TEST_DB_USER", "root"), env("LINGDONG_TEST_DB_PASSWORD", "root"))) {
            cleanup(connection, java.util.List.copyOf(SCHEMAS));
        } catch (Exception ignored) {
            // 数据库不可达时静默跳过
        }
    }

    static void cleanup(Connection connection, java.util.Collection<String> schemas) {
        // Validate the whole batch before issuing any destructive statement.
        var targets=schemas.stream().map(MysqlIsolatedSchemaCleanup::dropSql).toList();
        for(String sql:targets) {
            try(Statement statement=connection.createStatement()){statement.execute(sql);}
            catch(java.sql.SQLException failure){System.err.println("Exact isolated schema cleanup failed; no broader cleanup attempted");}
        }
    }

    static String dropSql(String schema) {
        if(schema==null || !schema.matches("(?:feature_management_|system_task_query_)[a-f0-9]{32}|reward_exchange_[a-f0-9]{32}_[0-9]+")) throw new IllegalArgumentException("Exact generated test schema required");
        return "DROP DATABASE IF EXISTS `"+schema+"`";
    }
    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null ? fallback : value;
    }
}
