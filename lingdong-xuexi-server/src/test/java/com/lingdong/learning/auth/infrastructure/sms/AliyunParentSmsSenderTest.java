package com.lingdong.learning.auth.infrastructure.sms;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingdong.learning.auth.application.ParentSmsPurpose;
import com.lingdong.learning.auth.application.SmsDeliveryUnavailableException;
import org.junit.jupiter.api.Test;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class AliyunParentSmsSenderTest {
    private final Clock clock = Clock.fixed(Instant.parse("2023-10-26T10:22:32Z"), ZoneOffset.UTC);

    @Test
    void matchesOfficialV3SignatureVector() throws Exception {
        var headers = new TreeMap<String, String>();
        headers.put("host", "ecs.cn-shanghai.aliyuncs.com");
        headers.put("x-acs-action", "RunInstances");
        headers.put("x-acs-content-sha256", AliyunParentSmsSender.sha256(""));
        headers.put("x-acs-date", "2023-10-26T10:22:32Z");
        headers.put("x-acs-signature-nonce", "3156853299f313e23d1673dc12e1703d");
        headers.put("x-acs-version", "2014-05-26");
        assertThat(AliyunParentSmsSender.authorization("YourAccessKeyId", "YourAccessKeySecret",
                "ImageId=win2019_1809_x64_dtc_zh-cn_40G_alibase_20230811.vhd&RegionId=cn-shanghai", headers))
                .endsWith("Signature=06563a9e1b43f5dfe96b81484da74bceab24a1d853912eee15083a6f0f3283c0");
    }

    @Test
    void missingConfigurationAndUnsafeEndpointsNeverReachTransport() {
        var props = new AliyunParentSmsProperties();
        var calls = new AtomicInteger();
        var sender = sender(props, (uri, headers, connect, read) -> { calls.incrementAndGet(); return "{}"; });
        assertThatThrownBy(() -> send(sender)).isInstanceOf(SmsDeliveryUnavailableException.class);
        props = configured();
        for (String endpoint : new String[]{"http://dysmsapi.aliyuncs.com", "https://evil.example",
                "https://dysmsapi.aliyuncs.com.evil.example", "https://dysmsapi.aliyuncs.com/?redirect=evil",
                "https://user@dysmsapi.aliyuncs.com", "https://dysmsapi.aliyuncs.com:8443"}) {
            props.setEndpoint(endpoint);
            var invalid = sender(props, (uri, headers, connect, read) -> { calls.incrementAndGet(); return "{}"; });
            assertThatThrownBy(() -> send(invalid)).isInstanceOf(SmsDeliveryUnavailableException.class);
        }
        assertThat(calls).hasValue(0);
    }

    @Test
    void submitsEncodedPurposeTemplateAndAcceptsOnlyOk() {
        var props = configured();
        props.getPurposeTemplates().put(ParentSmsPurpose.RESET_PASSWORD, "SMS_RESET");
        var sender = sender(props, (uri, headers, connect, read) -> {
            assertThat(uri.getHost()).isEqualTo("dysmsapi.aliyuncs.com");
            assertThat(uri.getRawQuery()).contains("TemplateCode=SMS_RESET", "TemplateParam=%7B%22code%22%3A%22123456%22%7D", "SignName=%E5%AD%A6%E4%B9%A0");
            assertThat(headers.get("Authorization")).startsWith("ACS3-HMAC-SHA256 Credential=test-id,");
            assertThat(connect).isEqualTo(2000);
            assertThat(read).isEqualTo(3000);
            return "{\"Code\":\"OK\",\"BizId\":\"accepted-only\"}";
        });
        sender.send("13800138000", ParentSmsPurpose.RESET_PASSWORD, "123456");
    }

    @Test
    void rejectionMalformedAndTimeoutAreSanitizedAndNeverRetried() {
        for (String body : new String[]{"{\"Code\":\"isv.BUSINESS_LIMIT_CONTROL\",\"Message\":\"13800138000 secret\"}", "not json", "{}", "{\"Code\":\"ok\"}"}) {
            var calls = new AtomicInteger();
            var sender = sender(configured(), (uri, headers, connect, read) -> { calls.incrementAndGet(); return body; });
            assertThatThrownBy(() -> send(sender)).isInstanceOf(SmsDeliveryUnavailableException.class)
                    .hasMessage("短信服务暂不可用").hasNoCause();
            assertThat(calls).hasValue(1);
        }
        var calls = new AtomicInteger();
        var sender = sender(configured(), (uri, headers, connect, read) -> {
            calls.incrementAndGet(); throw new SocketTimeoutException("13800138000 secret");
        });
        assertThatThrownBy(() -> send(sender)).isInstanceOf(SmsDeliveryUnavailableException.class).hasNoCause();
        assertThat(calls).hasValue(1);
    }

    private AliyunParentSmsSender sender(AliyunParentSmsProperties p, AliyunParentSmsSender.Transport t) {
        return new AliyunParentSmsSender(p, new ObjectMapper(), clock, () -> "nonce", t);
    }
    private void send(AliyunParentSmsSender sender) { sender.send("13800138000", ParentSmsPurpose.REGISTER_OR_LOGIN, "123456"); }
    private AliyunParentSmsProperties configured() {
        var p = new AliyunParentSmsProperties();
        p.setAccessKeyId("test-id"); p.setAccessKeySecret("test-secret");
        p.setSignName("学习"); p.setTemplateCode("SMS_TEST");
        return p;
    }
}
