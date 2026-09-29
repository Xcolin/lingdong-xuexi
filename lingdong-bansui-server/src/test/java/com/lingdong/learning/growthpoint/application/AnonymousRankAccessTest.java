package com.lingdong.learning.growthpoint.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.student.infrastructure.persistence.ParentStudentMapper;
import com.lingdong.learning.student.infrastructure.persistence.StudentOrganizationMapper;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserType;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnonymousRankAccessTest {
    private final UserMapper users=mock(UserMapper.class);
    private final UserRoleMapper roles=mock(UserRoleMapper.class);
    private final ParentStudentMapper parents=mock(ParentStudentMapper.class);
    private final StudentOrganizationMapper classes=mock(StudentOrganizationMapper.class);
    private final PermissionDecisionService permissions=mock(PermissionDecisionService.class);
    private final FeatureAccessService features=mock(FeatureAccessService.class);
    private final long parent=1874244142494691001L, student=1874244142494691002L, classroom=1874244142494691003L;
    private final AuthenticatedUser user=new AuthenticatedUser(parent,parent+9,"parent","家长",AuthClientType.WEB,List.of("PARENT"));
    private AnonymousRankAccess access;
    @BeforeEach void setup() {
        access=new AnonymousRankAccess(users,roles,parents,classes,permissions,features);
        when(users.findById(parent)).thenReturn(User.create(parent,"parent","家长",null,UserType.FAMILY));
        when(roles.hasRoleCode(parent,"PARENT")).thenReturn(true);
        when(parents.existsActiveByParentAndStudent(parent,student)).thenReturn(true);
        when(classes.existsActiveClass(student,classroom)).thenReturn(true);
        when(permissions.isAllowed(parent,PermissionClient.WEB,"ANONYMOUS_CLASS_RANK_READ")).thenReturn(true);
    }
    @Test void allowsCurrentParentAndChecksFeatureAndDynamicPermission() {
        assertThatCode(() -> access.requireRead(user,student,classroom)).doesNotThrowAnyException();
        verify(features).requireEnabled("ANONYMOUS_CLASS_RANK",null);
        verify(permissions).isAllowed(parent,PermissionClient.WEB,"ANONYMOUS_CLASS_RANK_READ");
    }
    @Test void refusesUnrelatedChildOrDifferentClass() {
        assertThatThrownBy(() -> access.requireRead(user,student,classroom+1)).isInstanceOf(SystemOperationAccessDeniedException.class);
        when(parents.existsActiveByParentAndStudent(parent,student)).thenReturn(false);
        assertThatThrownBy(() -> access.requireRead(user,student,classroom)).isInstanceOf(SystemOperationAccessDeniedException.class);
    }
    @Test void classOptionsRequireCurrentRelationshipPermissionAndFeature() {
        access.requireStudent(user,student);
        verify(features).requireEnabled("ANONYMOUS_CLASS_RANK",null);
        when(parents.existsActiveByParentAndStudent(parent,student)).thenReturn(false);
        assertThatThrownBy(() -> access.requireStudent(user,student)).isInstanceOf(SystemOperationAccessDeniedException.class);
        verifyNoInteractions(classes);
    }
    @Test void rechecksRolesAndPermissionInsteadOfTrustingSessionRoles() {
        when(roles.hasRoleCode(parent,"SYS_AUDITOR")).thenReturn(true);
        assertThatThrownBy(() -> access.requireRead(user,student,classroom)).isInstanceOf(SystemOperationAccessDeniedException.class);
        when(roles.hasRoleCode(parent,"SYS_AUDITOR")).thenReturn(false);
        when(permissions.isAllowed(parent,PermissionClient.WEB,"ANONYMOUS_CLASS_RANK_READ")).thenReturn(false);
        assertThatThrownBy(() -> access.requireRead(user,student,classroom)).isInstanceOf(SystemOperationAccessDeniedException.class);
        when(roles.hasRoleCode(parent,"PARENT")).thenReturn(false);
        assertThatThrownBy(() -> access.requireRead(user,student,classroom)).isInstanceOf(SystemOperationAccessDeniedException.class);
    }
    @Test void rejectsMissingIdentityMiniappAndInvalidIds() {
        assertThatThrownBy(() -> access.requireRead(null,student,classroom)).isInstanceOf(SystemOperationAccessDeniedException.class);
        var mini=new AuthenticatedUser(parent,parent+9,"parent","家长",AuthClientType.MINIAPP,List.of("PARENT"));
        assertThatThrownBy(() -> access.requireRead(mini,student,classroom)).isInstanceOf(SystemOperationAccessDeniedException.class);
        assertThatThrownBy(() -> access.requireRead(user,1L,classroom)).isInstanceOf(IllegalArgumentException.class);
        when(users.findById(parent)).thenReturn(null);
        assertThatThrownBy(() -> access.requireRead(user,student,classroom)).isInstanceOf(SystemOperationAccessDeniedException.class);
    }
}
