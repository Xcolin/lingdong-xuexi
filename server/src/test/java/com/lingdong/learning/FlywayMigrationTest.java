package com.lingdong.learning;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class FlywayMigrationTest {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsSystemConfigurationTableThroughFlyway() {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_name = 'sys_config'",
                Integer.class);

        assertThat(count).isEqualTo(1);
    }

    @Test
    void createsSixBuiltInRolesThroughFlyway() {
        Integer builtInRoleCount = jdbcTemplate.queryForObject(
                "select count(*) from sys_role where role_type = 'BUILT_IN' and status = 'ENABLED'",
                Integer.class);
        String auditorName = jdbcTemplate.queryForObject(
                "select role_name from sys_role where role_code = 'SYS_AUDITOR'",
                String.class);

        assertThat(builtInRoleCount).isEqualTo(6);
        assertThat(auditorName).isEqualTo("系统审核员");
    }

    @Test
    void createsFiveBuiltInOrganizationTypesThroughFlyway() {
        Integer organizationTypeCount = jdbcTemplate.queryForObject(
                "select count(*) from sys_organization_type where built_in = 1 and status = 'ENABLED'",
                Integer.class);

        assertThat(organizationTypeCount).isEqualTo(5);
    }

    @Test
    void createsUserOrganizationRelationTableThroughFlyway() {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_name = 'sys_user_organization'",
                Integer.class);

        assertThat(count).isEqualTo(1);
    }

    @Test
    void createsSystemTaskAuditTableThroughFlyway() {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_name = 'sys_system_task'",
                Integer.class);

        assertThat(count).isEqualTo(1);
    }

    @Test
    void createsDictionaryTablesThroughFlyway() {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_name in ('sys_dictionary_type', 'sys_dictionary_item')",
                Integer.class);

        assertThat(count).isEqualTo(2);
    }

    @Test
    void createsCacheOperationTableThroughFlyway() {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_name = 'sys_cache_operation_log'",
                Integer.class);

        assertThat(count).isEqualTo(1);
    }

    @Test
    void createsInterfaceServiceTablesWithSnowflakeIdsAndUniqueChangeTasksThroughFlyway() {
        Integer tableCount = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.tables
                where table_name in ('sys_interface_service', 'sys_interface_service_change', 'sys_interface_call_log')
                """, Integer.class);
        Integer idColumnCount = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.columns
                where table_name in ('sys_interface_service', 'sys_interface_service_change', 'sys_interface_call_log')
                  and column_name = 'id'
                  and upper(data_type) = 'BIGINT'
                  and is_identity = 'NO'
                """, Integer.class);
        Integer uniqueTaskConstraintCount = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.table_constraints constraints
                join information_schema.key_column_usage key_columns
                  on constraints.constraint_catalog = key_columns.constraint_catalog
                 and constraints.constraint_schema = key_columns.constraint_schema
                 and constraints.constraint_name = key_columns.constraint_name
                where constraints.table_name = 'sys_interface_service_change'
                  and constraints.constraint_type = 'UNIQUE'
                  and key_columns.column_name = 'task_id'
                """, Integer.class);
        Integer callLogColumnCount = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.columns
                where table_name = 'sys_interface_call_log'
                  and column_name in ('service_id', 'result', 'error_summary', 'trace_id')
                """, Integer.class);

        assertThat(tableCount).isEqualTo(3);
        assertThat(idColumnCount).isEqualTo(3);
        assertThat(uniqueTaskConstraintCount).isEqualTo(1);
        assertThat(callLogColumnCount).isEqualTo(4);
    }

    @Test
    void createsAttachmentCoreTablesWithSnowflakeIdsAndUniqueRuleExtensionsThroughFlyway() {
        Integer tableCount = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.tables
                where table_name in ('sys_attachment_rule', 'sys_attachment_rule_extension', 'sys_file', 'sys_file_relation')
                """, Integer.class);
        Integer idColumnCount = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.columns
                where table_name in ('sys_attachment_rule', 'sys_attachment_rule_extension', 'sys_file', 'sys_file_relation')
                  and column_name = 'id'
                  and upper(data_type) = 'BIGINT'
                  and is_identity = 'NO'
                """, Integer.class);
        Integer ruleKeyConstraintCount = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.table_constraints constraints
                join information_schema.key_column_usage key_columns
                  on constraints.constraint_catalog = key_columns.constraint_catalog
                 and constraints.constraint_schema = key_columns.constraint_schema
                 and constraints.constraint_name = key_columns.constraint_name
                where constraints.table_name = 'sys_attachment_rule'
                  and constraints.constraint_type = 'UNIQUE'
                  and key_columns.column_name in ('module_code', 'file_category')
                """, Integer.class);
        Integer relationColumnCount = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.columns
                where table_name = 'sys_file_relation'
                  and column_name in ('file_id', 'module_code', 'business_id', 'relation_type', 'visible_scope', 'status')
                """, Integer.class);

        assertThat(tableCount).isEqualTo(4);
        assertThat(idColumnCount).isEqualTo(4);
        assertThat(ruleKeyConstraintCount).isEqualTo(2);
        assertThat(relationColumnCount).isEqualTo(6);
    }

    @Test
    void createsImportExportTemplateTableWithSnowflakeIdAndDefaultScopeUniquenessThroughFlyway() {
        Integer tableCount = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_name = 'sys_import_export_template'",
                Integer.class);
        Integer idColumnCount = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.columns
                where table_name = 'sys_import_export_template'
                  and column_name = 'id'
                  and upper(data_type) = 'BIGINT'
                  and is_identity = 'NO'
                """, Integer.class);
        Integer uniqueKeyColumnCount = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.table_constraints constraints
                join information_schema.key_column_usage key_columns
                  on constraints.constraint_catalog = key_columns.constraint_catalog
                 and constraints.constraint_schema = key_columns.constraint_schema
                 and constraints.constraint_name = key_columns.constraint_name
                where constraints.table_name = 'sys_import_export_template'
                  and constraints.constraint_type = 'UNIQUE'
                  and constraints.constraint_name = 'uk_sys_template_default_scope'
                  and key_columns.column_name in ('module_code', 'template_type', 'default_scope_key')
                """, Integer.class);

        assertThat(tableCount).isEqualTo(1);
        assertThat(idColumnCount).isEqualTo(1);
        assertThat(uniqueKeyColumnCount).isEqualTo(3);
    }

    @Test
    void createsDeviceSessionTableWithSnowflakeIdAndTokenConstraintsThroughFlyway() {
        Integer tableCount = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_name = 'auth_device_session'",
                Integer.class);
        Integer idColumnCount = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.columns
                where table_name = 'auth_device_session'
                  and column_name = 'id'
                  and upper(data_type) = 'BIGINT'
                  and is_identity = 'NO'
                """, Integer.class);
        Integer tokenConstraintCount = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.table_constraints
                where table_name = 'auth_device_session'
                  and constraint_type = 'UNIQUE'
                  and constraint_name in ('uk_auth_session_access_token_hash', 'uk_auth_session_refresh_token_hash')
                """, Integer.class);
        Integer userStatusIndexCount = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.indexes
                where table_name = 'auth_device_session'
                  and index_name = 'idx_auth_session_user_status'
                """, Integer.class);

        assertThat(tableCount).isEqualTo(1);
        assertThat(idColumnCount).isEqualTo(1);
        assertThat(tokenConstraintCount).isEqualTo(2);
        assertThat(userStatusIndexCount).isEqualTo(1);
    }

    @Test
    void seedsWebIamManagementPermissionsForSystemAdministrators() {
        Integer permissionCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_permission
                where permission_code like 'IAM_%'
                  and resource_type = 'OPERATION'
                  and client_type = 'WEB'
                  and status = 'ENABLED'
                """, Integer.class);
        Integer systemAdministratorGrantCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_role_permission role_permission
                join sys_role role on role.id = role_permission.role_id
                join sys_permission permission on permission.id = role_permission.permission_id
                where role.role_code = 'SYS_ADMIN'
                  and permission.permission_code like 'IAM_%'
                """, Integer.class);
        Integer snowflakePermissionIdCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_permission
                where permission_code like 'IAM_%'
                  and id >= 1000000000000000000
                """, Integer.class);

        assertThat(permissionCount).isEqualTo(14);
        assertThat(systemAdministratorGrantCount).isEqualTo(14);
        assertThat(snowflakePermissionIdCount).isEqualTo(14);
    }

    @Test
    void seedsWebOrganizationManagementPermissionsForSystemAdministrators() {
        Integer permissionCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_permission
                where permission_code in ('ORG_TYPE_READ', 'ORG_TYPE_CREATE', 'ORG_NODE_READ', 'ORG_NODE_CREATE')
                  and resource_type = 'OPERATION'
                  and client_type = 'WEB'
                  and status = 'ENABLED'
                """, Integer.class);
        Integer systemAdministratorGrantCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_role_permission role_permission
                join sys_role role on role.id = role_permission.role_id
                join sys_permission permission on permission.id = role_permission.permission_id
                where role.role_code = 'SYS_ADMIN'
                  and permission.permission_code in ('ORG_TYPE_READ', 'ORG_TYPE_CREATE', 'ORG_NODE_READ', 'ORG_NODE_CREATE')
                  and role_permission.id >= 1000000000000000000
                """, Integer.class);
        Integer snowflakePermissionIdCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_permission
                where permission_code in ('ORG_TYPE_READ', 'ORG_TYPE_CREATE', 'ORG_NODE_READ', 'ORG_NODE_CREATE')
                  and id >= 1000000000000000000
                """, Integer.class);

        assertThat(permissionCount).isEqualTo(4);
        assertThat(systemAdministratorGrantCount).isEqualTo(4);
        assertThat(snowflakePermissionIdCount).isEqualTo(4);
    }

    @Test
    void createsStudentRelationshipTablesAndScopedPermissionsThroughFlyway() {
        Integer tableCount = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.tables
                where table_name in ('edu_student', 'edu_parent_student', 'edu_student_organization')
                """, Integer.class);
        Integer idColumnCount = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.columns
                where table_name in ('edu_student', 'edu_parent_student', 'edu_student_organization')
                  and column_name = 'id'
                  and upper(data_type) = 'BIGINT'
                  and is_identity = 'NO'
                """, Integer.class);
        Integer readGrantCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_role_permission role_permission
                join sys_permission permission on permission.id = role_permission.permission_id
                where permission.permission_code = 'STUDENT_READ'
                """, Integer.class);
        Integer createGrantCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_role_permission role_permission
                join sys_permission permission on permission.id = role_permission.permission_id
                where permission.permission_code = 'STUDENT_CREATE'
                """, Integer.class);

        assertThat(tableCount).isEqualTo(3);
        assertThat(idColumnCount).isEqualTo(3);
        assertThat(readGrantCount).isEqualTo(3);
        assertThat(createGrantCount).isEqualTo(2);
    }

    @Test
    void createsParentBindingInvitationTableAndScopedPermissionsThroughFlyway() {
        Integer tableCount = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_name = 'edu_parent_binding_invitation'",
                Integer.class);
        Integer idColumnCount = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.columns
                where table_name = 'edu_parent_binding_invitation'
                  and column_name = 'id'
                  and upper(data_type) = 'BIGINT'
                  and is_identity = 'NO'
                """, Integer.class);
        Integer uniqueConstraintCount = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.table_constraints
                where table_name = 'edu_parent_binding_invitation'
                  and constraint_type = 'UNIQUE'
                  and constraint_name in ('uk_edu_parent_invitation_token', 'uk_edu_parent_invitation_pending')
                """, Integer.class);
        Integer grantCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_role_permission role_permission
                join sys_permission permission on permission.id = role_permission.permission_id
                where permission.permission_code in ('STUDENT_PARENT_INVITE_CREATE', 'STUDENT_PARENT_INVITE_RESPOND')
                """, Integer.class);

        assertThat(tableCount).isEqualTo(1);
        assertThat(idColumnCount).isEqualTo(1);
        assertThat(uniqueConstraintCount).isEqualTo(2);
        assertThat(grantCount).isEqualTo(2);
    }

    @Test
    void createsStudentCodeLoginTablesPermissionsAndFeatureThroughFlyway() {
        Integer tableCount = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.tables
                where table_name in ('auth_student_account_sequence', 'auth_student_credential')
                """, Integer.class);
        Integer idColumnCount = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.columns
                where table_name in ('auth_student_account_sequence', 'auth_student_credential')
                  and column_name = 'id'
                  and upper(data_type) = 'BIGINT'
                  and is_identity = 'NO'
                """, Integer.class);
        Integer uniqueConstraintCount = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.table_constraints
                where constraint_type = 'UNIQUE'
                  and constraint_name in ('uk_auth_student_account_sequence_year',
                      'uk_auth_student_credential_user')
                """, Integer.class);
        Integer credentialForeignKeyCount = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.table_constraints
                where table_name = 'auth_student_credential'
                  and constraint_type = 'FOREIGN KEY'
                  and constraint_name = 'fk_auth_student_credential_user'
                """, Integer.class);
        Integer permissionCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_permission
                where permission_code in ('STUDENT_CREDENTIAL_INITIALIZE', 'STUDENT_LOGIN_CODE_RESET')
                """, Integer.class);
        Integer roleGrantCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_role_permission role_permission
                join sys_role role on role.id = role_permission.role_id
                join sys_permission permission on permission.id = role_permission.permission_id
                where role.role_code in ('PARENT', 'ORG_ADMIN')
                  and permission.permission_code in ('STUDENT_CREDENTIAL_INITIALIZE', 'STUDENT_LOGIN_CODE_RESET')
                """, Integer.class);
        Integer featureCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_feature_toggle
                where feature_code = 'STUDENT_CODE_LOGIN'
                  and scope_key = 'GLOBAL'
                  and status = 'ENABLED'
                """, Integer.class);

        assertThat(tableCount).isEqualTo(2);
        assertThat(idColumnCount).isEqualTo(2);
        assertThat(uniqueConstraintCount).isEqualTo(2);
        assertThat(credentialForeignKeyCount).isEqualTo(1);
        assertThat(permissionCount).isEqualTo(2);
        assertThat(roleGrantCount).isEqualTo(4);
        assertThat(featureCount).isEqualTo(1);
    }

    @Test
    void createsLearningTaskFoundationWithScopedPermissionsAndConfigurationThroughFlyway() {
        Integer tableCount = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.tables
                where table_name in ('edu_teacher_class', 'learn_task', 'learn_task_target',
                    'learn_task_tag', 'learn_task_assignment')
                """, Integer.class);
        Integer idColumnCount = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.columns
                where table_name in ('edu_teacher_class', 'learn_task', 'learn_task_target',
                    'learn_task_tag', 'learn_task_assignment')
                  and column_name = 'id'
                  and upper(data_type) = 'BIGINT'
                  and is_identity = 'NO'
                """, Integer.class);
        Integer uniqueConstraintCount = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.table_constraints
                where constraint_type = 'UNIQUE'
                  and constraint_name in ('uk_edu_teacher_class_pair', 'uk_learn_task_target',
                      'uk_learn_task_tag', 'uk_learn_task_assignment_task_student')
                """, Integer.class);
        Integer foreignKeyCount = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.table_constraints
                where constraint_type = 'FOREIGN KEY'
                  and constraint_name in ('fk_edu_teacher_class_teacher', 'fk_edu_teacher_class_class',
                      'fk_learn_task_source_organization', 'fk_learn_task_creator', 'fk_learn_task_reviewer',
                      'fk_learn_task_target_task', 'fk_learn_task_tag_task',
                      'fk_learn_task_assignment_task', 'fk_learn_task_assignment_student',
                      'fk_learn_task_assignment_source_organization', 'fk_learn_task_assignment_reviewer')
                """, Integer.class);
        Integer dictionaryTypeCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_dictionary_type
                where type_code in ('TASK_CATEGORY', 'TASK_TAG')
                  and status = 'ENABLED'
                """, Integer.class);
        Integer dictionaryItemCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_dictionary_item item
                join sys_dictionary_type type on type.id = item.type_id
                where type.type_code in ('TASK_CATEGORY', 'TASK_TAG')
                  and item.status = 'ENABLED'
                """, Integer.class);
        Integer featureCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_feature_toggle
                where feature_code = 'LEARNING_TASK_MANAGEMENT'
                  and scope_key = 'GLOBAL'
                  and status = 'ENABLED'
                """, Integer.class);
        Integer permissionCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_permission
                where permission_code in ('STUDENT_CLASS_ASSIGN', 'TEACHER_CLASS_ASSIGN',
                    'LEARNING_TASK_CREATE', 'LEARNING_TASK_READ_MANAGED',
                    'LEARNING_TASK_PUBLISH', 'TASK_ASSIGNMENT_READ_SELF')
                  and status = 'ENABLED'
                """, Integer.class);
        Integer roleGrantCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_role_permission role_permission
                join sys_role role on role.id = role_permission.role_id
                join sys_permission permission on permission.id = role_permission.permission_id
                where (role.role_code = 'ORG_ADMIN'
                        and permission.permission_code in ('STUDENT_CLASS_ASSIGN', 'TEACHER_CLASS_ASSIGN',
                            'LEARNING_TASK_CREATE', 'LEARNING_TASK_READ_MANAGED', 'LEARNING_TASK_PUBLISH'))
                   or (role.role_code in ('PARENT', 'TEACHER')
                        and permission.permission_code in ('LEARNING_TASK_CREATE',
                            'LEARNING_TASK_READ_MANAGED', 'LEARNING_TASK_PUBLISH'))
                   or (role.role_code = 'STUDENT'
                        and permission.permission_code = 'TASK_ASSIGNMENT_READ_SELF')
                """, Integer.class);

        assertThat(tableCount).isEqualTo(5);
        assertThat(idColumnCount).isEqualTo(5);
        assertThat(uniqueConstraintCount).isEqualTo(4);
        assertThat(foreignKeyCount).isEqualTo(11);
        assertThat(dictionaryTypeCount).isEqualTo(2);
        assertThat(dictionaryItemCount).isGreaterThanOrEqualTo(2);
        assertThat(featureCount).isEqualTo(1);
        assertThat(permissionCount).isEqualTo(6);
        assertThat(roleGrantCount).isEqualTo(12);
    }

    @Test
    void createsTaskExecutionHistoryAndPermissionsThroughFlyway() {
        Integer migrationCount = jdbcTemplate.queryForObject("""
                select count(*) from flyway_schema_history
                where version = '23' and success = true
                """, Integer.class);
        Integer tableCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.tables
                where table_name in ('learn_task_assignment_event', 'learn_task_pause',
                    'learn_task_checkin', 'learn_task_reviewer_transfer')
                """, Integer.class);
        Integer idColumnCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where table_name in ('learn_task_assignment_event', 'learn_task_pause',
                    'learn_task_checkin', 'learn_task_reviewer_transfer')
                  and column_name = 'id'
                  and upper(data_type) = 'BIGINT'
                  and is_identity = 'NO'
                """, Integer.class);
        Integer assignmentVersionColumnCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where table_name = 'learn_task_assignment'
                  and column_name in ('last_transition_at', 'version_no')
                """, Integer.class);
        Integer permissionCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_permission
                where permission_code in ('TASK_ASSIGNMENT_EXECUTE_SELF',
                    'TASK_ASSIGNMENT_REVIEW', 'TASK_ASSIGNMENT_EXEMPT')
                  and status = 'ENABLED'
                """, Integer.class);
        Integer roleGrantCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_role_permission role_permission
                join sys_role role on role.id = role_permission.role_id
                join sys_permission permission on permission.id = role_permission.permission_id
                where (role.role_code = 'STUDENT'
                        and permission.permission_code = 'TASK_ASSIGNMENT_EXECUTE_SELF')
                   or (role.role_code in ('PARENT', 'ORG_ADMIN', 'TEACHER')
                        and permission.permission_code in ('TASK_ASSIGNMENT_REVIEW',
                            'TASK_ASSIGNMENT_EXEMPT'))
                """, Integer.class);

        assertThat(migrationCount).isEqualTo(1);
        assertThat(tableCount).isEqualTo(4);
        assertThat(idColumnCount).isEqualTo(4);
        assertThat(assignmentVersionColumnCount).isEqualTo(2);
        assertThat(permissionCount).isEqualTo(3);
        assertThat(roleGrantCount).isEqualTo(7);
    }

    @Test
    void createsEveryCurrentTableWithAnExplicitNonIdentityBigintPrimaryId() {
        Integer idColumnCount = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.columns
                where table_name in ('sys_config', 'sys_organization', 'sys_user', 'sys_role', 'sys_permission',
                    'sys_user_role', 'sys_role_permission', 'sys_organization_admin', 'sys_organization_type',
                    'sys_user_organization', 'sys_system_task', 'sys_feature_toggle', 'sys_feature_toggle_change',
                    'sys_user_permission', 'sys_role_data_scope', 'sys_dictionary_type', 'sys_dictionary_item',
                    'sys_cache_operation_log', 'edu_student', 'edu_parent_student', 'edu_student_organization',
                    'edu_parent_binding_invitation', 'auth_student_account_sequence', 'auth_student_credential',
                    'edu_teacher_class', 'learn_task', 'learn_task_target', 'learn_task_tag',
                    'learn_task_assignment', 'learn_task_assignment_event', 'learn_task_pause',
                    'learn_task_checkin', 'learn_task_reviewer_transfer')
                  and column_name = 'id'
                  and upper(data_type) = 'BIGINT'
                  and is_identity = 'NO'
                """, Integer.class);

        assertThat(idColumnCount).isEqualTo(33);
    }

    @Test
    void seedsBuiltInDataWithNineteenDigitSnowflakeIds() {
        Integer roleCount = jdbcTemplate.queryForObject(
                "select count(*) from sys_role where role_code in ('SYS_ADMIN', 'SYS_AUDITOR', 'ORG_ADMIN', 'TEACHER', 'PARENT', 'STUDENT') and id >= 1000000000000000000",
                Integer.class);
        Integer organizationTypeCount = jdbcTemplate.queryForObject(
                "select count(*) from sys_organization_type where type_code in ('REGION', 'SCHOOL', 'CAMPUS', 'GRADE', 'CLASS') and id >= 1000000000000000000",
                Integer.class);
        Integer featureToggleCount = jdbcTemplate.queryForObject(
                "select count(*) from sys_feature_toggle where feature_code in ('GEO_ATTENDANCE', 'STUDENT_LOCATION_TRACK') and scope_key = 'GLOBAL' and id >= 1000000000000000000",
                Integer.class);

        assertThat(roleCount).isEqualTo(6);
        assertThat(organizationTypeCount).isEqualTo(5);
        assertThat(featureToggleCount).isEqualTo(2);
    }
}
