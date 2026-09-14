package com.lingdong.learning.auth.infrastructure.sms;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingdong.learning.auth.application.ParentSmsPurpose;
import com.lingdong.learning.auth.application.ParentSmsSender;
import com.lingdong.learning.auth.application.SmsDeliveryUnavailableException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** 阿里云 SendSms：成功仅表示供应商受理；不自动重试，不记录请求及响应。 */
@Component
@Profile("!local & !test")
@ConditionalOnProperty(prefix = "lingdong.auth.parent-sms", name = "provider", havingValue = "aliyun", matchIfMissing = true)
public class AliyunParentSmsSender implements ParentSmsSender {
    private final AliyunParentSmsProperties properties;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final Supplier<String> nonce;
    private final Transport transport;

    @Autowired
    public AliyunParentSmsSender(AliyunParentSmsProperties properties, ObjectMapper mapper, Clock clock) {
        this(properties, mapper, clock, () -> UUID.randomUUID().toString(), AliyunParentSmsSender::post);
    }

    AliyunParentSmsSender(AliyunParentSmsProperties properties, ObjectMapper mapper, Clock clock,
                          Supplier<String> nonce, Transport transport) {
        this.properties = properties; this.mapper = mapper; this.clock = clock;
        this.nonce = nonce; this.transport = transport;
    }

    @Override
    public void send(String mobile, ParentSmsPurpose purpose, String code) {
        try {
            URI endpoint = validatedEndpoint(properties.getEndpoint());
            String template = properties.getPurposeTemplates().getOrDefault(purpose, properties.getTemplateCode());
            requireText(properties.getAccessKeyId()); requireText(properties.getAccessKeySecret());
            requireText(properties.getSignName()); requireText(template);
            if (purpose == null || mobile == null || !mobile.matches("1[3-9][0-9]{9}")
                    || code == null || !code.matches("[0-9]{6}")) throw new SmsDeliveryUnavailableException();
            int connect = timeout(properties.getConnectTimeout());
            int read = timeout(properties.getReadTimeout());
            var query = new TreeMap<String, String>();
            query.put("PhoneNumbers", mobile);
            query.put("SignName", properties.getSignName());
            query.put("TemplateCode", template);
            query.put("TemplateParam", mapper.writeValueAsString(Map.of("code", code)));
            String canonicalQuery = query.entrySet().stream().map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                    .collect(Collectors.joining("&"));
            var headers = new TreeMap<String, String>();
            // 显式指定并签名内容类型，避免 JDK 为 POST 自动补入未签名的默认值。
            headers.put("content-type", "application/json");
            headers.put("host", endpoint.getHost());
            headers.put("x-acs-action", "SendSms");
            headers.put("x-acs-content-sha256", sha256(""));
            headers.put("x-acs-date", clock.instant().truncatedTo(ChronoUnit.SECONDS).toString());
            headers.put("x-acs-signature-nonce", nonce.get());
            headers.put("x-acs-version", "2017-05-25");
            headers.put("Authorization", authorization(properties.getAccessKeyId(), properties.getAccessKeySecret(), canonicalQuery, headers));
            String response = transport.post(URI.create(endpoint + "?" + canonicalQuery), headers, connect, read);
            if (response == null || response.length() > 8192 || !"OK".equals(mapper.readTree(response).path("Code").asText())) {
                throw new SmsDeliveryUnavailableException();
            }
        } catch (Exception ignored) {
            // 原始异常可能包含完整签名地址、手机号、验证码或凭据，不附带异常原因。
            throw new SmsDeliveryUnavailableException();
        }
    }

    static URI validatedEndpoint(String value) {
        if (!"https://dysmsapi.aliyuncs.com".equals(value) && !"https://dysmsapi.aliyuncs.com/".equals(value)) {
            throw new SmsDeliveryUnavailableException();
        }
        return URI.create("https://dysmsapi.aliyuncs.com/");
    }

    private static int timeout(Duration duration) {
        if (duration == null || duration.compareTo(Duration.ofSeconds(1)) < 0 || duration.compareTo(Duration.ofSeconds(10)) > 0) {
            throw new SmsDeliveryUnavailableException();
        }
        return Math.toIntExact(duration.toMillis());
    }

    private static void requireText(String value) {
        if (value == null || value.isBlank() || value.length() > 512 || value.contains("\r") || value.contains("\n")) {
            throw new SmsDeliveryUnavailableException();
        }
    }

    static String authorization(String keyId, String secret, String query, TreeMap<String, String> headers) throws Exception {
        String canonicalHeaders = headers.entrySet().stream().map(e -> e.getKey() + ":" + e.getValue().trim() + "\n")
                .collect(Collectors.joining());
        String signedHeaders = String.join(";", headers.keySet());
        String canonical = "POST\n/\n" + query + "\n" + canonicalHeaders + "\n" + signedHeaders + "\n" + sha256("");
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = HexFormat.of().formatHex(mac.doFinal(("ACS3-HMAC-SHA256\n" + sha256(canonical)).getBytes(StandardCharsets.UTF_8)));
        return "ACS3-HMAC-SHA256 Credential=" + keyId + ",SignedHeaders=" + signedHeaders + ",Signature=" + signature;
    }

    static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20").replace("*", "%2A").replace("%7E", "~");
    }

    static String post(URI uri, Map<String, String> headers, int connect, int read) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(connect);
            connection.setReadTimeout(read);
            connection.setUseCaches(false);
            connection.setDoOutput(true);
            // 固定长度流模式禁止 JDK 在认证或重定向时透明重放请求。
            connection.setFixedLengthStreamingMode(0);
            headers.forEach((key, value) -> { if (!"host".equals(key)) connection.setRequestProperty(key, value); });
            connection.setRequestProperty("Accept", "application/json");
            try (var output = connection.getOutputStream()) { output.flush(); }
            if (connection.getResponseCode() != 200) throw new IOException("SMS request rejected");
            try (var input = connection.getInputStream()) {
                byte[] body = input.readNBytes(8193);
                if (body.length > 8192) throw new IOException("SMS response too large");
                return new String(body, StandardCharsets.UTF_8);
            }
        } finally {
            connection.disconnect();
        }
    }

    @FunctionalInterface
    interface Transport {
        String post(URI uri, Map<String, String> headers, int connect, int read) throws IOException;
    }
}
