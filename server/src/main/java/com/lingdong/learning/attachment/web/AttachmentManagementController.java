package com.lingdong.learning.attachment.web;

import com.lingdong.learning.attachment.application.AttachmentFileQuery;
import com.lingdong.learning.attachment.application.AttachmentLedgerApplicationService;
import com.lingdong.learning.attachment.application.AttachmentRuleApplicationService;
import com.lingdong.learning.attachment.application.AttachmentRuleQuery;
import com.lingdong.learning.attachment.application.CreateAttachmentRuleCommand;
import com.lingdong.learning.attachment.application.UpdateAttachmentRuleCommand;
import com.lingdong.learning.attachment.domain.AttachmentRuleStatus;
import com.lingdong.learning.attachment.domain.FileStatus;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.RequirePermission;
import com.lingdong.learning.feature.application.FeatureAccessService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/** 提供受附件总开关和动态 RBAC 共同保护的后台附件管理 API。 */
@RestController
@RequestMapping("/api/v1/attachment-management")
public class AttachmentManagementController {
    private static final String FEATURE_CODE = "ATTACHMENT_SERVICE";

    private final AttachmentRuleApplicationService ruleService;
    private final AttachmentLedgerApplicationService ledgerService;
    private final FeatureAccessService featureAccessService;

    public AttachmentManagementController(
            AttachmentRuleApplicationService ruleService,
            AttachmentLedgerApplicationService ledgerService,
            FeatureAccessService featureAccessService
    ) {
        this.ruleService = ruleService;
        this.ledgerService = ledgerService;
        this.featureAccessService = featureAccessService;
    }

    @RequirePermission("ATTACHMENT_RULE_READ")
    @GetMapping("/rules")
    public List<AttachmentRuleResponse> listRules(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(required = false) String ruleName,
            @RequestParam(required = false) String moduleCode,
            @RequestParam(required = false) String fileCategory,
            @RequestParam(required = false) AttachmentRuleStatus status
    ) {
        requireFeature();
        return ruleService.listRules(new AttachmentRuleQuery(
                        currentUser.userId(), ruleName, moduleCode, fileCategory, status))
                .stream().map(AttachmentRuleResponse::from).toList();
    }

    @RequirePermission("ATTACHMENT_RULE_MANAGE")
    @PostMapping("/rules")
    @ResponseStatus(HttpStatus.CREATED)
    public AttachmentRuleResponse createRule(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody CreateAttachmentRuleRequest request
    ) {
        requireFeature();
        return AttachmentRuleResponse.from(ruleService.createRule(new CreateAttachmentRuleCommand(
                currentUser.userId(), request.moduleCode(), request.fileCategory(), request.ruleName(),
                request.allowedExtensions(), request.maxFileSizeBytes(), request.maxBatchCount(),
                request.previewEnabled())));
    }

    @RequirePermission("ATTACHMENT_RULE_MANAGE")
    @PutMapping("/rules/{id}")
    public AttachmentRuleResponse updateRule(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long id,
            @Valid @RequestBody UpdateAttachmentRuleRequest request
    ) {
        requireFeature();
        return AttachmentRuleResponse.from(ruleService.updateRule(new UpdateAttachmentRuleCommand(
                currentUser.userId(), id, request.ruleName(), request.allowedExtensions(),
                request.maxFileSizeBytes(), request.maxBatchCount(), request.previewEnabled(), request.versionNo())));
    }

    @RequirePermission("ATTACHMENT_RULE_MANAGE")
    @PostMapping("/rules/{id}/enable")
    public AttachmentRuleResponse enableRule(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long id,
            @Valid @RequestBody AttachmentRuleVersionRequest request
    ) {
        requireFeature();
        return AttachmentRuleResponse.from(ruleService.enableRule(currentUser.userId(), id, request.versionNo()));
    }

    @RequirePermission("ATTACHMENT_RULE_MANAGE")
    @PostMapping("/rules/{id}/disable")
    public AttachmentRuleResponse disableRule(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long id,
            @Valid @RequestBody AttachmentRuleVersionRequest request
    ) {
        requireFeature();
        return AttachmentRuleResponse.from(ruleService.disableRule(currentUser.userId(), id, request.versionNo()));
    }

    @RequirePermission("ATTACHMENT_FILE_LEDGER_READ")
    @GetMapping("/files")
    public List<AttachmentFileLedgerResponse> listFiles(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(required = false) String originalName,
            @RequestParam(required = false) String moduleCode,
            @RequestParam(required = false) String fileCategory,
            @RequestParam(required = false) FileStatus status,
            @RequestParam(required = false) Long uploaderId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo
    ) {
        requireFeature();
        return ledgerService.listFiles(new AttachmentFileQuery(
                        currentUser.userId(), originalName, moduleCode, fileCategory, status,
                        uploaderId, createdFrom, createdTo))
                .stream().map(AttachmentFileLedgerResponse::from).toList();
    }

    @RequirePermission("ATTACHMENT_FILE_LEDGER_READ")
    @GetMapping("/files/{id}/relations")
    public List<AttachmentRelationLedgerResponse> listRelations(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long id
    ) {
        requireFeature();
        return ledgerService.listRelations(currentUser.userId(), id).stream()
                .map(AttachmentRelationLedgerResponse::from).toList();
    }

    private void requireFeature() {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
    }
}
