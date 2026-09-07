package com.lingdong.learning.exportjob.infrastructure.config;

import com.lingdong.learning.exportjob.application.template.ExportTemplateParser;
import com.lingdong.learning.exportjob.application.template.ExportWorkbookWriter;
import com.lingdong.learning.exportjob.infrastructure.security.ExportSourceHasher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 装配无状态导出组件，并在启动时校验来源摘要密钥。 */
@Configuration
public class ExportJobConfiguration {
    @Bean
    ExportSourceHasher exportSourceHasher(ExportJobProperties properties) {
        return new ExportSourceHasher(properties.getSourceHmacSecret());
    }

    @Bean
    ExportTemplateParser exportTemplateParser() {
        return new ExportTemplateParser();
    }

    @Bean
    ExportWorkbookWriter exportWorkbookWriter() {
        return new ExportWorkbookWriter();
    }
}
