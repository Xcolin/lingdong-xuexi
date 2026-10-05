package com.lingdong.learning.exceptionreport.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.datascope.application.OrganizationDataScopeService;
import com.lingdong.learning.exceptionreport.infrastructure.persistence.ExceptionReportMapper;
import com.lingdong.learning.feature.application.FeatureAccessService;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** 系统审核员不能因兼任教师而进入学生异常报备领域。 */
class ExceptionReportAuditorBoundaryTest {
    private final ExceptionReportMapper mapper = mock(ExceptionReportMapper.class);
    private final ExceptionReportApplicationService service = new ExceptionReportApplicationService(
            mapper, mock(OrganizationDataScopeService.class), mock(FeatureAccessService.class),
            mock(IdGenerator.class), Clock.systemUTC(), org.mockito.Mockito.mock(com.lingdong.learning.permission.application.PermissionDecisionService.class, invocation -> invocation.getMethod().getName().equals("isAllowed") ? true : org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation)));
    private final AuthenticatedUser user = new AuthenticatedUser(1874244142494694001L,
            1874244142494694002L, "auditor", "测试审核员", AuthClientType.MINIAPP,
            List.of("TEACHER", "SYS_AUDITOR"));

    @Test void cannotReadReportsOrOptions() {
        assertThatThrownBy(() -> service.findPage(user, null, null, null, null, 1, 20))
                .isInstanceOf(SystemOperationAccessDeniedException.class);
        assertThatThrownBy(() -> service.findClassOptions(user))
                .isInstanceOf(SystemOperationAccessDeniedException.class);
        assertThatThrownBy(() -> service.findStudentOptions(user, 1874244142494694003L))
                .isInstanceOf(SystemOperationAccessDeniedException.class);
        verifyNoInteractions(mapper);
    }
}
