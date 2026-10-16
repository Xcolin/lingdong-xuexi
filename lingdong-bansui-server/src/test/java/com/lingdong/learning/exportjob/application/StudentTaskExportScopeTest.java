package com.lingdong.learning.exportjob.application;
import com.lingdong.learning.auth.application.*;
import com.lingdong.learning.auth.infrastructure.persistence.ParentAuthenticationMapper;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.datascope.application.*;
import com.lingdong.learning.exportjob.infrastructure.config.ExportJobProperties;
import com.lingdong.learning.exportjob.infrastructure.persistence.*;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.learningtask.domain.TeacherClassRelation;
import com.lingdong.learning.learningtask.infrastructure.persistence.TeacherClassMapper;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.student.infrastructure.persistence.StudentMapper;
import com.lingdong.learning.teacher.application.TeacherIdentityService;
import com.lingdong.learning.teacher.infrastructure.persistence.TeacherProfileMapper;
import com.lingdong.learning.user.domain.*;
import com.lingdong.learning.user.infrastructure.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real account/profile/class fixtures select SQL modes, independent of role grant names. */
class StudentTaskExportScopeTest {
    final long actor=11L,ownClass=22L,otherSchoolClass=99L;
    final UserMapper users=mock(UserMapper.class);
    final UserRoleMapper roles=mock(UserRoleMapper.class);
    final ParentAuthenticationMapper parents=mock(ParentAuthenticationMapper.class);
    final TeacherProfileMapper teacherProfiles=mock(TeacherProfileMapper.class);
    final FeatureAccessService features=mock(FeatureAccessService.class);
    final TeacherIdentityService teachers=new TeacherIdentityService(teacherProfiles,users,features);
    final TeacherClassMapper teacherClasses=mock(TeacherClassMapper.class);
    final OrganizationMapper organizations=mock(OrganizationMapper.class);
    final OrganizationDataScopeService scopes=mock(OrganizationDataScopeService.class);
    final PermissionDecisionService permissions=mock(PermissionDecisionService.class);
    final ExportActorIdentityService identities=new ExportActorIdentityService(users,
            new ParentActorAccessService(users,parents,roles),teachers,mock(StudentMapper.class),teacherClasses,organizations,scopes);
    final StudentTaskExportMapper tasks=mock(StudentTaskExportMapper.class);
    final OrgTaskStatExportMapper statistics=mock(OrgTaskStatExportMapper.class);
    final AttendanceLedgerExportMapper attendance=mock(AttendanceLedgerExportMapper.class);
    final StudentTaskExportAccessService taskAccess=new StudentTaskExportAccessService(users,roles,permissions,features,scopes,tasks,new ExportJobProperties(),identities);
    final OrgTaskStatExportAccessService statAccess=new OrgTaskStatExportAccessService(users,roles,permissions,features,scopes,statistics,identities);
    final AttendanceLedgerExportAccessService attendanceAccess=new AttendanceLedgerExportAccessService(users,roles,permissions,features,scopes,attendance,identities);
    @BeforeEach void allowActions() {
        when(permissions.isAllowed(eq(actor),eq(PermissionClient.WEB),anyString())).thenReturn(true);
        when(scopes.resolve(actor)).thenReturn(OrganizationDataScope.empty());
    }
    void family() {
        when(users.findById(actor)).thenReturn(User.create(actor,"custom_family","family",null,UserType.FAMILY));
        when(parents.findProfileByUserId(actor)).thenReturn(mock(ParentProfile.class));
        when(roles.hasAnyEnabledRole(actor)).thenReturn(true);
    }
    void teacher() {
        when(users.findById(actor)).thenReturn(User.create(actor,"custom_teacher","teacher",null,UserType.ORGANIZATION));
        when(teacherProfiles.enabled(actor)).thenReturn(true);
        when(teacherClasses.findActiveByTeacher(actor)).thenReturn(List.of(TeacherClassRelation.active(33L,actor,ownClass)));
        when(organizations.findById(ownClass)).thenReturn(Organization.create(ownClass,null,"ROOT","CLASS","class","CLASS","/own-school/class/",1,null));
    }
    @Test void customFamilyKeepsRelationshipModesDespiteOrganizationScopeGrant() {
        family();when(scopes.resolve(actor)).thenReturn(OrganizationDataScope.all(false));
        var visibility=taskAccess.require(actor);
        assertThat(visibility.role()).isEqualTo("PARENT");
        assertThat(visibility.rootPaths()).isEmpty();
        assertThat(attendanceAccess.require(actor).mode()).isEqualTo("PARENT");
        assertThatThrownBy(() -> statAccess.require(actor)).isInstanceOf(SystemOperationAccessDeniedException.class);
        verify(permissions).isAllowed(actor,PermissionClient.WEB,"LEARNING_TASK_READ_MANAGED");
    }
    @Test void customTeacherRequiresOccupationAndCurrentOperationalClass() {
        teacher();assertThat(taskAccess.require(actor).role()).isEqualTo("TEACHER");
        assertThat(attendanceAccess.require(actor).mode()).isEqualTo("TEACHER");
        when(teacherClasses.findActiveByTeacher(actor)).thenReturn(List.of());
        assertThatThrownBy(() -> taskAccess.require(actor)).isInstanceOf(SystemOperationAccessDeniedException.class);
        assertThatThrownBy(() -> attendanceAccess.require(actor)).isInstanceOf(SystemOperationAccessDeniedException.class);
    }
    @Test void frozenCrossSchoolClassesCannotBeDeliveredEvenWithExportAction() {
        when(users.findById(actor)).thenReturn(User.create(actor,"custom_org","org",null,UserType.ORGANIZATION));
        when(scopes.resolve(actor)).thenReturn(new OrganizationDataScope(false,false,List.of("/own-school/")));
        when(statistics.findVisibleOrgIds(any(),isNull(),isNull(),anyInt())).thenReturn(List.of(ownClass));
        when(attendance.findVisibleClassIds(any(),anyInt())).thenReturn(List.of(ownClass));
        var frozen=new ExportScopeSnapshot(null,100L,null,null,null,null,null,null,"ORG_ADMIN",List.of(otherSchoolClass),"ORGANIZATION",List.of(otherSchoolClass));
        assertThatThrownBy(() -> statAccess.requireFrozen(actor,frozen)).isInstanceOf(SystemOperationAccessDeniedException.class);
        assertThatThrownBy(() -> attendanceAccess.requireFrozen(actor,frozen)).isInstanceOf(SystemOperationAccessDeniedException.class);
    }
    @Test void frozenModeCannotUpgradeTeacherToOrganization() {
        teacher();when(attendance.findVisibleClassIds(any(),anyInt())).thenReturn(List.of(ownClass));
        var frozen=new ExportScopeSnapshot(null,100L,null,null,null,null,null,null,null,null,"ORGANIZATION",List.of(ownClass));
        assertThatThrownBy(() -> attendanceAccess.requireFrozen(actor,frozen)).isInstanceOf(SystemOperationAccessDeniedException.class);
        verify(attendance,never()).findVisibleClassIds(any(),anyInt());
    }
    @Test void familyReadDependencyCannotBeReplacedByTeacherProgressPermission() {
        family();when(permissions.isAllowed(actor,PermissionClient.WEB,"LEARNING_TASK_READ_MANAGED")).thenReturn(false);
        assertThatThrownBy(() -> taskAccess.require(actor)).isInstanceOf(SystemOperationAccessDeniedException.class);
        verify(permissions,never()).isAllowed(actor,PermissionClient.WEB,"LEARNING_TASK_PROGRESS_READ");
    }
}
