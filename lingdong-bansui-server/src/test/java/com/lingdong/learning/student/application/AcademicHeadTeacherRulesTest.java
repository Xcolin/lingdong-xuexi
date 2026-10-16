package com.lingdong.learning.student.application;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.student.infrastructure.persistence.AcademicMetadataStore;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.user.domain.*;
import com.lingdong.learning.learningtask.infrastructure.persistence.TeacherClassMapper;
import com.lingdong.learning.learningtask.domain.TeacherClassRelation;
import java.util.*;
class AcademicHeadTeacherRulesTest {
 final AcademicMetadataStore store=mock(AcademicMetadataStore.class);final StudentManagementAccessService access=mock(StudentManagementAccessService.class);final OrganizationMapper orgs=mock(OrganizationMapper.class);final UserMapper users=mock(UserMapper.class);final TeacherClassMapper teachers=mock(TeacherClassMapper.class);
 final AcademicMetadataService service=new AcademicMetadataService(store,access,orgs,users,teachers,mock(com.lingdong.learning.feature.application.FeatureAccessService.class));
 final AuthenticatedUser actor=new AuthenticatedUser(1L,2L,"actor","actor",AuthClientType.MINIAPP,List.of());
 AcademicHeadTeacherRulesTest(){when(store.headTeacherOptions(3L)).thenReturn(List.of(new AcademicMetadataStore.HeadTeacherOption(4L,"teacher",3L)));var school=Organization.create(2L,null,"ROOT","S","school","SCHOOL","/2/",1,null);var c=Organization.create(3L,2L,"S","C","class","CLASS","/2/3/",1,null);when(orgs.findById(2L)).thenReturn(school);when(orgs.findById(3L)).thenReturn(c);when(orgs.findByIdForUpdate(3L)).thenReturn(c);when(access.organizationAllowed(actor,3L)).thenReturn(true);when(users.findByIdForUpdate(4L)).thenReturn(User.create(4L,"teacher","teacher",null,UserType.ORGANIZATION));when(teachers.findByTeacherAndClass(4L,3L)).thenReturn(TeacherClassRelation.active(5L,4L,3L));}
 @Test void outsideScopeCannotReadOrWrite(){when(access.organizationAllowed(actor,3L)).thenReturn(false);assertThatThrownBy(()->service.setHeadTeacher(actor,3L,4L,0)).isInstanceOf(SystemOperationAccessDeniedException.class);assertThatThrownBy(()->service.classMetadata(actor,3L)).isInstanceOf(SystemOperationAccessDeniedException.class);verifyNoInteractions(store,users,teachers);}
 @Test void nonTeachingUserCannotBecomeHeadTeacher(){when(teachers.findByTeacherAndClass(4L,3L)).thenReturn(null);assertThatThrownBy(()->service.setHeadTeacher(actor,3L,4L,0)).isInstanceOf(IllegalArgumentException.class);verifyNoInteractions(store);}
 @Test void staleVersionCannotWrite(){when(store.classMetadata(3L)).thenReturn(Optional.of(new AcademicMetadataStore.ClassMetadata(3L,4L,2)));assertThatThrownBy(()->service.setHeadTeacher(actor,3L,4L,1)).isInstanceOf(IllegalStateException.class);verify(store,never()).updateClass(anyLong(),any(),anyInt());}
 @Test void successfulCreationRetainsExplicitTeacher(){when(store.classMetadata(3L)).thenReturn(Optional.empty(),Optional.of(new AcademicMetadataStore.ClassMetadata(3L,4L,1)));assertThat(service.setHeadTeacher(actor,3L,4L,0).headTeacherUserId()).isEqualTo(4L);verify(store).insertClass(3L,4L);}
 @Test void clearingTeacherNeedsNoNewTeachingIdentity(){when(store.classMetadata(3L)).thenReturn(Optional.of(new AcademicMetadataStore.ClassMetadata(3L,4L,1)),Optional.of(new AcademicMetadataStore.ClassMetadata(3L,null,2)));when(store.updateClass(3L,null,1)).thenReturn(true);assertThat(service.setHeadTeacher(actor,3L,null,1).headTeacherUserId()).isNull();verifyNoInteractions(users,teachers);}
 @Test void departedTeacherIsNotReturnedAsActive(){when(store.classMetadata(3L)).thenReturn(Optional.of(new AcademicMetadataStore.ClassMetadata(3L,4L,8)));when(users.findById(4L)).thenReturn(User.create(4L,"teacher","teacher",null,UserType.ORGANIZATION));when(teachers.findByTeacherAndClass(4L,3L)).thenReturn(null);var result=service.classMetadata(actor,3L);assertThat(result.headTeacherUserId()).isNull();assertThat(result.versionNo()).isEqualTo(8);}
 @Test void disabledTeacherCannotBeAssigned(){when(users.findByIdForUpdate(4L)).thenReturn(new User(4L,"teacher","teacher",null,null,UserType.ORGANIZATION,UserStatus.DISABLED,null,null));assertThatThrownBy(()->service.setHeadTeacher(actor,3L,4L,0)).isInstanceOf(IllegalArgumentException.class);verifyNoInteractions(store,teachers);}
 @Test void teacherAccountLockPrecedesTeachingCheckAndWrite(){when(store.classMetadata(3L)).thenReturn(Optional.empty(),Optional.of(new AcademicMetadataStore.ClassMetadata(3L,4L,1)));service.setHeadTeacher(actor,3L,4L,0);var order=inOrder(users,teachers,store);order.verify(users).findByIdForUpdate(4L);order.verify(teachers).findByTeacherAndClass(4L,3L);order.verify(store).classMetadata(3L);order.verify(store).insertClass(3L,4L);}
 @Test void inactiveTeacherProfileCannotBeSubmittedDirectly(){when(store.headTeacherOptions(3L)).thenReturn(List.of());assertThatThrownBy(()->service.setHeadTeacher(actor,3L,4L,0)).isInstanceOf(IllegalArgumentException.class);verify(store,never()).insertClass(anyLong(),any());}
 @Test void inactiveTeacherProfileDoesNotRemainVisible(){when(store.headTeacherOptions(3L)).thenReturn(List.of());when(store.classMetadata(3L)).thenReturn(Optional.of(new AcademicMetadataStore.ClassMetadata(3L,4L,9)));when(users.findById(4L)).thenReturn(User.create(4L,"teacher","teacher",null,UserType.ORGANIZATION));assertThat(service.classMetadata(actor,3L).headTeacherUserId()).isNull();}
 @Test void assignedTeacherViewUsesOnlyActualAssignedName(){when(store.classMetadata(3L)).thenReturn(Optional.of(new AcademicMetadataStore.ClassMetadata(3L,4L,9)));when(users.findById(4L)).thenReturn(User.create(4L,"teacher","teacher",null,UserType.ORGANIZATION));var view=service.classView(actor,3L);assertThat(view.headTeacherName()).isEqualTo("teacher");assertThat(view.metadata().headTeacherUserId()).isEqualTo(4L);verify(access).require(actor,"CLASS_READ");}
 @Test void missingTeacherViewDoesNotExposeCandidateName(){when(store.classMetadata(3L)).thenReturn(Optional.empty());assertThat(service.classView(actor,3L).headTeacherName()).isNull();verify(store,never()).headTeacherOptions(anyLong());}
}
