package com.lingdong.learning.student.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.datascope.infrastructure.persistence.OrganizationAdminMapper;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthPointAccountMapper;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthPointLifecycleMapper;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.domain.OrganizationStatus;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.student.domain.Student;
import com.lingdong.learning.student.infrastructure.persistence.ParentStudentMapper;
import com.lingdong.learning.student.infrastructure.persistence.StudentMapper;
import com.lingdong.learning.student.infrastructure.persistence.StudentOrganizationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 学生档案与首个归属关系的应用服务。
 * 家长和机构管理员的创建入口相互独立，读取范围只由当前用户的直接关系决定。
 */
@Service
public class StudentApplicationService {
    private static final String SYSTEM_ADMIN_ROLE = "SYS_ADMIN";
    private static final String PARENT_ROLE = "PARENT";
    private static final String ORGANIZATION_ADMIN_ROLE = "ORG_ADMIN";

    private final StudentMapper studentMapper;
    private final ParentStudentMapper parentStudentMapper;
    private final StudentOrganizationMapper studentOrganizationMapper;
    private final OrganizationMapper organizationMapper;
    private final OrganizationAdminMapper organizationAdminMapper;
    private final StudentIdentityProvisioningService identityProvisioningService;
    private final GrowthPointAccountMapper pointAccountMapper;
    private final GrowthPointLifecycleMapper pointLifecycleMapper;
    private final IdGenerator idGenerator;

    public StudentApplicationService(
            StudentMapper studentMapper,
            ParentStudentMapper parentStudentMapper,
            StudentOrganizationMapper studentOrganizationMapper,
            OrganizationMapper organizationMapper,
            OrganizationAdminMapper organizationAdminMapper,
            StudentIdentityProvisioningService identityProvisioningService,
            GrowthPointAccountMapper pointAccountMapper,
            GrowthPointLifecycleMapper pointLifecycleMapper,
            IdGenerator idGenerator
    ) {
        this.studentMapper = studentMapper;
        this.parentStudentMapper = parentStudentMapper;
        this.studentOrganizationMapper = studentOrganizationMapper;
        this.organizationMapper = organizationMapper;
        this.organizationAdminMapper = organizationAdminMapper;
        this.identityProvisioningService = identityProvisioningService;
        this.pointAccountMapper = pointAccountMapper;
        this.pointLifecycleMapper = pointLifecycleMapper;
        this.idGenerator = idGenerator;
    }

    /** 创建学生及其唯一的初始家长关系或机构关系。 */
    @Transactional
    public CreatedStudent createStudent(AuthenticatedUser currentUser, CreateStudentCommand command) {
        Objects.requireNonNull(currentUser, "当前登录用户不能为空");
        Objects.requireNonNull(command, "创建学生请求不能为空");

        String studentName = requiredText(command.studentName(), "学生姓名", 64);
        String gradeCode = optionalText(command.gradeCode(), "年级编码", 64);
        if (command.organizationId() == null) {
            requireRole(currentUser, PARENT_ROLE, "仅家长可创建家庭学生档案");
            IssuedStudentCredential issued = identityProvisioningService.issue(studentName);
            Student student = Student.create(idGenerator.nextId(), studentName, gradeCode, issued.studentUserId());
            studentMapper.insert(student);
            createPointAccount(student.id());
            parentStudentMapper.insertPrimary(idGenerator.nextId(), currentUser.userId(), student.id());
            return createdStudent(student.id(), issued);
        }

        requireRole(currentUser, ORGANIZATION_ADMIN_ROLE, "仅机构管理员可创建机构学生档案");
        Organization organization = organizationMapper.findById(command.organizationId());
        if (organization == null) {
            throw new ResourceNotFoundException("机构不存在：" + command.organizationId());
        }
        if (organization.status() != OrganizationStatus.ENABLED) {
            throw new IllegalStateException("机构已停用，不能创建学生档案");
        }
        if (!organizationAdminMapper.exists(currentUser.userId(), organization.id())) {
            throw new SystemOperationAccessDeniedException("当前用户不是该机构管理员");
        }
        IssuedStudentCredential issued = identityProvisioningService.issue(studentName);
        Student student = Student.create(idGenerator.nextId(), studentName, gradeCode, issued.studentUserId());
        studentMapper.insert(student);
        createPointAccount(student.id());
        studentOrganizationMapper.insertEnrollment(idGenerator.nextId(), student.id(), organization.id());
        return createdStudent(student.id(), issued);
    }

