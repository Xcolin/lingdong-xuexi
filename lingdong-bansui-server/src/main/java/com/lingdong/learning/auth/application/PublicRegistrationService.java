package com.lingdong.learning.auth.application;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.user.domain.*;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.common.id.IdGenerator;
import java.util.Objects;
@Service
public class PublicRegistrationService {
 private final UserMapper users;private final JdbcTemplate jdbc;private final IdGenerator ids;private final AuthenticationApplicationService auth;private final RegistrationVerificationService verification;
 @Value("${registration.agreement.version:2026-10-11}") private String version;
 @Value("${registration.agreement.content:灵动伴随用户协议：注册账号用于本人及合法授权家庭和机构的学习管理。请提供真实联系方式、妥善保管密码，不冒用他人身份，不上传违法或侵权内容。平台仅为认证、账号安全及经授权的学习服务处理必要个人信息，不出售联系方式。未成年人应由监护人协助使用；机构管理权限以审批及授权为准。您可通过账号设置和平台服务渠道申请更正资料、撤销授权或注销账号。继续注册表示您已阅读并同意本协议。}") private String content;
 public PublicRegistrationService(UserMapper users,JdbcTemplate jdbc,IdGenerator ids,AuthenticationApplicationService auth,RegistrationVerificationService verification){this.users=users;this.jdbc=jdbc;this.ids=ids;this.auth=auth;this.verification=verification;}
 public String version(){return version;}public String content(){return content;}
 public record Command(String channel,String destination,String code,String username,String displayName,String password,String passwordConfirmation,boolean agreementAccepted,String agreementVersion,AuthClientType clientType,String deviceId,String deviceName){}
 @Transactional
 public AuthenticatedSession register(Command c,String source){
  if(!c.agreementAccepted()||!version.equals(c.agreementVersion()))throw new IllegalArgumentException("请阅读并同意当前用户协议");
  if(c.clientType()!=AuthClientType.WEB&&c.clientType()!=AuthClientType.MINIAPP)throw new IllegalArgumentException("客户端无效");
  String username=text(c.username(),64),name=text(c.displayName(),100),device=text(c.deviceId(),128),deviceName=text(c.deviceName(),100);
  if(!username.matches("[A-Za-z0-9_][A-Za-z0-9_.-]{2,63}"))throw new IllegalArgumentException("账号须为3至64位字母、数字或下划线");
  if(!Objects.equals(c.password(),c.passwordConfirmation()))throw new IllegalArgumentException("两次密码不一致");
  String hash=auth.encodeManagedUserPassword(c.password());String destination=RegistrationVerificationService.normalize(c.channel(),c.destination());
  if(users.existsByUsername(username))throw new IllegalArgumentException("账号已存在，请使用已有账号登录");
  verification.consume(c.channel(),destination,c.code(),source);
  if(c.channel().equals("EMAIL")&&jdbc.queryForObject("select count(*) from auth_verified_email where email = ?",Long.class,destination)>0||c.channel().equals("SMS")&&users.existsByMobile(destination))throw new IllegalArgumentException("验证地址已绑定账号，请使用已有账号登录");
  Long id=ids.nextId();
  try {
   if(users.insert(User.create(id,username,name,c.channel().equals("SMS")?destination:null,UserType.PERSONAL))!=1||users.updatePasswordHash(id,hash)!=1)throw new IllegalStateException("账号保存失败");
   if(c.channel().equals("EMAIL"))jdbc.update("insert into auth_verified_email(user_id,email,verified_at) values(?,?,current_timestamp)",id,destination);
   if(c.channel().equals("SMS"))jdbc.update("insert into auth_verified_mobile(user_id,mobile,verified_at) values(?,?,current_timestamp)",id,destination);
   jdbc.update("insert into auth_registration_agreement_acceptance(user_id,version,client_type,accepted_at,source_digest) values(?,?,?,current_timestamp,?)",id,version,c.clientType().name(),RegistrationVerificationService.digest(source));
   return auth.createSession(id,c.clientType(),device,deviceName);
  }catch(DuplicateKeyException e){throw new IllegalArgumentException("账号或验证地址已存在，请使用已有账号登录");}
 }
 private static String text(String value,int max){if(value==null||value.isBlank()||value.trim().length()>max)throw new IllegalArgumentException("账号信息不完整或过长");return value.trim();}
}
