package com.lingdong.learning.auth.application;

import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.auth.infrastructure.config.ParentSmsProperties;
import com.lingdong.learning.auth.infrastructure.security.ParentSmsCodeHasher;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.regex.Pattern;

/** 编排家长短信验证码的发送、摘要保存、频控与原子消费。 */
@Service
public class ParentSmsVerificationService {
    private static final Pattern MAINLAND_MOBILE = Pattern.compile("^1[3-9]\\d{9}$");

    private final ParentSmsCodeGenerator codeGenerator;
    private final ParentSmsSender smsSender;
    private final ParentSmsVerificationStore store;
    private final ParentSmsCodeHasher hasher;
    private final ParentSmsProperties properties;
    private final Clock clock;

    public ParentSmsVerificationService(
            ParentSmsCodeGenerator codeGenerator,
            ParentSmsSender smsSender,
            ParentSmsVerificationStore store,
            ParentSmsCodeHasher hasher,
            ParentSmsProperties properties,
            Clock clock
    ) {
        this.codeGenerator = codeGenerator;
        this.smsSender = smsSender;
        this.store = store;
        this.hasher = hasher;
        this.properties = properties;
        this.clock = clock;
    }

    public IssuedParentSmsCode issue(
            String mobile,
            ParentSmsPurpose purpose,
            AuthClientType clientType,
        String sourceDigest
    ) {
        validateMobile(mobile);
        validatePurposeAndClient(purpose, clientType);
        if (sourceDigest == null || sourceDigest.isBlank()) {
            throw new IllegalArgumentException("来源摘要不能为空");
        }
        String mobileDigest = hasher.mobileDigest(mobile);
        store.checkIssueRate(mobileDigest, purpose, clientType, sourceDigest);
        String code = codeGenerator.generate();
        String codeDigest = hasher.codeDigest(mobile, purpose, clientType, code);
        store.save(mobileDigest, purpose, clientType, codeDigest, properties.getCodeTtl());
        try {
            smsSender.send(mobile, purpose, code);
        } catch (RuntimeException exception) {
            store.remove(mobileDigest, purpose, clientType);
            throw exception;
        }
        return new IssuedParentSmsCode(
                clock.instant().plus(properties.getCodeTtl()), properties.getIssueRateWindow().toSeconds());
    }

    public void verifyAndConsume(
            String mobile,
            ParentSmsPurpose purpose,
            AuthClientType clientType,
            String code
    ) {
        validateMobile(mobile);
        validatePurposeAndClient(purpose, clientType);
        if (code == null || !code.matches("\\d{6}")) {
            throw new ParentSmsVerificationFailedException();
        }
        String mobileDigest = hasher.mobileDigest(mobile);
        store.checkVerificationRate(mobileDigest, purpose, clientType);
        String codeDigest = hasher.codeDigest(mobile, purpose, clientType, code);
        if (!store.consumeIfMatches(mobileDigest, purpose, clientType, codeDigest)) {
            throw new ParentSmsVerificationFailedException();
        }
    }

    private void validateMobile(String mobile) {
        if (mobile == null || !MAINLAND_MOBILE.matcher(mobile).matches()) {
            throw new IllegalArgumentException("手机号格式不正确");
        }
    }

    private void validatePurposeAndClient(ParentSmsPurpose purpose, AuthClientType clientType) {
        if (purpose == null) {
            throw new IllegalArgumentException("验证码用途不能为空");
        }
        if (clientType == null) {
            throw new IllegalArgumentException("客户端类型不能为空");
        }
    }
}
