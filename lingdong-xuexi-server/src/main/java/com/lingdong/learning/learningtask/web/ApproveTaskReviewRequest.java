package com.lingdong.learning.learningtask.web;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 审核操作绑定用户实际查看的打卡；旧客户端缺失标识时拒绝操作。 */
public record ApproveTaskReviewRequest(
        @NotBlank @Pattern(regexp = "[1-9][0-9]{18}")
        @JsonDeserialize(using = ReviewCheckInIdDeserializer.class) String expectedCheckInId
) {
    Long checkInId() { return Long.valueOf(expectedCheckInId); }
}
