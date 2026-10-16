package com.lingdong.learning.student.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.learningtask.application.StudentOption;
import com.lingdong.learning.organization.application.OrganizationOperationalStatusService;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.student.infrastructure.persistence.StudentOrganizationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/** 班级上下文中的学生目录和入班候选，不依赖任务创建权限。 */
@Service
public class StudentClassQueryService {
    private final OrganizationMapper organizations;
    private final StudentOrganizationMapper students;
    private final StudentManagementAccessService access;
    private final FeatureAccessService features;

    public StudentClassQueryService(OrganizationMapper organizations, StudentOrganizationMapper students,
            StudentManagementAccessService access, FeatureAccessService features) {
        this.organizations = organizations;
        this.students = students;
        this.access = access;
        this.features = features;
    }

    @Transactional(readOnly = true)
    public List<StudentOption> options(AuthenticatedUser user, Long classId, boolean membersOnly) {
        access.require(user, membersOnly ? "STUDENT_READ" : "STUDENT_CLASS_ASSIGN");
        if (user.clientType() != AuthClientType.WEB)
            throw new SystemOperationAccessDeniedException("班级管理仅支持管理端");
        features.requireEnabled("CLASS_MANAGEMENT", null);
        if (!membersOnly) features.requireEnabled("STUDENT_ORGANIZATION_RELATIONSHIP", null);
        var classroom = classId == null ? null : organizations.findById(classId);
        if (classroom == null || !"CLASS".equals(classroom.typeCode()) || !access.organizationAllowed(user, classId)
                || !membersOnly && !OrganizationOperationalStatusService.isOperational(classroom))
            throw new ResourceNotFoundException("班级不存在或不可访问");
        var all=organizations.findAll(); var byId=new java.util.HashMap<Long,com.lingdong.learning.organization.domain.Organization>(); all.forEach(o -> byId.put(o.id(),o));
        var order=com.lingdong.learning.organization.application.OrganizationTreeOrder.comparator(all);
        var paths=new com.lingdong.learning.organization.application.OrganizationNamePathService(organizations).namePathsById();
        return students.findClassStudentOptions(classId,access.scope(user),membersOnly).stream().sorted((a,b) -> {
          var first=byId.get(a.currentClassId()); var second=byId.get(b.currentClassId());
          int c=first==null?(second==null?0:1):second==null?-1:order.compare(first,second);
          if(c==0)c=a.studentName().compareTo(b.studentName()); return c==0?a.id().compareTo(b.id()):c;
        }).map(o -> new StudentOption(o.id(),o.studentName(),o.studentAccountMasked(),o.currentClassId(),paths.getOrDefault(o.currentClassId(),o.currentClassName()),o.relationshipRole())).toList();
    }
}
