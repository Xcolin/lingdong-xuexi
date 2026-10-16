package com.lingdong.learning.auth.web;
import com.lingdong.learning.auth.application.*;
import com.lingdong.learning.auth.infrastructure.persistence.ParentWechatBindingMapper;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.id.IdGenerator;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
@RestController
@RequestMapping("/api/v1/auth")
public class AccountWechatAuthenticationController {
 @org.springframework.beans.factory.annotation.Autowired private com.lingdong.learning.user.infrastructure.persistence.UserMapper users;
 @org.springframework.beans.factory.annotation.Autowired private InitialPasswordStateService initial;
 @org.springframework.beans.factory.annotation.Autowired private RegistrationVerificationService codes;
 @org.springframework.beans.factory.annotation.Autowired private PublicRegistrationService registration;
 @org.springframework.beans.factory.annotation.Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;
 private final ParentWechatIdentityGateway gateway;private final ParentWechatBindingTicketService tickets;private final ParentWechatBindingMapper bindings;private final AuthenticationApplicationService auth;private final IdGenerator ids;private final ParentWechatIntegrationAccess access;private final com.lingdong.learning.feature.application.FeatureAccessService features;
 public AccountWechatAuthenticationController(ParentWechatIdentityGateway gateway,ParentWechatBindingTicketService tickets,ParentWechatBindingMapper bindings,AuthenticationApplicationService auth,IdGenerator ids,ParentWechatIntegrationAccess access,com.lingdong.learning.feature.application.FeatureAccessService features){this.gateway=gateway;this.tickets=tickets;this.bindings=bindings;this.auth=auth;this.ids=ids;this.access=access;this.features=features;}
 public record Exchange(String temporaryCode,String deviceId,String deviceName){}
 public record Result(boolean bindingRequired,String bindingTicket,AuthenticatedSession session,boolean registrationRequired){ public Result(boolean required,String ticket,AuthenticatedSession session){this(required,ticket,session,false);} }
 public record Bind(String bindingTicket,String username,String password,String deviceId,String deviceName,String email,String code){public Bind(String ticket,String username,String password,String deviceId,String deviceName){this(ticket,username,password,deviceId,deviceName,null,null);}}
 public record CurrentBind(String temporaryCode,String currentPassword){}
 private void available(){features.requireEnabled("PARENT_WECHAT_AUTH",null);access.requireAvailable();}
 @Transactional @PostMapping("/account-wechat-sessions") public Result exchange(@RequestBody Exchange c){available();var identity=gateway.exchange(c.temporaryCode());var binding=bindings.findActiveByIdentity(identity.appId(),identity.openId());if(binding==null)return new Result(true,tickets.issue(identity),null);return new Result(false,null,auth.loginExistingVerifiedAccount(binding.userId(),AuthClientType.MINIAPP,c.deviceId(),c.deviceName()));}
 @Transactional @PostMapping("/account-wechat-bindings") public Result bind(@RequestBody Bind c,jakarta.servlet.http.HttpServletRequest request){available();var session=auth.loginRegistrationByPassword(c.username(),c.password(),AuthClientType.MINIAPP,c.deviceId(),c.deviceName());Long userId=users.findByUsernameForUpdate(c.username()).id();if(initial.required(userId))return new Result(true,c.bindingTicket(),session);if(jdbc.queryForObject("select count(*) from auth_verified_email where user_id=?",Long.class,userId)==0){String email=RegistrationVerificationService.normalize("EMAIL",c.email());if(jdbc.queryForObject("select count(*) from auth_verified_email where email=?",Long.class,email)>0)throw new IllegalArgumentException("邮箱已绑定其他账号");codes.consume("EMAIL",email,c.code(),request.getRemoteAddr());jdbc.update("insert into auth_verified_email(user_id,email,verified_at) values(?,?,current_timestamp)",userId,email);}var identity=tickets.consume(c.bindingTicket());save(userId,identity,false);return new Result(false,null,session);}
 @Transactional @PostMapping("/account-settings/wechat") public void current(@AuthenticationPrincipal AuthenticatedUser actor,@RequestBody CurrentBind c){available();auth.verifyAccountPassword(actor.userId(),c.currentPassword());save(actor.userId(),gateway.exchange(c.temporaryCode()),true);auth.revokeAllActiveSessionsForUser(actor.userId());}
 public record EmailCode(String email){}
 public record EmailBind(String bindingTicket,String email,String code,String deviceId,String deviceName){}
 public record Register(String bindingTicket,PublicRegistrationService.Command registration){}
 @PostMapping("/account-wechat-email-codes") public void emailCode(@RequestBody EmailCode c,jakarta.servlet.http.HttpServletRequest request){available();codes.send("EMAIL",RegistrationVerificationService.normalize("EMAIL",c.email()),request.getRemoteAddr());}
 @Transactional @PostMapping("/account-wechat-email-bindings") public Result emailBind(@RequestBody EmailBind c,jakarta.servlet.http.HttpServletRequest request){available();String email=RegistrationVerificationService.normalize("EMAIL",c.email());codes.verifyForPurpose("EMAIL",email,c.code(),request.getRemoteAddr(),"REGISTRATION");var owners=jdbc.queryForList("select user_id from auth_verified_email where email=?",Long.class,email);if(owners.isEmpty())return new Result(true,c.bindingTicket(),null,true);Long userId=owners.get(0);var session=auth.loginExistingVerifiedAccount(userId,AuthClientType.MINIAPP,c.deviceId(),c.deviceName());codes.consume("EMAIL",email,c.code(),request.getRemoteAddr());if(initial.required(userId))return new Result(true,c.bindingTicket(),session);var identity=tickets.consume(c.bindingTicket());save(userId,identity,true);auth.revokeAllActiveSessionsForUser(userId);return new Result(false,null,auth.loginExistingVerifiedAccount(userId,AuthClientType.MINIAPP,c.deviceId(),c.deviceName()));}
 @Transactional @PostMapping("/account-wechat-registrations") public Result register(@RequestBody Register c,jakarta.servlet.http.HttpServletRequest request){available();if(!"EMAIL".equals(c.registration().channel())||c.registration().clientType()!=AuthClientType.MINIAPP)throw new IllegalArgumentException("微信注册必须验证邮箱");var identity=tickets.consume(c.bindingTicket());var session=registration.register(c.registration(),request.getRemoteAddr());Long userId=users.findByUsernameForUpdate(c.registration().username().trim()).id();save(userId,identity,false);return new Result(false,null,session);}
 private void save(Long userId,ParentWechatIdentity identity,boolean replace){var other=bindings.findActiveByIdentity(identity.appId(),identity.openId());var current=bindings.findByUserId(userId);if(other!=null&&!other.userId().equals(userId)||!replace&&current!=null&&(!current.appId().equals(identity.appId())||!current.openId().equals(identity.openId())))throw new IllegalArgumentException("微信已绑定其他账号，请先解除原绑定");if(replace&&current!=null){bindings.deleteByUserId(userId);current=null;}if(current==null&&bindings.insert(new ParentWechatBinding(ids.nextId(),userId,identity.appId(),identity.openId(),identity.unionId(),ParentWechatBindingStatus.ACTIVE,LocalDateTime.now(),null))!=1)throw new IllegalStateException("微信绑定失败");}
}






