package com.lingdong.learning.templateconfig.application;

import com.lingdong.learning.attachment.application.AttachmentFileApplicationService;
import com.lingdong.learning.attachment.application.AttachmentRuleApplicationService;
import com.lingdong.learning.attachment.application.ManagedAttachmentContentService;
import com.lingdong.learning.attachment.application.CompleteAttachmentUploadCommand;
import com.lingdong.learning.attachment.application.CreateAttachmentRuleCommand;
import com.lingdong.learning.attachment.application.ManagedFile;
import com.lingdong.learning.attachment.application.RegisterAttachmentFileCommand;
import com.lingdong.learning.attachment.domain.FileStatus;
import com.lingdong.learning.attachment.infrastructure.persistence.ManagedFileMapper;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.dictionary.domain.DictionaryItem;
import com.lingdong.learning.dictionary.infrastructure.persistence.DictionaryItemMapper;
import com.lingdong.learning.iam.application.CreateCustomRoleCommand;
import com.lingdong.learning.iam.application.RoleApplicationService;
import com.lingdong.learning.iam.domain.Role;
import com.lingdong.learning.iam.domain.RoleDataScope;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.permission.application.ConfigureRolePermissionCommand;
import com.lingdong.learning.permission.application.PermissionAdministrationService;
import com.lingdong.learning.permission.domain.Permission;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.permission.domain.PermissionEffect;
import com.lingdong.learning.permission.infrastructure.persistence.PermissionMapper;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.templateconfig.domain.ImportExportTemplateStatus;
import com.lingdong.learning.templateconfig.domain.TemplateType;
import com.lingdong.learning.templateconfig.infrastructure.persistence.ImportExportTemplateMapper;
import com.lingdong.learning.user.application.AssignRoleToUserCommand;
import com.lingdong.learning.user.application.CreateUserCommand;
import com.lingdong.learning.user.application.UserAccessApplicationService;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class ImportExportTemplateApplicationServiceTest {
    @Autowired private ImportExportTemplateApplicationService templateApplicationService;
    @Autowired private AttachmentRuleApplicationService attachmentRuleApplicationService;
    @Autowired private AttachmentFileApplicationService attachmentFileApplicationService;
    @Autowired private UserAccessApplicationService userAccessApplicationService;
    @Autowired private RoleMapper roleMapper;
    @Autowired private RoleApplicationService roleApplicationService;
    @Autowired private PermissionAdministrationService permissionAdministrationService;
    @Autowired private PermissionMapper permissionMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private IdGenerator idGenerator;
    @Autowired private ImportTemplateFieldApplicationService fieldApplicationService;

    @Test
    void switchesTheCurrentDefaultAndClearsItWhenTheTemplateIsDisabled() {
        User administrator = createUserWithRole("template_switch_admin", "模板管理员", "SYS_ADMIN");
        User uploader = createUser("template_switch_uploader", "模板上传人");
        attachmentRuleApplicationService.createRule(new CreateAttachmentRuleCommand(
                administrator.id(), "TEMPLATE_SWITCH", "SPREADSHEET", "模板表格",
                List.of("xlsx"), 10_240L, 1, true
        ));

        ImportExportTemplate firstTemplate = templateApplicationService.createTemplate(
                new CreateImportExportTemplateCommand(
                        administrator.id(), "学生导入模板", TemplateType.IMPORT, "STUDENT", "V1",
                        createAvailableFile(uploader, "TEMPLATE_SWITCH").id(), true
                ));
        ImportExportTemplate secondTemplate = templateApplicationService.createTemplate(
                new CreateImportExportTemplateCommand(
                        administrator.id(), "学生导入模板", TemplateType.IMPORT, "STUDENT", "V2",
                        createAvailableFile(uploader, "TEMPLATE_SWITCH").id(), true
                ));

        assertThat(Long.toString(firstTemplate.id())).hasSize(19);
        assertThat(templateApplicationService.findTemplate(administrator.id(), firstTemplate.id())
                .defaultTemplate()).isFalse();
        assertThat(templateApplicationService.findCurrentDefault("student", TemplateType.IMPORT).id())
                .isEqualTo(secondTemplate.id());

        ImportExportTemplate disabled = templateApplicationService.disableTemplate(
                administrator.id(), secondTemplate.id(), secondTemplate.versionNo());

        assertThat(disabled.defaultTemplate()).isFalse();
        assertThat(disabled.status()).isEqualTo(ImportExportTemplateStatus.DISABLED);
        assertThat(disabled.versionNo()).isEqualTo(1L);
        assertThat(templateApplicationService.findCurrentDefault("STUDENT", TemplateType.IMPORT)).isNull();
        assertThatThrownBy(() -> templateApplicationService.setDefaultTemplate(
                administrator.id(), secondTemplate.id(), disabled.versionNo()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已停用");
    }

    @Test
    void rejectsMissingPermissionAndFilesThatAreNotAvailable() {
        User administrator = createUserWithRole("template_validation_admin", "模板校验管理员", "SYS_ADMIN");
        User ordinaryUser = createUser("template_validation_user", "普通用户");
        User uploader = createUser("template_validation_uploader", "模板校验上传人");
        attachmentRuleApplicationService.createRule(new CreateAttachmentRuleCommand(
                administrator.id(), "TEMPLATE_VALIDATION", "SPREADSHEET", "模板校验表格",
                List.of("xlsx"), 10_240L, 1, true
        ));
        ManagedFile uploadingFile = attachmentFileApplicationService.registerUpload(new RegisterAttachmentFileCommand(
                uploader.id(), "TEMPLATE_VALIDATION", "SPREADSHEET", "pending.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", 1_024L
        ));

        assertThatThrownBy(() -> templateApplicationService.createTemplate(new CreateImportExportTemplateCommand(
                administrator.id(), "待完成模板", TemplateType.EXPORT, "LEARNING_TASK", "V1",
                uploadingFile.id(), false
        ))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未完成");

        assertThatThrownBy(() -> templateApplicationService.createTemplate(new CreateImportExportTemplateCommand(
                ordinaryUser.id(), "无权限模板", TemplateType.EXPORT, "LEARNING_TASK", "V2",
                createAvailableFile(uploader, "TEMPLATE_VALIDATION").id(), false
        ))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("模板管理权限");
    }

    @Test
    void queriesOptionsAndChangesStatusWithOptimisticConcurrency() {
        User administrator = createUserWithRole("template_lifecycle_admin", "模板生命周期管理员", "SYS_ADMIN");
        attachmentRuleApplicationService.createRule(new CreateAttachmentRuleCommand(
                administrator.id(), "TEMPLATE_LIFECYCLE", "TEMPLATE_FILE", "模板文件",
                List.of("xlsx"), 10_240L, 1, false
        ));
        ImportExportTemplate created = templateApplicationService.createTemplate(
                new CreateImportExportTemplateCommand(
                        administrator.id(), "学员导入模板", TemplateType.IMPORT, "STUDENT", "LIFECYCLE_V1",
                        createAvailableFile(administrator, "TEMPLATE_LIFECYCLE", "TEMPLATE_FILE", "template.xlsx").id(),
                        false
                ));

        ImportExportTemplateOptions options = templateApplicationService.findOptions(administrator.id());
        assertThat(options.templateTypes()).extracting(ImportExportTemplateOption::code)
                .containsExactly("IMPORT", "EXPORT");
        assertThat(options.modules()).extracting(ImportExportTemplateOption::code)
                .containsExactly("STUDENT", "LEARNING_TASK", "REPORT", "DICTIONARY_REPORT", "TEMPLATE_REPORT", "INTERFACE_REPORT", "CACHE_REPORT", "SYSTEM_TASK_REPORT", "REWARD_EXCHANGE_REPORT", "EXCEPTION_REPORT_EXPORT", "ATTACHMENT_LEDGER_REPORT", "STUDENT_TASK_REPORT_EXPORT", "ORGANIZATION_TASK_STATISTICS_EXPORT", "ATTENDANCE_LEDGER_EXPORT");
        assertThat(templateApplicationService.listTemplates(new ImportExportTemplateQuery(
                administrator.id(), "学员", TemplateType.IMPORT, "student", ImportExportTemplateStatus.ENABLED
        ))).extracting(ImportExportTemplate::id).containsExactly(created.id());

        ImportExportTemplate disabled = templateApplicationService.disableTemplate(
                administrator.id(), created.id(), created.versionNo());
        fieldApplicationService.replace(new ReplaceImportTemplateFieldsCommand(
                administrator.id(), created.id(), disabled.versionNo(), importFields()
        ));
        disabled = templateApplicationService.findTemplate(administrator.id(), created.id());
        ImportExportTemplate repeated = templateApplicationService.disableTemplate(
                administrator.id(), created.id(), disabled.versionNo());
        assertThat(repeated.versionNo()).isEqualTo(disabled.versionNo());
        assertThatThrownBy(() -> templateApplicationService.enableTemplate(
                administrator.id(), created.id(), created.versionNo()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("版本已变化");

        ImportExportTemplate enabled = templateApplicationService.enableTemplate(
                administrator.id(), created.id(), disabled.versionNo());
        ImportExportTemplate currentDefault = templateApplicationService.setDefaultTemplate(
                administrator.id(), created.id(), enabled.versionNo());
        assertThat(currentDefault.defaultTemplate()).isTrue();
        assertThat(currentDefault.versionNo()).isEqualTo(4L);
    }

    @Test
    void letsDynamicallyAuthorizedCustomRoleManageTemplates() {
        User administrator = createUserWithRole("template_permission_admin", "模板授权管理员", "SYS_ADMIN");
        Role operationsRole = roleApplicationService.createCustomRole(new CreateCustomRoleCommand(
                "TEMPLATE_OPS", "模板运维", "维护导入导出模板", RoleDataScope.ALL, administrator.id()
        ));
        grantPermission(administrator.id(), operationsRole.id(), "IMPORT_EXPORT_TEMPLATE_READ");
        grantPermission(administrator.id(), operationsRole.id(), "IMPORT_EXPORT_TEMPLATE_MANAGE");
        User operator = createUser("template_custom_operator", "模板运维人员");
        userAccessApplicationService.assignRole(new AssignRoleToUserCommand(operator.id(), operationsRole.id(), null));
        attachmentRuleApplicationService.createRule(new CreateAttachmentRuleCommand(
                administrator.id(), "TEMPLATE_CUSTOM_ROLE", "TEMPLATE_FILE", "运维模板文件",
                List.of("csv"), 10_240L, 1, false
        ));

        ImportExportTemplate created = templateApplicationService.createTemplate(
                new CreateImportExportTemplateCommand(
                        operator.id(), "任务导入模板", TemplateType.IMPORT, "LEARNING_TASK", "V1",
                        createAvailableFile(operator, "TEMPLATE_CUSTOM_ROLE", "TEMPLATE_FILE", "template.csv").id(),
                        false
                ));

        assertThat(templateApplicationService.listTemplates(new ImportExportTemplateQuery(
                operator.id(), null, null, "LEARNING_TASK", null
        ))).extracting(ImportExportTemplate::id).containsExactly(created.id());
    }

    @Test
    @Transactional
    void rejectsDisabledTemplateDictionaryOption() {
        User administrator = createUserWithRole("template_dictionary_admin", "模板字典管理员", "SYS_ADMIN");
        attachmentRuleApplicationService.createRule(new CreateAttachmentRuleCommand(
                administrator.id(), "TEMPLATE_DICTIONARY", "TEMPLATE_FILE", "字典模板文件",
                List.of("xlsx"), 10_240L, 1, false
        ));
        jdbcTemplate.update("""
                update sys_dictionary_item set status = 'DISABLED'
                where item_code = 'REPORT'
                  and type_id = (select id from sys_dictionary_type
                                 where type_code = 'IMPORT_EXPORT_TEMPLATE_MODULE')
                """);

        assertThatThrownBy(() -> templateApplicationService.createTemplate(new CreateImportExportTemplateCommand(
                administrator.id(), "报表导出模板", TemplateType.EXPORT, "REPORT", "V1",
                createAvailableFile(administrator, "TEMPLATE_DICTIONARY", "TEMPLATE_FILE", "report.xlsx").id(), false
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("适用模块");
    }

    @Test
    void uploadsRelatesAndDownloadsDisabledTemplateContent() {
        User administrator = createUserWithRole("template_upload_admin", "模板上传管理员", "SYS_ADMIN");
        ensureTemplateFileRule(administrator.id());
        byte[] content = "student,name\n10000001,张同学".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        ImportExportTemplate created = templateApplicationService.createTemplate(
                new CreateImportExportTemplateUploadCommand(
                        administrator.id(), "学生导入示例", TemplateType.IMPORT, "STUDENT", "UPLOAD_CONTENT_V1",
                        "students.csv", "text/csv", content, false, importFields()
                ));

        assertThat(jdbcTemplate.queryForObject(
                "select uploader_id from sys_file where id = ?", Long.class, created.fileId()))
                .isEqualTo(administrator.id());
        assertThat(jdbcTemplate.queryForMap(
                "select module_code, file_category, status from sys_file where id = ?", created.fileId()))
                .containsEntry("MODULE_CODE", "IMPORT_EXPORT_TEMPLATE")
                .containsEntry("FILE_CATEGORY", "TEMPLATE_FILE")
                .containsEntry("STATUS", "AVAILABLE");
        assertThat(jdbcTemplate.queryForMap("""
                select module_code, relation_type, visible_scope, status
                from sys_file_relation where file_id = ? and business_id = ?
                """, created.fileId(), created.id()))
                .containsEntry("MODULE_CODE", "IMPORT_EXPORT_TEMPLATE")
                .containsEntry("RELATION_TYPE", "TEMPLATE_FILE")
                .containsEntry("VISIBLE_SCOPE", "SYSTEM_CONFIGURATION")
                .containsEntry("STATUS", "ACTIVE");
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from sys_import_export_template_field
                where template_id = ? and field_code = 'STUDENT_CODE'
                """, Integer.class, created.id())).isEqualTo(1);

        ImportExportTemplate disabled = templateApplicationService.disableTemplate(
                administrator.id(), created.id(), created.versionNo());
        ImportExportTemplateContent downloaded = templateApplicationService.downloadTemplate(
                administrator.id(), disabled.id());

        assertThat(downloaded.originalName()).isEqualTo("students.csv");
        assertThat(downloaded.contentType()).isEqualTo("text/csv");
        assertThat(downloaded.content()).containsExactly(content);
    }

    @Test
    void rejectsDownloadingTemplateWhoseFileIsNotAvailable() {
        User administrator = createUserWithRole("template_pending_admin", "模板待上传管理员", "SYS_ADMIN");
        ensureTemplateFileRule(administrator.id());
        ManagedFile pendingFile = attachmentFileApplicationService.registerUpload(
                new RegisterAttachmentFileCommand(
                        administrator.id(), "IMPORT_EXPORT_TEMPLATE", "TEMPLATE_FILE", "pending.csv",
                        "text/csv", 128L
                ));
        long templateId = idGenerator.nextId();
        jdbcTemplate.update("""
                insert into sys_import_export_template
                (id, template_name, template_type, module_code, version, file_id,
                 is_default, default_scope_key, status, version_no)
                values (?, '待完成模板', 'IMPORT', 'STUDENT', 'PENDING_FILE_V1', ?,
                        0, ?, 'ENABLED', 0)
                """, templateId, pendingFile.id(), "ID:" + templateId);

        assertThatThrownBy(() -> templateApplicationService.downloadTemplate(administrator.id(), templateId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未完成");
    }

    @Test
    void removesStoredContentWhenTemplatePersistenceFails() {
        ImportExportTemplateMapper templateMapper = mock(ImportExportTemplateMapper.class);
        ManagedFileMapper fileMapper = mock(ManagedFileMapper.class);
        DictionaryItemMapper dictionaryItemMapper = mock(DictionaryItemMapper.class);
        PermissionDecisionService permissionDecisionService = mock(PermissionDecisionService.class);
        IdGenerator localIdGenerator = mock(IdGenerator.class);
        ManagedAttachmentContentService contentService = mock(ManagedAttachmentContentService.class);
        AttachmentFileApplicationService fileService = mock(AttachmentFileApplicationService.class);
        ImportTemplateFieldApplicationService fieldService = mock(ImportTemplateFieldApplicationService.class);
        ImportExportTemplateApplicationService service = new ImportExportTemplateApplicationService(
                templateMapper, fileMapper, dictionaryItemMapper, permissionDecisionService,
                localIdGenerator, contentService, fileService, fieldService
        );
        long operatorId = 1874244142494646701L;
        ManagedFile storedFile = new ManagedFile(
                1874244142494646702L, "attachment/template/failure", "failure.csv", "csv", "text/csv",
                3L, operatorId, "IMPORT_EXPORT_TEMPLATE", "TEMPLATE_FILE", "0".repeat(64),
                FileStatus.AVAILABLE
        );
        when(permissionDecisionService.isAllowed(operatorId, PermissionClient.WEB,
                "IMPORT_EXPORT_TEMPLATE_MANAGE")).thenReturn(true);
        when(dictionaryItemMapper.findEnabledByTypeCode("IMPORT_EXPORT_TEMPLATE_TYPE"))
                .thenReturn(List.of(DictionaryItem.enabled(1L, 1L, "IMPORT", "导入", 10, true)));
        when(dictionaryItemMapper.findEnabledByTypeCode("IMPORT_EXPORT_TEMPLATE_MODULE"))
                .thenReturn(List.of(DictionaryItem.enabled(2L, 2L, "STUDENT", "学生", 10, true)));
        when(contentService.store(operatorId, "IMPORT_EXPORT_TEMPLATE", "TEMPLATE_FILE",
                "failure.csv", "text/csv", new byte[]{1, 2, 3})).thenReturn(storedFile);
        when(localIdGenerator.nextId()).thenReturn(1874244142494646703L);
        when(templateMapper.insert(any())).thenReturn(0);

        assertThatThrownBy(() -> service.createTemplate(new CreateImportExportTemplateUploadCommand(
                operatorId, "失败补偿模板", TemplateType.IMPORT, "STUDENT", "FAILURE_V1",
                "failure.csv", "text/csv", new byte[]{1, 2, 3}, false, importFields()
        ))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("模板保存失败");

        verify(contentService).discardContent("attachment/template/failure");
    }

    @Test
    void rejectsEnablingHistoricalImportTemplateWithoutFieldMapping() {
        User administrator = createUserWithRole(
                "template_missing_field_admin", "模板缺失字段管理员", "SYS_ADMIN");
        attachmentRuleApplicationService.createRule(new CreateAttachmentRuleCommand(
                administrator.id(), "TEMPLATE_MISSING_FIELD", "TEMPLATE_FILE", "历史模板文件",
                List.of("xlsx"), 10_240L, 1, false
        ));
        ImportExportTemplate created = templateApplicationService.createTemplate(
                new CreateImportExportTemplateCommand(
                        administrator.id(), "历史导入模板", TemplateType.IMPORT, "STUDENT", "HISTORY_V1",
                        createAvailableFile(administrator, "TEMPLATE_MISSING_FIELD", "TEMPLATE_FILE",
                                "history.xlsx").id(), false
                ));
        ImportExportTemplate disabled = templateApplicationService.disableTemplate(
                administrator.id(), created.id(), created.versionNo());

        assertThatThrownBy(() -> templateApplicationService.enableTemplate(
                administrator.id(), disabled.id(), disabled.versionNo()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("尚未配置字段映射");
    }

    private ManagedFile createAvailableFile(User uploader, String moduleCode) {
        return createAvailableFile(uploader, moduleCode, "SPREADSHEET", "template.xlsx");
    }

    private static List<ImportTemplateFieldInput> importFields() {
        return List.of(new ImportTemplateFieldInput(
                "STUDENT_CODE", "学生账号", com.lingdong.learning.templateconfig.domain.ImportTemplateFieldDataType.TEXT,
                true, 8, null, 10
        ));
    }

    private void ensureTemplateFileRule(Long administratorId) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*) from sys_attachment_rule
                where module_code = 'IMPORT_EXPORT_TEMPLATE' and file_category = 'TEMPLATE_FILE'
                """, Integer.class);
        if (count != null && count == 0) {
            attachmentRuleApplicationService.createRule(new CreateAttachmentRuleCommand(
                    administratorId, "IMPORT_EXPORT_TEMPLATE", "TEMPLATE_FILE", "导入导出模板文件",
                    List.of("csv", "xlsx"), 10_485_760L, 1, false
            ));
        }
    }

    private ManagedFile createAvailableFile(
            User uploader, String moduleCode, String fileCategory, String fileName
    ) {
        String contentType = fileName.endsWith(".csv")
                ? "text/csv"
                : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        ManagedFile file = attachmentFileApplicationService.registerUpload(new RegisterAttachmentFileCommand(
                uploader.id(), moduleCode, fileCategory, fileName, contentType, 1_024L
        ));
        return attachmentFileApplicationService.completeUpload(new CompleteAttachmentUploadCommand(
                file.id(), 1_024L, contentType
        ));
    }

    private void grantPermission(Long administratorId, Long roleId, String permissionCode) {
        Permission permission = permissionMapper.findByCode(permissionCode);
        permissionAdministrationService.configureRolePermission(new ConfigureRolePermissionCommand(
                administratorId, roleId, permission.id(), PermissionEffect.ALLOW
        ));
    }

    private User createUserWithRole(String username, String displayName, String roleCode) {
        User user = createUser(username, displayName);
        Role role = roleMapper.findByCode(roleCode);
        userAccessApplicationService.assignRole(new AssignRoleToUserCommand(user.id(), role.id(), null));
        return user;
    }

    private User createUser(String username, String displayName) {
        return userAccessApplicationService.createUser(
                new CreateUserCommand(username, displayName, null, UserType.PLATFORM));
    }
}
