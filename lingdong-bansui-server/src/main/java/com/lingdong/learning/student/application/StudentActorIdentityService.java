package com.lingdong.learning.student.application;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.application.ParentActorAccessService;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.user.domain.*;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import org.springframework.stereotype.Service;

/** Account identity is independent of the exact student-management action and its resource scope. */
@Service
public class StudentActorIdentityService {
    private final UserMapper users;
    private final ParentActorAccessService parents;
    public StudentActorIdentityService(UserMapper users,ParentActorAccessService parents) { this.users=users;this.parents=parents; }
    public void requireParent(AuthenticatedUser actor) { parents.require(actor); }
    public boolean isParent(AuthenticatedUser actor) {
        try { parents.require(actor);return true; }
        catch(SystemOperationAccessDeniedException exception) { return false; }
    }
    public boolean isInstitutionOperator(AuthenticatedUser actor) {
        var user=actor==null?null:users.findById(actor.userId());
        return user!=null && user.status()==UserStatus.ENABLED
                && (user.type()==UserType.ORGANIZATION || user.type()==UserType.PLATFORM);
    }
    /** The exact permission and institution scope are checked by the caller, independently of the original signup type. */
    public void requireInstitutionCreationAccount(AuthenticatedUser actor) {
        var user=actor==null?null:users.findById(actor.userId());
        if(user==null || user.status()!=UserStatus.ENABLED || user.type()==null || user.type()==UserType.STUDENT)
            throw new SystemOperationAccessDeniedException("当前账号不能创建机构成员");
    }
    public void requireInstitutionOperator(AuthenticatedUser actor) {
        if(!isInstitutionOperator(actor))throw new SystemOperationAccessDeniedException("当前账号不是启用的机构管理主体");
    }
}
