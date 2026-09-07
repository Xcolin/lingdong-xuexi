package com.lingdong.learning.importjob.web;

import com.lingdong.learning.importjob.application.ImportJobOptionView;
import com.lingdong.learning.importjob.application.ImportJobOptionsView;

import java.util.List;

/** 导入校验页面选项响应。 */
public record ImportJobOptionsResponse(
        List<ImportJobOptionView> templates,
        List<ImportJobOptionView> organizations
) {
    public static ImportJobOptionsResponse from(ImportJobOptionsView options) {
        return new ImportJobOptionsResponse(options.templates(), options.organizations());
    }
}
