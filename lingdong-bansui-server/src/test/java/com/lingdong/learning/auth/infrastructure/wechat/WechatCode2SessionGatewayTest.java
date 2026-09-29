package com.lingdong.learning.auth.infrastructure.wechat;

import com.lingdong.learning.auth.application.ParentWechatIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class WechatCode2SessionGatewayTest {
    private MockRestServiceServer server;
    private WechatCode2SessionGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        WechatMiniappProperties properties = new WechatMiniappProperties();
        properties.setAppId("wx-test-app");
        properties.setAppSecret("test-secret-never-log");
        gateway = new WechatCode2SessionGateway(
                builder.baseUrl("https://api.weixin.qq.com").build(), properties);
    }

    @Test
    void exchangesTemporaryCodeForServerSideIdentity() {
        server.expect(once(), requestTo("https://api.weixin.qq.com/sns/jscode2session"
                        + "?appid=wx-test-app&secret=test-secret-never-log&js_code=temporary-code&grant_type=authorization_code"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"openid":"openid-1","unionid":"unionid-1","session_key":"session-key-1"}
                        """, MediaType.APPLICATION_JSON));

        ParentWechatIdentity identity = gateway.exchange("temporary-code");

        assertThat(identity.appId()).isEqualTo("wx-test-app");
        assertThat(identity.openId()).isEqualTo("openid-1");
        assertThat(identity.unionId()).isEqualTo("unionid-1");
        assertThat(identity.sessionKey()).isEqualTo("session-key-1");
        server.verify();
    }

    @Test
    void mapsWechatErrorToControlledFailureWithoutEchoingSensitiveValues() {
        server.expect(once(), requestTo("https://api.weixin.qq.com/sns/jscode2session"
                        + "?appid=wx-test-app&secret=test-secret-never-log&js_code=bad-code&grant_type=authorization_code"))
                .andRespond(withSuccess("{" + "\"errcode\":40029,\"errmsg\":\"invalid code\"}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.exchange("bad-code"))
                .isInstanceOf(WechatAuthenticationUnavailableException.class)
                .hasMessageNotContaining("bad-code")
                .hasMessageNotContaining("test-secret-never-log");
    }

    @Test
    void rejectsSuccessfulHttpResponseWhenRequiredIdentityFieldsAreMissing() {
        server.expect(once(), requestTo("https://api.weixin.qq.com/sns/jscode2session"
                        + "?appid=wx-test-app&secret=test-secret-never-log&js_code=incomplete-code&grant_type=authorization_code"))
                .andRespond(withSuccess("{\"session_key\":\"session-key-only\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.exchange("incomplete-code"))
                .isInstanceOf(WechatAuthenticationUnavailableException.class);
    }

    @Test
    void removesPotentiallySensitiveRequestDetailsFromNetworkFailureChain() {
        server.expect(once(), requestTo("https://api.weixin.qq.com/sns/jscode2session"
                        + "?appid=wx-test-app&secret=test-secret-never-log&js_code=network-code&grant_type=authorization_code"))
                .andRespond(request -> {
                    throw new ResourceAccessException(
                            "请求失败: secret=test-secret-never-log, js_code=network-code");
                });

        assertThatThrownBy(() -> gateway.exchange("network-code"))
                .isInstanceOf(WechatAuthenticationUnavailableException.class)
                .hasMessageNotContaining("network-code")
                .hasMessageNotContaining("test-secret-never-log")
                .hasNoCause();
    }
}
