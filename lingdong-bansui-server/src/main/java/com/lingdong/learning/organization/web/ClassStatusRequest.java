package com.lingdong.learning.organization.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 班级启停的乐观锁版本请求。 */
public record ClassStatusRequest(
        @NotNull @Positive Integer versionNo
) {
}
