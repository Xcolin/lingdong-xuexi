package com.lingdong.learning.teacher.application;
import com.lingdong.learning.teacher.infrastructure.persistence.TeacherProfileMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.user.domain.UserType;
import com.lingdong.learning.user.domain.UserStatus;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import org.springframework.stereotype.Service;
/** Occupation is persisted independently of role grants; removing or renaming a role does not erase the profile. */
@Service
public class TeacherIdentityService {
 @org.springframework.beans.factory.annotation.Autowired private com.lingdong.learning.permission.application.PermissionDecisionService permissions;
 private final TeacherProfileMapper profiles; private final UserMapper users; private final com.lingdong.learning.feature.application.FeatureAccessService features;
 public TeacherIdentityService(TeacherProfileMapper profiles,UserMapper users,com.lingdong.learning.feature.application.FeatureAccessService features){this.profiles=profiles;this.users=users;this.features=features;}
 public boolean isTeacher(Long userId){var user=userId==null?null:users.findById(userId);return user!=null&&user.type()==UserType.ORGANIZATION&&user.status()==UserStatus.ENABLED&&profiles.enabled(userId);}
 public boolean hasProfile(Long userId){var user=userId==null?null:users.findById(userId);return user!=null&&user.type()==UserType.ORGANIZATION&&profiles.enabled(userId);}
 public void requireTeacher(Long userId){if(!isTeacher(userId))throw new SystemOperationAccessDeniedException("需要启用中的教师职业档案");}
 /** Trusted creation/rebuild primitive. No HTTP endpoint grants occupational identity from a requested role code. */
 public void register(Long userId){var user=userId==null?null:users.findById(userId);if(user==null||user.type()!=UserType.ORGANIZATION||user.status()!=UserStatus.ENABLED)throw new IllegalArgumentException("教师档案必须关联启用机构账号");features.requireEnabled("TEACHER_MANAGEMENT",null);profiles.insert(userId);}
 /** Trusted account creation only; occupational metadata does not enable teacher-management APIs. */
 public void registerCreatedIdentity(Long actor,Long userId){
  if(!permissions.isAllowed(actor,com.lingdong.learning.permission.domain.PermissionClient.WEB,"IAM_USER_CREATE"))throw new SystemOperationAccessDeniedException("缺少创建用户权限");
  var user=userId==null?null:users.findById(userId);
  if(user==null||user.type()!=UserType.ORGANIZATION||user.status()!=UserStatus.ENABLED)throw new IllegalArgumentException("教师档案必须关联启用机构账号");
  profiles.insert(userId);
 }
}
