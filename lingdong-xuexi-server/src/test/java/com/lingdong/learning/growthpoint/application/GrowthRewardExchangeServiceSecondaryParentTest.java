package com.lingdong.learning.growthpoint.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.growthpoint.domain.GrowthRewardExchange;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthPointAccountMapper;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthPointLedgerMapper;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthRewardExchangeMapper;
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

class GrowthRewardExchangeServiceSecondaryParentTest {
    @Test void mixedAuditorCannotReadExchangesEvenAsParent() {
        var user = new AuthenticatedUser(8910000000000000811L, 1L, "auditor", "审核员",
                AuthClientType.WEB, List.of("PARENT", "SYS_AUDITOR"));
        when(parentStudentMapper.existsActiveByParentAndStudent(user.userId(), 8910000000000000812L)).thenReturn(true);
        assertThatThrownBy(() -> service.findManaged(user, 8910000000000000812L, 1, 20))
                .isInstanceOf(com.lingdong.learning.common.security.SystemOperationAccessDeniedException.class);
    }
    private final GrowthRewardExchangeMapper exchangeMapper = mock(GrowthRewardExchangeMapper.class);
    private final ParentStudentMapper parentStudentMapper = mock(ParentStudentMapper.class);
    private final GrowthRewardExchangeService service = new GrowthRewardExchangeService(
            exchangeMapper, mock(GrowthRewardMapper.class), mock(GrowthPointAccountMapper.class),
            mock(GrowthPointLedgerMapper.class), parentStudentMapper,
            mock(CurrentStudentAccessService.class), mock(FeatureAccessService.class),
            mock(IdGenerator.class), Clock.systemDefaultZone());

    @Test
    void secondaryParentReadsExchangesButCannotApproveOne() {
        long parentId = 8910000000000000811L;
        long studentId = 8910000000000000812L;
        long exchangeId = 8910000000000000813L;
        AuthenticatedUser secondaryParent = new AuthenticatedUser(
                parentId, 1L, "secondary", "副家长", AuthClientType.WEB, List.of("PARENT"));
        when(parentStudentMapper.existsActiveByParentAndStudent(parentId, studentId)).thenReturn(true);
        when(parentStudentMapper.existsActivePrimaryByParentAndStudent(parentId, studentId))
                .thenReturn(false);
        when(exchangeMapper.findByStudentId(studentId, 0, 20)).thenReturn(List.of());
        GrowthRewardExchange exchange = mock(GrowthRewardExchange.class);
        when(exchange.studentId()).thenReturn(studentId);
        when(exchangeMapper.findByIdForUpdate(exchangeId)).thenReturn(exchange);

        assertThat(service.findManaged(secondaryParent, studentId, 1, 20).items()).isEmpty();
        assertThatThrownBy(() -> service.approve(secondaryParent, exchangeId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
