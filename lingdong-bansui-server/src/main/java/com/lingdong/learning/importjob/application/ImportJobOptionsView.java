package com.lingdong.learning.importjob.application;

import java.util.List;

/** 可用导入模板和当前组织范围选项。 */
public record ImportJobOptionsView(
        List<ImportJobOptionView> templates,
        List<ImportJobOptionView> organizations
) { }
