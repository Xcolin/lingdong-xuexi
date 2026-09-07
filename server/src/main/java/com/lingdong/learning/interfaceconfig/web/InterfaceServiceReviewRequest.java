package com.lingdong.learning.interfaceconfig.web;

import jakarta.validation.constraints.Size;

/** 接口服务审核意见。驳回时由系统任务服务继续校验意见必填。 */
public record InterfaceServiceReviewRequest(
        @Size(max = 500) String comment
) { }
