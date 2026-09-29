package com.lingdong.learning.student.application;

import com.lingdong.learning.auth.infrastructure.security.StudentLoginCodeDigest;
import com.lingdong.learning.auth.infrastructure.security.StudentLoginCodeGenerator;
import com.lingdong.learning.auth.infrastructure.security.StudentLoginCodeHasher;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.iam.domain.Role;
import com.lingdong.learning.iam.domain.RoleStatus;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.student.domain.StudentCredential;
import com.lingdong.learning.student.infrastructure.persistence.StudentCredentialMapper;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserType;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 原子签发学生用户、固定角色和不可逆登录凭证。 */
@Service
public class StudentIdentityProvisioningService {
    private static final String STUDENT_ROLE_CODE = "STUDENT";
    private static final String GLOBAL_SCOPE_KEY = "GLOBAL";

    private final StudentAccountAllocator accountAllocator;
    private final StudentLoginCodeGenerator loginCodeGenerator;
    private final StudentLoginCodeHasher loginCodeHasher;
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final StudentCredentialMapper credentialMapper;
    private final IdGenerator idGenerator;

    public StudentIdentityProvisioningService(
            StudentAccountAllocator accountAllocator,
            StudentLoginCodeGenerator loginCodeGenerator,
            StudentLoginCodeHasher loginCodeHasher,
            UserMapper userMapper,
            RoleMapper roleMapper,
            UserRoleMapper userRoleMapper,
            StudentCredentialMapper credentialMapper,
            IdGenerator idGenerator
    ) {
        this.accountAllocator = accountAllocator;
        this.loginCodeGenerator = loginCodeGenerator;
        this.loginCodeHasher = loginCodeHasher;
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.credentialMapper = credentialMapper;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public IssuedStudentCredential issue(String studentName) {
        String normalizedName = normalizeStudentName(studentName);
        String studentAccount = accountAllocator.allocate();
        Long studentUserId = idGenerator.nextId();
        User studentUser = User.create(studentUserId, studentAccount, normalizedName, null, UserType.STUDENT);
        if (userMapper.insert(studentUser) != 1) {
            throw new IllegalStateException("学生用户创建失败");
        }

        Role studentRole = roleMapper.findByCode(STUDENT_ROLE_CODE);
        if (studentRole == null || studentRole.status() != RoleStatus.ENABLED) {
            throw new IllegalStateException("内置学生角色不可用");
        }
        if (userRoleMapper.insert(idGenerator.nextId(), studentUserId, studentRole.id(), null, GLOBAL_SCOPE_KEY) != 1) {
            throw new IllegalStateException("学生角色授权失败");
        }

        String plainLoginCode = loginCodeGenerator.generate();
        StudentLoginCodeDigest digest = loginCodeHasher.hash(plainLoginCode);
        StudentCredential credential = StudentCredential.initial(idGenerator.nextId(), studentUserId, digest);
        if (credentialMapper.insert(credential) != 1) {
            throw new IllegalStateException("学生登录凭证创建失败");
        }
        return new IssuedStudentCredential(studentUserId, studentAccount, plainLoginCode);
    }

    /** 签发新登录码并覆盖旧摘要，调用方负责在同一事务撤销既有会话。 */
    @Transactional
    public String resetLoginCode(Long studentUserId) {
        if (studentUserId == null) {
            throw new IllegalArgumentException("学生用户标识不能为空");
        }
        String plainLoginCode = loginCodeGenerator.generate();
        StudentLoginCodeDigest digest = loginCodeHasher.hash(plainLoginCode);
        if (credentialMapper.resetLoginCode(studentUserId, digest) != 1) {
            throw new IllegalStateException("学生登录凭证不存在");
        }
        return plainLoginCode;
    }

    private String normalizeStudentName(String studentName) {
        if (studentName == null || studentName.trim().isEmpty()) {
            throw new IllegalArgumentException("学生姓名不能为空");
        }
        String normalized = studentName.trim();
        if (normalized.length() > 64) {
            throw new IllegalArgumentException("学生姓名长度不能超过64个字符");
        }
        return normalized;
    }
}
