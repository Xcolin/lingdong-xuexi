package com.lingdong.learning.user.application;

import com.lingdong.learning.auth.application.AuthenticationApplicationService;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.iam.domain.Role;
import com.lingdong.learning.iam.audit.application.IamChangeAuditEventType;
import com.lingdong.learning.iam.audit.application.IamChangeAuditService;
import com.lingdong.learning.iam.audit.application.IamChangeTargetType;
import com.lingdong.learning.iam.domain.RoleStatus;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.application.OrganizationOperationalStatusService;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserStatus;
import com.lingdong.learning.user.domain.UserType;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserOrganizationMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Application service for user accounts, organization membership, and role grants.
 */
@Service
public class UserAccessApplicationService {
    @org.springframework.beans.factory.annotation.Autowired private com.lingdong.learning.auth.application.InitialPasswordStateService initialPasswords;
    @org.springframework.beans.factory.annotation.Autowired private com.lingdong.learning.organization.onboarding.application.OrganizationOwnerGuard ownerGuard;
    @org.springframework.beans.factory.annotation.Autowired private RoleIdentityCompatibilityService roleIdentityCompatibility;
    @org.springframework.beans.factory.annotation.Autowired private com.lingdong.learning.teacher.application.TeacherIdentityService teacherIdentity;
    @org.springframework.beans.factory.annotation.Autowired private com.lingdong.learning.learningtask.application.TeacherClassAssignmentService teacherClasses;
    @org.springframework.beans.factory.annotation.Autowired private com.lingdong.learning.iam.application.MemberScopeService memberScopes;
    @org.springframework.beans.factory.annotation.Autowired private com.lingdong.learning.permission.application.PermissionDecisionService decisions;
    @org.springframework.beans.factory.annotation.Autowired private com.lingdong.learning.datascope.infrastructure.persistence.OrganizationAdminMapper administrators;
    private static final String GLOBAL_SCOPE_KEY = "GLOBAL";

    private final UserMapper userMapper;
    private final OrganizationMapper organizationMapper;
    private final RoleMapper roleMapper;
    private final UserOrganizationMapper userOrganizationMapper;
    private final UserRoleMapper userRoleMapper;
    private final IdGenerator idGenerator;
    private final AuthenticationApplicationService authenticationApplicationService;
    private final IamChangeAuditService auditService;
    private final com.lingdong.learning.student.application.ManagedStudentProfileService studentProfiles;

    @org.springframework.beans.factory.annotation.Autowired
    public UserAccessApplicationService(
            UserMapper userMapper,
            OrganizationMapper organizationMapper,
            RoleMapper roleMapper,
            UserOrganizationMapper userOrganizationMapper,
            UserRoleMapper userRoleMapper,
            IdGenerator idGenerator,
            AuthenticationApplicationService authenticationApplicationService,
            IamChangeAuditService auditService,
            com.lingdong.learning.student.application.ManagedStudentProfileService studentProfiles
    ) {
        this.userMapper = userMapper;
        this.organizationMapper = organizationMapper;
        this.roleMapper = roleMapper;
        this.userOrganizationMapper = userOrganizationMapper;
        this.userRoleMapper = userRoleMapper;
        this.idGenerator = idGenerator;
        this.authenticationApplicationService = authenticationApplicationService;
        this.auditService = auditService;
        this.studentProfiles = studentProfiles;
    }

    public UserAccessApplicationService(UserMapper users, OrganizationMapper organizations, RoleMapper roles,
            UserOrganizationMapper memberships, UserRoleMapper grants, IdGenerator ids,
            AuthenticationApplicationService auth, IamChangeAuditService audits) {
        this(users, organizations, roles, memberships, grants, ids, auth, audits, null);
    }
    /**
     * Creates an account without a credential. Credential setup belongs to the later authentication flow.
     */
    @Transactional
    public User createUser(CreateUserCommand command) {
        Objects.requireNonNull(command, "创建用户请求不能为空");

        String username = requiredText(command.username(), "用户账号", 64);
        String displayName = requiredText(command.displayName(), "用户名称", 64);
        String mobile = optionalText(command.mobile(), 32);
        UserType type = Objects.requireNonNull(command.type(), "用户类型不能为空");

        if (userMapper.existsByUsername(username)) {
            throw new DuplicateUserAccountException(username);
        }
        if (mobile != null && userMapper.existsByMobile(mobile)) {
            throw new DuplicateUserAccountException(mobile);
        }

        User user = User.create(idGenerator.nextId(), username, displayName, mobile, type);
        try {
            userMapper.insert(user);
            User created = userMapper.findByUsername(username);
            auditService.record(IamChangeAuditEventType.USER_CREATE, command.operatorId(),
                    IamChangeTargetType.USER, created.id(), null, null, null, created.type().name());
            return created;
        } catch (DuplicateKeyException exception) {
            throw new DuplicateUserAccountException(username);
        }
    }

