package com.lingdong.learning.auth.application;

import java.time.Duration;

/** 学生预认证风控的可替换存储边界。 */
public interface StudentLoginProtectionStore {
    void checkLoginRate(String accountDeviceDigest, String sourceDigest);

    void checkCaptchaIssueRate(String accountDeviceDigest);

    void saveCaptcha(String challengeId, StoredCaptchaChallenge challenge, Duration ttl);

    StoredCaptchaChallenge consumeCaptcha(String challengeId);
}
