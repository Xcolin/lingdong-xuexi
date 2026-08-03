package com.lingdong.learning.auth.infrastructure.security;

import com.lingdong.learning.auth.infrastructure.config.StudentLoginCodeProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;

/** 使用服务端版本化密钥和凭证独立随机盐保护4位学生登录码。 */
@Component
public class StudentLoginCodeHasher {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int SALT_BYTES = 16;
    private static final Pattern LOGIN_CODE_PATTERN = Pattern.compile("\\d{4}");

    private final StudentLoginCodeProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public StudentLoginCodeHasher(StudentLoginCodeProperties properties) {
        this.properties = properties;
    }

    public StudentLoginCodeDigest hash(String plainLoginCode) {
        validateLoginCode(plainLoginCode);
        byte[] salt = new byte[SALT_BYTES];
        secureRandom.nextBytes(salt);
        String keyVersion = properties.getActiveKeyVersion();
        byte[] digest = digest(plainLoginCode, salt, properties.keyBytes(keyVersion));
        return new StudentLoginCodeDigest(
                Base64.getEncoder().encodeToString(digest),
                Base64.getEncoder().encodeToString(salt),
                keyVersion
        );
    }

    public boolean matches(String plainLoginCode, String expectedHash, String encodedSalt, String keyVersion) {
        if (!isValidLoginCode(plainLoginCode) || expectedHash == null || encodedSalt == null || keyVersion == null) {
            return false;
        }
        try {
            byte[] expected = Base64.getDecoder().decode(expectedHash);
            byte[] salt = Base64.getDecoder().decode(encodedSalt);
            byte[] actual = digest(plainLoginCode, salt, properties.keyBytes(keyVersion));
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private byte[] digest(String plainLoginCode, byte[] salt, byte[] keyBytes) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(keyBytes, HMAC_ALGORITHM));
            mac.update(salt);
            return mac.doFinal(plainLoginCode.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("学生登录码摘要计算失败", exception);
        }
    }

    private void validateLoginCode(String plainLoginCode) {
        if (!isValidLoginCode(plainLoginCode)) {
            throw new IllegalArgumentException("学生登录码必须为4位数字");
        }
    }

    private boolean isValidLoginCode(String plainLoginCode) {
        return plainLoginCode != null && LOGIN_CODE_PATTERN.matcher(plainLoginCode).matches();
    }
}
