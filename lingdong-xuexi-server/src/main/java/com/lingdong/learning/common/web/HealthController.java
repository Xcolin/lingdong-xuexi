package com.lingdong.learning.common.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康监测（R09-6.2）：真实探测数据库与 Redis 连通性。
 * 全部依赖可用返回 200 UP；任一不可用返回 503 DEGRADED。
 * 响应只包含布尔探测结果，不输出异常细节或内部地址，避免信息泄露。
 */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {
    private static final Logger LOGGER = LoggerFactory.getLogger(HealthController.class);

    private final DataSource dataSource;
    private final ObjectProvider<RedisConnectionFactory> redisConnectionFactory;

    public HealthController(DataSource dataSource, ObjectProvider<RedisConnectionFactory> redisConnectionFactory) {
        this.dataSource = dataSource;
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        boolean db = checkDatabase();
        boolean redis = checkRedis();
        boolean up = db && redis;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", up ? "UP" : "DEGRADED");
        body.put("application", "lingdong-learning");
        body.put("db", db);
        body.put("redis", redis);
        return ResponseEntity.status(up ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    private boolean checkDatabase() {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("SELECT 1");
            return true;
        } catch (Exception exception) {
            LOGGER.debug("健康探测数据库不可用，异常类型={}", exception.getClass().getSimpleName());
            return false;
        }
    }

    private boolean checkRedis() {
        RedisConnectionFactory factory = redisConnectionFactory.getIfAvailable();
        if (factory == null) {
            return true;
        }
        try {
            return "PONG".equalsIgnoreCase(factory.getConnection().ping());
        } catch (Exception exception) {
            LOGGER.debug("健康探测 Redis 不可用，异常类型={}", exception.getClass().getSimpleName());
            return false;
        }
    }
}
