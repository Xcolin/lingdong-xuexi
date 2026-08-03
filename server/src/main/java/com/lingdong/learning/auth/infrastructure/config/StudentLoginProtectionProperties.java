package com.lingdong.learning.auth.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** 学生预认证验证码、限流和锁定策略配置。 */
@ConfigurationProperties(prefix = "lingdong.auth.student-protection")
public class StudentLoginProtectionProperties {
    private Duration captchaTtl = Duration.ofMinutes(2);
    private int captchaRequestsPerWindow = 10;
    private Duration captchaRateWindow = Duration.ofMinutes(5);
    private int accountDeviceLoginRequestsPerWindow = 10;
    private int sourceLoginRequestsPerWindow = 60;
    private Duration loginRateWindow = Duration.ofMinutes(1);
    private Duration accountLockDuration = Duration.ofMinutes(15);

    public Duration getCaptchaTtl() { return captchaTtl; }
    public void setCaptchaTtl(Duration captchaTtl) { this.captchaTtl = captchaTtl; }
    public int getCaptchaRequestsPerWindow() { return captchaRequestsPerWindow; }
    public void setCaptchaRequestsPerWindow(int value) { this.captchaRequestsPerWindow = value; }
    public Duration getCaptchaRateWindow() { return captchaRateWindow; }
    public void setCaptchaRateWindow(Duration captchaRateWindow) { this.captchaRateWindow = captchaRateWindow; }
    public int getAccountDeviceLoginRequestsPerWindow() { return accountDeviceLoginRequestsPerWindow; }
    public void setAccountDeviceLoginRequestsPerWindow(int value) { this.accountDeviceLoginRequestsPerWindow = value; }
    public int getSourceLoginRequestsPerWindow() { return sourceLoginRequestsPerWindow; }
    public void setSourceLoginRequestsPerWindow(int value) { this.sourceLoginRequestsPerWindow = value; }
    public Duration getLoginRateWindow() { return loginRateWindow; }
    public void setLoginRateWindow(Duration loginRateWindow) { this.loginRateWindow = loginRateWindow; }
    public Duration getAccountLockDuration() { return accountLockDuration; }
    public void setAccountLockDuration(Duration accountLockDuration) { this.accountLockDuration = accountLockDuration; }
}
