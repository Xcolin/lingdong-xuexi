package com.lingdong.learning.common.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthControllerTest {
    private DataSource dataSource;
    private Connection connection;
    private Statement statement;
    @SuppressWarnings("unchecked")
    private final ObjectProvider<RedisConnectionFactory> redisProvider = mock(ObjectProvider.class);
    private RedisConnectionFactory redisFactory;
    private RedisConnection redisConnection;
    private HealthController controller;

    @BeforeEach
    void setUp() throws SQLException {
        dataSource = mock(DataSource.class);
        connection = mock(Connection.class);
        statement = mock(Statement.class);
        redisFactory = mock(RedisConnectionFactory.class);
        redisConnection = mock(RedisConnection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(redisProvider.getIfAvailable()).thenReturn(redisFactory);
        when(redisFactory.getConnection()).thenReturn(redisConnection);
        controller = new HealthController(dataSource, redisProvider);
    }

    @Test
    void reportsUpWhenDatabaseAndRedisAreReachable() throws SQLException {
        when(statement.execute("SELECT 1")).thenReturn(true);
        when(redisConnection.ping()).thenReturn("PONG");

        ResponseEntity<Map<String, Object>> response = controller.health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "UP")
                .containsEntry("db", true).containsEntry("redis", true);
    }

    @Test
    void reportsDegradedWithoutLeakingDetailsWhenDatabaseIsDown() throws SQLException {
        when(dataSource.getConnection()).thenThrow(new SQLException("内部地址与凭据细节"));
        when(redisConnection.ping()).thenReturn("PONG");

        ResponseEntity<Map<String, Object>> response = controller.health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).containsEntry("status", "DEGRADED").containsEntry("db", false);
        assertThat(response.getBody()).doesNotContainKey("error");
        assertThat(response.getBody().toString()).doesNotContain("内部地址与凭据细节");
    }

    @Test
    void reportsDegradedWhenRedisIsUnreachable() {
        when(redisConnection.ping()).thenThrow(new RuntimeException("连接失败"));

        ResponseEntity<Map<String, Object>> response = controller.health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).containsEntry("status", "DEGRADED").containsEntry("redis", false);
    }

    @Test
    void treatsMissingRedisDependencyAsReachable() {
        when(redisProvider.getIfAvailable()).thenReturn(null);

        ResponseEntity<Map<String, Object>> response = controller.health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("redis", true);
    }
}
