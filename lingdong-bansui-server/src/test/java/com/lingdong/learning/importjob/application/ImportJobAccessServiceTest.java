package com.lingdong.learning.importjob.application;

import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.datascope.application.OrganizationDataScopeService;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.importjob.domain.ImportJobRecord;
import com.lingdong.learning.importjob.domain.ImportJobStatus;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ImportJobAccessServiceTest {
    private PermissionDecisionService permissionDecisionService;
    private OrganizationDataScopeService dataScopeService;
    private UserRoleMapper userRoleMapper;
    private ImportJobAccessService service;

    @BeforeEach
    void setUp() {
        permissionDecisionService = mock(PermissionDecisionService.class);
        dataScopeService = mock(OrganizationDataScopeService.class);
        userRoleMapper = mock(UserRoleMapper.class);
        service = new ImportJobAccessService(
                mock(FeatureAccessService.class), permissionDecisionService,
                dataScopeService, userRoleMapper);
    }

    @Test
    void ownerCanReadOrganizationJobOnlyWhileOrganizationRemainsAccessible() {
        Long userId = 8L;
        ImportJobRecord job = job(userId, 11L);
        allowRead(userId);
        when(dataScopeService.canAccess(userId, 11L)).thenReturn(true);

        assertThatCode(() -> service.requireRead(userId, job)).doesNotThrowAnyException();

        when(dataScopeService.canAccess(userId, 11L)).thenReturn(false);
        assertThatThrownBy(() -> service.requireRead(userId, job))
                .isInstanceOf(SystemOperationAccessDeniedException.class);
    }

    @Test
    void organizationFreeJobIsVisibleOnlyToItsOwnerOrSystemAdministrator() {
        ImportJobRecord job = job(8L, null);
        allowRead(8L);
        assertThatCode(() -> service.requireRead(8L, job)).doesNotThrowAnyException();

        allowRead(9L);
        assertThatThrownBy(() -> service.requireRead(9L, job))
                .isInstanceOf(SystemOperationAccessDeniedException.class);

        allowRead(10L);
        when(userRoleMapper.hasRoleCode(10L, "SYS_ADMIN")).thenReturn(true);
        // 权限驱动重构后非属主读取不再因内置角色名放行：仅按权限编码与数据范围判定（isSystemAdministrator 已废弃恒 false）
        assertThatThrownBy(() -> service.requireRead(10L, job))
                .isInstanceOf(SystemOperationAccessDeniedException.class);
        assertThat(service.isSystemAdministrator(10L)).isFalse();
    }

    @Test
    void currentDenyOrDisabledAccountRevokesReadImmediately() {
        Long userId = 8L;
        when(permissionDecisionService.isAllowed(userId, PermissionClient.WEB, "IMPORT_JOB_READ"))
                .thenReturn(false);

        assertThatThrownBy(() -> service.requireRead(userId, job(userId, null)))
                .isInstanceOf(SystemOperationAccessDeniedException.class)
                .hasMessageContaining("IMPORT_JOB_READ");
    }

    private void allowRead(Long userId) {
        when(permissionDecisionService.isAllowed(userId, PermissionClient.WEB, "IMPORT_JOB_READ"))
                .thenReturn(true);
    }

    private ImportJobRecord job(Long requesterId, Long organizationId) {
        LocalDateTime now = LocalDateTime.now();
        return new ImportJobRecord(
                1L, "IMP-1", 2L, "V1", "模板", "[]", 3L, null,
                requesterId, organizationId, ImportJobStatus.QUEUED, 0L, null, null,
                0, 0, 0, 0, now, null, null, now, now);
    }
}
