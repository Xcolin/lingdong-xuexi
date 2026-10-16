package com.lingdong.learning.auth.web;
import com.lingdong.learning.auth.application.*;
import com.lingdong.learning.auth.infrastructure.persistence.ParentWechatBindingMapper;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.user.domain.UserType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
@RestController
@RequestMapping("/api/v1/auth/account-settings")
public class AccountSettingsController {
 private final JdbcTemplate jdbc; private final UserMapper users; private final RegistrationVerificationService proofs;
 private final ParentWechatBindingMapper bindings;
 @org.springframework.beans.factory.annotation.Autowired private AuthenticationApplicationService auth;
 @org.springframework.beans.factory.annotation.Autowired private InitialPasswordStateService initialPasswords;
 @org.springframework.beans.factory.annotation.Autowired private com.lingdong.learning.permission.application.PermissionDecisionService permissions;
 @org.springframework.beans.factory.annotation.Autowired private com.lingdong.learning.datascope.application.OrganizationDataScopeService scopes;
 public AccountSettingsController(JdbcTemplate jdbc,UserMapper users,RegistrationVerificationService proofs,ParentWechatBindingMapper bindings){this.jdbc=jdbc;this.users=users;this.proofs=proofs;this.bindings=bindings;}
 public record Settings(String mobile,String email,boolean wechatBound,boolean passwordChangeRequired,boolean hasPassword,boolean canManageUsers){}
 public record Codes(String channel,String destination,String purpose){}
 public record Contact(String channel,String destination,String code,String oldCode,String currentPassword){}
 private String contact(Long id,String channel){String table=table(channel);var values=jdbc.queryForList("select "+column(channel)+" from "+table+" where user_id=?",String.class,id);return values.isEmpty()?null:values.get(0);}
 private static String table(String channel){if("SMS".equals(channel))return "auth_verified_mobile";if("EMAIL".equals(channel))return "auth_verified_email";throw new IllegalArgumentException("验证方式无效");}
 private static String column(String channel){table(channel);return "SMS".equals(channel)?"mobile":"email";}
 private void actor(AuthenticatedUser actor){var user=users.findById(actor.userId());if(user==null||user.type()==UserType.STUDENT)throw new AuthenticationFailedException();}
 @GetMapping public Settings get(@AuthenticationPrincipal AuthenticatedUser actor,@RequestParam(required=false) Long organizationId){actor(actor);return new Settings(contact(actor.userId(),"SMS"),contact(actor.userId(),"EMAIL"),bindings.findByUserId(actor.userId())!=null,initialPasswords.required(actor.userId()),users.findById(actor.userId()).passwordHash()!=null,canManage(actor,organizationId));}
 @PostMapping("/codes") public void codes(@AuthenticationPrincipal AuthenticatedUser actor,@RequestBody Codes c,HttpServletRequest servlet){actor(actor);String destination=RegistrationVerificationService.normalize(c.channel(),c.destination());if(!Set.of("BIND","CHANGE_OLD","CHANGE_NEW").contains(c.purpose()))throw new IllegalArgumentException("验证用途无效");if("CHANGE_OLD".equals(c.purpose())&&!Objects.equals(contact(actor.userId(),c.channel()),destination))throw new IllegalArgumentException("请验证当前联系方式");proofs.sendForPurpose(c.channel(),destination,servlet.getRemoteAddr(),"CONTACT:"+actor.userId()+":"+c.purpose());}
 @Transactional @PostMapping("/contact") public void bind(@AuthenticationPrincipal AuthenticatedUser actor,@RequestBody Contact c,HttpServletRequest servlet){actor(actor);auth.verifyAccountPassword(actor.userId(),c.currentPassword());users.findByIdForUpdate(actor.userId());String destination=RegistrationVerificationService.normalize(c.channel(),c.destination()),old=contact(actor.userId(),c.channel());if(old!=null){proofs.consumeForPurpose(c.channel(),old,c.oldCode(),servlet.getRemoteAddr(),"CONTACT:"+actor.userId()+":CHANGE_OLD");}proofs.consumeForPurpose(c.channel(),destination,c.code(),servlet.getRemoteAddr(),"CONTACT:"+actor.userId()+":"+(old==null?"BIND":"CHANGE_NEW"));Long conflict=jdbc.queryForObject("select count(*) from "+table(c.channel())+" where "+column(c.channel())+"=? and user_id<>?",Long.class,destination,actor.userId());if(conflict!=null&&conflict>0)throw new IllegalArgumentException("联系方式已被其他账号绑定");if("SMS".equals(c.channel())){var existing=users.findByMobileForUpdate(destination);if(existing!=null&&!existing.id().equals(actor.userId()))throw new IllegalArgumentException("手机号已被其他账号绑定");var user=users.findByIdForUpdate(actor.userId());if(users.updateMobileIfExpected(actor.userId(),user.mobile(),destination)!=1)throw new IllegalArgumentException("联系方式已变更，请重试");}jdbc.update("delete from "+table(c.channel())+" where user_id=?",actor.userId());jdbc.update("insert into "+table(c.channel())+"(user_id,"+column(c.channel())+",verified_at) values(?,?,current_timestamp)",actor.userId(),destination);}
 public record EstablishPassword(String channel,String code,String newPassword){}
 @Transactional @PostMapping("/password") public void establish(@AuthenticationPrincipal AuthenticatedUser actor,@RequestBody EstablishPassword c,HttpServletRequest servlet){actor(actor);var user=users.findByIdForUpdate(actor.userId());if(user.passwordHash()!=null)throw new IllegalArgumentException("请使用旧密码修改密码");String destination=contact(actor.userId(),c.channel());if(destination==null)throw new IllegalArgumentException("请先通过账号恢复渠道验证身份");proofs.consumeForPurpose(c.channel(),destination,c.code(),servlet.getRemoteAddr(),"CONTACT:"+actor.userId()+":CHANGE_OLD");if(users.updatePasswordHash(actor.userId(),auth.encodeManagedUserPassword(c.newPassword()))!=1)throw new IllegalStateException("密码保存失败");initialPasswords.clear(actor.userId());auth.revokeAllActiveSessionsForUser(actor.userId());} private boolean canManage(AuthenticatedUser actor,Long organizationId){return organizationId!=null&&permissions.isAllowed(actor.userId(),com.lingdong.learning.permission.domain.PermissionClient.valueOf(actor.clientType().name()),"IAM_USER_PASSWORD_SET")&&scopes.canAccess(actor.userId(),organizationId);}
}

