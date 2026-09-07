package com.lingdong.learning.templateconfig.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** 模板启停和默认切换使用的乐观锁版本请求。 */
public record ImportExportTemplateVersionRequest(
        @NotNull @PositiveOrZero Long versionNo
) { }
