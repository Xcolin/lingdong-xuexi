package com.lingdong.learning.common.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 错误告警（R09-6.2）：未预期服务端错误时输出结构化告警日志（LINGDONG_ALERT）。
 * 告警内容为白名单字段（异常类型、请求路径、追踪标识），不包含异常消息与堆栈，避免敏感细节进入告警通道。
 * 外置 webhook 通过 ALERT_WEBHOOK_URL 注入；未配置时仅本地记录，不虚构送达。发送结果（成功/失败）均记录日志。
 */
@Service
public class ErrorAlertService {
    private static final Logger ALERT_LOG = LoggerFactory.getLogger("LINGDONG_ALERT");

    private final String webhookUrl;
    private final RestClient restClient;

    public ErrorAlertService(
            @Value("${lingdong.alert.webhook-url:}") String webhookUrl,
            ObjectProvider<RestClient.Builder> restClientBuilder
    ) {
        this.webhookUrl = webhookUrl == null ? "" : webhookUrl.trim();
        this.restClient = this.webhookUrl.isEmpty()
                ? null
                : restClientBuilder.getIfAvailable(RestClient::builder).build();
    }

    /** 记录一次服务端错误告警；webhook 已配置时分发，失败不抛出以免掩盖原始错误。 */
    public void recordServerError(String path, String traceId, Class<? extends Exception> exceptionType) {
        ALERT_LOG.error("[ALERT] exceptionType={}, path={}, traceId={}",
                exceptionType.getSimpleName(), path, traceId);
        if (restClient == null) {
            return;
        }
        try {
            restClient.post().uri(webhookUrl)
                    .body(Map.of("exceptionType", exceptionType.getSimpleName(),
                            "path", path, "traceId", traceId, "application", "lingdong-bansui-server"))
                    .retrieve().toBodilessEntity();
            ALERT_LOG.info("[ALERT] webhook 分发成功，traceId={}", traceId);
        } catch (RuntimeException exception) {
            ALERT_LOG.warn("[ALERT] webhook 分发失败，traceId={}, 异常类型={}",
                    traceId, exception.getClass().getSimpleName());
        }
    }
}