    /** Creates account and initial organization membership in one transaction. */
    @Transactional
    public User createUserInOrganization(CreateUserCommand command, Long organizationId) {
        Organization organization = organizationId == null ? null : organizationMapper.findByIdForUpdate(organizationId);
        if (organization == null) throw new ResourceNotFoundException("组织不存在：" + organizationId);
        if (!OrganizationOperationalStatusService.isOperational(organization)) throw new IllegalStateException("组织已停用，不能创建用户");
        User created = createUser(command);
        associateIdentityOrganization(new AssociateUserWithOrganizationCommand(created.id(), organizationId, command.operatorId()));
        return created;
    }

    /** Creates a managed Web account, organization membership and initial credential atomically. */
    @Transactional
    public User createManagedUserInOrganization(CreateUserCommand command, Long organizationId, String password) {
        return createManagedUserInOrganization(command, organizationId, password, null, null);
    }

    @Transactional
    public User createManagedUserInOrganization(CreateUserCommand command, Long organizationId, String password,
            Long studentSchoolOrganizationId, String gradeCode) {
        return createManagedUserResult(command, organizationId, password, studentSchoolOrganizationId, gradeCode).user();
    }

    @Transactional
    public ManagedUserCreationResult createManagedUserResult(CreateUserCommand command, Long organizationId, String password,
            Long studentSchoolOrganizationId, String gradeCode) {
        return createManagedUserResult(command,organizationId,password,studentSchoolOrganizationId,gradeCode,null);
    }

    @Transactional
    public ManagedUserCreationResult createManagedUserResult(CreateUserCommand command, Long organizationId, String password,
            Long studentSchoolOrganizationId, String gradeCode, String defaultRoleCode) {
        if(command==null||command.operatorId()==null||!decisions.isAllowed(command.operatorId(),com.lingdong.learning.permission.domain.PermissionClient.WEB,"IAM_USER_CREATE"))
            throw new com.lingdong.learning.common.security.SystemOperationAccessDeniedException("缺少创建用户权限");
        String roleCode=defaultRoleCode(command.type(),defaultRoleCode);
        Organization target=memberScopes.requireTarget(command.operatorId(),organizationId);
        OrganizationUserTypePolicy.validate(target.typeCode(),command.type());
        Organization teacherSchool="TEACHER".equals(roleCode)?memberScopes.school(target):null;
        if("TEACHER".equals(roleCode)&&teacherSchool==null)throw new IllegalArgumentException("教师必须归属学校组织");
        Role defaultRole=requireEnabledDefaultRole(roleCode);
        if(("PLATFORM_USER".equals(roleCode)||"ORG_MEMBER".equals(roleCode))&&defaultRole.dataScope()!=com.lingdong.learning.iam.domain.RoleDataScope.SELF)
            throw new IllegalStateException("普通默认角色必须使用SELF数据范围");
        if (command.type() == UserType.STUDENT) {
            if (!command.username().trim().matches("[A-Za-z0-9_.@-]{1,64}")) throw new IllegalArgumentException("学生账号格式不支持登录");
            if (studentProfiles == null) throw new IllegalStateException("学生档案服务不可用");
            studentProfiles.validateInitialPlacement(command.operatorId(), organizationId, studentSchoolOrganizationId, gradeCode);
        }
        if (command.type() == UserType.STUDENT && password != null && !password.isBlank()) {
            throw new IllegalArgumentException("学生密码请使用学生凭据管理流程");
        }
        String passwordHash = command.type() == UserType.STUDENT ? null
                : authenticationApplicationService.encodeManagedUserPassword(password);
        User created = createUserInOrganization(command, organizationId);
        if (passwordHash != null) {
            if(initialPasswords!=null)initialPasswords.require(created.id());
            if (userMapper.updatePasswordHash(created.id(), passwordHash) != 1) {
                throw new IllegalStateException("初始密码保存失败");
            }
        }
        Long roleOrganizationId=teacherSchool==null?null:teacherSchool.id();
        if(roleOrganizationId!=null&&!userOrganizationMapper.exists(created.id(),roleOrganizationId)) associateIdentityOrganization(new AssociateUserWithOrganizationCommand(created.id(),roleOrganizationId,command.operatorId()));
        if(!userRoleMapper.exists(created.id(),defaultRole.id(),roleOrganizationId==null?GLOBAL_SCOPE_KEY:"ORG:"+roleOrganizationId))
            if(teacherSchool!=null)assignSchoolIdentityRole(new AssignRoleToUserCommand(created.id(),defaultRole.id(),roleOrganizationId,command.operatorId()));
            else assignRole(new AssignRoleToUserCommand(created.id(),defaultRole.id(),roleOrganizationId,command.operatorId()));
        if(teacherSchool!=null)teacherIdentity.registerCreatedIdentity(command.operatorId(),created.id());
        if(teacherSchool!=null&&"CLASS".equals(target.typeCode())) {
            User operator=requireUser(command.operatorId());
            teacherClasses.assignCreatedTeacher(new com.lingdong.learning.auth.application.AuthenticatedUser(operator.id(),null,operator.username(),operator.displayName(),com.lingdong.learning.auth.domain.AuthClientType.WEB,List.of()),created.id(),target.id());
        }
        var profile = command.type() == UserType.STUDENT
                ? studentProfiles.completeCreatedIdentity(command.operatorId(), created.id(), organizationId, studentSchoolOrganizationId, gradeCode) : null;
        return new ManagedUserCreationResult(userMapper.findById(created.id()), profile == null ? null : profile.student().id(),
                profile == null ? null : profile.initialLoginCode());
    }

