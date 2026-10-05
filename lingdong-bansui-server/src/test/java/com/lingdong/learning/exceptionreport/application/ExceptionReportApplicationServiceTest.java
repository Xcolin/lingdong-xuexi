package com.lingdong.learning.exceptionreport.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.datascope.application.OrganizationDataScope;
import com.lingdong.learning.datascope.application.OrganizationDataScopeService;
import com.lingdong.learning.exceptionreport.domain.ExceptionReportStatus;
import com.lingdong.learning.exceptionreport.domain.ExceptionReportType;
import com.lingdong.learning.exceptionreport.infrastructure.persistence.ExceptionReportMapper;
import com.lingdong.learning.exceptionreport.infrastructure.persistence.ExceptionReportQuery;
import com.lingdong.learning.exceptionreport.infrastructure.persistence.ExceptionReportRow;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.organization.domain.Organization;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExceptionReportApplicationServiceTest {
    private final ExceptionReportMapper mapper = mock(ExceptionReportMapper.class);
    private final OrganizationDataScopeService dataScopeService = mock(OrganizationDataScopeService.class);
    private final FeatureAccessService featureAccessService = mock(FeatureAccessService.class);
    private final IdGenerator idGenerator = mock(IdGenerator.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-06T09:30:00Z"), ZoneId.of("Asia/Shanghai"));
    private final ExceptionReportApplicationService service = new ExceptionReportApplicationService(
            mapper, dataScopeService, featureAccessService, idGenerator, clock, org.mockito.Mockito.mock(com.lingdong.learning.permission.application.PermissionDecisionService.class, invocation -> invocation.getMethod().getName().equals("isAllowed") ? true : org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation)));

    @Test
    void createsReportHistoryAndOrganizationEventForAuthorizedTeacherStudent() {
        AuthenticatedUser teacher = user(8910000000000001001L, "TEACHER", AuthClientType.MINIAPP);
        long reportId = 8910000000000001002L;
        long classId = 8910000000000001003L;
        long studentId = 8910000000000001004L;
        when(mapper.existsActiveTeacherStudent(teacher.userId(), classId, studentId)).thenReturn(true);
        when(idGenerator.nextId()).thenReturn(reportId, 8910000000000001005L, 8910000000000001006L);
        when(mapper.insert(any())).thenReturn(1);
        when(mapper.insertAction(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(mapper.insertLocalEvent(any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(mapper.findVisibleById(any(ExceptionReportQuery.class), eq(reportId)))
                .thenReturn(row(reportId, classId, studentId, teacher.userId(), ExceptionReportStatus.SUBMITTED, 0));

        service.create(teacher, new CreateExceptionReportCommand(
                classId, studentId, ExceptionReportType.LEARNING_STATUS, "课堂注意力明显下降", "report-key-1001"));

        verify(mapper).insertAction(any(), eq(reportId), eq("SUBMIT"), eq(teacher.userId()),
                eq(null), eq("SUBMITTED"), eq("课堂注意力明显下降"), any());
        verify(mapper).insertLocalEvent(any(), eq("EXCEPTION_REPORT_SUBMITTED"), eq(reportId),
                eq("ORGANIZATION"), eq(classId), eq("有新的学生异常报备待处理"), any());
    }

    @Test
    void rejectsTeacherWhenStudentIsOutsideCurrentClass() {
        AuthenticatedUser teacher = user(8910000000000001011L, "TEACHER", AuthClientType.WEB);
        when(mapper.existsActiveTeacherStudent(any(), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.create(teacher, new CreateExceptionReportCommand(
                8910000000000001012L, 8910000000000001013L,
                ExceptionReportType.MENTAL_STATE, "情绪持续低落", "report-key-1011")))
                .isInstanceOf(com.lingdong.learning.common.web.ResourceNotFoundException.class);
    }

    @Test
    void handlesReportWithImmutableActionAndTeacherEvent() {
        AuthenticatedUser administrator = user(8910000000000001021L, "ORG_ADMIN", AuthClientType.WEB);
        long reportId = 8910000000000001022L;
        long teacherId = 8910000000000001023L;
        when(dataScopeService.resolve(administrator.userId())).thenReturn(OrganizationDataScope.all(false));
        when(mapper.findVisibleById(any(ExceptionReportQuery.class), eq(reportId)))
                .thenReturn(row(reportId, 8910000000000001024L, 8910000000000001025L,
                        teacherId, ExceptionReportStatus.SUBMITTED, 3))
                .thenReturn(row(reportId, 8910000000000001024L, 8910000000000001025L,
                        teacherId, ExceptionReportStatus.HANDLED, 4));
        when(mapper.handle(reportId, administrator.userId(), LocalDateTime.now(clock), 3)).thenReturn(1);
        when(idGenerator.nextId()).thenReturn(8910000000000001026L, 8910000000000001027L);
        when(mapper.insertAction(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(mapper.insertLocalEvent(any(), any(), any(), any(), any(), any(), any())).thenReturn(1);

        service.handle(administrator, reportId, new HandleExceptionReportCommand(3, "已联系班主任持续观察"));

        verify(mapper).insertAction(any(), eq(reportId), eq("HANDLE"), eq(administrator.userId()),
                eq("SUBMITTED"), eq("HANDLED"), eq("已联系班主任持续观察"), any());
        verify(mapper).insertLocalEvent(any(), eq("EXCEPTION_REPORT_HANDLED"), eq(reportId),
                eq("USER"), eq(teacherId), eq("学生异常报备已处理"), any());
    }

    @Test
    void rejectsConcurrentHandleWhenVersionWriteDoesNotMatch() {
        AuthenticatedUser administrator = user(8910000000000001031L, "ORG_ADMIN", AuthClientType.MINIAPP);
        long reportId = 8910000000000001032L;
        when(dataScopeService.resolve(administrator.userId())).thenReturn(OrganizationDataScope.all(false));
        when(mapper.findVisibleById(any(ExceptionReportQuery.class), eq(reportId)))
                .thenReturn(row(reportId, 8910000000000001033L, 8910000000000001034L,
                        8910000000000001035L, ExceptionReportStatus.SUBMITTED, 2));
        when(mapper.handle(eq(reportId), eq(administrator.userId()), any(), eq(2L))).thenReturn(0);

        assertThatThrownBy(() -> service.handle(administrator, reportId,
                new HandleExceptionReportCommand(2, "处理完成")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("异常报备状态已变化");
    }

    @Test
    void returnsOnlyAccessibleClassesForOrganizationAdministrator() {
        AuthenticatedUser administrator = user(8910000000000001041L, "ORG_ADMIN", AuthClientType.WEB);
        Organization school = Organization.create(8910000000000001042L, null, null,
                "SCHOOL-1042", "示例学校", "SCHOOL", "/SCHOOL-1042/", 1, null);
        Organization classOrganization = Organization.create(8910000000000001043L, school.id(), null,
                "CLASS-1043", "一年级一班", "CLASS", "/SCHOOL-1042/CLASS-1043/", 1, null);
        when(dataScopeService.findAccessibleOrganizations(administrator.userId()))
                .thenReturn(List.of(school, classOrganization));

        assertThat(service.findClassOptions(administrator))
                .containsExactly(new ExceptionReportClassOption(classOrganization.id(), classOrganization.name()));
    }

    private AuthenticatedUser user(long id, String role, AuthClientType client) {
        return new AuthenticatedUser(id, 1L, "account", "测试用户", client, List.of(role));
    }

    private ExceptionReportRow row(long id, long classId, long studentId, long reporterId,
            ExceptionReportStatus status, long version) {
        return new ExceptionReportRow(id, studentId, "张同学", "26010001", classId, "一年级一班",
                reporterId, "李老师", ExceptionReportType.LEARNING_STATUS, "课堂注意力明显下降",
                status, status == ExceptionReportStatus.HANDLED ? 8910000000000001099L : null,
                status == ExceptionReportStatus.HANDLED ? "机构管理员" : null,
                LocalDateTime.now(clock), status == ExceptionReportStatus.HANDLED ? LocalDateTime.now(clock) : null,
                version);
    }
}
