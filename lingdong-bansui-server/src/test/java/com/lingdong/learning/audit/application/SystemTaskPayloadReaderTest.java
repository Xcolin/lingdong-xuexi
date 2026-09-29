package com.lingdong.learning.audit.application;

import com.lingdong.learning.cache.infrastructure.persistence.CacheOperationMapper;
import com.lingdong.learning.feature.infrastructure.persistence.FeatureToggleChangeMapper;
import com.lingdong.learning.feature.domain.FeatureStatus;
import com.lingdong.learning.feature.application.FeatureToggleChange;
import com.lingdong.learning.interfaceconfig.infrastructure.persistence.InterfaceServiceChangeMapper;
import com.lingdong.learning.interfaceconfig.domain.*;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationChangeMapper;
import com.lingdong.learning.organization.domain.*;
import com.lingdong.learning.exportjob.infrastructure.persistence.ExportJobMapper;
import com.lingdong.learning.exportjob.domain.ExportJobRecord;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SystemTaskPayloadReaderTest {
    private final CacheOperationMapper caches = mock(CacheOperationMapper.class);
    private final FeatureToggleChangeMapper features = mock(FeatureToggleChangeMapper.class);
    private final InterfaceServiceChangeMapper interfaces = mock(InterfaceServiceChangeMapper.class);
    private final OrganizationChangeMapper organizations = mock(OrganizationChangeMapper.class);
    private final ExportJobMapper exports = mock(ExportJobMapper.class);
    private final SystemTaskPayloadReader reader = new SystemTaskPayloadReader(caches, features, interfaces, organizations, exports, new com.fasterxml.jackson.databind.ObjectMapper());
    private static final Long TASK_ID = 1900000000000000001L;

    @Test void featureDifferenceUsesPersistedBeforeValueAndKeepsUnknownHistoryExplicit() {
        when(features.findByTaskId(TASK_ID)).thenReturn(new FeatureToggleChange(1L,TASK_ID,"TEST",FeatureStatus.ENABLED,FeatureStatus.DISABLED,2L));
        var payload = reader.read(task(SystemTaskType.GLOBAL_FEATURE_TOGGLE));
        assertThat(payload.differences()).containsExactly(new SystemTaskPayloadReader.Difference("开关状态","DISABLED","ENABLED"));
        when(features.findByTaskId(TASK_ID)).thenReturn(new FeatureToggleChange(1L,TASK_ID,"TEST",FeatureStatus.ENABLED));
        var legacy = reader.read(task(SystemTaskType.GLOBAL_FEATURE_TOGGLE));
        assertThat(legacy.differences().get(0).before()).isNull();
        assertThat(legacy.notice()).contains("未记录");
    }

    @Test void organizationMovePreservesStringIdsAndFailureResult() {
        when(organizations.findByTaskId(TASK_ID)).thenReturn(new OrganizationChange(1L,TASK_ID,1900000000000000002L,
                OrganizationChangeType.MOVE,1900000000000000004L,2,"ORG","测试组织",1900000000000000003L,"调整归属",
                OrganizationChangeExecutionStatus.FAILED,"版本冲突",null,null));
        var payload=reader.read(task(SystemTaskType.ORGANIZATION_MOVE));
        assertThat(payload.differences()).containsExactly(new SystemTaskPayloadReader.Difference("上级组织 ID","1900000000000000003","1900000000000000004"));
        assertThat(payload.executionStatus()).isEqualTo("FAILED");
        assertThat(payload.failureReason()).isEqualTo("版本冲突");
    }

    @Test void interfaceRequestDoesNotInventBeforeValueOrReadLiveService() {
        when(interfaces.findByTaskId(TASK_ID)).thenReturn(InterfaceServiceChange.disable(1L,TASK_ID,1900000000000000002L));
        var payload=reader.read(task(SystemTaskType.INTERFACE_SERVICE_CHANGE));
        assertThat(payload.fields()).contains(new SystemTaskPayloadReader.Field("服务 ID","1900000000000000002"));
        assertThat(payload.differences().get(0)).isEqualTo(new SystemTaskPayloadReader.Difference("服务状态",null,"DISABLED"));
        assertThat(payload.notice()).contains("未记录变更前");
    }

    @Test void newInterfaceRequestShowsPersistedBeforeStatus() {
        var service = new InterfaceService(1900000000000000002L,"测试服务",InterfaceDirection.OUTBOUND,
                InterfacePurpose.SMS,"test",InterfaceAuthorizationScope.GLOBAL,null,1900000000000000003L,
                InterfaceServiceStatus.ENABLED,null,null);
        when(interfaces.findByTaskId(TASK_ID)).thenReturn(InterfaceServiceChange.disable(1L,TASK_ID,service.id()).withBefore(service));
        var payload=reader.read(task(SystemTaskType.INTERFACE_SERVICE_CHANGE));
        assertThat(payload.differences()).containsExactly(new SystemTaskPayloadReader.Difference("服务状态","ENABLED","DISABLED"));
        assertThat(payload.notice()).isNull();
    }

    @Test void exportDoesNotExposePrivateSnapshotOrFileReferences() {
        var job=mock(ExportJobRecord.class);
        when(job.jobCode()).thenReturn("EXPORT_TEST");
        when(exports.findBySystemTaskId(TASK_ID)).thenReturn(job);
        var payload=reader.read(task(SystemTaskType.SENSITIVE_DATA_EXPORT));
        assertThat(payload.fields()).contains(new SystemTaskPayloadReader.Field("导出编号","EXPORT_TEST"));
        verify(job,never()).scopeSnapshot();
        verify(job,never()).resultFileId();
        verify(job,never()).requestSourceHash();
    }

    @Test void missingPayloadIsExplicitAndDoesNotPretendToBeComplete() {
        assertThat(reader.read(task(SystemTaskType.CACHE_CLEAR)).notice()).contains("缺少");
    }

    @Test void exportShowsOnlyWhitelistedApprovalParametersFromSnapshots() {
        var job=mock(ExportJobRecord.class);
        when(job.filterSnapshot()).thenReturn("{\"startedAt\":\"2026-09-01T00:00:00\",\"endedAt\":\"2026-09-02T00:00:00\",\"eventType\":\"USER_CREATE\",\"secret\":\"never-expose\"}");
        when(job.columnSnapshot()).thenReturn("[{\"code\":\"OCCURRED_AT\",\"header\":\"发生时间\"}]");
        when(job.maskPolicySnapshot()).thenReturn("{\"namePolicy\":\"FAMILY_NAME\",\"version\":1}");
        when(exports.findBySystemTaskId(TASK_ID)).thenReturn(job);
        var payload=reader.read(task(SystemTaskType.SENSITIVE_DATA_EXPORT));
        assertThat(payload.fields()).contains(new SystemTaskPayloadReader.Field("筛选开始时间","2026-09-01T00:00:00"),
                new SystemTaskPayloadReader.Field("导出列","发生时间（OCCURRED_AT）"));
        assertThat(payload.toString()).doesNotContain("never-expose");
        verify(job,never()).scopeSnapshot();
    }

    private SystemTask task(SystemTaskType type) {
        return new SystemTask(TASK_ID,"TASK",type,"测试申请","合成说明",ImpactScope.GLOBAL,SystemTaskStatus.PENDING_REVIEW,
                1900000000000000099L,null,null,null,null,null,null);
    }
}
