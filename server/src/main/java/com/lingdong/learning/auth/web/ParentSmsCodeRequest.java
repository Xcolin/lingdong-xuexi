package com.lingdong.learning.auth.web;

import com.lingdong.learning.auth.application.ParentSmsPurpose;
import com.lingdong.learning.auth.domain.AuthClientType;

/** 家长短信验证码发送请求。 */
public record ParentSmsCodeRequest(String mobile, ParentSmsPurpose purpose, AuthClientType clientType) {
}
