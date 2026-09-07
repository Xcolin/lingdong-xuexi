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

        assertThat(permissionCount).isEqualTo(15);
        assertThat(systemAdministratorGrantCount).isEqualTo(15);
        assertThat(snowflakePermissionIdCount).isEqualTo(15);
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
                      'uk_learn_task_tag', 'uk_learn_task_assignment_task_student_date')
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
    void createsGrowthPointAccountAndImmutableTaskRewardLedgerThroughFlyway() {
        Integer migrationCount = jdbcTemplate.queryForObject("""
                select count(*) from flyway_schema_history
                where version = '24' and success = true
                """, Integer.class);
        Integer tableCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.tables
                where table_name in ('growth_point_account', 'growth_point_ledger')
                """, Integer.class);
        Integer idColumnCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where table_name in ('growth_point_account', 'growth_point_ledger')
                  and column_name = 'id'
                  and upper(data_type) = 'BIGINT'
                  and is_identity = 'NO'
                """, Integer.class);
        Integer approvalConstraintCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.table_constraints
                where constraint_type = 'CHECK'
                  and constraint_name in ('ck_task_checkin_status',
                      'ck_task_assignment_event_type')
                """, Integer.class);

        assertThat(migrationCount).isEqualTo(1);
        assertThat(tableCount).isEqualTo(2);
        assertThat(idColumnCount).isEqualTo(2);
        assertThat(approvalConstraintCount).isEqualTo(2);
    }

    @Test
    void seedsGrowthPointQueryFeatureAndRolePermissions() {
        Integer migrationCount = jdbcTemplate.queryForObject("""
                select count(*) from flyway_schema_history
                where version = '25' and success = true
                """, Integer.class);
        Integer featureCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_feature_toggle
                where feature_code = 'GROWTH_POINT_QUERY'
                  and scope_key = 'GLOBAL'
                  and status = 'ENABLED'
                """, Integer.class);
        Integer permissionCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_permission
                where (permission_code = 'GROWTH_POINT_READ_SELF' and client_type = 'MINIAPP')
                   or (permission_code = 'GROWTH_POINT_READ_CHILD' and client_type = 'WEB')
                """, Integer.class);
        Integer grantCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_role_permission role_permission
                join sys_role role on role.id = role_permission.role_id
                join sys_permission permission on permission.id = role_permission.permission_id
                where (role.role_code = 'STUDENT' and permission.permission_code = 'GROWTH_POINT_READ_SELF')
                   or (role.role_code = 'PARENT' and permission.permission_code = 'GROWTH_POINT_READ_CHILD')
                """, Integer.class);

        assertThat(migrationCount).isEqualTo(1);
        assertThat(featureCount).isEqualTo(1);
        assertThat(permissionCount).isEqualTo(2);
        assertThat(grantCount).isEqualTo(2);
    }

    @Test
    void addsGrowthPointCorrectionRulesAndParentAccess() {
        Integer migrationCount = jdbcTemplate.queryForObject("""
                select count(*) from flyway_schema_history
                where version = '26' and success = true
                """, Integer.class);
        Integer featureCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_feature_toggle
                where feature_code = 'GROWTH_POINT_CORRECTION'
                  and scope_key = 'GLOBAL'
                  and status = 'ENABLED'
                """, Integer.class);
        Integer permissionCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_permission
                where permission_code = 'GROWTH_POINT_CORRECT_CHILD'
                  and client_type = 'WEB'
                """, Integer.class);
        Integer grantCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_role_permission role_permission
                join sys_role role on role.id = role_permission.role_id
                join sys_permission permission on permission.id = role_permission.permission_id
                where role.role_code = 'PARENT'
                  and permission.permission_code = 'GROWTH_POINT_CORRECT_CHILD'
                """, Integer.class);
        Integer correctionUniqueCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.table_constraints
                where table_name = 'growth_point_ledger'
                  and constraint_type = 'UNIQUE'
                  and constraint_name = 'uk_growth_point_ledger_correction_of'
                """, Integer.class);
        Integer obsoleteUniqueCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.table_constraints
                where table_name = 'growth_point_ledger'
                  and constraint_type = 'UNIQUE'
                  and constraint_name = 'uk_growth_point_ledger_task_reward'
                """, Integer.class);
        Integer correctionCheckCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.table_constraints
                where table_name = 'growth_point_ledger'
                  and constraint_type = 'CHECK'
                  and constraint_name = 'ck_growth_point_ledger_correction'
                """, Integer.class);

        assertThat(migrationCount).isEqualTo(1);
        assertThat(featureCount).isEqualTo(1);
        assertThat(permissionCount).isEqualTo(1);
        assertThat(grantCount).isEqualTo(1);
        assertThat(correctionUniqueCount).isEqualTo(1);
        assertThat(obsoleteUniqueCount).isZero();
        assertThat(correctionCheckCount).isEqualTo(1);
    }

    @Test
    void createsRewardExchangeFoundationAndScopedAccess() {
        Integer migrationCount = jdbcTemplate.queryForObject("""
                select count(*) from flyway_schema_history
                where version = '27' and success = true
                """, Integer.class);
        Integer tableCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.tables
                where table_name in ('growth_reward', 'growth_reward_exchange')
                """, Integer.class);
        Integer idColumnCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where table_name in ('growth_reward', 'growth_reward_exchange')
                  and column_name = 'id'
                  and upper(data_type) = 'BIGINT'
                  and is_identity = 'NO'
                """, Integer.class);
        Integer ledgerExchangeColumnCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where table_name = 'growth_point_ledger'
                  and column_name = 'source_exchange_id'
                  and upper(data_type) = 'BIGINT'
                """, Integer.class);
        Integer ledgerExchangeUniqueCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.table_constraints
                where table_name = 'growth_point_ledger'
                  and constraint_type = 'UNIQUE'
                  and constraint_name = 'uk_growth_point_ledger_exchange'
                """, Integer.class);
        Integer featureCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_feature_toggle
                where feature_code = 'REWARD_EXCHANGE'
                  and scope_key = 'GLOBAL'
                  and status = 'ENABLED'
                """, Integer.class);
        Integer permissionCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_permission
                where permission_code in ('REWARD_MANAGE_CHILD',
                    'REWARD_EXCHANGE_REVIEW_CHILD', 'REWARD_EXCHANGE_SELF')
                """, Integer.class);
        Integer grantCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_role_permission role_permission
                join sys_role role on role.id = role_permission.role_id
                join sys_permission permission on permission.id = role_permission.permission_id
                where (role.role_code = 'PARENT'
                        and permission.permission_code in ('REWARD_MANAGE_CHILD',
                            'REWARD_EXCHANGE_REVIEW_CHILD'))
                   or (role.role_code = 'STUDENT'
                        and permission.permission_code = 'REWARD_EXCHANGE_SELF')
                """, Integer.class);

        assertThat(migrationCount).isEqualTo(1);
        assertThat(tableCount).isEqualTo(2);
        assertThat(idColumnCount).isEqualTo(2);
        assertThat(ledgerExchangeColumnCount).isEqualTo(1);
        assertThat(ledgerExchangeUniqueCount).isEqualTo(1);
        assertThat(featureCount).isEqualTo(1);
        assertThat(permissionCount).isEqualTo(3);
        assertThat(grantCount).isEqualTo(3);
    }

    @Test
    void createsGrowthReviewFoundationAndScopedAccess() {
        Integer migrationCount = jdbcTemplate.queryForObject("""
                select count(*) from flyway_schema_history
                where version = '28' and success = true
                """, Integer.class);
        Integer tableCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.tables
                where table_name in ('growth_review', 'growth_review_snapshot',
                    'growth_review_category_stat', 'growth_review_daily_trend',
                    'growth_review_supplement')
                """, Integer.class);
        Integer idColumnCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where table_name in ('growth_review', 'growth_review_snapshot',
                    'growth_review_category_stat', 'growth_review_daily_trend',
                    'growth_review_supplement')
                  and column_name = 'id'
                  and upper(data_type) = 'BIGINT'
                  and is_identity = 'NO'
                """, Integer.class);
        Integer uniqueConstraintCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.table_constraints
                where constraint_type = 'UNIQUE'
                  and constraint_name in ('uk_growth_review_period',
                    'uk_growth_review_snapshot_version',
                    'uk_growth_review_category', 'uk_growth_review_trend_date')
                """, Integer.class);
        Integer featureCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_feature_toggle
                where feature_code in ('DAILY_GROWTH_REVIEW', 'PERIODIC_GROWTH_REPORT')
                  and scope_key = 'GLOBAL' and status = 'ENABLED'
                """, Integer.class);
        Integer permissionCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_permission
                where permission_code in ('GROWTH_REVIEW_READ_SELF', 'GROWTH_REVIEW_READ_CHILD',
                    'GROWTH_REVIEW_SUPPLEMENT_SELF', 'GROWTH_REVIEW_SUPPLEMENT_CHILD')
                """, Integer.class);
        Integer grantCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_role_permission role_permission
                join sys_role role on role.id = role_permission.role_id
                join sys_permission permission on permission.id = role_permission.permission_id
                where (role.role_code = 'PARENT'
                        and permission.permission_code in ('GROWTH_REVIEW_READ_CHILD',
                            'GROWTH_REVIEW_SUPPLEMENT_CHILD'))
                   or (role.role_code = 'STUDENT'
                        and permission.permission_code in ('GROWTH_REVIEW_READ_SELF',
                            'GROWTH_REVIEW_SUPPLEMENT_SELF'))
                """, Integer.class);

        assertThat(migrationCount).isEqualTo(1);
        assertThat(tableCount).isEqualTo(5);
        assertThat(idColumnCount).isEqualTo(5);
        assertThat(uniqueConstraintCount).isEqualTo(4);
        assertThat(featureCount).isEqualTo(2);
        assertThat(permissionCount).isEqualTo(4);
        assertThat(grantCount).isEqualTo(4);
    }

    @Test
    void createsPointLifecycleFoundationAndAuditableRules() {
        Integer migrationCount = jdbcTemplate.queryForObject("""
                select count(*) from flyway_schema_history
                where version = '29' and success = true
                """, Integer.class);
        Integer tableCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.tables
                where table_name in ('growth_point_decay_rule',
                    'growth_point_dormancy_state', 'growth_point_dormancy_notice')
                """, Integer.class);
        Integer idColumnCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where table_name in ('growth_point_decay_rule',
                    'growth_point_dormancy_state', 'growth_point_dormancy_notice')
                  and column_name = 'id'
                  and upper(data_type) = 'BIGINT'
                  and is_identity = 'NO'
                """, Integer.class);
        Integer ledgerAuditColumnCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where table_name = 'growth_point_ledger'
                  and column_name in ('source_task_id', 'source_dormancy_notice_id',
                    'base_points_snapshot', 'decay_percent', 'streak_days', 'decay_rule_id')
                """, Integer.class);
        Integer assignmentDateUniqueCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.table_constraints
                where table_name = 'learn_task_assignment'
                  and constraint_type = 'UNIQUE'
                  and constraint_name = 'uk_learn_task_assignment_task_student_date'
                """, Integer.class);
        Integer ruleCount = jdbcTemplate.queryForObject("""
                select count(*) from growth_point_decay_rule
                where (start_streak_day = 8 and decay_percent = 20)
                   or (start_streak_day = 16 and decay_percent = 40)
                """, Integer.class);
        Integer featureCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_feature_toggle
                where feature_code = 'POINT_LIFECYCLE'
                  and scope_key = 'GLOBAL' and status = 'ENABLED'
                  and id >= 1000000000000000000
                """, Integer.class);

        assertThat(migrationCount).isEqualTo(1);
        assertThat(tableCount).isEqualTo(3);
        assertThat(idColumnCount).isEqualTo(3);
        assertThat(ledgerAuditColumnCount).isEqualTo(6);
        assertThat(assignmentDateUniqueCount).isEqualTo(1);
        assertThat(ruleCount).isEqualTo(2);
        assertThat(featureCount).isEqualTo(1);
    }

    @Test
    void createsRecurringTaskFoundationWithSnowflakeIdAndScheduleConstraints() {
        Integer migrationCount = jdbcTemplate.queryForObject("""
                select count(*) from flyway_schema_history
                where version = '30' and success = true
                """, Integer.class);
        Integer tableCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.tables
                where table_name = 'learn_task_recurrence'
                """, Integer.class);
        Integer idColumnCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where table_name = 'learn_task_recurrence'
                  and column_name = 'id'
                  and upper(data_type) = 'BIGINT'
                  and is_identity = 'NO'
                """, Integer.class);
        Integer taskConfigColumnCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where table_name = 'learn_task'
                  and column_name in ('recurrence_enabled', 'recurrence_end_date')
                """, Integer.class);
        Integer taskUniqueCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.table_constraints
                where table_name = 'learn_task_recurrence'
                  and constraint_type = 'UNIQUE'
                  and constraint_name = 'uk_learn_task_recurrence_task'
                """, Integer.class);
        Integer dueIndexCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.indexes
                where table_name = 'learn_task_recurrence'
                  and index_name = 'idx_learn_task_recurrence_due'
                """, Integer.class);

        assertThat(migrationCount).isEqualTo(1);
        assertThat(tableCount).isEqualTo(1);
        assertThat(idColumnCount).isEqualTo(1);
        assertThat(taskConfigColumnCount).isEqualTo(2);
        assertThat(taskUniqueCount).isEqualTo(1);
        assertThat(dueIndexCount).isEqualTo(1);
    }

    @Test
    void addsTaskCheckInImageRuleAndFileOwnershipMetadata() {
        Integer migrationCount = jdbcTemplate.queryForObject("""
                select count(*) from flyway_schema_history
                where version = '31' and success = true
                """, Integer.class);
        Integer fileColumnCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where table_name = 'sys_file'
                  and column_name in ('module_code', 'file_category', 'content_sha256')
                """, Integer.class);
        Integer imageRuleCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_attachment_rule
                where module_code = 'LEARNING_TASK_CHECKIN'
                  and file_category = 'IMAGE'
                  and max_file_size_bytes = 10485760
                  and max_batch_count = 9
                  and preview_enabled = 1
                  and status = 'ENABLED'
                  and id >= 1000000000000000000
                """, Integer.class);
        Integer extensionCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_attachment_rule_extension extension
                join sys_attachment_rule rule on rule.id = extension.rule_id
                where rule.module_code = 'LEARNING_TASK_CHECKIN'
                  and rule.file_category = 'IMAGE'
                  and extension.extension in ('jpg', 'jpeg', 'png')
                  and extension.id >= 1000000000000000000
                """, Integer.class);
        Integer permissionCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_permission
                where permission_code in ('ATTACHMENT_UPLOAD', 'ATTACHMENT_READ')
                  and ((permission_code = 'ATTACHMENT_UPLOAD' and client_type = 'MINIAPP')
                    or (permission_code = 'ATTACHMENT_READ' and client_type = 'BOTH'))
                  and id >= 1000000000000000000
                """, Integer.class);
        Integer nullableCheckInContentCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where table_name = 'learn_task_checkin'
                  and column_name = 'content'
                  and is_nullable = 'YES'
                """, Integer.class);

        assertThat(migrationCount).isEqualTo(1);
        assertThat(fileColumnCount).isEqualTo(3);
        assertThat(imageRuleCount).isEqualTo(1);
        assertThat(extensionCount).isEqualTo(3);
        assertThat(permissionCount).isEqualTo(2);
        assertThat(nullableCheckInContentCount).isEqualTo(1);
    }

    @Test
    void addsTaskOverdueAndDeferFoundation() {
        Integer migrationCount = jdbcTemplate.queryForObject("""
                select count(*) from flyway_schema_history
                where version = '32' and success = true
                """, Integer.class);
        Integer historyTableCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.tables
                where table_name = 'learn_task_defer_history'
                """, Integer.class);
        Integer historyIdCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where table_name = 'learn_task_defer_history'
                  and column_name = 'id'
                  and upper(data_type) = 'BIGINT'
                  and is_identity = 'NO'
                """, Integer.class);
        Integer taskColumnCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where table_name = 'learn_task'
                  and column_name in ('generation_type', 'origin_task_id')
                """, Integer.class);
        Integer assignmentColumnCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where table_name = 'learn_task_assignment'
                  and column_name in ('last_defer_type', 'defer_count', 'overnight_migrated',
                    'last_deferred_by_user_id', 'last_deferred_at')
                """, Integer.class);
        Integer nullableSystemOperatorCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where table_name = 'learn_task_assignment_event'
                  and column_name = 'operator_user_id'
                  and is_nullable = 'YES'
                """, Integer.class);
        Integer permissionCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_permission
                where permission_code = 'TASK_ASSIGNMENT_DEFER'
                  and client_type = 'BOTH'
                  and id >= 1000000000000000000
                """, Integer.class);
        Integer grantCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_role_permission role_permission
                join sys_role role on role.id = role_permission.role_id
                join sys_permission permission on permission.id = role_permission.permission_id
                where permission.permission_code = 'TASK_ASSIGNMENT_DEFER'
                  and role.role_code in ('PARENT', 'TEACHER', 'ORG_ADMIN')
                """, Integer.class);

        assertThat(migrationCount).isEqualTo(1);
        assertThat(historyTableCount).isEqualTo(1);
        assertThat(historyIdCount).isEqualTo(1);
        assertThat(taskColumnCount).isEqualTo(2);
        assertThat(assignmentColumnCount).isEqualTo(5);
        assertThat(nullableSystemOperatorCount).isEqualTo(1);
        assertThat(permissionCount).isEqualTo(1);
        assertThat(grantCount).isEqualTo(3);
    }

    @Test
    void addsPreviousDayTaskCopyFoundation() {
        Integer migrationCount = jdbcTemplate.queryForObject("""
                select count(*) from flyway_schema_history
                where version = '33' and success = true
                """, Integer.class);
        Integer tableCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.tables
                where table_name in ('learn_task_copy_batch', 'learn_task_copy_item')
                """, Integer.class);
        Integer idColumnCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where table_name in ('learn_task_copy_batch', 'learn_task_copy_item')
                  and column_name = 'id'
                  and upper(data_type) = 'BIGINT'
                  and is_identity = 'NO'
                """, Integer.class);
        Integer dailyUniqueCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.table_constraints
                where table_name = 'learn_task_copy_batch'
                  and constraint_type = 'UNIQUE'
                  and constraint_name = 'uk_task_copy_batch_student_date'
                """, Integer.class);
        Integer itemUniqueCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.table_constraints
                where table_name = 'learn_task_copy_item'
                  and constraint_type = 'UNIQUE'
                  and constraint_name = 'uk_task_copy_item_batch_source'
                """, Integer.class);
        Integer featureCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_feature_toggle
                where feature_code = 'COPY_PREVIOUS_DAY_TASK'
                  and scope_key = 'GLOBAL' and status = 'ENABLED'
                  and id >= 1000000000000000000
                """, Integer.class);
        Integer permissionGrantCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_role_permission role_permission
                join sys_role role on role.id = role_permission.role_id
                join sys_permission permission on permission.id = role_permission.permission_id
                where permission.permission_code = 'LEARNING_TASK_COPY_PREVIOUS_DAY'
                  and permission.client_type = 'WEB'
                  and role.role_code = 'PARENT'
                """, Integer.class);

        assertThat(migrationCount).isEqualTo(1);
        assertThat(tableCount).isEqualTo(2);
        assertThat(idColumnCount).isEqualTo(2);
        assertThat(dailyUniqueCount).isEqualTo(1);
        assertThat(itemUniqueCount).isEqualTo(1);
        assertThat(featureCount).isEqualTo(1);
        assertThat(permissionGrantCount).isEqualTo(1);
    }

    @Test
    void addsLearningTaskTemplateFoundation() {
        Integer migrationCount = jdbcTemplate.queryForObject("""
                select count(*) from flyway_schema_history
                where version = '34' and success = true
                """, Integer.class);
        Integer tableCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.tables
                where table_name in ('learn_task_template', 'learn_task_template_tag')
                """, Integer.class);
        Integer idColumnCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where table_name in ('learn_task_template', 'learn_task_template_tag')
                  and column_name = 'id'
                  and upper(data_type) = 'BIGINT'
                  and is_identity = 'NO'
                """, Integer.class);
        Integer systemTemplateCount = jdbcTemplate.queryForObject("""
                select count(*) from learn_task_template
                where template_scope = 'SYSTEM' and owner_user_id is null
                  and template_name in ('每日阅读30分钟', '口算练习')
                  and status = 'ENABLED' and id >= 1000000000000000000
                """, Integer.class);
        Integer systemTagCount = jdbcTemplate.queryForObject("""
                select count(*)
                from learn_task_template_tag tag
                join learn_task_template template on template.id = tag.template_id
                where template.template_scope = 'SYSTEM'
                  and tag.tag_code = 'DAILY'
                  and tag.id >= 1000000000000000000
                """, Integer.class);
        Integer featureCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_feature_toggle
                where feature_code = 'LEARNING_TASK_TEMPLATE'
                  and scope_key = 'GLOBAL' and status = 'ENABLED'
                  and id >= 1000000000000000000
                """, Integer.class);
        Integer permissionGrantCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_role_permission role_permission
                join sys_role role on role.id = role_permission.role_id
                join sys_permission permission on permission.id = role_permission.permission_id
                where permission.permission_code in (
                    'LEARNING_TASK_TEMPLATE_READ', 'LEARNING_TASK_TEMPLATE_MANAGE_PERSONAL'
                ) and permission.client_type = 'WEB'
                  and role.role_code = 'PARENT'
                """, Integer.class);

        assertThat(migrationCount).isEqualTo(1);
        assertThat(tableCount).isEqualTo(2);
        assertThat(idColumnCount).isEqualTo(2);
        assertThat(systemTemplateCount).isEqualTo(2);
        assertThat(systemTagCount).isEqualTo(2);
        assertThat(featureCount).isEqualTo(1);
        assertThat(permissionGrantCount).isEqualTo(2);
    }

    @Test
    void createsStudentQrLoginTicketAndScopedAccessThroughFlyway() {
        Integer migrationCount = jdbcTemplate.queryForObject("""
                select count(*) from flyway_schema_history
                where version = '35' and success = true
                """, Integer.class);
        Integer tableCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.tables
                where table_name = 'auth_student_qr_ticket'
                """, Integer.class);
        Integer idColumnCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where table_name = 'auth_student_qr_ticket'
                  and column_name = 'id'
                  and upper(data_type) = 'BIGINT'
                  and is_identity = 'NO'
                """, Integer.class);
        Integer featureCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_feature_toggle
                where feature_code = 'STUDENT_QR_LOGIN'
                  and scope_key = 'GLOBAL' and status = 'ENABLED'
                  and id >= 1000000000000000000
                """, Integer.class);
        Integer permissionGrantCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_role_permission role_permission
                join sys_role role on role.id = role_permission.role_id
                join sys_permission permission on permission.id = role_permission.permission_id
                where permission.permission_code = 'STUDENT_LOGIN_QR_CREATE'
                  and permission.client_type = 'WEB'
                  and role.role_code in ('PARENT', 'ORG_ADMIN')
                """, Integer.class);

        assertThat(migrationCount).isEqualTo(1);
        assertThat(tableCount).isEqualTo(1);
        assertThat(idColumnCount).isEqualTo(1);
        assertThat(featureCount).isEqualTo(1);
        assertThat(permissionGrantCount).isEqualTo(2);
    }

    @Test
    void createsParentPhoneAuthenticationDataFoundationThroughFlyway() {
        Integer migrationCount = jdbcTemplate.queryForObject("""
                select count(*) from flyway_schema_history
                where version = '36' and success = true
                """, Integer.class);
        Integer tableCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.tables
                where table_name in ('auth_user_agreement_acceptance', 'auth_parent_profile')
                """, Integer.class);
        Integer idColumnCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where table_name in ('auth_user_agreement_acceptance', 'auth_parent_profile')
                  and column_name = 'id' and upper(data_type) = 'BIGINT' and is_identity = 'NO'
                """, Integer.class);
        Integer mobileUniqueCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.table_constraints
                where table_name = 'sys_user' and constraint_name = 'uk_sys_user_mobile'
                  and constraint_type = 'UNIQUE'
                """, Integer.class);
        Integer featureCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_feature_toggle
                where feature_code = 'PARENT_PHONE_AUTH' and status = 'ENABLED'
                """, Integer.class);
        Integer agreementVersionCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_config
                where config_key = 'auth.parent-agreement.current-version'
                  and config_value = '1' and status = 'ENABLED'
                """, Integer.class);

        assertThat(migrationCount).isEqualTo(1);
        assertThat(tableCount).isEqualTo(2);
        assertThat(idColumnCount).isEqualTo(2);
        assertThat(mobileUniqueCount).isEqualTo(1);
        assertThat(featureCount).isEqualTo(1);
        assertThat(agreementVersionCount).isEqualTo(1);
    }

    @Test
    void createsParentWechatBindingWithOneToOneConstraintsThroughFlyway() {
        Integer migrationCount = jdbcTemplate.queryForObject("""
                select count(*) from flyway_schema_history
                where version = '37' and success = true
                """, Integer.class);
        Integer tableCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.tables
                where table_name = 'auth_parent_wechat_binding'
                """, Integer.class);
        Integer idColumnCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where table_name = 'auth_parent_wechat_binding'
                  and column_name = 'id' and upper(data_type) = 'BIGINT' and is_identity = 'NO'
                """, Integer.class);
        Integer uniqueConstraintCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.table_constraints
                where table_name = 'auth_parent_wechat_binding'
                  and constraint_type = 'UNIQUE'
                  and constraint_name in ('uk_auth_parent_wechat_binding_user',
                    'uk_auth_parent_wechat_binding_identity')
                """, Integer.class);
        Integer featureCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_feature_toggle
                where feature_code = 'PARENT_WECHAT_AUTH'
                  and scope_key = 'GLOBAL' and status = 'DISABLED'
                """, Integer.class);

        assertThat(migrationCount).isEqualTo(1);
        assertThat(tableCount).isEqualTo(1);
        assertThat(idColumnCount).isEqualTo(1);
        assertThat(uniqueConstraintCount).isEqualTo(2);
        assertThat(featureCount).isEqualTo(1);
    }

    @Test
    void createsParentRelationshipLifecycleWithUniqueRolesAndAuditThroughFlyway() {
        Integer migrationCount = jdbcTemplate.queryForObject("""
                select count(*) from flyway_schema_history
                where version = '38' and success = true
                """, Integer.class);
        Integer tableCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.tables
                where table_name in ('edu_parent_relationship_invitation',
                    'edu_parent_relationship_change_log')
                """, Integer.class);
        Integer idColumnCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where table_name in ('edu_parent_relationship_invitation',
                    'edu_parent_relationship_change_log')
                  and column_name = 'id' and upper(data_type) = 'BIGINT' and is_identity = 'NO'
                """, Integer.class);
        Integer scopeLength = jdbcTemplate.queryForObject("""
                select character_maximum_length from information_schema.columns
                where table_name = 'edu_parent_student' and column_name = 'primary_scope_key'
                """, Integer.class);
        Integer roleLength = jdbcTemplate.queryForObject("""
                select character_maximum_length from information_schema.columns
                where table_name = 'edu_parent_student' and column_name = 'relation_role'
                """, Integer.class);
        Integer uniqueConstraintCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.table_constraints
                where constraint_type = 'UNIQUE'
                  and ((table_name = 'edu_parent_relationship_invitation'
                    and constraint_name = 'uk_parent_relationship_invitation_pending')
                    or (table_name = 'edu_parent_student'
                    and constraint_name = 'uk_edu_parent_student_primary'))
                """, Integer.class);
        Integer featureCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_feature_toggle
                where feature_code = 'PARENT_RELATIONSHIP_MANAGEMENT'
                  and scope_key = 'GLOBAL' and status = 'DISABLED'
                  and id >= 1000000000000000000
                """, Integer.class);

        assertThat(migrationCount).isEqualTo(1);
        assertThat(tableCount).isEqualTo(2);
        assertThat(idColumnCount).isEqualTo(2);
        assertThat(scopeLength).isEqualTo(32);
        assertThat(roleLength).isEqualTo(32);
        assertThat(uniqueConstraintCount).isEqualTo(2);
        assertThat(featureCount).isEqualTo(1);
    }

    @Test
    void createsAccountSecurityEventsAndDeviceHistoryIndexThroughFlyway() {
        Integer migrationCount = jdbcTemplate.queryForObject("""
                select count(*) from flyway_schema_history
                where version = '39' and success = true
                """, Integer.class);
        Integer tableCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.tables
                where table_name = 'auth_security_event'
                """, Integer.class);
        Integer idColumnCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where table_name = 'auth_security_event'
                  and column_name = 'id' and upper(data_type) = 'BIGINT' and is_identity = 'NO'
                """, Integer.class);
        Integer checkConstraintCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.table_constraints
                where table_name = 'auth_security_event'
                  and constraint_type = 'CHECK'
                  and constraint_name in ('ck_auth_security_event_type',
                    'ck_auth_security_event_risk', 'ck_auth_security_event_status')
                """, Integer.class);
        Integer uniqueConstraintCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.table_constraints
                where table_name = 'auth_security_event'
                  and constraint_type = 'UNIQUE'
                  and constraint_name = 'uk_auth_security_event_scope'
                """, Integer.class);
        Integer indexCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.indexes
                where (table_name = 'auth_security_event'
                    and index_name = 'idx_auth_security_event_user_status_time')
                   or (table_name = 'auth_device_session'
                    and index_name = 'idx_auth_session_user_client_device')
                """, Integer.class);
        Integer featureCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_feature_toggle
                where feature_code = 'ACCOUNT_SECURITY_MANAGEMENT'
                  and scope_key = 'GLOBAL' and status = 'ENABLED'
                  and id >= 1000000000000000000
                """, Integer.class);

        assertThat(migrationCount).isEqualTo(1);
        assertThat(tableCount).isEqualTo(1);
        assertThat(idColumnCount).isEqualTo(1);
        assertThat(checkConstraintCount).isEqualTo(3);
        assertThat(uniqueConstraintCount).isEqualTo(1);
        assertThat(indexCount).isEqualTo(2);
        assertThat(featureCount).isEqualTo(1);
    }

    @Test
    void seedsOrganizationMiniappAuthenticationFeatureThroughFlyway() {
        Integer migrationCount = jdbcTemplate.queryForObject("""
                select count(*) from flyway_schema_history
                where version = '40' and success = true
                """, Integer.class);
        Integer featureCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_feature_toggle
                where feature_code = 'ORGANIZATION_MINIAPP_AUTH'
                  and scope_type = 'GLOBAL'
                  and scope_key = 'GLOBAL'
                  and status = 'ENABLED'
                  and built_in = true
                  and id >= 1000000000000000000
                """, Integer.class);

        assertThat(migrationCount).isEqualTo(1);
        assertThat(featureCount).isEqualTo(1);
    }

    @Test
    void createsStudentOrganizationLifecycleAuditAndBothClientPermissionThroughFlyway() {
        Integer migrationCount = jdbcTemplate.queryForObject("""
                select count(*) from flyway_schema_history
                where version = '41' and success = true
                """, Integer.class);
        Integer tableCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.tables
                where table_name = 'edu_student_organization_change'
                """, Integer.class);
        Integer idColumnCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where table_name = 'edu_student_organization_change'
                  and column_name = 'id' and upper(data_type) = 'BIGINT' and is_identity = 'NO'
                """, Integer.class);
        Integer featureCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_feature_toggle
                where feature_code = 'STUDENT_ORGANIZATION_RELATIONSHIP'
                  and scope_key = 'GLOBAL' and status = 'ENABLED'
                  and id >= 1000000000000000000
                """, Integer.class);
        Integer permissionCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_permission
                where permission_code = 'STUDENT_ORGANIZATION_MANAGE'
                  and client_type = 'BOTH' and status = 'ENABLED'
                  and id >= 1000000000000000000
                """, Integer.class);
        Integer organizationAdministratorGrantCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_role_permission role_permission
                join sys_role role on role.id = role_permission.role_id
                join sys_permission permission on permission.id = role_permission.permission_id
                where role.role_code = 'ORG_ADMIN'
                  and permission.permission_code = 'STUDENT_ORGANIZATION_MANAGE'
                  and role_permission.id >= 1000000000000000000
                """, Integer.class);

        assertThat(migrationCount).isEqualTo(1);
        assertThat(tableCount).isEqualTo(1);
        assertThat(idColumnCount).isEqualTo(1);
        assertThat(featureCount).isEqualTo(1);
        assertThat(permissionCount).isEqualTo(1);
        assertThat(organizationAdministratorGrantCount).isEqualTo(1);
    }

    @Test
    void createsParentAccountLifecycleTablesAndBothClientPermissionThroughFlyway() {
        Integer migrationCount = jdbcTemplate.queryForObject("""
                select count(*) from flyway_schema_history
                where version = '42' and success = true
                """, Integer.class);
        Integer tableCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.tables
                where table_name in (
                    'auth_parent_mobile_change',
                    'auth_parent_account_cancellation'
                )
                """, Integer.class);
        Integer idColumnCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where table_name in (
                    'auth_parent_mobile_change',
                    'auth_parent_account_cancellation'
                )
                  and column_name = 'id'
                  and upper(data_type) = 'BIGINT'
                  and is_identity = 'NO'
                """, Integer.class);
        Integer featureCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_feature_toggle
                where feature_code = 'PARENT_ACCOUNT_LIFECYCLE'
                  and scope_key = 'GLOBAL' and status = 'ENABLED'
                  and id >= 1000000000000000000
                """, Integer.class);
        Integer permissionCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_permission
                where permission_code = 'PARENT_ACCOUNT_LIFECYCLE_MANAGE'
                  and client_type = 'BOTH' and status = 'ENABLED'
                  and id >= 1000000000000000000
                """, Integer.class);
        Integer parentGrantCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_role_permission role_permission
                join sys_role role on role.id = role_permission.role_id
                join sys_permission permission on permission.id = role_permission.permission_id
                where role.role_code = 'PARENT'
                  and permission.permission_code = 'PARENT_ACCOUNT_LIFECYCLE_MANAGE'
                  and role_permission.id >= 1000000000000000000
                """, Integer.class);

        assertThat(migrationCount).isEqualTo(1);
        assertThat(tableCount).isEqualTo(2);
        assertThat(idColumnCount).isEqualTo(2);
        assertThat(featureCount).isEqualTo(1);
        assertThat(permissionCount).isEqualTo(1);
        assertThat(parentGrantCount).isEqualTo(1);
    }

    @Test
    void addsParentAccountFinalizationRetryStateThroughFlyway() {
        Integer migrationCount = jdbcTemplate.queryForObject("""
                select count(*) from flyway_schema_history
                where version = '43' and success = true
                """, Integer.class);
        Integer columnCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where table_name = 'auth_parent_account_cancellation'
                  and column_name in (
                    'finalization_attempts',
                    'next_finalize_at',
                    'last_finalize_error_code'
                  )
                """, Integer.class);
        Integer dueIndexCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.indexes
                where table_name = 'auth_parent_account_cancellation'
                  and index_name = 'idx_parent_account_cancellation_due'
                """, Integer.class);

        assertThat(migrationCount).isEqualTo(1);
        assertThat(columnCount).isEqualTo(3);
        assertThat(dueIndexCount).isEqualTo(1);
    }

    @Test
    void createsParentMobileManualRecoveryAuditAndDisabledAccessThroughFlyway() {
        Integer migrationCount = jdbcTemplate.queryForObject("""
                select count(*) from flyway_schema_history
                where version = '44' and success = true
                """, Integer.class);
        Integer tableCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.tables
                where table_name = 'auth_parent_mobile_manual_recovery'
                """, Integer.class);
        Integer idColumnCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where table_name = 'auth_parent_mobile_manual_recovery'
                  and column_name = 'id'
                  and upper(data_type) = 'BIGINT'
                  and is_identity = 'NO'
                """, Integer.class);
        Integer featureCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_feature_toggle
                where feature_code = 'PARENT_MOBILE_MANUAL_RECOVERY'
                  and scope_key = 'GLOBAL' and status = 'DISABLED'
                  and id >= 1000000000000000000
                """, Integer.class);
        Integer permissionCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_permission
                where permission_code = 'PARENT_MOBILE_MANUAL_RECOVERY_MANAGE'
                  and client_type = 'BOTH' and status = 'ENABLED'
                  and id >= 1000000000000000000
                """, Integer.class);
        Integer organizationAdministratorGrantCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_role_permission role_permission
                join sys_role role on role.id = role_permission.role_id
                join sys_permission permission on permission.id = role_permission.permission_id
                where role.role_code = 'ORG_ADMIN'
                  and permission.permission_code = 'PARENT_MOBILE_MANUAL_RECOVERY_MANAGE'
                  and role_permission.id >= 1000000000000000000
                """, Integer.class);

        assertThat(migrationCount).isEqualTo(1);
        assertThat(tableCount).isEqualTo(1);
        assertThat(idColumnCount).isEqualTo(1);
        assertThat(featureCount).isEqualTo(1);
        assertThat(permissionCount).isEqualTo(1);
        assertThat(organizationAdministratorGrantCount).isEqualTo(1);
    }

    @Test
    void createsStudentAccountCancellationAuditAndDisabledAccessThroughFlyway() {
        Integer migrationCount = jdbcTemplate.queryForObject("""
                select count(*) from flyway_schema_history
                where version = '45' and success = true
                """, Integer.class);
        Integer tableCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.tables
                where table_name = 'auth_student_account_cancellation'
                """, Integer.class);
        Integer idColumnCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where table_name = 'auth_student_account_cancellation'
                  and column_name = 'id'
                  and upper(data_type) = 'BIGINT'
                  and is_identity = 'NO'
                """, Integer.class);
        Integer featureCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_feature_toggle
                where feature_code = 'STUDENT_ACCOUNT_CANCELLATION'
                  and scope_key = 'GLOBAL' and status = 'DISABLED'
                  and id >= 1000000000000000000
                """, Integer.class);
        Integer permissionCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_permission
                where permission_code = 'STUDENT_ACCOUNT_CANCELLATION_MANAGE'
                  and client_type = 'BOTH' and status = 'ENABLED'
                  and id >= 1000000000000000000
                """, Integer.class);
        Integer organizationAdministratorGrantCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_role_permission role_permission
                join sys_role role on role.id = role_permission.role_id
                join sys_permission permission on permission.id = role_permission.permission_id
                where role.role_code = 'ORG_ADMIN'
                  and permission.permission_code = 'STUDENT_ACCOUNT_CANCELLATION_MANAGE'
                  and role_permission.id >= 1000000000000000000
                """, Integer.class);

        assertThat(migrationCount).isEqualTo(1);
        assertThat(tableCount).isEqualTo(1);
        assertThat(idColumnCount).isEqualTo(1);
        assertThat(featureCount).isEqualTo(1);
        assertThat(permissionCount).isEqualTo(1);
        assertThat(organizationAdministratorGrantCount).isEqualTo(1);
    }

    @Test
    void createsStudentWechatBindingAuditAndDisabledParentAccessThroughFlyway() {
        Integer migrationCount = jdbcTemplate.queryForObject("""
                select count(*) from flyway_schema_history
                where version = '46' and success = true
                """, Integer.class);
        Integer tableCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.tables
                where table_name in (
                    'auth_student_wechat_binding',
                    'auth_student_wechat_binding_audit'
                )
                """, Integer.class);
        Integer idColumnCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where table_name in (
                    'auth_student_wechat_binding',
                    'auth_student_wechat_binding_audit'
                )
                  and column_name = 'id'
                  and upper(data_type) = 'BIGINT'
                  and is_identity = 'NO'
                """, Integer.class);
        Integer uniqueConstraintCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.table_constraints
                where table_name = 'auth_student_wechat_binding'
                  and constraint_type = 'UNIQUE'
                  and constraint_name in (
                    'uk_auth_student_wechat_binding_student',
                    'uk_auth_student_wechat_binding_user',
                    'uk_auth_student_wechat_binding_wechat'
                  )
                """, Integer.class);
        Integer featureCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_feature_toggle
                where feature_code = 'STUDENT_WECHAT_AUTH'
                  and scope_key = 'GLOBAL' and status = 'DISABLED'
                  and id >= 1000000000000000000
                """, Integer.class);
        Integer permissionCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_permission
                where permission_code = 'STUDENT_WECHAT_UNBIND'
                  and client_type = 'BOTH' and status = 'ENABLED'
                  and id >= 1000000000000000000
                """, Integer.class);
        Integer parentGrantCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_role_permission role_permission
                join sys_role role on role.id = role_permission.role_id
                join sys_permission permission on permission.id = role_permission.permission_id
                where role.role_code = 'PARENT'
                  and permission.permission_code = 'STUDENT_WECHAT_UNBIND'
                  and role_permission.id >= 1000000000000000000
                """, Integer.class);

        assertThat(migrationCount).isEqualTo(1);
        assertThat(tableCount).isEqualTo(2);
        assertThat(idColumnCount).isEqualTo(2);
        assertThat(uniqueConstraintCount).isEqualTo(3);
        assertThat(featureCount).isEqualTo(1);
        assertThat(permissionCount).isEqualTo(1);
        assertThat(parentGrantCount).isEqualTo(1);
    }

    @Test
    void createsOrganizationNodeLifecycleAndRoleSeparatedApprovalThroughFlyway() {
        Integer migrationCount = jdbcTemplate.queryForObject("""
                select count(*) from flyway_schema_history
                where version = '47' and success = true
                """, Integer.class);
        Integer tableCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.tables
                where table_name in ('sys_organization_change', 'sys_organization_change_audit')
                """, Integer.class);
        Integer idColumnCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where table_name in ('sys_organization_change', 'sys_organization_change_audit')
                  and column_name = 'id' and upper(data_type) = 'BIGINT' and is_identity = 'NO'
                """, Integer.class);
        Integer lifecycleColumnCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where table_name = 'sys_organization'
                  and column_name in ('effective_status', 'version_no')
                """, Integer.class);
        Integer featureCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_feature_toggle
                where feature_code = 'ORGANIZATION_MANAGEMENT'
                  and scope_key = 'GLOBAL' and status = 'ENABLED'
                  and id >= 1000000000000000000
                """, Integer.class);
        Integer permissionCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_permission
                where permission_code in (
                    'ORG_NODE_UPDATE', 'ORG_NODE_CHANGE_SUBMIT', 'ORG_NODE_CHANGE_REVIEW'
                )
                  and client_type = 'WEB' and status = 'ENABLED'
                  and id >= 1000000000000000000
                """, Integer.class);
        Integer grantCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_role_permission role_permission
                join sys_role role on role.id = role_permission.role_id
                join sys_permission permission on permission.id = role_permission.permission_id
                where (role.role_code = 'SYS_ADMIN'
                         and permission.permission_code in ('ORG_NODE_UPDATE', 'ORG_NODE_CHANGE_SUBMIT'))
                   or (role.role_code = 'SYS_AUDITOR'
                         and permission.permission_code = 'ORG_NODE_CHANGE_REVIEW')
                """, Integer.class);

        assertThat(migrationCount).isEqualTo(1);
        assertThat(tableCount).isEqualTo(2);
        assertThat(idColumnCount).isEqualTo(2);
        assertThat(lifecycleColumnCount).isEqualTo(2);
        assertThat(featureCount).isEqualTo(1);
        assertThat(permissionCount).isEqualTo(3);
        assertThat(grantCount).isEqualTo(3);
    }

    @Test
    void createsClassManagementPermissionsAndInvalidationStatusesThroughFlyway() {
        Integer migrationCount = jdbcTemplate.queryForObject("""
                select count(*) from flyway_schema_history
                where version = '48' and success = true
                """, Integer.class);
        Integer featureCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_feature_toggle
                where feature_code = 'CLASS_MANAGEMENT'
                  and scope_key = 'GLOBAL' and status = 'ENABLED'
                  and id >= 1000000000000000000
                """, Integer.class);
        Integer permissionCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_permission
                where permission_code in (
                    'CLASS_READ', 'CLASS_CREATE', 'CLASS_UPDATE', 'CLASS_STATUS_CHANGE'
                )
                  and client_type = 'BOTH' and status = 'ENABLED'
                  and id >= 1000000000000000000
                """, Integer.class);
        Integer grantCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_role_permission role_permission
                join sys_role role on role.id = role_permission.role_id
                join sys_permission permission on permission.id = role_permission.permission_id
                where role.role_code = 'ORG_ADMIN'
                  and permission.permission_code in (
                    'CLASS_READ', 'CLASS_CREATE', 'CLASS_UPDATE', 'CLASS_STATUS_CHANGE'
                  )
                  and role_permission.id >= 1000000000000000000
                """, Integer.class);
        Integer teacherPermissionCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_permission
                where permission_code = 'TEACHER_CLASS_ASSIGN'
                  and client_type = 'BOTH' and status = 'ENABLED'
                """, Integer.class);
        Integer assignmentStatusConstraintCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.check_constraints
                where constraint_name = 'ck_learn_task_assignment_status'
                  and upper(check_clause) like '%INVALIDATED%'
                """, Integer.class);
        Integer eventTypeConstraintCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.check_constraints
                where constraint_name = 'ck_task_assignment_event_type'
                  and upper(check_clause) like '%CLASS_INVALIDATED%'
                """, Integer.class);

        assertThat(migrationCount).isEqualTo(1);
        assertThat(featureCount).isEqualTo(1);
        assertThat(permissionCount).isEqualTo(4);
        assertThat(grantCount).isEqualTo(4);
        assertThat(teacherPermissionCount).isEqualTo(1);
        assertThat(assignmentStatusConstraintCount).isEqualTo(1);
        assertThat(eventTypeConstraintCount).isEqualTo(1);
    }

    @Test
    void addsRolePermissionEffectsThroughFlyway() {
        Integer migrationCount = jdbcTemplate.queryForObject("""
                select count(*) from flyway_schema_history
                where version = '49' and success = true
                """, Integer.class);
        Integer effectColumnCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where table_name = 'sys_role_permission'
                  and column_name = 'effect'
                  and upper(column_default) like '%ALLOW%'
                """, Integer.class);
        Integer effectConstraintCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.check_constraints
                where constraint_name = 'ck_sys_role_permission_effect'
                  and upper(check_clause) like '%ALLOW%'
                  and upper(check_clause) like '%DENY%'
                """, Integer.class);
        Integer nonAllowExistingGrantCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_role_permission
                where effect is null or effect <> 'ALLOW'
                """, Integer.class);

        assertThat(migrationCount).isEqualTo(1);
        assertThat(effectColumnCount).isEqualTo(1);
        assertThat(effectConstraintCount).isEqualTo(1);
        assertThat(nonAllowExistingGrantCount).isZero();
    }

    @Test
    void createsImmutableIamChangeAuditAndReadPermissionThroughFlyway() {
        Integer migrationCount = jdbcTemplate.queryForObject("""
                select count(*) from flyway_schema_history
                where version = '50' and success = true
                """, Integer.class);
        Integer auditTableCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.tables
                where table_name = 'sys_iam_change_audit'
                """, Integer.class);
        Integer nonIdentityIdCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where table_name = 'sys_iam_change_audit'
                  and column_name = 'id'
                  and upper(data_type) = 'BIGINT'
                  and is_identity = 'NO'
                """, Integer.class);
        Integer permissionCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_role_permission role_permission
                join sys_role role on role.id = role_permission.role_id
                join sys_permission permission on permission.id = role_permission.permission_id
                where role.role_code = 'SYS_ADMIN'
                  and permission.permission_code = 'IAM_AUDIT_READ'
                  and role_permission.effect = 'ALLOW'
                  and permission.id >= 1000000000000000000
                  and role_permission.id >= 1000000000000000000
                """, Integer.class);

        assertThat(migrationCount).isEqualTo(1);
        assertThat(auditTableCount).isEqualTo(1);
        assertThat(nonIdentityIdCount).isEqualTo(1);
        assertThat(permissionCount).isEqualTo(1);
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
                    'sys_cache_operation_log', 'sys_interface_service', 'sys_interface_service_change',
                    'sys_interface_call_log', 'sys_attachment_rule', 'sys_attachment_rule_extension',
                    'sys_file', 'sys_file_relation', 'sys_import_export_template', 'auth_device_session',
                    'edu_student', 'edu_parent_student', 'edu_student_organization',
                    'edu_parent_binding_invitation', 'auth_student_account_sequence', 'auth_student_credential',
                    'edu_teacher_class', 'learn_task', 'learn_task_target', 'learn_task_tag',
                    'learn_task_assignment', 'learn_task_assignment_event', 'learn_task_pause',
                    'learn_task_checkin', 'learn_task_reviewer_transfer', 'growth_point_account',
                    'growth_point_ledger', 'growth_reward', 'growth_reward_exchange',
                    'growth_review', 'growth_review_snapshot', 'growth_review_category_stat',
                    'growth_review_daily_trend', 'growth_review_supplement',
                    'growth_point_decay_rule', 'growth_point_dormancy_state',
                    'growth_point_dormancy_notice', 'learn_task_recurrence',
                    'learn_task_defer_history', 'learn_task_copy_batch', 'learn_task_copy_item',
                    'learn_task_template', 'learn_task_template_tag', 'auth_student_qr_ticket',
                    'auth_user_agreement_acceptance', 'auth_parent_profile',
                    'auth_parent_wechat_binding', 'edu_parent_relationship_invitation',
                    'edu_parent_relationship_change_log', 'auth_security_event',
                    'edu_student_organization_change', 'auth_parent_mobile_change',
                    'auth_parent_account_cancellation', 'auth_parent_mobile_manual_recovery',
                    'auth_student_account_cancellation', 'auth_student_wechat_binding',
                    'auth_student_wechat_binding_audit', 'sys_organization_change',
                    'sys_organization_change_audit', 'sys_iam_change_audit')
                  and column_name = 'id'
                  and upper(data_type) = 'BIGINT'
                  and is_identity = 'NO'
                """, Integer.class);

        assertThat(idColumnCount).isEqualTo(77);
    }

    @Test
    void addsDictionaryManagementCapabilityAndPermissionsThroughV51() {
        Integer migrationCount = jdbcTemplate.queryForObject("""
                select count(*) from flyway_schema_history
                where version = '51' and success = true
                """, Integer.class);
        Integer featureCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_feature_toggle
                where feature_code = 'DICTIONARY_MANAGEMENT'
                  and scope_key = 'GLOBAL'
                  and id >= 1000000000000000000
                """, Integer.class);
        Integer permissionCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_permission
                where permission_code in ('DICTIONARY_READ', 'DICTIONARY_MANAGE')
                  and client_type = 'WEB'
                  and id >= 1000000000000000000
                """, Integer.class);
        Integer administratorGrantCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_role_permission role_permission
                join sys_role role on role.id = role_permission.role_id
                join sys_permission permission on permission.id = role_permission.permission_id
                where role.role_code = 'SYS_ADMIN'
                  and permission.permission_code in ('DICTIONARY_READ', 'DICTIONARY_MANAGE')
                  and role_permission.effect = 'ALLOW'
                  and role_permission.id >= 1000000000000000000
                """, Integer.class);
        assertThat(migrationCount).isEqualTo(1);
        assertThat(featureCount).isEqualTo(1);
        assertThat(permissionCount).isEqualTo(2);
        assertThat(administratorGrantCount).isEqualTo(2);
    }

    @Test
    void addsCacheManagementCapabilityAndPermissionsThroughV52() {
        Integer migrationCount = jdbcTemplate.queryForObject("""
                select count(*) from flyway_schema_history
                where version = '52' and success = true
                """, Integer.class);
        Integer featureCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_feature_toggle
                where feature_code = 'CACHE_MANAGEMENT'
                  and scope_key = 'GLOBAL'
                  and id >= 1000000000000000000
                """, Integer.class);
        Integer permissionCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_permission
                where permission_code in ('CACHE_READ', 'CACHE_MANAGE', 'CACHE_REVIEW')
                  and client_type = 'WEB'
                  and id >= 1000000000000000000
                """, Integer.class);
        Integer grantCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_role_permission role_permission
                join sys_role role on role.id = role_permission.role_id
                join sys_permission permission on permission.id = role_permission.permission_id
                where ((role.role_code = 'SYS_ADMIN'
                         and permission.permission_code in ('CACHE_READ', 'CACHE_MANAGE'))
                    or (role.role_code = 'SYS_AUDITOR'
                         and permission.permission_code in ('CACHE_READ', 'CACHE_REVIEW')))
                  and role_permission.effect = 'ALLOW'
                  and role_permission.id >= 1000000000000000000
                """, Integer.class);

        assertThat(migrationCount).isEqualTo(1);
        assertThat(featureCount).isEqualTo(1);
        assertThat(permissionCount).isEqualTo(3);
        assertThat(grantCount).isEqualTo(4);
    }

    @Test
    void addsInterfaceServiceManagementCapabilityAndPermissionsThroughV53() {
        Integer migrationCount = jdbcTemplate.queryForObject("""
                select count(*) from flyway_schema_history
                where version = '53' and success = true
                """, Integer.class);
        Integer featureCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_feature_toggle
                where feature_code = 'INTERFACE_SERVICE_MANAGEMENT'
                  and scope_key = 'GLOBAL'
                  and id >= 1000000000000000000
                """, Integer.class);
        Integer permissionCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_permission
                where permission_code in ('INTERFACE_SERVICE_READ', 'INTERFACE_SERVICE_MANAGE', 'INTERFACE_SERVICE_REVIEW')
                  and client_type = 'WEB'
                  and id >= 1000000000000000000
                """, Integer.class);
        Integer grantCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_role_permission role_permission
                join sys_role role on role.id = role_permission.role_id
                join sys_permission permission on permission.id = role_permission.permission_id
                where ((role.role_code = 'SYS_ADMIN'
                         and permission.permission_code in ('INTERFACE_SERVICE_READ', 'INTERFACE_SERVICE_MANAGE'))
                    or (role.role_code = 'SYS_AUDITOR'
                         and permission.permission_code in ('INTERFACE_SERVICE_READ', 'INTERFACE_SERVICE_REVIEW')))
                  and role_permission.effect = 'ALLOW'
                  and role_permission.id >= 1000000000000000000
                """, Integer.class);

        assertThat(migrationCount).isEqualTo(1);
        assertThat(featureCount).isEqualTo(1);
        assertThat(permissionCount).isEqualTo(3);
        assertThat(grantCount).isEqualTo(4);
    }

    @Test
    void addsAttachmentManagementCapabilityAndPermissionsThroughV54() {
        Integer migrationCount = jdbcTemplate.queryForObject("""
                select count(*) from flyway_schema_history
                where version = '54' and success = true
                """, Integer.class);
        Integer ruleVersionColumnCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where table_name = 'sys_attachment_rule'
                  and column_name = 'version_no'
                  and upper(data_type) = 'BIGINT'
                  and is_nullable = 'NO'
                  and column_default = '0'
                """, Integer.class);
        Integer featureCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_feature_toggle
                where feature_code = 'ATTACHMENT_SERVICE'
                  and scope_key = 'GLOBAL'
                  and status = 'ENABLED'
                  and id >= 1000000000000000000
                """, Integer.class);
        Integer permissionCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_permission
                where permission_code in ('ATTACHMENT_RULE_READ', 'ATTACHMENT_RULE_MANAGE',
                    'ATTACHMENT_FILE_LEDGER_READ')
                  and client_type = 'WEB'
                  and id >= 1000000000000000000
                """, Integer.class);
        Integer grantCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_role_permission role_permission
                join sys_role role on role.id = role_permission.role_id
                join sys_permission permission on permission.id = role_permission.permission_id
                where role.role_code = 'SYS_ADMIN'
                  and permission.permission_code in ('ATTACHMENT_RULE_READ', 'ATTACHMENT_RULE_MANAGE',
                    'ATTACHMENT_FILE_LEDGER_READ')
                  and role_permission.effect = 'ALLOW'
                  and role_permission.id >= 1000000000000000000
                """, Integer.class);

        assertThat(migrationCount).isEqualTo(1);
        assertThat(ruleVersionColumnCount).isEqualTo(1);
        assertThat(featureCount).isEqualTo(1);
        assertThat(permissionCount).isEqualTo(3);
        assertThat(grantCount).isEqualTo(3);
    }

    @Test
    void addsImportExportTemplateManagementThroughV55() {
        Integer migrationCount = jdbcTemplate.queryForObject("""
                select count(*) from flyway_schema_history
                where version = '55' and success = true
                """, Integer.class);
        Integer versionColumnCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where table_name = 'sys_import_export_template'
                  and column_name = 'version_no'
                  and upper(data_type) = 'BIGINT'
                  and is_nullable = 'NO'
                  and column_default = '0'
                """, Integer.class);
        Integer featureCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_feature_toggle
                where feature_code = 'IMPORT_EXPORT_TEMPLATE_MANAGEMENT'
                  and scope_key = 'GLOBAL'
                  and status = 'ENABLED'
                  and id >= 1000000000000000000
                """, Integer.class);
        Integer permissionCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_permission
                where permission_code in ('IMPORT_EXPORT_TEMPLATE_READ', 'IMPORT_EXPORT_TEMPLATE_MANAGE')
                  and client_type = 'WEB'
                  and id >= 1000000000000000000
                """, Integer.class);
        Integer administratorGrantCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_role_permission role_permission
                join sys_role role on role.id = role_permission.role_id
                join sys_permission permission on permission.id = role_permission.permission_id
                where role.role_code = 'SYS_ADMIN'
                  and permission.permission_code in ('IMPORT_EXPORT_TEMPLATE_READ', 'IMPORT_EXPORT_TEMPLATE_MANAGE')
                  and role_permission.effect = 'ALLOW'
                  and role_permission.id >= 1000000000000000000
                """, Integer.class);
        Integer dictionaryTypeCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_dictionary_type
                where type_code in ('IMPORT_EXPORT_TEMPLATE_TYPE', 'IMPORT_EXPORT_TEMPLATE_MODULE',
                    'IMPORT_EXPORT_TEMPLATE_STATUS')
                  and id >= 1000000000000000000
                """, Integer.class);
        Integer dictionaryItemCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_dictionary_item item
                join sys_dictionary_type type on type.id = item.type_id
                where type.type_code in ('IMPORT_EXPORT_TEMPLATE_TYPE', 'IMPORT_EXPORT_TEMPLATE_MODULE',
                    'IMPORT_EXPORT_TEMPLATE_STATUS')
                  and item.id >= 1000000000000000000
                """, Integer.class);

        assertThat(migrationCount).isEqualTo(1);
        assertThat(versionColumnCount).isEqualTo(1);
        assertThat(featureCount).isEqualTo(1);
        assertThat(permissionCount).isEqualTo(2);
        assertThat(administratorGrantCount).isEqualTo(2);
        assertThat(dictionaryTypeCount).isEqualTo(3);
        assertThat(dictionaryItemCount).isEqualTo(7);
    }

    @Test
    void addsImportValidationJobFoundationThroughV56() {
        Integer migrationCount = jdbcTemplate.queryForObject("""
                select count(*) from flyway_schema_history
                where version = '56' and success = true
                """, Integer.class);
        Integer tableCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.tables
                where table_name in (
                    'sys_import_export_template_field',
                    'sys_import_job',
                    'sys_import_job_row_result'
                )
                """, Integer.class);
        Integer idColumnCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where table_name in (
                    'sys_import_export_template_field',
                    'sys_import_job',
                    'sys_import_job_row_result'
                )
                  and column_name = 'id'
                  and upper(data_type) = 'BIGINT'
                  and is_identity = 'NO'
                """, Integer.class);
        Integer featureCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_feature_toggle
                where feature_code = 'DATA_IMPORT_VALIDATION'
                  and scope_key = 'GLOBAL'
                  and status = 'ENABLED'
                  and id >= 1000000000000000000
                """, Integer.class);
        Integer permissionCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_permission
                where permission_code in ('IMPORT_JOB_READ', 'IMPORT_JOB_CREATE')
                  and client_type = 'WEB'
                  and id >= 1000000000000000000
                """, Integer.class);
        Integer administratorGrantCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_role_permission role_permission
                join sys_role role on role.id = role_permission.role_id
                join sys_permission permission on permission.id = role_permission.permission_id
                where role.role_code = 'SYS_ADMIN'
                  and permission.permission_code in ('IMPORT_JOB_READ', 'IMPORT_JOB_CREATE')
                  and role_permission.effect = 'ALLOW'
                  and role_permission.id >= 1000000000000000000
                """, Integer.class);
        Integer attachmentRuleCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_attachment_rule rule
                join sys_attachment_rule_extension extension on extension.rule_id = rule.id
                where rule.module_code = 'IMPORT_JOB'
                  and rule.file_category = 'IMPORT_VALIDATION'
                  and rule.status = 'ENABLED'
                  and rule.max_batch_count = 1
                  and rule.max_file_size_bytes = 10485760
                  and extension.extension = 'xlsx'
                  and rule.id >= 1000000000000000000
                  and extension.id >= 1000000000000000000
                """, Integer.class);

        assertThat(migrationCount).isEqualTo(1);
        assertThat(tableCount).isEqualTo(3);
        assertThat(idColumnCount).isEqualTo(3);
        assertThat(featureCount).isEqualTo(1);
        assertThat(permissionCount).isEqualTo(2);
        assertThat(administratorGrantCount).isEqualTo(2);
        assertThat(attachmentRuleCount).isEqualTo(1);
    }

    @Test
    void addsAsyncExportJobFoundationThroughV57() {
        Integer migrationCount = jdbcTemplate.queryForObject("""
                select count(*) from flyway_schema_history
                where version = '57' and success = true
                """, Integer.class);
        Integer tableCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.tables
                where table_name in ('sys_export_job', 'sys_export_job_event')
                """, Integer.class);
        Integer idColumnCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where table_name in ('sys_export_job', 'sys_export_job_event')
                  and column_name = 'id'
                  and upper(data_type) = 'BIGINT'
                  and is_identity = 'NO'
                """, Integer.class);
        Integer primaryKeyTableCount = jdbcTemplate.queryForObject("""
                select count(distinct constraints.table_name)
                from information_schema.table_constraints constraints
                join information_schema.key_column_usage key_columns
                  on constraints.constraint_catalog = key_columns.constraint_catalog
                 and constraints.constraint_schema = key_columns.constraint_schema
                 and constraints.constraint_name = key_columns.constraint_name
                where constraints.constraint_type = 'PRIMARY KEY'
                  and key_columns.column_name = 'id'
                  and upper(constraints.table_schema) = 'PUBLIC'
                """, Integer.class);
        Integer featureCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_feature_toggle
                where feature_code = 'DATA_EXPORT'
                  and scope_key = 'GLOBAL'
                  and status = 'ENABLED'
                  and id >= 1000000000000000000
                """, Integer.class);
        Integer permissionCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_permission
                where permission_code in ('EXPORT_JOB_READ', 'EXPORT_JOB_CREATE',
                    'EXPORT_SENSITIVE_SUBMIT', 'EXPORT_SENSITIVE_REVIEW')
                  and client_type = 'WEB'
                  and id >= 1000000000000000000
                """, Integer.class);
        Integer expectedGrantCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_role_permission role_permission
                join sys_role role on role.id = role_permission.role_id
                join sys_permission permission on permission.id = role_permission.permission_id
                where role_permission.effect = 'ALLOW'
                  and ((role.role_code = 'PARENT'
                        and permission.permission_code in ('EXPORT_JOB_READ', 'EXPORT_JOB_CREATE'))
                    or (role.role_code = 'SYS_ADMIN'
                        and permission.permission_code in ('EXPORT_JOB_READ', 'EXPORT_SENSITIVE_SUBMIT'))
                    or (role.role_code = 'SYS_AUDITOR'
                        and permission.permission_code = 'EXPORT_SENSITIVE_REVIEW'))
                  and role_permission.id >= 1000000000000000000
                """, Integer.class);
        Integer unexpectedGrantCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_role_permission role_permission
                join sys_role role on role.id = role_permission.role_id
                join sys_permission permission on permission.id = role_permission.permission_id
                where role.role_code in ('SYS_ADMIN', 'SYS_AUDITOR', 'PARENT')
                  and permission.permission_code in ('EXPORT_JOB_READ', 'EXPORT_JOB_CREATE',
                      'EXPORT_SENSITIVE_SUBMIT', 'EXPORT_SENSITIVE_REVIEW')
                  and role_permission.effect = 'ALLOW'
                  and not ((role.role_code = 'PARENT'
                            and permission.permission_code in ('EXPORT_JOB_READ', 'EXPORT_JOB_CREATE'))
                        or (role.role_code = 'SYS_ADMIN'
                            and permission.permission_code in ('EXPORT_JOB_READ', 'EXPORT_SENSITIVE_SUBMIT'))
                        or (role.role_code = 'SYS_AUDITOR'
                            and permission.permission_code = 'EXPORT_SENSITIVE_REVIEW'))
                """, Integer.class);
        Integer attachmentRuleCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_attachment_rule rule
                join sys_attachment_rule_extension extension on extension.rule_id = rule.id
                where rule.module_code = 'EXPORT_JOB'
                  and rule.file_category = 'REPORT_EXPORT'
                  and rule.status = 'ENABLED'
                  and rule.max_batch_count = 1
                  and extension.extension = 'xlsx'
                  and rule.id >= 1000000000000000000
                  and extension.id >= 1000000000000000000
                """, Integer.class);
        Integer requiredIndexCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.indexes
                where upper(table_name) in ('SYS_EXPORT_JOB', 'SYS_EXPORT_JOB_EVENT')
                  and upper(index_name) in ('IDX_SYS_EXPORT_JOB_STATUS_QUEUE',
                      'IDX_SYS_EXPORT_JOB_REQUESTER_CREATED',
                      'IDX_SYS_EXPORT_JOB_TYPE_CREATED',
                      'IDX_SYS_EXPORT_JOB_EVENT_JOB_TIME')
                """, Integer.class);

        assertThat(migrationCount).isEqualTo(1);
        assertThat(tableCount).isEqualTo(2);
        assertThat(idColumnCount).isEqualTo(2);
        assertThat(primaryKeyTableCount).isGreaterThanOrEqualTo(82);
        assertThat(featureCount).isEqualTo(1);
        assertThat(permissionCount).isEqualTo(4);
        assertThat(expectedGrantCount).isEqualTo(5);
        assertThat(unexpectedGrantCount).isZero();
        assertThat(attachmentRuleCount).isEqualTo(1);
        assertThat(requiredIndexCount).isEqualTo(4);
    }

    @Test
    void addsStudentBatchImportThroughV58() {
        Integer migrationCount = jdbcTemplate.queryForObject("""
                select count(*) from flyway_schema_history
                where version = '58' and success = true
                """, Integer.class);
        Integer tableCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.tables
                where table_name in ('sys_student_import_execution', 'sys_student_import_row')
                """, Integer.class);
        Integer idColumnCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where table_name in ('sys_student_import_execution', 'sys_student_import_row')
                  and column_name = 'id'
                  and upper(data_type) = 'BIGINT'
                  and is_identity = 'NO'
                """, Integer.class);
        Integer primaryKeyTableCount = jdbcTemplate.queryForObject("""
                select count(distinct constraints.table_name)
                from information_schema.table_constraints constraints
                join information_schema.key_column_usage key_columns
                  on constraints.constraint_catalog = key_columns.constraint_catalog
                 and constraints.constraint_schema = key_columns.constraint_schema
                 and constraints.constraint_name = key_columns.constraint_name
                where constraints.constraint_type = 'PRIMARY KEY'
                  and key_columns.column_name = 'id'
                  and upper(constraints.table_schema) = 'PUBLIC'
                """, Integer.class);
        Integer featureCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_feature_toggle
                where feature_code = 'STUDENT_BATCH_IMPORT'
                  and scope_key = 'GLOBAL'
                  and status = 'ENABLED'
                  and id >= 1000000000000000000
                """, Integer.class);
        Integer permissionCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_permission
                where permission_code in ('STUDENT_IMPORT_EXECUTE',
                    'STUDENT_IMPORT_RESULT_READ', 'STUDENT_IMPORT_CREDENTIAL_DOWNLOAD')
                  and client_type = 'WEB'
                  and id >= 1000000000000000000
                """, Integer.class);
        Integer organizationAdministratorGrantCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_role_permission role_permission
                join sys_role role on role.id = role_permission.role_id
                join sys_permission permission on permission.id = role_permission.permission_id
                where role.role_code = 'ORG_ADMIN'
                  and permission.permission_code in ('IMPORT_JOB_READ', 'IMPORT_JOB_CREATE',
                      'STUDENT_IMPORT_EXECUTE', 'STUDENT_IMPORT_RESULT_READ',
                      'STUDENT_IMPORT_CREDENTIAL_DOWNLOAD')
                  and role_permission.effect = 'ALLOW'
                  and role_permission.id >= 1000000000000000000
                """, Integer.class);
        Integer uniqueExecutionConstraintCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.table_constraints
                where upper(table_name) = 'SYS_STUDENT_IMPORT_EXECUTION'
                  and upper(constraint_name) = 'UK_SYS_STUDENT_IMPORT_VALIDATION_JOB'
                  and constraint_type = 'UNIQUE'
                """, Integer.class);
        Integer attachmentRuleCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_attachment_rule rule
                join sys_attachment_rule_extension extension on extension.rule_id = rule.id
                where rule.module_code = 'STUDENT_IMPORT'
                  and rule.file_category = 'INITIAL_CREDENTIAL'
                  and rule.status = 'ENABLED'
                  and rule.max_batch_count = 1
                  and extension.extension = 'enc'
                  and rule.id >= 1000000000000000000
                  and extension.id >= 1000000000000000000
                """, Integer.class);

        assertThat(migrationCount).isEqualTo(1);
        assertThat(tableCount).isEqualTo(2);
        assertThat(idColumnCount).isEqualTo(2);
        assertThat(primaryKeyTableCount).isGreaterThanOrEqualTo(84);
        assertThat(featureCount).isEqualTo(1);
        assertThat(permissionCount).isEqualTo(3);
        assertThat(organizationAdministratorGrantCount).isEqualTo(5);
        assertThat(uniqueExecutionConstraintCount).isEqualTo(1);
        assertThat(attachmentRuleCount).isEqualTo(1);
    }

    @Test
    void addsTeacherManagementThroughV59() {
        Integer migrationCount = jdbcTemplate.queryForObject("""
                select count(*) from flyway_schema_history
                where version = '59' and success = true
                """, Integer.class);
        Integer auditTableCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.tables
                where upper(table_name) = 'EDU_TEACHER_CLASS_CHANGE_LOG'
                """, Integer.class);
        Integer auditIdColumnCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where upper(table_name) = 'EDU_TEACHER_CLASS_CHANGE_LOG'
                  and lower(column_name) = 'id'
                  and upper(data_type) = 'BIGINT'
                  and is_identity = 'NO'
                """, Integer.class);
        Integer primaryKeyTableCount = jdbcTemplate.queryForObject("""
                select count(distinct constraints.table_name)
                from information_schema.table_constraints constraints
                join information_schema.key_column_usage key_columns
                  on constraints.constraint_catalog = key_columns.constraint_catalog
                 and constraints.constraint_schema = key_columns.constraint_schema
                 and constraints.constraint_name = key_columns.constraint_name
                where constraints.constraint_type = 'PRIMARY KEY'
                  and key_columns.column_name = 'id'
                  and upper(constraints.table_schema) = 'PUBLIC'
                """, Integer.class);
        Integer featureCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_feature_toggle
                where feature_code = 'TEACHER_MANAGEMENT'
                  and scope_key = 'GLOBAL'
                  and status = 'ENABLED'
                  and id >= 1000000000000000000
                """, Integer.class);
        Integer permissionCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_permission
                where permission_code in ('TEACHER_READ', 'TEACHER_CREATE', 'TEACHER_UPDATE',
                    'TEACHER_STATUS_CHANGE', 'TEACHER_PASSWORD_RESET', 'TEACHER_BATCH_MANAGE')
                  and ((permission_code = 'TEACHER_BATCH_MANAGE' and client_type = 'WEB')
                    or (permission_code <> 'TEACHER_BATCH_MANAGE' and client_type = 'BOTH'))
                  and id >= 1000000000000000000
                """, Integer.class);
        Integer organizationAdministratorGrantCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_role_permission role_permission
                join sys_role role on role.id = role_permission.role_id
                join sys_permission permission on permission.id = role_permission.permission_id
                where role.role_code = 'ORG_ADMIN'
                  and permission.permission_code in ('TEACHER_READ', 'TEACHER_CREATE', 'TEACHER_UPDATE',
                    'TEACHER_STATUS_CHANGE', 'TEACHER_PASSWORD_RESET', 'TEACHER_BATCH_MANAGE')
                  and role_permission.effect = 'ALLOW'
                  and role_permission.id >= 1000000000000000000
                """, Integer.class);
        Integer auditIndexCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.indexes
                where upper(table_name) = 'EDU_TEACHER_CLASS_CHANGE_LOG'
                  and upper(index_name) in ('IDX_TEACHER_CLASS_CHANGE_TEACHER_TIME',
                      'IDX_TEACHER_CLASS_CHANGE_CLASS_TIME')
                """, Integer.class);

        assertThat(migrationCount).isEqualTo(1);
        assertThat(auditTableCount).isEqualTo(1);
        assertThat(auditIdColumnCount).isEqualTo(1);
        assertThat(primaryKeyTableCount).isEqualTo(90);
        assertThat(featureCount).isEqualTo(1);
        assertThat(permissionCount).isEqualTo(6);
        assertThat(organizationAdministratorGrantCount).isEqualTo(6);
        assertThat(auditIndexCount).isEqualTo(2);
    }

    @Test
    void addsStudentExceptionReportThroughV61() {
        Integer migrationCount = jdbcTemplate.queryForObject("""
                select count(*) from flyway_schema_history
                where version = '61' and success = true
                """, Integer.class);
        Integer tableCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.tables
                where upper(table_name) in ('EDU_EXCEPTION_REPORT',
                    'EDU_EXCEPTION_REPORT_ACTION', 'MSG_LOCAL_EVENT')
                """, Integer.class);
        Integer nonIdentityIdCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where upper(table_name) in ('EDU_EXCEPTION_REPORT',
                    'EDU_EXCEPTION_REPORT_ACTION', 'MSG_LOCAL_EVENT')
                  and lower(column_name) = 'id'
                  and upper(data_type) = 'BIGINT'
                  and is_identity = 'NO'
                """, Integer.class);
        Integer featureCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_feature_toggle
                where feature_code = 'STUDENT_EXCEPTION_REPORT'
                  and scope_key = 'GLOBAL'
                  and status = 'ENABLED'
                  and id >= 1000000000000000000
                """, Integer.class);
        Integer permissionCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_permission
                where permission_code in ('EXCEPTION_REPORT_CREATE',
                    'EXCEPTION_REPORT_READ', 'EXCEPTION_REPORT_HANDLE')
                  and client_type = 'BOTH'
                  and id >= 1000000000000000000
                """, Integer.class);
        Integer grantCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sys_role_permission role_permission
                join sys_role role on role.id = role_permission.role_id
                join sys_permission permission on permission.id = role_permission.permission_id
                where ((role.role_code = 'TEACHER'
                        and permission.permission_code in ('EXCEPTION_REPORT_CREATE', 'EXCEPTION_REPORT_READ'))
                    or (role.role_code = 'ORG_ADMIN'
                        and permission.permission_code in ('EXCEPTION_REPORT_READ', 'EXCEPTION_REPORT_HANDLE')))
                  and role_permission.effect = 'ALLOW'
                  and role_permission.id >= 1000000000000000000
                """, Integer.class);

        assertThat(migrationCount).isEqualTo(1);
        assertThat(tableCount).isEqualTo(3);
        assertThat(nonIdentityIdCount).isEqualTo(3);
        assertThat(featureCount).isEqualTo(1);
        assertThat(permissionCount).isEqualTo(3);
        assertThat(grantCount).isEqualTo(4);
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
