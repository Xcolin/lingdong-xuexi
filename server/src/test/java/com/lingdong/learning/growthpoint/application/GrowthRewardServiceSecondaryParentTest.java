package com.lingdong.learning.growthpoint.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.growthpoint.domain.GrowthRewardStatus;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthPointQueryMapper;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthRewardMapper;
import com.lingdong.learning.learningtask.application.CurrentStudentAccessService;
import com.lingdong.learning.student.infrastructure.persistence.ParentStudentMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GrowthRewardServiceSecondaryParentTest {
    private final GrowthRewardMapper rewardMapper = mock(GrowthRewardMapper.class);
    private final ParentStudentMapper parentStudentMapper = mock(ParentStudentMapper.class);
    private final GrowthRewardService service = new GrowthRewardService(
            rewardMapper, mock(GrowthPointQueryMapper.class), parentStudentMapper,
            mock(CurrentStudentAccessService.class), mock(FeatureAccessService.class),
            mock(IdGenerator.class), Clock.systemDefaultZone());

    @Test
    void secondaryParentReadsRewardsButCannotCreateOne() {
        long parentId = 8910000000000000801L;
        long studentId = 8910000000000000802L;
        AuthenticatedUser secondaryParent = new AuthenticatedUser(
                parentId, 1L, "secondary", "副家长", AuthClientType.WEB, List.of("PARENT"));
        when(parentStudentMapper.existsActiveByParentAndStudent(parentId, studentId)).thenReturn(true);
        when(parentStudentMapper.existsActivePrimaryByParentAndStudent(parentId, studentId)).thenReturn(false);
        when(rewardMapper.findManagedByStudentId(studentId, 0, 20)).thenReturn(List.of());

        assertThat(service.findManaged(secondaryParent, studentId, 1, 20).items()).isEmpty();
        assertThatThrownBy(() -> service.create(
                secondaryParent, studentId,
                new SaveGrowthRewardCommand(
                        "周末奖励", 100L, null, null, GrowthRewardStatus.ONLINE)))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
