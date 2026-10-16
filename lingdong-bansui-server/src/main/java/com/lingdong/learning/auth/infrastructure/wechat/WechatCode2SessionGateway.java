package com.lingdong.learning.auth.infrastructure.wechat;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lingdong.learning.auth.application.ParentWechatIdentity;
import com.lingdong.learning.auth.application.ParentWechatIdentityGateway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** 调用微信 code2Session，仅向业务层返回经过校验的身份值对象。 */
@Component
@Profile("!test")
public class WechatCode2SessionGateway implements ParentWechatIdentityGateway {
    private final RestClient restClient;
    private final WechatMiniappProperties properties;

    public WechatCode2SessionGateway(
            @Qualifier("wechatRestClient") RestClient restClient,
            WechatMiniappProperties properties
    ) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public ParentWechatIdentity exchange(String temporaryCode) {
        if (!properties.isComplete() || temporaryCode == null || temporaryCode.isBlank()
                || temporaryCode.length() > 128) {
            throw new WechatAuthenticationUnavailableException();
        }
        try {
            String payload = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/sns/jscode2session")
                            .queryParam("appid", properties.getAppId())
                            .queryParam("secret", properties.getAppSecret())
                            .queryParam("js_code", temporaryCode)
                            .queryParam("grant_type", "authorization_code")
                            .build())
                    .retrieve()
                    .body(String.class);
            Code2SessionResponse response = payload == null ? null : new com.fasterxml.jackson.databind.ObjectMapper().readValue(payload, Code2SessionResponse.class);
            if (response == null || (response.errcode() != null && response.errcode() != 0) || isBlank(response.openId())
                    || isBlank(response.sessionKey())) {
                throw new WechatAuthenticationUnavailableException();
            }
            return new ParentWechatIdentity(
                    properties.getAppId(), response.openId(), response.unionId(), response.sessionKey());
        } catch (WechatAuthenticationUnavailableException exception) {
            throw exception;
        } catch (RestClientException | com.fasterxml.jackson.core.JsonProcessingException exception) {
            // 第三方异常可能包含带密钥和临时凭证的完整请求 URI，不保留原始异常链。
            throw new WechatAuthenticationUnavailableException();
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private record Code2SessionResponse(
            @JsonProperty("openid") String openId,
            @JsonProperty("unionid") String unionId,
            @JsonProperty("session_key") String sessionKey,
            @JsonProperty("errcode") Integer errcode
    ) {
    }
}