    public record CreationOrganizationOption(String id,String parentId,String name,String typeCode,boolean disabled) { }
    public List<CreationOrganizationOption> creationOrganizations(Long actor,UserType type) {
        if(!decisions.isAllowed(actor,com.lingdong.learning.permission.domain.PermissionClient.WEB,"IAM_USER_CREATE"))throw new com.lingdong.learning.common.security.SystemOperationAccessDeniedException("缺少创建用户权限");
        return memberScopes.orderedAccessible(actor,null).stream().filter(org->{
            if(type==null)return true;
            try{OrganizationUserTypePolicy.validate(org.typeCode(),type);return true;}catch(IllegalArgumentException e){return false;}
        }).map(org->new CreationOrganizationOption(org.id().toString(),org.parentId()==null?null:org.parentId().toString(),org.name(),org.typeCode(),!OrganizationOperationalStatusService.isOperational(org))).toList();
    }

    public List<CreationOrganizationOption> scopeOrganizations(Long actor,String permission){
        if(!java.util.Set.of("IAM_USER_LIST","IAM_USER_ROLE_ASSIGN","IAM_USER_UPDATE","ORG_MEMBER_ASSOCIATE","ORG_ADMIN_ASSIGN").contains(permission)
            ||!decisions.isAllowed(actor,com.lingdong.learning.common.security.RequestPermissionClient.forOperator(actor),permission))throw new com.lingdong.learning.common.security.SystemOperationAccessDeniedException("缺少对应组织选择权限");
        if("IAM_USER_LIST".equals(permission)) {
            List<Long> visible=userOrganizationMapper.isEnabledSystemAdmin(actor)?null:userOrganizationMapper.findDirectoryOrganizationIds(actor);
            var choices=organizationMapper.findAll().stream().filter(org->visible==null||visible.contains(org.id())).toList();
            var ids=choices.stream().map(Organization::id).collect(java.util.stream.Collectors.toSet());
            return choices.stream().map(org->new CreationOrganizationOption(org.id().toString(),org.parentId()!=null&&ids.contains(org.parentId())?org.parentId().toString():null,org.name(),org.typeCode(),false)).toList();
        }
        return memberScopes.orderedAccessible(actor,null).stream().map(org->new CreationOrganizationOption(org.id().toString(),org.parentId()==null?null:org.parentId().toString(),org.name(),org.typeCode(),!OrganizationOperationalStatusService.isOperational(org))).toList();
    }

    static String defaultRoleCode(UserType type,String requested) {
        if(requested!=null&&!requested.isBlank()) {
            if(type!=UserType.ORGANIZATION||!"TEACHER".equals(requested)) throw new IllegalArgumentException("不支持的默认角色");
            return "TEACHER";
        }
        return switch(type) {case PERSONAL -> throw new IllegalArgumentException("普通账号通过公开注册创建，不授予默认角色");case STUDENT -> "STUDENT";case FAMILY -> "PARENT";case PLATFORM -> "PLATFORM_USER";case ORGANIZATION -> "ORG_MEMBER";};
    }
    private Role requireEnabledDefaultRole(String code) {
        Role role=roleMapper.findByCode(code);
        if(role==null||role.status()!=RoleStatus.ENABLED)throw new IllegalStateException("默认角色不可用："+code);
        return role;
    }

