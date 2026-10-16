package com.lingdong.learning.teacher.application;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.datascope.application.*;
import com.lingdong.learning.teacher.infrastructure.persistence.*;
import com.lingdong.learning.user.infrastructure.persistence.*;
import java.util.*;
class TeacherSubjectRulesTest {
 final PermissionDecisionService permissions=mock(PermissionDecisionService.class);final TeacherManagementAccessService access=mock(TeacherManagementAccessService.class);final OrganizationDataScopeService scopes=mock(OrganizationDataScopeService.class);final TeacherManagementMapper teachers=mock(TeacherManagementMapper.class);final UserOrganizationMapper members=mock(UserOrganizationMapper.class);final UserMapper users=mock(UserMapper.class);final TeacherSubjectStore store=mock(TeacherSubjectStore.class);final TeacherSubjectDictionary dictionary=mock(TeacherSubjectDictionary.class);final TeacherSubjectService service=new TeacherSubjectService(permissions,access,scopes,teachers,members,users,store,dictionary);final AuthenticatedUser actor=new AuthenticatedUser(1L,2L,"actor","actor",AuthClientType.MINIAPP,List.of());
 TeacherSubjectRulesTest(){when(permissions.isAllowed(eq(1L),eq(PermissionClient.MINIAPP),anyString())).thenReturn(true);when(scopes.resolve(1L)).thenReturn(OrganizationDataScope.all(false));when(teachers.findAccessibleById(4L,true,List.of())).thenReturn(mock(TeacherDirectoryRow.class));when(members.exists(4L,2L)).thenReturn(true);when(users.findByIdForUpdate(4L)).thenReturn(mock(com.lingdong.learning.user.domain.User.class));when(dictionary.validate(nullable(String.class))).thenAnswer(i->i.getArgument(0));}
 @Test void noMetadataRemainsNullWithVersionZero(){var value=service.read(actor,4L,2L);assertThat(value.subjectCode()).isNull();assertThat(value.versionNo()).isZero();verifyNoInteractions(dictionary);}
 @Test void foreignInstitutionMembershipCannotReadOrWrite(){when(members.exists(4L,2L)).thenReturn(false);assertThatThrownBy(()->service.read(actor,4L,2L)).isInstanceOf(com.lingdong.learning.common.web.ResourceNotFoundException.class);assertThatThrownBy(()->service.update(actor,4L,2L,"MATH",0)).isInstanceOf(com.lingdong.learning.common.web.ResourceNotFoundException.class);verifyNoInteractions(store,dictionary);}
 @Test void emptyScopeRejectsBeforeTeacherQuery(){when(scopes.resolve(1L)).thenReturn(OrganizationDataScope.empty());assertThatThrownBy(()->service.read(actor,4L,2L)).isInstanceOf(com.lingdong.learning.common.web.ResourceNotFoundException.class);verifyNoInteractions(teachers,store,dictionary);}
 @Test void staleVersionNeverOverwrites(){when(store.read(4L,2L)).thenReturn(Optional.of(new TeacherSubjectStore.Metadata(4L,2L,"MATH",2)));assertThatThrownBy(()->service.update(actor,4L,2L,"ART",1)).isInstanceOf(IllegalStateException.class);verify(store,never()).update(anyLong(),anyLong(),any(),anyInt());}
 @Test void editorCanClearWithoutReadPermission(){when(permissions.isAllowed(1L,PermissionClient.MINIAPP,"TEACHER_READ")).thenReturn(false);when(store.read(4L,2L)).thenReturn(Optional.of(new TeacherSubjectStore.Metadata(4L,2L,"MATH",1)),Optional.of(new TeacherSubjectStore.Metadata(4L,2L,null,2)));when(store.update(4L,2L,null,1)).thenReturn(true);assertThat(service.update(actor,4L,2L,null,1).versionNo()).isEqualTo(2);verify(permissions,never()).isAllowed(1L,PermissionClient.MINIAPP,"TEACHER_READ");verify(store).update(4L,2L,null,1);}
}
