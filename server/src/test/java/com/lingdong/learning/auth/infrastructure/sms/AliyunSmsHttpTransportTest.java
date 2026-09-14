package com.lingdong.learning.auth.infrastructure.sms;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

/** 仅连接回环地址，验证真实 JDK 传输边界；生产发送入口仍强制官方 HTTPS 地址。 */
class AliyunSmsHttpTransportTest {
    @Test
    void doesNotFollowRedirectOrReplayAuthenticationAndRejectsNon200() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var redirected = new AtomicInteger();
        var calls = new AtomicInteger();
        server.createContext("/target", exchange -> {
            redirected.incrementAndGet(); exchange.sendResponseHeaders(200, -1); exchange.close();
        });
        for (int status : new int[]{302, 401, 429, 500}) {
            server.createContext("/status/" + status, exchange -> {
                calls.incrementAndGet();
                exchange.getResponseHeaders().add("Location", "/target");
                exchange.getResponseHeaders().add("WWW-Authenticate", "Basic realm=\"sms\"");
                exchange.sendResponseHeaders(status, -1); exchange.close();
            });
        }
        server.start();
        try {
            for (int status : new int[]{302, 401, 429, 500}) {
                assertThatThrownBy(() -> post(server, "/status/" + status)).isInstanceOf(IOException.class);
            }
            assertThat(calls).hasValue(4);
            assertThat(redirected).hasValue(0);
        } finally { server.stop(0); }
    }

    @Test
    void acceptsBoundedResponseAndRejectsOversizedResponse() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ok", exchange -> {
            byte[] body = "{\"Code\":\"OK\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.createContext("/large", exchange -> {
            exchange.sendResponseHeaders(200, 8193);
            try (var output = exchange.getResponseBody()) { output.write(new byte[8193]); }
        });
        server.start();
        try {
            assertThat(post(server, "/ok")).isEqualTo("{\"Code\":\"OK\"}");
            assertThatThrownBy(() -> post(server, "/large")).isInstanceOf(IOException.class);
        } finally { server.stop(0); }
    }

    private String post(HttpServer server, String path) throws IOException {
        return AliyunParentSmsSender.post(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path),
                Map.of("Authorization", "test-only", "content-type", "application/json"), 1000, 1000);
    }
}
