package com.lingdong.learning.exceptionreport.application;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.datascope.application.*;
import com.lingdong.learning.exceptionreport.infrastructure.persistence.*;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.teacher.application.TeacherIdentityService;
import com.lingdong.learning.teacher.infrastructure.persistence.TeacherProfileMapper;
import com.lingdong.learning.user.domain.*;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExceptionReportIdentityScopeTest {
    final UserMapper users=mock(UserMapper.class);
    final TeacherProfileMapper profiles=mock(TeacherProfileMapper.class);
    final FeatureAccessService features=mock(FeatureAccessService.class);
    final TeacherIdentityService teachers=new TeacherIdentityService(profiles,users,features);
    final OrganizationDataScopeService scopes=mock(OrganizationDataScopeService.class);
    final ExceptionReportMapper mapper=mock(ExceptionReportMapper.class);
    final PermissionDecisionService permissions=mock(PermissionDecisionService.class);
    final ExceptionReportApplicationService service=new ExceptionReportApplicationService(mapper,scopes,features,
        mock(IdGenerator.class),Clock.systemUTC(),permissions,mock(com.lingdong.learning.organization.application.OrganizationNamePathService.class),teachers,users);
    final AuthenticatedUser actor=new AuthenticatedUser(1L,2L,"custom","custom",AuthClientType.WEB,List.of("CUSTOM_TEACHER","SYS_AUDITOR"));
    void teacher() {
        when(users.findById(1L)).thenReturn(User.create(1L,"teacher","teacher",null,UserType.ORGANIZATION));
        when(profiles.enabled(1L)).thenReturn(true);when(scopes.resolve(1L)).thenReturn(OrganizationDataScope.empty());
        when(permissions.isAllowed(1L,PermissionClient.WEB,"EXCEPTION_REPORT_READ")).thenReturn(true);
        when(mapper.findTeacherClassOptions(1L)).thenReturn(List.of(new ExceptionReportClassOption(3L,"own class")));
    }
    @Test void customTeacherUsesActualAssignedClassScope() {
        teacher();assertThat(service.findClassOptions(actor)).containsExactly(new ExceptionReportClassOption(3L,"own class"));
        service.findPage(actor,null,null,null,null,1,20);
        verify(mapper).findPage(new ExceptionReportQuery(1L,true,false,List.of(),null,null,null,null,20,0));
    }
    @Test void realOrganizationScopeTakesPriorityOverTeacherClassBranch() {
        teacher();when(scopes.resolve(1L)).thenReturn(new OrganizationDataScope(false,false,List.of("/own-school/")));
        assertThat(service.teacherOnlyScope(actor)).isFalse();
        service.findPage(actor,null,null,null,null,1,20);
        verify(mapper).findPage(new ExceptionReportQuery(1L,false,false,List.of("/own-school/"),null,null,null,null,20,0));
    }
    @Test void revokedTeacherProfileWithoutOrganizationScopeCannotReadReports() {
        teacher();when(profiles.enabled(1L)).thenReturn(false);
        assertThatThrownBy(() -> service.findPage(actor,null,null,null,null,1,20)).isInstanceOf(SystemOperationAccessDeniedException.class);
        verify(mapper,never()).findPage(any());
    }
}