    @Transactional
    public User completeStudentProfile(Long operatorId, Long userId, Long organizationId, Long schoolId, String gradeCode) {
        return completeStudentProfileResult(operatorId, userId, organizationId, schoolId, gradeCode).user();
    }

    @Transactional
    public ManagedUserCreationResult completeStudentProfileResult(Long operatorId, Long userId, Long organizationId, Long schoolId, String gradeCode) {
        if (organizationId == null || !userOrganizationMapper.exists(userId, organizationId))
            throw new com.lingdong.learning.common.security.SystemOperationAccessDeniedException("用户未关联所选组织，不能补齐学生档案");
        var profile = studentProfiles.completeWithCredential(operatorId, userId, organizationId, schoolId, gradeCode);
        return new ManagedUserCreationResult(requireUser(userId), profile.student().id(), profile.initialLoginCode());
    }

    public record ManagedUserCreationResult(User user, Long studentId, String initialLoginCode) {
        @Override public String toString() { return "ManagedUserCreationResult[initialLoginCode=REDACTED]"; }
    }

    /**
     * Records an explicit personnel-to-organization association before an organization-scoped role is granted.
     */
    @Transactional
    public void associateAuthorizedOrganization(AssociateUserWithOrganizationCommand command) {
        Objects.requireNonNull(command.operatorId(),"需要当前登录账号");
        Organization target=memberScopes.requireTarget(command.operatorId(),command.organizationId());
        memberScopes.requireUser(command.operatorId(),command.userId(),target);
        associateWithOrganization(command);
    }
    @Transactional
    public void associateWithOrganization(AssociateUserWithOrganizationCommand command) {associateIdentityOrganization(command);}
    /** Internal membership for a newly created identity or an already checked school administrator. */
    @Transactional
    public void associateIdentityOrganization(AssociateUserWithOrganizationCommand command) {
        Objects.requireNonNull(command, "用户组织关联请求不能为空");
        requireUser(command.userId());
        Organization organization = requireOrganization(command.organizationId());
        if (!OrganizationOperationalStatusService.isOperational(organization)) {
            throw new IllegalStateException("组织已停用，不能建立用户组织关联：" + organization.id());
        }
        if (userOrganizationMapper.exists(command.userId(), command.organizationId())) {
            throw new IllegalStateException("用户已关联该组织");
        }
        try {
            Long relationId = idGenerator.nextId();
            userOrganizationMapper.insert(relationId, command.userId(), command.organizationId());
            auditService.record(IamChangeAuditEventType.USER_ORGANIZATION_ASSOCIATE, command.operatorId(),
                    IamChangeTargetType.USER_ORGANIZATION, command.userId(), command.organizationId(),
                    command.organizationId(), null, "ASSOCIATED");
        } catch (DuplicateKeyException exception) {
            throw new IllegalStateException("用户已关联该组织");
        }
    }

    /**
     * Grants a role in a global or one-organization scope. Caller authorization is enforced later by security filters.
     */
    @Transactional
    public void assignAuthorizedRole(AssignRoleToUserCommand command) {
        Objects.requireNonNull(command.operatorId(),"需要当前登录账号");
        assignRoleInternal(command,true);
    }
    /** Internal grant primitive used by trusted provisioning; HTTP uses assignAuthorizedRole. */
    @Transactional
    public void assignRole(AssignRoleToUserCommand command) {assignRoleInternal(command,false);}

