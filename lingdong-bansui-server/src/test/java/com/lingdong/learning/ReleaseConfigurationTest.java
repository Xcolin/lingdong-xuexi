package com.lingdong.learning;

import com.lingdong.learning.auth.infrastructure.config.StudentLoginCodeProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

/** 只加载配置，不创建数据源、Redis 或业务调度器。 */
class ReleaseConfigurationTest {
    @TempDir Path directory;
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer());

    @Test void doesNotLoadLocalCredentialsAndFailsWithoutRequiredKey() {
        runner.withUserConfiguration(Keys.class).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseMessage("学生登录码密钥至少32字节：v1");
        });
    }

    @Test void missingExplicitExternalFileFailsBeforeAnyInfrastructureStarts() {
        runner.withPropertyValues("spring.config.additional-location=" + directory.resolve("missing.yml").toUri())
                .run(context -> assertThat(context).hasFailed());
    }

    @Test void explicitExternalFileIsLoaded() throws Exception {
        Path config = directory.resolve("external.yml");
        Files.writeString(config, "release-check:\n  marker: external-file-loaded\n");
        runner.withPropertyValues("spring.config.additional-location=" + config.toUri()).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getEnvironment().getProperty("release-check.marker")).isEqualTo("external-file-loaded");
        });
    }

    @Test void testProfileUsesOnlyEmbeddedDatabaseConfiguration() {
        runner.withUserConfiguration(Keys.class).withPropertyValues("spring.profiles.active=test").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getEnvironment().getProperty("spring.datasource.url")).startsWith("jdbc:h2:mem:");
            assertThat(context.getEnvironment().getProperty("spring.datasource.driver-class-name")).isEqualTo("org.h2.Driver");
            assertThat(context.getEnvironment().getProperty("spring.config.import")).isNull();
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(StudentLoginCodeProperties.class)
    static class Keys { }
}
