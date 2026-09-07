package com.lingdong.learning.organization.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class OrganizationChangePersistenceTest {
    private static final Set<String> EXPECTED_REFERENCES = Set.of(
            "SYS_ORGANIZATION.PARENT_ID",
            "SYS_USER_ROLE.ORGANIZATION_ID",
            "SYS_ORGANIZATION_ADMIN.ORGANIZATION_ID",
            "SYS_USER_ORGANIZATION.ORGANIZATION_ID",
            "SYS_FEATURE_TOGGLE.ORGANIZATION_ID",
            "SYS_ROLE_DATA_SCOPE.ORGANIZATION_ID",
            "SYS_IMPORT_JOB.ORGANIZATION_ID",
            "SYS_STUDENT_IMPORT_EXECUTION.ORGANIZATION_ID",
            "SYS_STUDENT_IMPORT_EXECUTION.CLASS_ORGANIZATION_ID",
            "EDU_STUDENT_ORGANIZATION.ORGANIZATION_ID",
            "EDU_PARENT_BINDING_INVITATION.ORGANIZATION_ID",
            "EDU_TEACHER_CLASS.CLASS_ORGANIZATION_ID",
            "EDU_TEACHER_CLASS_CHANGE_LOG.CLASS_ORGANIZATION_ID",
            "LEARN_TASK.SOURCE_ORGANIZATION_ID",
            "LEARN_TASK_ASSIGNMENT.SOURCE_ORGANIZATION_ID",
            "GROWTH_POINT_LEDGER.SOURCE_ORGANIZATION_ID",
            "EDU_STUDENT_ORGANIZATION_CHANGE.FROM_ORGANIZATION_ID",
            "EDU_STUDENT_ORGANIZATION_CHANGE.TO_ORGANIZATION_ID",
            "AUTH_PARENT_MOBILE_MANUAL_RECOVERY.ORGANIZATION_ID",
            "AUTH_STUDENT_ACCOUNT_CANCELLATION.ORGANIZATION_ID",
            "EDU_EXCEPTION_REPORT.CLASS_ORGANIZATION_ID",
            "ATTENDANCE_RECORD.CLASS_ORGANIZATION_ID"
    );

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void explicitDeleteGuardCoversEveryOrganizationForeignKey() throws Exception {
        Set<String> exportedKeys = jdbcTemplate.execute((ConnectionCallback<Set<String>>) connection -> {
            Set<String> references = new LinkedHashSet<>();
            try (ResultSet resultSet = connection.getMetaData()
                    .getExportedKeys(null, null, "sys_organization")) {
                while (resultSet.next()) {
                    references.add((resultSet.getString("FKTABLE_NAME")
                            + "." + resultSet.getString("FKCOLUMN_NAME")).toUpperCase());
                }
            }
            return references;
        });
        assertThat(exportedKeys).containsExactlyInAnyOrderElementsOf(EXPECTED_REFERENCES);

        String mapperXml;
        try (var inputStream = new ClassPathResource(
                "mapper/organization/OrganizationMapper.xml").getInputStream()) {
            mapperXml = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).toUpperCase();
        }
        for (String reference : EXPECTED_REFERENCES) {
            String[] parts = reference.split("\\.");
            assertThat(mapperXml).contains("FROM " + parts[0]);
            assertThat(mapperXml).contains(parts[1]);
        }
    }
}