    /** Internal identity grant; caller already checks the actual managed organization. */
    @Transactional
    public void assignSchoolIdentityRole(AssignRoleToUserCommand command) {
        Role role=requireRole(command.roleId());
        if(!java.util.Set.of("TEACHER","ORG_ADMIN").contains(role.code())||!com.lingdong.learning.organization.application.OrganizationHierarchy.isInstitutionRoot(requireOrganization(command.organizationId())))throw new IllegalArgumentException("学校身份角色无效");
        assignRoleInternal(command,false);
    }
    private void assignRoleInternal(AssignRoleToUserCommand command,boolean enforceScope) {
        Objects.requireNonNull(command, "用户角色授权请求不能为空");
        requireUser(command.userId());
        Role role = requireRole(command.roleId());
        if (role.status() != RoleStatus.ENABLED) {
            throw new IllegalStateException("角色已停用，不能授予用户：" + role.code());
        }

        if(enforceScope) {
            roleIdentityCompatibility.requireCompatible(command.userId(),role.id());
            requireRoleScope(command.operatorId(),role,command.organizationId());
            requireSelfUserScope(command.operatorId(),command.userId(),role,command.organizationId());
        }
        String scopeKey = enforceScope?resolveAuthorizedScopeKey(command.operatorId(),command.userId(),command.organizationId()):resolveScopeKey(command.userId(),command.organizationId());
        if (userRoleMapper.exists(command.userId(), command.roleId(), scopeKey)) {
            throw new DuplicateUserRoleAssignmentException();
        }

        try {
            Long assignmentId = idGenerator.nextId();
            userRoleMapper.insert(assignmentId, command.userId(), command.roleId(), command.organizationId(), scopeKey);
            auditService.record(IamChangeAuditEventType.USER_ROLE_ASSIGN, command.operatorId(),
                    IamChangeTargetType.USER_ROLE, command.userId(), command.roleId(),
                    command.organizationId(), null, "ASSIGNED");
        } catch (DuplicateKeyException exception) {
            throw new DuplicateUserRoleAssignmentException();
        }
    }

    /**
     * Removes an explicit user-to-organization membership. Administrator relations must be revoked first.
     */
    @Transactional
    public void dissociateFromOrganization(AssociateUserWithOrganizationCommand command) {
        Objects.requireNonNull(command, "用户组织关联撤销请求不能为空");
        requireUser(command.userId());
        requireOrganization(command.organizationId());
        if(ownerGuard!=null)ownerGuard.requireRemovable(command.userId(),command.organizationId());
        if (userOrganizationMapper.lockRelation(command.userId(), command.organizationId()) == null) {
            throw new IllegalStateException("用户未关联该组织");
        }
        if (userOrganizationMapper.delete(command.userId(), command.organizationId()) != 1) {
            throw new IllegalStateException("用户组织关联撤销失败");
        }
        auditService.record(IamChangeAuditEventType.USER_ORGANIZATION_DISSOCIATE, command.operatorId(),
                IamChangeTargetType.USER_ORGANIZATION, command.userId(), command.organizationId(),
                command.organizationId(), "ASSOCIATED", "DISSOCIATED");
    }

    /**
     * Removes a role grant in a global or one-organization scope. Caller authorization is enforced later by security filters.
     */
    @Transactional
    public void removeRole(AssignRoleToUserCommand command) {removeRoleInternal(command,true);}
    @Transactional
    public void removeSchoolIdentityRole(AssignRoleToUserCommand command) {
        Role role=requireRole(command.roleId());
        if(!"ORG_ADMIN".equals(role.code())||!com.lingdong.learning.organization.application.OrganizationHierarchy.isInstitutionRoot(requireOrganization(command.organizationId())))throw new IllegalArgumentException("学校身份角色撤销请求无效");
        removeRoleInternal(command,false);
    }
    @Transactional
    public void removeAutomaticOrganizationAdminRole(AssignRoleToUserCommand command) {
        if(!java.util.Set.of("ORG_ADMIN","PARENT_ADMIN").contains(requireRole(command.roleId()).code()))throw new IllegalArgumentException("管理员角色撤销请求无效");
        memberScopes.requireTarget(command.operatorId(),command.organizationId());
        removeRoleInternal(command,false);
    }
    private void removeRoleInternal(AssignRoleToUserCommand command,boolean enforceScope) {
        Objects.requireNonNull(command, "用户角色移除请求不能为空");
        requireUser(command.userId());
        Role role=requireRole(command.roleId());
        if(enforceScope) {
            requireRoleRemovalScope(command.operatorId(),role,command.organizationId());
            requireSelfUserScope(command.operatorId(),command.userId(),role,command.organizationId());
            requireNoAdministratorIdentity(command.userId(),role,command.organizationId());
        }
        String scopeKey = command.organizationId()==null?GLOBAL_SCOPE_KEY:"ORG:"+command.organizationId();
        if (userRoleMapper.lockRelation(command.userId(), command.roleId(), scopeKey) == null) {
            throw new RoleRemovalStateException("用户未持有该角色的授权");
        }
        if(ownerGuard!=null && java.util.Set.of("ORG_ADMIN","PARENT_ADMIN").contains(role.code()))ownerGuard.requireRemovable(command.userId(),command.organizationId());
        if (userRoleMapper.deleteScoped(command.userId(), command.roleId(), scopeKey) != 1) {
            throw new RoleRemovalStateException("用户角色移除失败");
        }
        auditService.record(IamChangeAuditEventType.USER_ROLE_REMOVE, command.operatorId(),
                IamChangeTargetType.USER_ROLE, command.userId(), command.roleId(),
                command.organizationId(), "ASSIGNED", "REMOVED");
    }

