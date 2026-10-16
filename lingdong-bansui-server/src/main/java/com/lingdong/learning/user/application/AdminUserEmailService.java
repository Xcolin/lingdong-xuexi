package com.lingdong.learning.user.application;

import com.lingdong.learning.auth.application.AuthenticationApplicationService;
import com.lingdong.learning.iam.application.MemberScopeService;
import com.lingdong.learning.iam.audit.application.*;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.user.infrastructure.persistence.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Trusted IAM maintenance; this does not represent self-service address proof. */
@Service
public class AdminUserEmailService {
 private final JdbcTemplate jdbc; private final MemberScopeService scopes; private final PermissionDecisionService decisions;
 private final UserMapper users; private final UserOrganizationMapper memberships; private final AuthenticationApplicationService auth; private final IamChangeAuditService audit;
 public AdminUserEmailService(JdbcTemplate jdbc,MemberScopeService scopes,PermissionDecisionService decisions,UserMapper users,UserOrganizationMapper memberships,AuthenticationApplicationService auth,IamChangeAuditService audit){this.jdbc=jdbc;this.scopes=scopes;this.decisions=decisions;this.users=users;this.memberships=memberships;this.auth=auth;this.audit=audit;}
 public record Email(String email,String source){}
 public Map<Long,Email> findForUsers(List<Long> ids){
  if(ids.isEmpty())return Map.of();
  Map<Long,Email> result=new HashMap<>();
  jdbc.query("select user_id,email,maintenance_source from auth_verified_email where user_id in ("+String.join(",",Collections.nCopies(ids.size(),"?"))+")",rs->{result.put(rs.getLong("user_id"),new Email(rs.getString("email"),rs.getString("maintenance_source")));},ids.toArray());
  return result;
 }
 @Transactional public void maintain(Long actor,Long id,com.fasterxml.jackson.databind.JsonNode submitted){
  if(submitted==null)return;
  if(!decisions.isAllowed(actor,PermissionClient.WEB,"IAM_USER_UPDATE"))throw new com.lingdong.learning.common.security.SystemOperationAccessDeniedException("缺少用户编辑权限");
  scopes.requireVisibleUser(actor,id);
  var user=users.findByIdForUpdate(id);
  if(user==null)throw new com.lingdong.learning.common.web.ResourceNotFoundException("用户不存在");
  if(user.status()==com.lingdong.learning.user.domain.UserStatus.CANCELLED)throw new IllegalArgumentException("已注销用户不能编辑");
  for(Long organization:memberships.findOrganizationIds(id))scopes.requireTarget(actor,organization);
  if(!submitted.isNull()&&!submitted.isTextual())throw new IllegalArgumentException("邮箱格式无效");
  String email=submitted.isNull()?null:submitted.asText().trim().toLowerCase(Locale.ROOT);
  if(email!=null&&email.isEmpty())email=null;
  if(email!=null&&(email.length()>254||!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")))throw new IllegalArgumentException("邮箱格式无效");
  var old=findForUsers(List.of(id)).get(id);
  if(Objects.equals(email,old==null?null:old.email()))return;
  try {
   if(email==null)jdbc.update("delete from auth_verified_email where user_id=?",id);
   else if(old==null)jdbc.update("insert into auth_verified_email(user_id,email,verified_at,maintenance_source) values(?,?,current_timestamp,'ADMIN_MAINTAINED')",id,email);
   else jdbc.update("update auth_verified_email set email=?,verified_at=current_timestamp,maintenance_source='ADMIN_MAINTAINED' where user_id=?",email,id);
  }catch(org.springframework.dao.DuplicateKeyException e){throw new IllegalArgumentException("邮箱已绑定其他账号");}
  auth.revokeAllActiveSessionsForUser(id);
  audit.record(IamChangeAuditEventType.USER_PROFILE_CHANGE,actor,IamChangeTargetType.USER,id,null,null,"email:"+(old==null?"":old.email()),"email:"+(email==null?"":email));
 }
 /** Called inside the transaction creating the account, never as an existing-account edit. */
 @Transactional public void initializeCreatedUser(Long actor,Long id,Long organizationId,String submitted){
  String email=submitted==null?null:submitted.trim().toLowerCase(Locale.ROOT);
  if(email==null||email.isEmpty())return;
  if(email.length()>254||!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))throw new IllegalArgumentException("邮箱格式无效");
  if(!decisions.isAllowed(actor,PermissionClient.WEB,"IAM_USER_CREATE"))throw new com.lingdong.learning.common.security.SystemOperationAccessDeniedException("缺少用户创建权限");
  scopes.requireTarget(actor,organizationId);
  if(!memberships.findOrganizationIds(id).contains(organizationId))throw new IllegalArgumentException("新账号所属组织不一致");
  try {jdbc.update("insert into auth_verified_email(user_id,email,verified_at,maintenance_source) values(?,?,current_timestamp,'ADMIN_MAINTAINED')",id,email);}
  catch(org.springframework.dao.DuplicateKeyException e){throw new IllegalArgumentException("邮箱已绑定其他账号");}
  audit.record(IamChangeAuditEventType.USER_PROFILE_CHANGE,actor,IamChangeTargetType.USER,id,null,organizationId,null,"email:"+email);
 }

}
