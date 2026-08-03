package com.lingdong.learning.templateconfig.application;

import com.lingdong.learning.attachment.application.AttachmentFileApplicationService;
import com.lingdong.learning.attachment.application.AttachmentRuleApplicationService;
import com.lingdong.learning.attachment.application.CompleteAttachmentUploadCommand;
import com.lingdong.learning.attachment.application.CreateAttachmentRuleCommand;
import com.lingdong.learning.attachment.application.ManagedFile;
import com.lingdong.learning.attachment.application.RegisterAttachmentFileCommand;
import com.lingdong.learning.iam.domain.Role;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.templateconfig.domain.ImportExportTemplateStatus;
import com.lingdong.learning.templateconfig.domain.TemplateType;
import com.lingdong.learning.user.application.AssignRoleToUserCommand;
import com.lingdong.learning.user.application.CreateUserCommand;
import com.lingdong.learning.user.application.UserAccessApplicationService;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ImportExportTemplateApplicationServiceTest {
    @Autowired private ImportExportTemplateApplicationService templateApplicationService;
    @Autowired private AttachmentRuleApplicationService attachmentRuleApplicationService;
    @Autowired private AttachmentFileApplicationService attachmentFileApplicationService;
    @Autowired private UserAccessApplicationService userAccessApplicationService;
    @Autowired private RoleMapper roleMapper;

    @Test
    void switchesTheCurrentDefaultAndClearsItWhenTheTemplateIsDisabled() {
        User administrator = createUserWithRole("template_switch_admin", "模板管理员", "SYS_ADMIN");
        User uploader = createUser("template_switch_uploader", "模板上传人");
        attachmentRuleApplicationService.createRule(new CreateAttachmentRuleCommand(
                administrator.id(), "TEMPLATE_SWITCH", "SPREADSHEET", "模板表格", List.of("xlsx"), 10_240L, 1, true
        ));

        ImportExportTemplate firstTemplate = templateApplicationService.createTemplate(new CreateImportExportTemplateCommand(
                administrator.id(), "学生导入模板", TemplateType.IMPORT, "STUDENT", "V1",
                createAvailableFile(uploader, "TEMPLATE_SWITCH").id(), true
        ));
        ImportExportTemplate secondTemplate = templateApplicationService.createTemplate(new CreateImportExportTemplateCommand(
                administrator.id(), "学生导入模板", TemplateType.IMPORT, "STUDENT", "V2",
                createAvailableFile(uploader, "TEMPLATE_SWITCH").id(), true
        ));

        assertThat(Long.toString(firstTemplate.id())).hasSize(19);
        assertThat(templateApplicationService.findTemplate(firstTemplate.id()).defaultTemplate()).isFalse();
        assertThat(templateApplicationService.findCurrentDefault("student", TemplateType.IMPORT).id())
                .isEqualTo(secondTemplate.id());

        templateApplicationService.disableTemplate(administrator.id(), secondTemplate.id());

        assertThat(templateApplicationService.findTemplate(secondTemplate.id()).defaultTemplate()).isFalse();
        assertThat(templateApplicationService.findTemplate(secondTemplate.id()).status())
                .isEqualTo(ImportExportTemplateStatus.DISABLED);
        assertThat(templateApplicationService.findCurrentDefault("STUDENT", TemplateType.IMPORT)).isNull();
        assertThatThrownBy(() -> templateApplicationService.setDefaultTemplate(administrator.id(), secondTemplate.id()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已停用");
    }

    @Test
    void allowsOnlySystemAdministratorsAndRejectsFilesThatAreNotAvailable() {
        User administrator = createUserWithRole("template_validation_admin", "模板校验管理员", "SYS_ADMIN");
        User ordinaryUser = createUser("template_validation_user", "普通用户");
        User uploader = createUser("template_validation_uploader", "模板校验上传人");
        attachmentRuleApplicationService.createRule(new CreateAttachmentRuleCommand(
                administrator.id(), "TEMPLATE_VALIDATION", "SPREADSHEET", "模板校验表格", List.of("xlsx"), 10_240L, 1, true
        ));
        ManagedFile uploadingFile = attachmentFileApplicationService.registerUpload(new RegisterAttachmentFileCommand(
                uploader.id(), "TEMPLATE_VALIDATION", "SPREADSHEET", "pending.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", 1_024L
        ));

        assertThatThrownBy(() -> templateApplicationService.createTemplate(new CreateImportExportTemplateCommand(
                administrator.id(), "待完成模板", TemplateType.EXPORT, "TASK", "V1", uploadingFile.id(), false
        ))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未完成");

        assertThatThrownBy(() -> templateApplicationService.createTemplate(new CreateImportExportTemplateCommand(
                ordinaryUser.id(), "无权限模板", TemplateType.EXPORT, "TASK", "V2",
                createAvailableFile(uploader, "TEMPLATE_VALIDATION").id(), false
        ))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("系统管理员");
    }

    private ManagedFile createAvailableFile(User uploader, String moduleCode) {
        ManagedFile file = attachmentFileApplicationService.registerUpload(new RegisterAttachmentFileCommand(
                uploader.id(), moduleCode, "SPREADSHEET", "template.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", 1_024L
        ));
        return attachmentFileApplicationService.completeUpload(new CompleteAttachmentUploadCommand(
                file.id(), 1_024L, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        ));
    }

    private User createUserWithRole(String username, String displayName, String roleCode) {
        User user = createUser(username, displayName);
        Role role = roleMapper.findByCode(roleCode);
        userAccessApplicationService.assignRole(new AssignRoleToUserCommand(user.id(), role.id(), null));
        return user;
    }

    private User createUser(String username, String displayName) {
        return userAccessApplicationService.createUser(new CreateUserCommand(username, displayName, null, UserType.PLATFORM));
    }
}
