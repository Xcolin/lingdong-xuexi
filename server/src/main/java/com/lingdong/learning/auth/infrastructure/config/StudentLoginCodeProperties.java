package com.lingdong.learning.auth.infrastructure.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** 学生登录码带密钥摘要配置，支持按版本读取历史密钥。 */
@ConfigurationProperties(prefix = "lingdong.auth.student-code")
public class StudentLoginCodeProperties implements InitializingBean {
    private static final int MINIMUM_KEY_BYTES = 32;

    private String activeKeyVersion;
    private Map<String, String> keys = new LinkedHashMap<>();

    public String getActiveKeyVersion() {
        return activeKeyVersion;
    }

    public void setActiveKeyVersion(String activeKeyVersion) {
        this.activeKeyVersion = activeKeyVersion;
    }

    public Map<String, String> getKeys() {
        return keys;
    }

    public void setKeys(Map<String, String> keys) {
        this.keys = keys == null ? new LinkedHashMap<>() : new LinkedHashMap<>(keys);
    }

    /** 返回指定版本的密钥副本，调用方不得持久化或记录该值。 */
    public byte[] keyBytes(String keyVersion) {
        String encodedKey = keys.get(keyVersion);
        if (encodedKey == null || encodedKey.isBlank()) {
            throw new IllegalStateException("学生登录码密钥版本不存在：" + keyVersion);
        }
        return decodeAndValidate(encodedKey, keyVersion);
    }

    @Override
    public void afterPropertiesSet() {
        if (activeKeyVersion == null || activeKeyVersion.isBlank() || !keys.containsKey(activeKeyVersion)) {
            throw new IllegalStateException("学生登录码活动密钥未配置");
        }
        keys.forEach((keyVersion, encodedKey) -> decodeAndValidate(encodedKey, keyVersion));
    }

    private byte[] decodeAndValidate(String encodedKey, String keyVersion) {
        final byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(encodedKey);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("学生登录码密钥必须使用Base64编码：" + keyVersion, exception);
        }
        if (keyBytes.length < MINIMUM_KEY_BYTES) {
            throw new IllegalStateException("学生登录码密钥至少32字节：" + keyVersion);
        }
        return keyBytes;
    }
}
