package com.lingdong.learning.growthpoint.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthPointQueryMapper;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthRewardExchangeMapper;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthRewardMapper;
import com.lingdong.learning.learningtask.application.CurrentStudentAccessService;
import com.lingdong.learning.student.infrastructure.persistence.ParentStudentMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 家长端奖励/兑换列表可选状态筛选：合法值透传、ALL 与缺省不过滤、非法值显式拒绝。 */
class GrowthRewardManagedStatusFilterTest {
    private final GrowthRewardMapper rewardMapper = mock(GrowthRewardMapper.class);
    private final GrowthRewardExchangeMapper exchangeMapper = mock(GrowthRewardExchangeMapper.class);
    private final ParentStudentMapper parentStudentMapper = mock(ParentStudentMapper.class);
    private final GrowthRewardService rewardService = new GrowthRewardService(
            rewardMapper, mock(GrowthPointQueryMapper.class), parentStudentMapper,
            mock(CurrentStudentAccessService.class), mock(FeatureAccessService.class),
            mock(IdGenerator.class), Clock.systemDefaultZone(), mock(com.lingdong.learning.auth.application.ParentActorAccessService.class));
    private final GrowthRewardExchangeService exchangeService = new GrowthRewardExchangeService(
            exchangeMapper, mock(GrowthRewardMapper.class),
            mock(com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthPointAccountMapper.class),
            mock(com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthPointLedgerMapper.class),
            parentStudentMapper,
            mock(com.lingdong.learning.learningtask.application.CurrentStudentAccessService.class),
            mock(FeatureAccessService.class), mock(IdGenerator.class), Clock.systemDefaultZone(),
            mock(com.lingdong.learning.auth.application.ParentActorAccessService.class));
    private final AuthenticatedUser parent = new AuthenticatedUser(
            8910000000000000901L, 1L, "parent", "主家长", AuthClientType.MINIAPP, List.of("CUSTOM_PARENT"));
    private final long studentId = 8910000000000000902L;

    GrowthRewardManagedStatusFilterTest() {
        when(parentStudentMapper.existsActiveByParentAndStudent(parent.userId(), studentId)).thenReturn(true);
        when(rewardMapper.findManagedByStudentId(eq(studentId), anyString(), anyInt(), anyInt())).thenReturn(List.of());
        when(exchangeMapper.findByStudentId(eq(studentId), anyString(), anyInt(), anyInt())).thenReturn(List.of());
    }

    @Test
    void rewardStatusFilterPassesThroughAndRejectsInvalid() {
        rewardService.findManaged(parent, studentId, 1, 20, "ONLINE");
        verify(rewardMapper).findManagedByStudentId(eq(studentId), eq("ONLINE"), eq(0), eq(20));
        verify(rewardMapper).countManagedByStudentId(studentId, "ONLINE");

        rewardService.findManaged(parent, studentId, 1, 20, "ALL");
        verify(rewardMapper).findManagedByStudentId(eq(studentId), isNull(), eq(0), eq(20));

        assertThatThrownBy(() -> rewardService.findManaged(parent, studentId, 1, 20, "PENDING"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(rewardService.findManaged(parent, studentId, 1, 20, "OFFLINE").items()).isEmpty();
    }

    @Test
    void exchangeStatusFilterPassesThroughAndRejectsInvalid() {
        exchangeService.findManaged(parent, studentId, 1, 20, "PENDING_VERIFICATION");
        verify(exchangeMapper).findByStudentId(eq(studentId), eq("PENDING_VERIFICATION"), eq(0), eq(20));
        verify(exchangeMapper).countByStudentId(studentId, "PENDING_VERIFICATION");

        exchangeService.findManaged(parent, studentId, 1, 20, null);
        verify(exchangeMapper).findByStudentId(eq(studentId), isNull(), eq(0), eq(20));

        assertThatThrownBy(() -> exchangeService.findManaged(parent, studentId, 1, 20, "ONLINE"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(exchangeService.findManaged(parent, studentId, 1, 20, "REJECTED").items()).isEmpty();
    }

    @Test
    void legacyFourArgumentCallsKeepUnfilteredSemanticsForWeb() {
        // Web 旧调用（GrowthRewardController/GrowthRewardExchangeController 四参重载）不应传筛选条件：count 与 list 均收到 null
        rewardService.findManaged(parent, studentId, 1, 20);
        verify(rewardMapper).findManagedByStudentId(eq(studentId), isNull(), eq(0), eq(20));
        verify(rewardMapper).countManagedByStudentId(studentId, null);

        exchangeService.findManaged(parent, studentId, 1, 20);
        verify(exchangeMapper).findByStudentId(eq(studentId), isNull(), eq(0), eq(20));
        verify(exchangeMapper).countByStudentId(studentId, null);
    }

    @Test
    void unrelatedParentCannotReadAnyFilteredList() {
        when(parentStudentMapper.existsActiveByParentAndStudent(parent.userId(), studentId)).thenReturn(false);
        assertThatThrownBy(() -> rewardService.findManaged(parent, studentId, 1, 20, "ONLINE"))
                .isInstanceOf(com.lingdong.learning.common.web.ResourceNotFoundException.class);
        assertThatThrownBy(() -> exchangeService.findManaged(parent, studentId, 1, 20, "PENDING_APPROVAL"))
                .isInstanceOf(com.lingdong.learning.common.web.ResourceNotFoundException.class);
        assertThatThrownBy(() -> rewardService.findManaged(parent, studentId, 1, 20))
                .isInstanceOf(com.lingdong.learning.common.web.ResourceNotFoundException.class);
        verify(rewardMapper, org.mockito.Mockito.never()).findManagedByStudentId(anyLong(), anyString(), anyInt(), anyInt());
        verify(exchangeMapper, org.mockito.Mockito.never()).findByStudentId(anyLong(), anyString(), anyInt(), anyInt());
    }
}