    /**
     * 角色侧批量授予用户：逐用户复用既有授予用例（保留组织范围校验与逐条审计），
     * 组织范围角色必填组织；任一失败整批回滚并报告失败用户及原因。
     */
    @Transactional
    public void batchAssignRole(BatchAssignRoleToRoleCommand command) {
        Objects.requireNonNull(command, "批量授予请求不能为空");
        if (command.assignments() == null || command.assignments().isEmpty() || command.assignments().size() > 200) {
            throw new IllegalArgumentException("批量授予名单无效");
        }
        Role role = requireRole(command.roleId());
        if (role.status() != RoleStatus.ENABLED) {
            throw new IllegalStateException("角色已停用，不能授予用户：" + role.code());
        }
        boolean organizationScoped = role.dataScope() != null && role.dataScope() != com.lingdong.learning.iam.domain.RoleDataScope.ALL && role.dataScope() != com.lingdong.learning.iam.domain.RoleDataScope.SELF;
        List<String> failures = new java.util.ArrayList<>();
        // 预检阶段：先全量校验再写入，保证任一失败时不产生半批写入（并发冲突仍由唯一约束与事务兜底）
        for (BatchRoleAssignmentItem item : command.assignments()) {
            try {
                if (item == null || item.userId() == null) {
                    throw new IllegalArgumentException("用户标识不能为空");
                }
                if (organizationScoped && item.organizationId() == null) {
                    throw new IllegalArgumentException("组织范围角色必须指定组织");
                }
                if (!organizationScoped && item.organizationId() != null) {
                    throw new IllegalArgumentException("全局数据范围角色不需要指定组织");
                }
                requireRoleScope(command.operatorId(),role,item.organizationId());
                requireSelfUserScope(command.operatorId(),item.userId(),role,item.organizationId());
                requireUser(item.userId());
                roleIdentityCompatibility.requireCompatible(item.userId(),role.id());
                String scopeKey = resolveAuthorizedScopeKey(command.operatorId(),item.userId(),item.organizationId());
                if (userRoleMapper.exists(item.userId(), command.roleId(), scopeKey)) {
                    throw new DuplicateUserRoleAssignmentException();
                }
            } catch (RuntimeException exception) {
                failures.add(item == null || item.userId() == null ? "无效条目（" + exception.getMessage() + "）"
                        : item.userId() + "（" + exception.getMessage() + "）");
            }
        }
        if (!failures.isEmpty()) {
            throw new BatchRoleAssignmentException(failures);
        }
        for (BatchRoleAssignmentItem item : command.assignments()) {
            assignAuthorizedRole(new AssignRoleToUserCommand(item.userId(), command.roleId(), item.organizationId(), command.operatorId()));
        }
    }

    /** 更新账号状态；停用或锁定后立即撤销该账号的活动设备会话。 */
    @Transactional
    public User updateProfile(Long operatorId,Long userId,String submittedName,String submittedMobile) {
        Objects.requireNonNull(operatorId,"需要当前登录账号");
        if(!decisions.isAllowed(operatorId,com.lingdong.learning.permission.domain.PermissionClient.WEB,"IAM_USER_UPDATE"))
            throw new com.lingdong.learning.common.security.SystemOperationAccessDeniedException("缺少用户编辑权限");
        memberScopes.requireVisibleUser(operatorId,userId);
        User current=userMapper.findByIdForUpdate(userId);
        if(current==null)throw new ResourceNotFoundException("用户不存在");
        if(current.status()==UserStatus.CANCELLED)throw new IllegalStateException("已注销用户不能编辑");
        String name=requiredText(submittedName,"名称",64);
        String mobile=optionalText(submittedMobile,32);
        if(!Objects.equals(mobile,current.mobile())&&mobile!=null&&userMapper.existsByMobile(mobile))throw new DuplicateUserAccountException(mobile);
        if(Objects.equals(name,current.displayName())&&Objects.equals(mobile,current.mobile()))return current;
        try { if(userMapper.updateProfile(userId,name,mobile)!=1)throw new IllegalStateException("用户更新失败"); }
        catch(DuplicateKeyException exception){throw new DuplicateUserAccountException(mobile);}
        if(current.type()==UserType.STUDENT)userMapper.updateStudentName(userId,name);
        if(!Objects.equals(mobile,current.mobile()))authenticationApplicationService.revokeAllActiveSessionsForUser(userId);
        auditService.record(IamChangeAuditEventType.USER_PROFILE_CHANGE,operatorId,IamChangeTargetType.USER,userId,null,null,null,null);
        return requireUser(userId);
    }

