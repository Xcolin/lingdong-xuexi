package com.lingdong.learning.exportjob.infrastructure.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

/** 使用独立 HMAC 密钥摘要请求来源，数据库不保存原始地址。 */
public class ExportSourceHasher {
    private static final String ALGORITHM = "HmacSHA256";
    private final byte[] secret;

    public ExportSourceHasher(String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("导出来源摘要密钥长度不能少于32字节");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8).clone();
    }

    public String hash(String source) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("导出请求来源不能为空");
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            byte[] value = ("export-source:" + source.trim()).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(mac.doFinal(value));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("运行环境不支持导出来源摘要算法", exception);
        }
    }
}
