package com.lingdong.learning.student.infrastructure.security;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/** 以安全随机数签发 URL 安全令牌，数据库只保存其 SHA-256 摘要。 */
@Component
public class SecureInvitationTokenService implements InvitationTokenService {
    private static final int TOKEN_BYTES = 32;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Override
    public String hash(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("邀请令牌不能为空");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256", exception);
        }
    }
}
