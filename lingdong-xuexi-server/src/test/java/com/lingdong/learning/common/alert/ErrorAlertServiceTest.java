package com.lingdong.learning.common.alert;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ErrorAlertServiceTest {
    @SuppressWarnings("unchecked")
    private final ObjectProvider<RestClient.Builder> builderProvider = mock(ObjectProvider.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Logger alertLogger;

    @BeforeEach
    void setUp() {
        alertLogger = (Logger) LoggerFactory.getLogger("LINGDONG_ALERT");
        appender.start();
        alertLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        alertLogger.detachAppender(appender);
    }

    @Test
    void recordsStructuredAlertWithoutMessageDetailsWhenWebhookIsAbsent() {
        ErrorAlertService service = new ErrorAlertService("  ", builderProvider);

        service.recordServerError("/api/v1/students", "trace-1", IllegalStateException.class);

        List<ILoggingEvent> events = appender.list;
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getLevel()).isEqualTo(Level.ERROR);
        assertThat(events.get(0).getFormattedMessage())
                .contains("[ALERT]").contains("IllegalStateException").contains("/api/v1/students").contains("trace-1");
    }

    @Test
    void dispatchesWebhookPayloadToConfiguredEndpointAndRecordsSuccess() throws Exception {
        AtomicReference<String> received = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", exchange -> {
            received.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            when(builderProvider.getIfAvailable(any())).thenReturn(RestClient.builder());
            ErrorAlertService service = new ErrorAlertService(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/hook", builderProvider);

            service.recordServerError("/api/v1/tasks", "trace-2", NullPointerException.class);

            assertThat(received.get()).contains("NullPointerException").contains("/api/v1/tasks").contains("trace-2");
            assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                    .anySatisfy(message -> assertThat(message).contains("[ALERT] webhook 分发成功").contains("trace-2"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void keepsServingAndRecordsFailureWhenWebhookIsUnreachable() {
        when(builderProvider.getIfAvailable(any())).thenReturn(RestClient.builder());
        // 端口 1（tcpmux）默认无监听，保证分发失败路径真实执行。
        ErrorAlertService service = new ErrorAlertService("http://127.0.0.1:1/hook", builderProvider);

        service.recordServerError("/api/v1/tasks", "trace-3", RuntimeException.class);

        assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> assertThat(message).contains("[ALERT] webhook 分发失败").contains("trace-3"));
    }
}