    /** 以角色与直接关系并集查询学生目录，不允许通过前端参数扩大数据范围。 */
    public StudentDirectoryPage listStudents(AuthenticatedUser currentUser, String keyword, int page, int pageSize) {
        validatePage(page, pageSize);
        StudentDirectoryQuery query = directoryQuery(currentUser, keyword, page, pageSize);
        return new StudentDirectoryPage(studentMapper.findPage(query), page, pageSize, studentMapper.count(query));
    }

    /** 返回当前用户可见的学生档案；无权读取时统一表现为资源不存在。 */
    public Student findStudent(AuthenticatedUser currentUser, Long studentId) {
        if (studentId == null) {
            throw new IllegalArgumentException("学生标识不能为空");
        }
        Student student = studentMapper.findById(studentId);
        if (student == null || !canReadStudent(currentUser, studentId)) {
            throw new ResourceNotFoundException("学生档案不存在或无权访问");
        }
        return student;
    }

    private StudentDirectoryQuery directoryQuery(AuthenticatedUser currentUser, String keyword, int page, int pageSize) {
        Objects.requireNonNull(currentUser, "当前登录用户不能为空");
        boolean systemAdministrator = hasRole(currentUser, SYSTEM_ADMIN_ROLE);
        boolean parent = hasRole(currentUser, PARENT_ROLE);
        boolean organizationAdministrator = hasRole(currentUser, ORGANIZATION_ADMIN_ROLE);
        if (!systemAdministrator && !parent && !organizationAdministrator) {
            throw new SystemOperationAccessDeniedException("当前角色没有学生数据范围");
        }
        return new StudentDirectoryQuery(
                optionalText(keyword, "关键字", 64), currentUser.userId(), systemAdministrator, parent,
                organizationAdministrator, Math.multiplyExact(page - 1, pageSize), pageSize
        );
    }

    private boolean canReadStudent(AuthenticatedUser currentUser, Long studentId) {
        if (currentUser == null) {
            return false;
        }
        if (hasRole(currentUser, SYSTEM_ADMIN_ROLE)) {
            return true;
        }
        boolean parentRelation = hasRole(currentUser, PARENT_ROLE)
                && parentStudentMapper.existsActiveByParentAndStudent(currentUser.userId(), studentId);
        boolean organizationRelation = hasRole(currentUser, ORGANIZATION_ADMIN_ROLE)
                && studentOrganizationMapper.existsActiveByOrganizationAdministratorAndStudent(currentUser.userId(), studentId);
        return parentRelation || organizationRelation;
    }

    /** 回查数据库生成的审计时间，避免创建响应使用未持久化的临时对象。 */
    private Student loadPersistedStudent(Long studentId) {
        Student persistedStudent = studentMapper.findById(studentId);
        if (persistedStudent == null) {
            throw new IllegalStateException("学生档案写入后未找到");
        }
        return persistedStudent;
    }

    private CreatedStudent createdStudent(Long studentId, IssuedStudentCredential issued) {
        return new CreatedStudent(loadPersistedStudent(studentId), issued.studentAccount(), issued.plainLoginCode());
    }

    private void createPointAccount(Long studentId) {
        if (pointAccountMapper.insertInitial(studentId) != 1) {
            throw new IllegalStateException("学生积分账户创建失败");
        }
        if (pointLifecycleMapper.insertDormancyState(studentId) != 1) {
            throw new IllegalStateException("学生积分生命周期状态创建失败");
        }
    }


    private void validatePage(int page, int pageSize) {
        if (page < 1 || page > 1_000_000) {
            throw new IllegalArgumentException("页码必须在 1 至 1000000 之间");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("每页数量必须在 1 至 100 之间");
        }
    }

    private void requireRole(AuthenticatedUser currentUser, String roleCode, String message) {
        if (!hasRole(currentUser, roleCode)) {
            throw new SystemOperationAccessDeniedException(message);
        }
    }

    private boolean hasRole(AuthenticatedUser currentUser, String roleCode) {
        return currentUser != null && currentUser.roleCodes().contains(roleCode);
    }

    private String requiredText(String value, String fieldName, int maxLength) {
        String normalized = optionalText(value, fieldName, maxLength);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return normalized;
    }

    private String optionalText(String value, String fieldName, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "长度不能超过 " + maxLength + " 个字符");
        }
        return normalized;
    }
}
