package com.lingdong.learning.teacher.application;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.datascope.application.OrganizationDataScopeService;
import com.lingdong.learning.teacher.infrastructure.persistence.*;
import com.lingdong.learning.user.infrastructure.persistence.*;
import java.util.List;
class TeacherSubjectAccessTest {
 @Test void deniedReadAndWriteCannotProbeTeacherOrInstitution(){var permissions=mock(PermissionDecisionService.class);var access=mock(TeacherManagementAccessService.class);var scopes=mock(OrganizationDataScopeService.class);var teachers=mock(TeacherManagementMapper.class);var memberships=mock(UserOrganizationMapper.class);var users=mock(UserMapper.class);var store=mock(TeacherSubjectStore.class);var dictionary=mock(TeacherSubjectDictionary.class);var service=new TeacherSubjectService(permissions,access,scopes,teachers,memberships,users,store,dictionary);var actor=new AuthenticatedUser(1L,2L,"actor","actor",AuthClientType.MINIAPP,List.of());assertThatThrownBy(()->service.read(actor,4L,2L)).isInstanceOf(SystemOperationAccessDeniedException.class);assertThatThrownBy(()->service.update(actor,4L,2L,"MATH",0)).isInstanceOf(SystemOperationAccessDeniedException.class);verifyNoInteractions(access,scopes,teachers,memberships,users,store,dictionary);}
}
