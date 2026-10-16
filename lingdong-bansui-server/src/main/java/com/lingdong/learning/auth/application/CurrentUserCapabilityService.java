package com.lingdong.learning.auth.application;

import com.lingdong.learning.auth.infrastructure.persistence.ParentAuthenticationMapper;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.learningtask.application.LearningTaskScopeService;
import com.lingdong.learning.teacher.application.TeacherIdentityService;
import com.lingdong.learning.user.domain.UserStatus;
import com.lingdong.learning.user.domain.UserType;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import org.springframework.stereotype.Service;
import java.util.List;

/** Current persisted identities are returned separately from granted role and action names. */
@Service
public class CurrentUserCapabilityService {
    private final UserMapper users;
    private final ParentAuthenticationMapper parents;
    private final TeacherIdentityService teachers;
    private final LearningTaskScopeService tasks;
    public CurrentUserCapabilityService(UserMapper users,ParentAuthenticationMapper parents,
            TeacherIdentityService teachers,LearningTaskScopeService tasks) {
        this.users=users;this.parents=parents;this.teachers=teachers;this.tasks=tasks;
    }
    public Capabilities describe(AuthenticatedUser actor) {
        var user=actor==null?null:users.findById(actor.userId());
        if(user==null || user.status()!=UserStatus.ENABLED)
            throw new SystemOperationAccessDeniedException("当前账号不存在或已停用");
        return new Capabilities(user.type(),user.type()!=UserType.STUDENT&&parents.findProfileByUserId(user.id())!=null,
                teachers.isTeacher(user.id()),tasks.allowedSourceTypes(actor));
    }
    public record Capabilities(UserType userType,boolean parentProfile,boolean teacherProfile,
            List<String> learningTaskSourceTypes) { }
}
