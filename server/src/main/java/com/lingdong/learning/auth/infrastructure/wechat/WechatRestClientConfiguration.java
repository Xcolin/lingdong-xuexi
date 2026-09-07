package com.lingdong.learning.auth.infrastructure.wechat;

import com.lingdong.learning.auth.infrastructure.config.ParentWechatProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** 为微信服务端调用提供独立超时，避免第三方故障占满认证线程。 */
@Configuration
@Profile("!test")
public class WechatRestClientConfiguration {
    @Bean
    @Qualifier("wechatRestClient")
    RestClient wechatRestClient(RestClient.Builder builder, ParentWechatProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());
        return builder.requestFactory(requestFactory)
                .baseUrl("https://api.weixin.qq.com")
                .build();
    }
}
