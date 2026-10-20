package com.lingdong.learning.growthpoint.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthPointAccountViewRow;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthPointQueryMapper;
import com.lingdong.learning.learningtask.application.CurrentStudentAccessService;
import com.lingdong.learning.student.infrastructure.persistence.ParentStudentMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GrowthPointQueryServiceSecondaryParentTest {
    @Test void rejectsUnrelatedChildEvenWithCustomFamilyRole() {
        var parent = new AuthenticatedUser(1L, 2L, "family", "family", AuthClientType.WEB, List.of("CUSTOM_PARENT"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.findChildAccount(parent, 99L))
                .isInstanceOf(com.lingdong.learning.common.web.ResourceNotFoundException.class);
        org.mockito.Mockito.verify(queryMapper, org.mockito.Mockito.never()).findAccountByStudentId(99L);
    }

    private final GrowthPointQueryMapper queryMapper = mock(GrowthPointQueryMapper.class);
    private final CurrentStudentAccessService studentAccessService = mock(CurrentStudentAccessService.class);
    private final ParentStudentMapper parentStudentMapper = mock(ParentStudentMapper.class);
    private final FeatureAccessService featureAccessService = mock(FeatureAccessService.class);
    private final GrowthPointQueryService service = new GrowthPointQueryService(
            queryMapper, studentAccessService, parentStudentMapper,
            featureAccessService, Clock.systemDefaultZone(), mock(com.lingdong.learning.auth.application.ParentActorAccessService.class));

    @Test
    void secondaryParentReadsChildPointAccountWithoutReceivingPrimaryWriteScope() {
        long parentId = 8910000000000000701L;
        long studentId = 8910000000000000702L;
        AuthenticatedUser secondaryParent = new AuthenticatedUser(
                parentId, 1L, "secondary", "副家长", AuthClientType.WEB, List.of("CUSTOM_PARENT"));
        when(parentStudentMapper.existsActiveByParentAndStudent(parentId, studentId)).thenReturn(true);
        when(parentStudentMapper.existsActivePrimaryByParentAndStudent(parentId, studentId)).thenReturn(false);
        when(queryMapper.findAccountByStudentId(studentId)).thenReturn(
                new GrowthPointAccountViewRow(
                        studentId, "学生", "三年级2班", 100L, 80L, LocalDateTime.of(2026, 8, 9, 14, 0)));

        GrowthPointAccountView result = service.findChildAccount(secondaryParent, studentId);

        assertThat(result.studentId()).isEqualTo(studentId);
        assertThat(result.availablePoints()).isEqualTo(80L);
    }
}
