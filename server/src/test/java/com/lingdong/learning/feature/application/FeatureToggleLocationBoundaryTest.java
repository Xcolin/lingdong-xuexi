package com.lingdong.learning.feature.application;

import com.lingdong.learning.audit.application.SystemTaskApplicationService;
import com.lingdong.learning.auth.infrastructure.persistence.DeviceSessionMapper;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.feature.domain.FeatureStatus;
import com.lingdong.learning.feature.domain.FeatureScope;
import com.lingdong.learning.feature.domain.FeatureToggle;
import com.lingdong.learning.feature.infrastructure.persistence.FeatureToggleChangeMapper;
import com.lingdong.learning.feature.infrastructure.persistence.FeatureToggleMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** 当前发布边界不允许创建或执行定位启用申请，包括历史待审申请。 */
class FeatureToggleLocationBoundaryTest {
    private final FeatureToggleMapper toggles = mock(FeatureToggleMapper.class);
    private final FeatureToggleChangeMapper changes = mock(FeatureToggleChangeMapper.class);
    private final SystemTaskApplicationService tasks = mock(SystemTaskApplicationService.class);
    private final DeviceSessionMapper sessions = mock(DeviceSessionMapper.class);
    private final FeatureToggleChangeService service = new FeatureToggleChangeService(
            toggles, changes, tasks, mock(IdGenerator.class), sessions);

    @ParameterizedTest
    @ValueSource(strings = {"GEO_ATTENDANCE", "STUDENT_LOCATION_TRACK"})
    void rejectsNewLocationEnableRequests(String code) {
        when(tasks.createDraft(any())).thenReturn(mock(com.lingdong.learning.audit.application.SystemTask.class));
        when(toggles.findGlobal(code)).thenReturn(new FeatureToggle(1874244142494646284L,
                code, "定位能力", FeatureScope.GLOBAL, null, "GLOBAL", FeatureStatus.DISABLED, true, ""));
        assertThatThrownBy(() -> service.createDraft(new CreateGlobalFeatureToggleChangeCommand(
                1874244142494647201L, code, FeatureStatus.ENABLED, "启用申请", "测试边界")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("定位能力保持关闭");
        verifyNoInteractions(tasks, changes, sessions);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GEO_ATTENDANCE", "STUDENT_LOCATION_TRACK"})
    void rejectsHistoricalLocationEnableBeforeApproval(String code) {
        long taskId = 1874244142494647202L;
        when(changes.findByTaskId(taskId)).thenReturn(new FeatureToggleChange(
                1874244142494647203L, taskId, code, FeatureStatus.ENABLED));
        when(toggles.updateGlobalStatus(code, FeatureStatus.ENABLED)).thenReturn(1);
        assertThatThrownBy(() -> service.approveAndApply(taskId, 1874244142494647204L, "同意"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("定位能力保持关闭");
        verifyNoInteractions(tasks, toggles, sessions);
    }
}
