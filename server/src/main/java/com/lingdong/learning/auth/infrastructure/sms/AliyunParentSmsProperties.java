package com.lingdong.learning.auth.infrastructure.sms;

import com.lingdong.learning.auth.application.ParentSmsPurpose;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

/** 仅外置供应商配置；不提供任何可用默认凭据。 */
@ConfigurationProperties(prefix = "lingdong.auth.parent-sms.aliyun")
public class AliyunParentSmsProperties {
    private String endpoint = "https://dysmsapi.aliyuncs.com";
    private String accessKeyId = "";
    private String accessKeySecret = "";
    private String signName = "";
    private String templateCode = "";
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration readTimeout = Duration.ofSeconds(3);
    private Map<ParentSmsPurpose, String> purposeTemplates = new EnumMap<>(ParentSmsPurpose.class);

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String value) { endpoint = value; }
    public String getAccessKeyId() { return accessKeyId; }
    public void setAccessKeyId(String value) { accessKeyId = value; }
    public String getAccessKeySecret() { return accessKeySecret; }
    public void setAccessKeySecret(String value) { accessKeySecret = value; }
    public String getSignName() { return signName; }
    public void setSignName(String value) { signName = value; }
    public String getTemplateCode() { return templateCode; }
    public void setTemplateCode(String value) { templateCode = value; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration value) { connectTimeout = value; }
    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration value) { readTimeout = value; }
    public Map<ParentSmsPurpose, String> getPurposeTemplates() { return purposeTemplates; }
    public void setPurposeTemplates(Map<ParentSmsPurpose, String> value) { purposeTemplates = value; }
}
