package com.lingdong.learning.student.web;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.student.application.StudentAccountCancellationCandidate;
import com.lingdong.learning.student.application.StudentAccountCancellationCommand;
import com.lingdong.learning.student.application.StudentAccountCancellationService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudentAccountCancellationControllerTest {
    private static final long OPERATOR_ID = 8940000000000000101L;
    private static final long STUDENT_ID = 8940000000000000102L;
    private static final long ORGANIZATION_ID = 8940000000000000103L;

    private final StudentAccountCancellationService service =
            mock(StudentAccountCancellationService.class);
    private final StudentAccountCancellationController controller =
            new StudentAccountCancellationController(service);

    @Test
    void returnsSnowflakeIdentifiersAsStrings() {
        when(service.listCandidates(operator())).thenReturn(List.of(
                new StudentAccountCancellationCandidate(
                        STUDENT_ID, "待注销学生", "20260001",
                        ORGANIZATION_ID, "原学校")));

        assertThat(controller.candidates(operator()))
                .containsExactly(new StudentAccountCancellationCandidateResponse(
                        Long.toString(STUDENT_ID), "待注销学生", "20260001",
                        Long.toString(ORGANIZATION_ID), "原学校"));
    }

    @Test
    void forwardsPathStudentAndValidatedBodyToService() {
        controller.cancel(operator(), STUDENT_ID,
                new StudentAccountCancellationRequest(
                        "学生已经退学并解除全部家长关系", "确认注销学生账号"));

        verify(service).cancel(operator(), new StudentAccountCancellationCommand(
                STUDENT_ID, "学生已经退学并解除全部家长关系", "确认注销学生账号"));
    }

    private AuthenticatedUser operator() {
        return new AuthenticatedUser(
                OPERATOR_ID, 1L, "student_cancel_operator", "注销管理员",
                AuthClientType.WEB, List.of("ORG_ADMIN"));
    }
}
