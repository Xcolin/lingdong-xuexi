package com.lingdong.learning.interfaceconfig.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 接口服务启用或停用任务的提交请求。 */
public record CreateInterfaceServiceStatusSubmissionRequest(
        @NotBlank @Size(max = 100) String title,
        @NotBlank @Size(max = 1000) String description
) { }
