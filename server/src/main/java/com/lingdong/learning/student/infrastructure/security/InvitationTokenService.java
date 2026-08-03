package com.lingdong.learning.student.infrastructure.security;

/** 为家长邀请生成并校验不可预测令牌摘要的边界。 */
public interface InvitationTokenService {
    String newToken();

    String hash(String token);
}