    @Transactional
    public User updateAuthorizedStatus(UpdateUserStatusCommand command) {
        Objects.requireNonNull(command.operatorId(),"需要当前登录账号");
        if(!decisions.isAllowed(command.operatorId(),com.lingdong.learning.permission.domain.PermissionClient.WEB,"IAM_USER_STATUS_CHANGE"))throw new com.lingdong.learning.common.security.SystemOperationAccessDeniedException("缺少用户状态修改权限");
        memberScopes.requireVisibleUser(command.operatorId(),command.userId());
        return updateStatus(command);
    }

    @Transactional
    public User updateStatus(UpdateUserStatusCommand command) {
        Objects.requireNonNull(command, "用户状态变更请求不能为空");
        User user = requireUser(command.userId());
        UserStatus targetStatus = Objects.requireNonNull(command.status(), "用户状态不能为空");
        if (targetStatus == UserStatus.CANCELLED || user.status() == UserStatus.CANCELLED) {
            throw new IllegalArgumentException("已注销状态只能由账号注销流程维护");
        }
        if (user.status() == targetStatus) {
            return user;
        }
        if(targetStatus!=UserStatus.ENABLED && ownerGuard!=null)ownerGuard.requireAccountAvailable(user.id());
        if (userMapper.updateStatus(user.id(), targetStatus) != 1) {
            throw new IllegalStateException("用户状态更新失败");
        }
        if (targetStatus != UserStatus.ENABLED) {
            authenticationApplicationService.revokeAllActiveSessionsForUser(user.id());
        }
        auditService.record(IamChangeAuditEventType.USER_STATUS_CHANGE, command.operatorId(),
                IamChangeTargetType.USER, user.id(), null, null, user.status().name(), targetStatus.name());
        return requireUser(user.id());
    }

    public void validateRoleGrant(Long operatorId,Long roleId,Long organizationId) {
        Objects.requireNonNull(operatorId,"需要当前登录账号");
        requireRoleScope(operatorId,requireRole(roleId),organizationId);
    }
    private void requireRoleScope(Long operatorId,Role role,Long organizationId) {
        if(organizationId==null) {
            if(role.dataScope()==com.lingdong.learning.iam.domain.RoleDataScope.SELF)return;
            if(!memberScopes.all(operatorId))throw new com.lingdong.learning.common.security.SystemOperationAccessDeniedException("全局角色授权需要全部组织范围");
            if(role.dataScope()!=com.lingdong.learning.iam.domain.RoleDataScope.ALL&&role.dataScope()!=com.lingdong.learning.iam.domain.RoleDataScope.SELF)throw new IllegalArgumentException("请选择角色授权组织");
        } else {
            Organization org=memberScopes.requireTarget(operatorId,organizationId);
            if(role.dataScope()==com.lingdong.learning.iam.domain.RoleDataScope.ALL)throw new IllegalArgumentException("全部范围角色应使用全局授权");
            if(role.dataScope()==com.lingdong.learning.iam.domain.RoleDataScope.SCHOOL||role.dataScope()==com.lingdong.learning.iam.domain.RoleDataScope.REGION||role.dataScope()==com.lingdong.learning.iam.domain.RoleDataScope.CLASS||role.dataScope()==com.lingdong.learning.iam.domain.RoleDataScope.FAMILY) {
                if(!(role.dataScope()==com.lingdong.learning.iam.domain.RoleDataScope.SCHOOL ? com.lingdong.learning.organization.application.OrganizationHierarchy.isInstitutionRoot(org) : role.dataScope().name().equals(org.typeCode())))throw new IllegalArgumentException("角色授权组织类型不匹配");
            }
        }
    }
    /** Revocation checks the existing scope without applying new-grant type eligibility. */
    private void requireRoleRemovalScope(Long operatorId,Role role,Long organizationId) {
        Objects.requireNonNull(operatorId,"需要当前登录账号");
        if(organizationId!=null) memberScopes.requireTarget(operatorId,organizationId);
        else if(role.dataScope()!=com.lingdong.learning.iam.domain.RoleDataScope.SELF && !memberScopes.all(operatorId))
            throw new com.lingdong.learning.common.security.SystemOperationAccessDeniedException("移除全局授权需要全部组织范围");
    }
    private void requireSelfUserScope(Long operatorId,Long userId,Role role,Long organizationId) {
        if(role.dataScope()==com.lingdong.learning.iam.domain.RoleDataScope.SELF&&organizationId==null)memberScopes.requireVisibleUser(operatorId,userId);
    }
    private void requireNoAdministratorIdentity(Long userId,Role role,Long organizationId) {
        if(!java.util.Set.of("ORG_ADMIN","PARENT_ADMIN").contains(role.code()))return;
        boolean managed=administrators.findOrganizationIds(userId).stream().map(organizationMapper::findById).filter(Objects::nonNull)
            .anyMatch(org->{Organization school=memberScopes.school(org);return Objects.equals(organizationId,school==null?org.id():school.id());});
        if(managed)throw new RoleRemovalStateException("请先取消对应组织的管理员身份，再移除该角色");
    }
    @Transactional
    public void batchRemoveRole(BatchAssignRoleToRoleCommand command) {
        if(command.assignments()==null||command.assignments().isEmpty()||command.assignments().size()>200)throw new IllegalArgumentException("角色关联列表无效");
        Role role=requireRole(command.roleId());
        java.util.Set<String> keys=new java.util.HashSet<>();
        for(var item:command.assignments()) {
            if(item==null||item.userId()==null)throw new IllegalArgumentException("角色关联项无效");
            requireRoleRemovalScope(command.operatorId(),role,item.organizationId());
            requireSelfUserScope(command.operatorId(),item.userId(),role,item.organizationId());
            requireNoAdministratorIdentity(item.userId(),role,item.organizationId());
            if(!keys.add(item.userId()+":"+item.organizationId()))throw new IllegalArgumentException("角色关联项重复");
            if(!userRoleMapper.exists(item.userId(),role.id(),item.organizationId()==null?GLOBAL_SCOPE_KEY:"ORG:"+item.organizationId()))throw new RoleRemovalStateException("用户未持有该组织范围的角色");
        }
        for(var item:command.assignments())removeRole(new AssignRoleToUserCommand(item.userId(),role.id(),item.organizationId(),command.operatorId()));
    }

