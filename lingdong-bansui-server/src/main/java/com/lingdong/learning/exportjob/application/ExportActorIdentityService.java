package com.lingdong.learning.exportjob.application;
import com.lingdong.learning.auth.application.*;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.datascope.application.OrganizationDataScopeService;
import com.lingdong.learning.learningtask.domain.TeacherClassStatus;
import com.lingdong.learning.learningtask.infrastructure.persistence.TeacherClassMapper;
import com.lingdong.learning.organization.application.OrganizationOperationalStatusService;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.student.domain.StudentStatus;
import com.lingdong.learning.student.infrastructure.persistence.StudentMapper;
import com.lingdong.learning.teacher.application.TeacherIdentityService;
import com.lingdong.learning.user.domain.*;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import org.springframework.stereotype.Service;
import java.util.List;

/** Persisted actor identity selects a legacy SQL visibility mode; mode names are never role grants. */
@Service
public class ExportActorIdentityService {
    private final UserMapper users;
    private final ParentActorAccessService parents;
    private final TeacherIdentityService teachers;
    private final StudentMapper students;
    private final TeacherClassMapper teacherClasses;
    private final OrganizationMapper organizations;
    private final OrganizationDataScopeService scopes;
    public ExportActorIdentityService(UserMapper users,ParentActorAccessService parents,TeacherIdentityService teachers,
            StudentMapper students,TeacherClassMapper teacherClasses,OrganizationMapper organizations,OrganizationDataScopeService scopes) {
        this.users=users;this.parents=parents;this.teachers=teachers;this.students=students;
        this.teacherClasses=teacherClasses;this.organizations=organizations;this.scopes=scopes;
    }
    public Scope require(long userId) {
        var user=users.findById(userId);
        if(user==null || user.status()!=UserStatus.ENABLED)throw denied();
        if(user.type()==UserType.FAMILY) {
            parents.require(new AuthenticatedUser(userId,null,"","",AuthClientType.WEB,List.of()));
            return new Scope("PARENT",false,List.of());
        }
        if(user.type()==UserType.STUDENT) {
            var student=students.findByStudentUserId(userId);
            if(student==null || student.status()!=StudentStatus.ENABLED)throw denied();
            return new Scope("STUDENT",false,List.of());
        }
        if(user.type()!=UserType.ORGANIZATION && user.type()!=UserType.PLATFORM)throw denied();
        var scope=scopes.resolve(userId);
        if(scope!=null && (scope.allOrganizations() || !scope.rootPaths().isEmpty()))
            return new Scope("ORG_ADMIN",scope.allOrganizations(),scope.rootPaths());
        if(teachers.isTeacher(userId) && teacherClasses.findActiveByTeacher(userId).stream().anyMatch(relation -> {
            var organization=organizations.findById(relation.classOrganizationId());
            return relation.status()==TeacherClassStatus.ACTIVE && organization!=null
                    && "CLASS".equals(organization.typeCode()) && OrganizationOperationalStatusService.isOperational(organization);
        }))return new Scope("TEACHER",false,List.of());
        throw denied();
    }
    public record Scope(String mode,boolean allOrganizations,List<String> rootPaths) { }
    private static SystemOperationAccessDeniedException denied() {
        return new SystemOperationAccessDeniedException("导出需要真实启用身份及当前有效家庭、教师班级或组织范围");
    }
}
