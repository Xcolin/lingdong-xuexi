package com.lingdong.learning.student.application;

import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.datascope.application.OrganizationDataScopeService;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthPointAccountMapper;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthPointLifecycleMapper;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.student.domain.Student;
import com.lingdong.learning.student.infrastructure.persistence.StudentMapper;
import com.lingdong.learning.student.infrastructure.persistence.StudentOrganizationMapper;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserType;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ManagedStudentProfileServiceTest {
    final UserMapper users = mock(UserMapper.class);
    final OrganizationMapper organizations = mock(OrganizationMapper.class);
    final StudentMapper students = mock(StudentMapper.class);
    final StudentOrganizationMapper relationships = mock(StudentOrganizationMapper.class);
    final GrowthPointAccountMapper points = mock(GrowthPointAccountMapper.class);
    final GrowthPointLifecycleMapper lifecycle = mock(GrowthPointLifecycleMapper.class);
    final StudentIdentityProvisioningService identities = mock(StudentIdentityProvisioningService.class);
    final PermissionDecisionService permissions = mock(PermissionDecisionService.class);
    final OrganizationDataScopeService scopes = mock(OrganizationDataScopeService.class);
    final FeatureAccessService features = mock(FeatureAccessService.class);
    final IdGenerator ids = mock(IdGenerator.class);
    final ManagedStudentProfileService service = new ManagedStudentProfileService(users, organizations, students,
            relationships, points, lifecycle, identities, permissions, scopes, features, ids);

    @BeforeEach void setup() {
        when(permissions.isAllowed(eq(1L), eq(PermissionClient.WEB), anyString())).thenReturn(true);
        when(scopes.canAccess(eq(1L), anyLong())).thenReturn(true);
        when(scopes.resolve(1L)).thenReturn(com.lingdong.learning.datascope.application.OrganizationDataScope.all(false));
        when(relationships.existsActiveEnrollmentInScope(anyLong(), anyLong(), any())).thenReturn(true);
        when(organizations.findByIdForUpdate(10L)).thenReturn(org(10L, null, "SCHOOL", "学校"));
        when(organizations.findByIdForUpdate(11L)).thenReturn(org(11L, 10L, "GRADE", "三年级"));
        when(organizations.findByIdForUpdate(12L)).thenReturn(org(12L, 11L, "CLASS", "一班"));
        when(organizations.findByIdForUpdate(20L)).thenReturn(org(20L, null, "FAMILY", "住宅"));
    }
    Organization org(long id, Long parent, String type, String name) {
        return Organization.create(id, parent, "PARENT", "CODE" + id, name, type, "/" + id + "/", 1, null);
    }
    @Test void familyProfileNeedsNeitherSchoolNorGrade() {
        var placement = service.validate(1L, 20L, null, null);
        assertThat(placement.schoolId()).isNull();
        assertThat(placement.gradeCode()).isNull();
    }
    @Test void trainingRootNeedsNoAdministrativeGrade() {
        when(organizations.findByIdForUpdate(30L)).thenReturn(org(30L, null, "TRAINING_INSTITUTION", "培训"));
        assertThat(service.validate(1L, 30L, null, null).schoolId()).isEqualTo(30L);
    }
    @Test void resolvesSchoolGradeClassAndNonEducationExplicitSchool() {
        assertThat(service.validate(1L, 10L, null, "三年级").classId()).isNull();
        assertThat(service.validate(1L, 11L, null, null).gradeCode()).isEqualTo("三年级");
        var placement = service.validate(1L, 12L, null, null);
        assertThat(placement.schoolId()).isEqualTo(10L);
        assertThat(placement.classId()).isEqualTo(12L);
        assertThat(service.validate(1L, 20L, 10L, "四年级").gradeCode()).isEqualTo("四年级");
        verify(features).requireEnabled("CLASS_MANAGEMENT", null);
        verify(features, never()).requireEnabled("LEARNING_TASK_MANAGEMENT", null);
    }
    @Test void rejectsMissingGradeAndMismatchedOrNonSchoolPlacement() {
        assertThatThrownBy(() -> service.validate(1L, 10L, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.validate(1L, 12L, 20L, null)).hasMessageContaining("不一致");
        assertThat(service.validate(1L, 20L, null, "三年级").schoolId()).isNull();
        assertThatThrownBy(() -> service.validate(1L, 20L, 11L, "三年级")).hasMessageContaining("学校组织");
    }
    @Test void rejectsScopeAndPermissionBeforeWritingAnyStudent() {
        when(scopes.canAccess(1L, 10L)).thenReturn(false);
        assertThatThrownBy(() -> service.complete(1L, 100L, 20L, 10L, "三年级"))
                .isInstanceOf(SystemOperationAccessDeniedException.class);
        when(permissions.isAllowed(1L, PermissionClient.WEB, "IAM_USER_CREATE")).thenReturn(false);
        assertThatThrownBy(() -> service.complete(1L, 100L, 12L, null, null)).isInstanceOf(SystemOperationAccessDeniedException.class);
        verifyNoInteractions(users, students, points, lifecycle, relationships, identities);
    }
    @Test void createsProfileBoundToSameCustomUserAndSchoolClass() {
        User user = User.create(100L, "xiangsihan", "向思涵", null, UserType.STUDENT);
        when(users.findByIdForUpdate(100L)).thenReturn(user);
        when(ids.nextId()).thenReturn(101L, 102L, 103L);
        when(students.insert(any())).thenReturn(1);
        when(points.insertInitial(101L)).thenReturn(1);
        when(lifecycle.insertDormancyState(101L)).thenReturn(1);
        when(relationships.insertEnrollment(anyLong(), eq(101L), eq(10L))).thenReturn(1);
        when(relationships.insertClass(anyLong(), eq(101L), eq(12L))).thenReturn(1);
        service.complete(1L, 100L, 12L, null, null);
        verify(students).insert(argThat(s -> s.studentUserId().equals(100L) && s.gradeCode().equals("三年级")));
        verify(identities).ensureExistingUserIdentity(user);
        verify(users, never()).insert(any());
    }
    @Test void refusesDisabledUserAndDisabledRelationshipFeature() {
        User enabled = User.create(100L, "xiangsihan", "向思涵", null, UserType.STUDENT);
        User disabled = new User(enabled.id(), enabled.username(), enabled.displayName(), enabled.mobile(),
                enabled.passwordHash(), enabled.type(), com.lingdong.learning.user.domain.UserStatus.DISABLED,
                enabled.createdAt(), enabled.updatedAt());
        when(users.findByIdForUpdate(100L)).thenReturn(disabled);
        assertThatThrownBy(() -> service.complete(1L, 100L, 12L, null, null)).hasMessageContaining("不能补齐");
        verifyNoInteractions(students, points, lifecycle, relationships, identities);
        doThrow(new IllegalStateException("关闭")).when(features).requireEnabled("STUDENT_ORGANIZATION_RELATIONSHIP", null);
        assertThatThrownBy(() -> service.validate(1L, 12L, null, null)).hasMessageContaining("关闭");
    }
    @Test void repairPreservesExistingEnrollmentClassAndGradeAndRejectsConflict() {
        User user = User.create(100L, "xiangsihan", "向思涵", null, UserType.STUDENT);
        Student student = Student.create(101L, "已有姓名", "旧年级", 100L);
        when(users.findByIdForUpdate(100L)).thenReturn(user);
        when(students.findByStudentUserId(100L)).thenReturn(student);
        when(students.findByIdForUpdate(101L)).thenReturn(student);
        when(relationships.findActiveEnrollmentOrganizationIds(101L)).thenReturn(List.of(10L));
        when(relationships.findActiveClassOrganizationIdsInEnrollment(101L,10L)).thenReturn(List.of(12L));
        when(relationships.existsActiveClass(101L, 12L)).thenReturn(true);
        when(points.insertInitial(101L)).thenReturn(1);
        when(lifecycle.insertDormancyState(101L)).thenReturn(1);
        service.complete(1L, 100L, 12L, null, "新年级");
        verify(students, never()).insert(any());
        verify(relationships, never()).insertEnrollment(any(), any(), any());
        verify(relationships, never()).insertClass(any(), any(), any());
        when(relationships.findActiveEnrollmentOrganizationIds(101L)).thenReturn(List.of(99L));
        assertThatThrownBy(() -> service.complete(1L, 100L, 12L, null, null)).hasMessageContaining("不能覆盖");
        when(relationships.findActiveEnrollmentOrganizationIds(101L)).thenReturn(List.of(10L));
        when(relationships.findActiveClassOrganizationIdsInEnrollment(101L,10L)).thenReturn(List.of(99L));
        assertThatThrownBy(() -> service.complete(1L, 100L, 12L, null, null)).hasMessageContaining("班级关系");
        when(relationships.findActiveEnrollmentOrganizationIds(101L)).thenReturn(List.of());
        when(relationships.existsEnrollment(101L, 10L)).thenReturn(true);
        assertThatThrownBy(() -> service.complete(1L, 100L, 12L, null, null)).hasMessageContaining("恢复流程");
    }

 @Test void campusPlacementKeepsSchoolIdentityAndRequiresExplicitGrade() {
  when(organizations.findByIdForUpdate(13L)).thenReturn(org(13L,10L,"CAMPUS","校区"));
  assertThat(service.validate(1L,13L,null,"四年级").schoolId()).isEqualTo(10L);
  assertThat(service.validate(1L,13L,null,"四年级").classId()).isNull();
  assertThatThrownBy(() -> service.validate(1L,13L,99L,"四年级")).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(() -> service.validate(1L,13L,null,null)).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void userCreateAloneIncludesDefaultStudentClassPlacementWithoutExpandingScope(){
  when(permissions.isAllowed(eq(1L),eq(PermissionClient.WEB),anyString())).thenReturn(false);
  when(permissions.isAllowed(1L,PermissionClient.WEB,"IAM_USER_CREATE")).thenReturn(true);
  when(scopes.canAccess(1L,10L)).thenReturn(false);
  assertThat(service.validate(1L,12L,null,null).classId()).isEqualTo(12L);
  verify(permissions,never()).isAllowed(1L,PermissionClient.WEB,"STUDENT_CREATE");
  verify(permissions,never()).isAllowed(1L,PermissionClient.WEB,"STUDENT_CLASS_ASSIGN");
  when(scopes.canAccess(1L,12L)).thenReturn(false);
  assertThatThrownBy(()->service.validate(1L,12L,null,null)).isInstanceOf(SystemOperationAccessDeniedException.class);
 }
 @Test void trustedInitialPlacementWorksWithManagementFlagsOffWhileRepairStaysProtected(){
  doThrow(new IllegalStateException("关系管理关闭")).when(features).requireEnabled("STUDENT_ORGANIZATION_RELATIONSHIP",null);
  doThrow(new IllegalStateException("班级管理关闭")).when(features).requireEnabled("CLASS_MANAGEMENT",null);
  assertThat(service.validateInitialPlacement(1L,12L,null,null).classId()).isEqualTo(12L);
  assertThatThrownBy(()->service.validate(1L,12L,null,null)).hasMessageContaining("关系管理关闭");
  when(scopes.canAccess(1L,12L)).thenReturn(false);
  assertThatThrownBy(()->service.validateInitialPlacement(1L,12L,null,null)).isInstanceOf(SystemOperationAccessDeniedException.class);
 }
}