    /** Authorization may use an existing descendant relation without broadening user membership. */
    private String resolveAuthorizedScopeKey(Long operatorId,Long userId,Long organizationId) {
        memberScopes.requireVisibleUser(operatorId,userId);
        if(organizationId==null)return GLOBAL_SCOPE_KEY;
        Organization grantRoot=memberScopes.requireTarget(operatorId,organizationId);
        if(!OrganizationOperationalStatusService.isOperational(grantRoot))throw new IllegalStateException("组织已停用，不能授予组织范围角色");
        boolean belongs=userOrganizationMapper.findOrganizationIds(userId).stream().map(organizationMapper::findById).filter(Objects::nonNull)
            .anyMatch(org->org.path().startsWith(grantRoot.path()));
        if(!belongs)throw new IllegalStateException("用户未关联授权组织或其下级组织");
        return "ORG:"+organizationId;
    }
    private String resolveScopeKey(Long userId, Long organizationId) {
        if (organizationId == null) {
            return GLOBAL_SCOPE_KEY;
        }

        Organization organization = requireOrganization(organizationId);
        if (!OrganizationOperationalStatusService.isOperational(organization)) {
            throw new IllegalStateException("组织已停用，不能授予组织范围角色：" + organization.id());
        }
        if (!userOrganizationMapper.exists(userId, organizationId)) {
            throw new IllegalStateException("用户尚未建立该组织关联，不能授予组织范围角色");
        }
        return "ORG:" + organizationId;
    }

    private User requireUser(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("用户标识不能为空");
        }
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new ResourceNotFoundException("用户不存在：" + userId);
        }
        return user;
    }

    private Organization requireOrganization(Long organizationId) {
        if (organizationId == null) {
            throw new IllegalArgumentException("组织标识不能为空");
        }
        Organization organization = organizationMapper.findById(organizationId);
        if (organization == null) {
            throw new ResourceNotFoundException("组织不存在：" + organizationId);
        }
        return organization;
    }

    private Role requireRole(Long roleId) {
        if (roleId == null) {
            throw new IllegalArgumentException("角色标识不能为空");
        }
        Role role = roleMapper.findById(roleId);
        if (role == null) {
            throw new ResourceNotFoundException("角色不存在：" + roleId);
        }
        return role;
    }

    private String requiredText(String value, String fieldName, int maxLength) {
        String normalized = optionalText(value, maxLength);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return normalized;
    }

    private String optionalText(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("文本长度不能超过" + maxLength + "个字符");
        }
        return normalized;
    }
}




