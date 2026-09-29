package com.lingdong.learning.auth.infrastructure.security;

import com.lingdong.learning.auth.application.AuthProtectionUnavailableException;
import com.lingdong.learning.auth.application.ParentSmsPurpose;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.auth.infrastructure.config.ParentSmsProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

/** 使用服务端 HMAC 密钥生成不可逆的手机号标识和验证码摘要。 */
@Component
public class ParentSmsCodeHasher {
    private static final String ALGORITHM = "HmacSHA256";
    private static final int MINIMUM_SECRET_BYTES = 32;

    private final ParentSmsProperties properties;

    public ParentSmsCodeHasher(ParentSmsProperties properties) {
        this.properties = properties;
    }

    public String mobileDigest(String mobile) {
        return hmac("mobile:" + mobile);
    }

    public String codeDigest(
            String mobile,
            ParentSmsPurpose purpose,
            AuthClientType clientType,
            String code
    ) {
        return hmac("code:" + mobile + ":" + purpose.name() + ":" + clientType.name() + ":" + code);
    }

    private String hmac(String value) {
        byte[] secret = properties.getHmacSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < MINIMUM_SECRET_BYTES) {
            throw new AuthProtectionUnavailableException(
                    new IllegalStateException("家长短信验证码 HMAC 密钥未配置或长度不足"));
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new AuthProtectionUnavailableException(exception);
        }
    }
}
