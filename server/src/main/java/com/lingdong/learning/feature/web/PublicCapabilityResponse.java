package com.lingdong.learning.feature.web;

/** 客户端启动前可读取的非敏感功能能力摘要。 */
public record PublicCapabilityResponse(
        String client,
        boolean studentCodeLoginEnabled,
        boolean learningTaskManagementEnabled
) {
}
