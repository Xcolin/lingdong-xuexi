package com.lingdong.learning.learningtask.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.learningtask.application.ReviewerOption;

/** 服务端裁剪后的审核候选用户响应。 */
public record ReviewerOptionResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long userId,
        String displayName
) {
    static ReviewerOptionResponse from(ReviewerOption option) {
        return new ReviewerOptionResponse(option.userId(), option.displayName());
    }
}
