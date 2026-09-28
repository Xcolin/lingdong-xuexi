package com.lingdong.learning.exportjob.application;

import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.exportjob.domain.ExportJobRecord;
import com.lingdong.learning.exportjob.domain.ExportJobStatus;
import com.lingdong.learning.exportjob.domain.ExportJobType;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthPointQueryMapper;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthPointStudentOptionRow;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserStatus;
import com.lingdong.learning.user.domain.UserType;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExportJobAccessServiceTest {
    private static final long USER_ID = 1874244142494646911L;
    private static final long STUDENT_ID = 1874244142494646912L;

    private FeatureAccessService featureAccessService;
    private PermissionDecisionService permissionDecisionService;
    private UserRoleMapper userRoleMapper;
    private UserMapper userMapper;
    private GrowthPointQueryMapper growthPointQueryMapper;
    private ExportJobAccessService service;

    @BeforeEach
    void setUp() {
        featureAccessService = mock(FeatureAccessService.class);
        permissionDecisionService = mock(PermissionDecisionService.class);
        userRoleMapper = mock(UserRoleMapper.class);
        userMapper = mock(UserMapper.class);
        growthPointQueryMapper = mock(GrowthPointQueryMapper.class);
        service = new ExportJobAccessService(
                featureAccessService, permissionDecisionService, userRoleMapper,
                userMapper, growthPointQueryMapper);
        when(userMapper.findById(USER_ID)).thenReturn(enabledUser());
    }

    @Test
    void allowsParentToCreateAndReadOnlyCurrentPrimaryStudentExport() {
        allow("EXPORT_JOB_CREATE");
        allow("EXPORT_JOB_READ");
        allow("GROWTH_POINT_READ_CHILD");
        when(userRoleMapper.hasRoleCode(USER_ID, "PARENT")).thenReturn(true);
        when(growthPointQueryMapper.findPrimaryStudentsByParentUserId(USER_ID))
                .thenReturn(List.of(new GrowthPointStudentOptionRow(STUDENT_ID, "小灵")));

        assertThatCode(() -> service.requireOrdinaryCreate(USER_ID, STUDENT_ID))
                .doesNotThrowAnyException();
        assertThatCode(() -> service.requireOwnerDownload(USER_ID, job(
                ExportJobType.GROWTH_POINT_LEDGER, USER_ID, STUDENT_ID,
                ExportJobStatus.SUCCEEDED, 1874244142494646913L)))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> service.requireOrdinaryCreate(USER_ID, STUDENT_ID + 1))
                .isInstanceOf(SystemOperationAccessDeniedException.class);
    }

    @Test
    void allowsOnlyFixedSystemRolesForSensitiveSubmitAndReview() {
        allow("EXPORT_SENSITIVE_SUBMIT");
        allow("IAM_AUDIT_READ");
        when(userRoleMapper.hasRoleCode(USER_ID, "SYS_ADMIN")).thenReturn(true);

        assertThatCode(() -> service.requireSensitiveSubmit(USER_ID)).doesNotThrowAnyException();

        when(userRoleMapper.hasRoleCode(USER_ID, "SYS_ADMIN")).thenReturn(false);
        when(userRoleMapper.hasRoleCode(USER_ID, "SYS_AUDITOR")).thenReturn(true);
        allow("EXPORT_SENSITIVE_REVIEW");
        assertThatCode(() -> service.requireSensitiveReview(USER_ID)).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.requireSensitiveSubmit(USER_ID))
                .isInstanceOf(SystemOperationAccessDeniedException.class);
    }

    @Test
    void rejectsDisabledUserDeniedPermissionAndDifferentOwner() {
        when(userMapper.findById(USER_ID)).thenReturn(new User(
                USER_ID, "disabled", "停用账号", null, null,
                UserType.FAMILY, UserStatus.DISABLED, null, null));
        assertThatThrownBy(() -> service.requireSensitiveReview(USER_ID))
                .isInstanceOf(SystemOperationAccessDeniedException.class);

        when(userMapper.findById(USER_ID)).thenReturn(enabledUser());
        when(userRoleMapper.hasRoleCode(USER_ID, "PARENT")).thenReturn(true);
        when(growthPointQueryMapper.findPrimaryStudentsByParentUserId(USER_ID))
                .thenReturn(List.of(new GrowthPointStudentOptionRow(STUDENT_ID, "小灵")));
        assertThatThrownBy(() -> service.requireOrdinaryCreate(USER_ID, STUDENT_ID))
                .isInstanceOf(SystemOperationAccessDeniedException.class);

        allow("EXPORT_JOB_READ");
        allow("GROWTH_POINT_READ_CHILD");
        assertThatThrownBy(() -> service.requireOwnerRead(USER_ID, job(
                ExportJobType.GROWTH_POINT_LEDGER, USER_ID + 1, STUDENT_ID,
                ExportJobStatus.SUCCEEDED, 1874244142494646913L)))
                .isInstanceOf(SystemOperationAccessDeniedException.class);
    }

    @Test
    void dictionaryExportRechecksRevokedPermissionAndFeatureAtEveryAccessPoint() {
        allow("DICTIONARY_READ");
        allow("DICTIONARY_EXPORT");
        allow("EXPORT_JOB_READ");
        when(userRoleMapper.hasRoleCode(USER_ID, "SYS_ADMIN")).thenReturn(true);
        var export = job(ExportJobType.DICTIONARY_LEDGER, USER_ID, null,
                ExportJobStatus.SUCCEEDED, 1874244142494646913L);
        assertThatCode(() -> service.requireDictionaryExport(USER_ID)).doesNotThrowAnyException();
        assertThatCode(() -> service.requireExecution(export)).doesNotThrowAnyException();
        assertThatCode(() -> service.requireOwnerDownload(USER_ID, export)).doesNotThrowAnyException();

        when(permissionDecisionService.isAllowed(USER_ID, PermissionClient.WEB, "DICTIONARY_EXPORT")).thenReturn(false);
        assertThatThrownBy(() -> service.requireDictionaryExport(USER_ID)).isInstanceOf(SystemOperationAccessDeniedException.class);
        assertThatThrownBy(() -> service.requireExecution(export)).isInstanceOf(SystemOperationAccessDeniedException.class);
        assertThatThrownBy(() -> service.requireOwnerDownload(USER_ID, export)).isInstanceOf(SystemOperationAccessDeniedException.class);

        allow("DICTIONARY_EXPORT");
        org.mockito.Mockito.doThrow(new com.lingdong.learning.feature.application.FeatureDisabledException("DICTIONARY_MANAGEMENT"))
                .when(featureAccessService).requireEnabled("DICTIONARY_MANAGEMENT", null);
        assertThatThrownBy(() -> service.requireDictionaryExport(USER_ID)).isInstanceOf(com.lingdong.learning.feature.application.FeatureDisabledException.class);
        assertThatThrownBy(() -> service.requireExecution(export)).isInstanceOf(com.lingdong.learning.feature.application.FeatureDisabledException.class);
        assertThatThrownBy(() -> service.requireOwnerDownload(USER_ID, export)).isInstanceOf(com.lingdong.learning.feature.application.FeatureDisabledException.class);
    }

    @Test
    void templateExportRechecksReadAndExportPermissionsAndFeature() {
        allow("IMPORT_EXPORT_TEMPLATE_READ");
        allow("IMPORT_EXPORT_TEMPLATE_EXPORT");
        allow("EXPORT_JOB_READ");
        when(userRoleMapper.hasRoleCode(USER_ID, "SYS_ADMIN")).thenReturn(true);
        var export = job(ExportJobType.TEMPLATE_LEDGER, USER_ID, null,
                ExportJobStatus.SUCCEEDED, 1874244142494646913L);
        assertThatCode(() -> service.requireTemplateExport(USER_ID)).doesNotThrowAnyException();
        assertThatCode(() -> service.requireExecution(export)).doesNotThrowAnyException();
        assertThatCode(() -> service.requireOwnerDownload(USER_ID, export)).doesNotThrowAnyException();
        for (String permission : List.of("IMPORT_EXPORT_TEMPLATE_READ", "IMPORT_EXPORT_TEMPLATE_EXPORT")) {
            when(permissionDecisionService.isAllowed(USER_ID, PermissionClient.WEB, permission)).thenReturn(false);
            assertThatThrownBy(() -> service.requireTemplateExport(USER_ID)).isInstanceOf(SystemOperationAccessDeniedException.class);
            assertThatThrownBy(() -> service.requireExecution(export)).isInstanceOf(SystemOperationAccessDeniedException.class);
            assertThatThrownBy(() -> service.requireOwnerDownload(USER_ID, export)).isInstanceOf(SystemOperationAccessDeniedException.class);
            allow(permission);
        }
        org.mockito.Mockito.doThrow(new com.lingdong.learning.feature.application.FeatureDisabledException("IMPORT_EXPORT_TEMPLATE_MANAGEMENT"))
                .when(featureAccessService).requireEnabled("IMPORT_EXPORT_TEMPLATE_MANAGEMENT", null);
        assertThatThrownBy(() -> service.requireTemplateExport(USER_ID)).isInstanceOf(com.lingdong.learning.feature.application.FeatureDisabledException.class);
        assertThatThrownBy(() -> service.requireExecution(export)).isInstanceOf(com.lingdong.learning.feature.application.FeatureDisabledException.class);
        assertThatThrownBy(() -> service.requireOwnerDownload(USER_ID, export)).isInstanceOf(com.lingdong.learning.feature.application.FeatureDisabledException.class);
    }

    @Test
    void interfaceExportRechecksReadAndExportPermissionsAndFeature() {
        allow("INTERFACE_SERVICE_READ");
        allow("INTERFACE_SERVICE_EXPORT");
        allow("EXPORT_JOB_READ");
        when(userRoleMapper.hasRoleCode(USER_ID, "SYS_ADMIN")).thenReturn(true);
        var export = job(ExportJobType.INTERFACE_SERVICE_LEDGER, USER_ID, null,
                ExportJobStatus.SUCCEEDED, 1874244142494646913L);
        assertThatCode(() -> service.requireInterfaceExport(USER_ID)).doesNotThrowAnyException();
        assertThatCode(() -> service.requireExecution(export)).doesNotThrowAnyException();
        assertThatCode(() -> service.requireOwnerDownload(USER_ID, export)).doesNotThrowAnyException();
        for (String permission : List.of("INTERFACE_SERVICE_READ", "INTERFACE_SERVICE_EXPORT")) {
            when(permissionDecisionService.isAllowed(USER_ID, PermissionClient.WEB, permission)).thenReturn(false);
            assertThatThrownBy(() -> service.requireInterfaceExport(USER_ID)).isInstanceOf(SystemOperationAccessDeniedException.class);
            assertThatThrownBy(() -> service.requireExecution(export)).isInstanceOf(SystemOperationAccessDeniedException.class);
            assertThatThrownBy(() -> service.requireOwnerDownload(USER_ID, export)).isInstanceOf(SystemOperationAccessDeniedException.class);
            allow(permission);
        }
        org.mockito.Mockito.doThrow(new com.lingdong.learning.feature.application.FeatureDisabledException("INTERFACE_SERVICE_MANAGEMENT"))
                .when(featureAccessService).requireEnabled("INTERFACE_SERVICE_MANAGEMENT", null);
        assertThatThrownBy(() -> service.requireInterfaceExport(USER_ID)).isInstanceOf(com.lingdong.learning.feature.application.FeatureDisabledException.class);
        assertThatThrownBy(() -> service.requireExecution(export)).isInstanceOf(com.lingdong.learning.feature.application.FeatureDisabledException.class);
        assertThatThrownBy(() -> service.requireOwnerDownload(USER_ID, export)).isInstanceOf(com.lingdong.learning.feature.application.FeatureDisabledException.class);
    }

    @Test
    void cacheExportRechecksReadAndExportPermissionsAndFeature() {
        allow("CACHE_READ");
        allow("CACHE_EXPORT");
        allow("EXPORT_JOB_READ");
        when(userRoleMapper.hasRoleCode(USER_ID, "SYS_ADMIN")).thenReturn(true);
        var export = job(ExportJobType.CACHE_OPERATION_LOG, USER_ID, null,
                ExportJobStatus.SUCCEEDED, 1874244142494646913L);
        assertThatCode(() -> service.requireCacheExport(USER_ID)).doesNotThrowAnyException();
        assertThatCode(() -> service.requireExecution(export)).doesNotThrowAnyException();
        assertThatCode(() -> service.requireOwnerDownload(USER_ID, export)).doesNotThrowAnyException();
        for (String permission : List.of("CACHE_READ", "CACHE_EXPORT")) {
            when(permissionDecisionService.isAllowed(USER_ID, PermissionClient.WEB, permission)).thenReturn(false);
            assertThatThrownBy(() -> service.requireCacheExport(USER_ID)).isInstanceOf(SystemOperationAccessDeniedException.class);
            assertThatThrownBy(() -> service.requireExecution(export)).isInstanceOf(SystemOperationAccessDeniedException.class);
            assertThatThrownBy(() -> service.requireOwnerDownload(USER_ID, export)).isInstanceOf(SystemOperationAccessDeniedException.class);
            allow(permission);
        }
        org.mockito.Mockito.doThrow(new com.lingdong.learning.feature.application.FeatureDisabledException("CACHE_MANAGEMENT"))
                .when(featureAccessService).requireEnabled("CACHE_MANAGEMENT", null);
        assertThatThrownBy(() -> service.requireCacheExport(USER_ID)).isInstanceOf(com.lingdong.learning.feature.application.FeatureDisabledException.class);
        assertThatThrownBy(() -> service.requireExecution(export)).isInstanceOf(com.lingdong.learning.feature.application.FeatureDisabledException.class);
        assertThatThrownBy(() -> service.requireOwnerDownload(USER_ID, export)).isInstanceOf(com.lingdong.learning.feature.application.FeatureDisabledException.class);
    }

    @Test
    void taskSnapshotRejectsNarrowingRoleChangesAndMissingScopeButAllowsAdditionalDomains() throws Exception {
        var query=mock(com.lingdong.learning.audit.application.SystemTaskQueryService.class);
        var json=new com.fasterxml.jackson.databind.ObjectMapper();
        service=new ExportJobAccessService(featureAccessService,permissionDecisionService,userRoleMapper,
                userMapper,growthPointQueryMapper,query,json);
        allow("SYSTEM_TASK_EXPORT");allow("EXPORT_JOB_READ");
        var cache=com.lingdong.learning.audit.application.SystemTaskType.CACHE_CLEAR;
        var organization=com.lingdong.learning.audit.application.SystemTaskType.ORGANIZATION_MOVE;
        var frozen=new ExportScopeSnapshot(null,100L,false,List.of(cache));
        var export=mock(ExportJobRecord.class,org.mockito.AdditionalAnswers.delegatesTo(
                job(ExportJobType.SYSTEM_TASK_LEDGER,USER_ID,null,ExportJobStatus.SUCCEEDED,1874244142494646913L)));
        org.mockito.Mockito.doReturn(json.writeValueAsString(frozen)).when(export).scopeSnapshot();
        when(query.resolveScope(USER_ID)).thenReturn(new com.lingdong.learning.audit.application.SystemTaskQueryService.VisibilityScope(false,List.of(cache,organization)));
        assertThatCode(()->service.requireExecution(export)).doesNotThrowAnyException();
        assertThatCode(()->service.requireOwnerDownload(USER_ID,export)).doesNotThrowAnyException();
        when(query.resolveScope(USER_ID)).thenReturn(new com.lingdong.learning.audit.application.SystemTaskQueryService.VisibilityScope(false,List.of(organization)));
        assertThatThrownBy(()->service.requireExecution(export)).isInstanceOf(SystemOperationAccessDeniedException.class);
        assertThatThrownBy(()->service.requireOwnerDownload(USER_ID,export)).isInstanceOf(SystemOperationAccessDeniedException.class);
        when(query.resolveScope(USER_ID)).thenReturn(new com.lingdong.learning.audit.application.SystemTaskQueryService.VisibilityScope(true,List.of(cache)));
        assertThatThrownBy(()->service.requireExecution(export)).isInstanceOf(SystemOperationAccessDeniedException.class);
        when(query.resolveScope(USER_ID)).thenReturn(new com.lingdong.learning.audit.application.SystemTaskQueryService.VisibilityScope(false,List.of(cache)));
        org.mockito.Mockito.doReturn("{\"studentId\":null,\"upperBound\":100}").when(export).scopeSnapshot();
        assertThatThrownBy(()->service.requireOwnerRead(USER_ID,export)).isInstanceOf(SystemOperationAccessDeniedException.class);
        org.assertj.core.api.Assertions.assertThat(json.readValue("{\"studentId\":null,\"upperBound\":100}",ExportScopeSnapshot.class))
                .isEqualTo(new ExportScopeSnapshot(null,100));
    }

    private void allow(String code) {
        when(permissionDecisionService.isAllowed(USER_ID, PermissionClient.WEB, code)).thenReturn(true);
    }

    private User enabledUser() {
        return new User(USER_ID, "export-user", "导出用户", null, null,
                UserType.FAMILY, UserStatus.ENABLED, null, null);
    }

    private ExportJobRecord job(
            ExportJobType type,
            long requesterId,
            Long studentId,
            ExportJobStatus status,
            Long resultFileId
    ) {
        LocalDateTime now = LocalDateTime.of(2026, 9, 3, 10, 0);
        return new ExportJobRecord(
                1874244142494646914L, "EXP-1874244142494646914", type,
                1874244142494646915L, "导出模板", "V1", requesterId, studentId,
                null, "{}", "[]", "{}", "{}", "测试导出", false, status,
                0L, resultFileId, 1L, 1L, null, null, "0".repeat(64),
                now, null, now, now, now, now, now
        );
    }
}
