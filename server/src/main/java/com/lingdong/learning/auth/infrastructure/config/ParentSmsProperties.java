package com.lingdong.learning.auth.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** 家长短信验证码的有效期、频控与摘要密钥配置。 */
@ConfigurationProperties(prefix = "lingdong.auth.parent-sms")
public class ParentSmsProperties {
    private Duration codeTtl = Duration.ofMinutes(5);
    private Duration issueRateWindow = Duration.ofMinutes(1);
    private int issuesPerWindow = 3;
    private int sourceIssuesPerWindow = 30;
    private Duration verificationRateWindow = Duration.ofMinutes(5);
    private int verificationsPerWindow = 10;
    private Duration mobileChangeTicketTtl = Duration.ofMinutes(5);
    private String hmacSecret = "";

    public Duration getCodeTtl() {
        return codeTtl;
    }

    public void setCodeTtl(Duration codeTtl) {
        this.codeTtl = codeTtl;
    }

    public Duration getIssueRateWindow() {
        return issueRateWindow;
    }

    public void setIssueRateWindow(Duration issueRateWindow) {
        this.issueRateWindow = issueRateWindow;
    }

    public int getIssuesPerWindow() {
        return issuesPerWindow;
    }

    public void setIssuesPerWindow(int issuesPerWindow) {
        this.issuesPerWindow = issuesPerWindow;
    }

    public int getSourceIssuesPerWindow() {
        return sourceIssuesPerWindow;
    }

    public void setSourceIssuesPerWindow(int sourceIssuesPerWindow) {
        this.sourceIssuesPerWindow = sourceIssuesPerWindow;
    }

    public Duration getVerificationRateWindow() {
        return verificationRateWindow;
    }

    public void setVerificationRateWindow(Duration verificationRateWindow) {
        this.verificationRateWindow = verificationRateWindow;
    }

    public int getVerificationsPerWindow() {
        return verificationsPerWindow;
    }

    public void setVerificationsPerWindow(int verificationsPerWindow) {
        this.verificationsPerWindow = verificationsPerWindow;
    }

    public Duration getMobileChangeTicketTtl() {
        return mobileChangeTicketTtl;
    }

    public void setMobileChangeTicketTtl(Duration mobileChangeTicketTtl) {
        this.mobileChangeTicketTtl = mobileChangeTicketTtl;
    }

    public String getHmacSecret() {
        return hmacSecret;
    }

    public void setHmacSecret(String hmacSecret) {
        this.hmacSecret = hmacSecret;
    }
}
