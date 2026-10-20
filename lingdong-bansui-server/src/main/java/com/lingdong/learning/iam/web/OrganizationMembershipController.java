package com.lingdong.learning.iam.web;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.RequirePermission;
import com.lingdong.learning.iam.application.OrganizationMembershipService;
import com.lingdong.learning.user.application.RoleUserAssignment;
import com.lingdong.learning.user.domain.UserStatus;
import com.lingdong.learning.user.domain.UserType;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;

@RestController @RequestMapping("/api/v1")
public class OrganizationMembershipController {
 private final OrganizationMembershipService service;
 public OrganizationMembershipController(OrganizationMembershipService service){this.service=service;}
 @GetMapping("/organizations/{organizationId}/member-picker")
 public PickerPageResponse memberPicker(@AuthenticationPrincipal AuthenticatedUser current,@PathVariable Long organizationId,
  @RequestParam(defaultValue="CANDIDATES")String side,@RequestParam(defaultValue="false")boolean administrator,
  @RequestParam(required=false)Long filterOrganizationId,@RequestParam(defaultValue="SCHOOL")String group,
  @RequestParam(required=false)String keyword,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int pageSize) {
  requireMemberClient(current);
  return pickerResponse(service.picker(current.userId(),organizationId,null,null,administrator,side,filterOrganizationId,group,keyword,page,pageSize));
 }
 @GetMapping("/roles/{roleId}/user-picker") @RequirePermission("IAM_USER_ROLE_ASSIGN")
 public PickerPageResponse rolePicker(@AuthenticationPrincipal AuthenticatedUser current,@PathVariable Long roleId,
  @RequestParam(defaultValue="CANDIDATES")String side,@RequestParam(required=false)Long grantOrganizationId,
  @RequestParam(required=false)Long filterOrganizationId,@RequestParam(required=false)String keyword,
  @RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int pageSize) {
  return pickerResponse(service.picker(current.userId(),null,roleId,grantOrganizationId,false,side,filterOrganizationId,null,keyword,page,pageSize));
 }
 private PickerPageResponse pickerResponse(OrganizationMembershipService.PickerPage result) {
  return new PickerPageResponse(result.items().stream().map(m->{var u=UserResponse.from(m.user());return new PickerResponse(u.id(),u.username(),u.displayName(),u.mobile(),u.type(),u.status(),u.createdAt(),u.updatedAt(),m.administrator(),m.organizationNames(),m.assignments());}).toList(),result.page(),result.pageSize(),result.total(),result.allowedUnbound());
 }
 public record PickerPageResponse(List<PickerResponse> items,int page,int pageSize,long total,boolean allowedUnbound) { }
 public record PickerResponse(@JsonSerialize(using=ToStringSerializer.class)Long id,String username,String displayName,String mobile,UserType type,UserStatus status,LocalDateTime createdAt,LocalDateTime updatedAt,boolean administrator,List<String> organizationNames,List<RoleUserAssignment> assignments) { }
 @GetMapping("/roles/{roleId}/users") @RequirePermission("IAM_USER_ROLE_ASSIGN")
 public List<RoleUserAssignment> roleUsers(@AuthenticationPrincipal AuthenticatedUser current,@PathVariable Long roleId){return service.roleUsers(current.userId(),roleId);}
 @GetMapping("/organizations/{organizationId}/member-relations")
 public List<OrganizationMembershipService.MemberRelation> memberRelations(@AuthenticationPrincipal AuthenticatedUser current,
  @PathVariable Long organizationId,@RequestParam List<Long> userIds,@RequestParam(defaultValue="false")boolean administrator){requireMemberClient(current);return service.memberRelations(current.userId(),organizationId,userIds,administrator);}
 @GetMapping("/organizations/{organizationId}/administrators") @RequirePermission("ORG_MEMBER_READ")
 public List<AdminResponse> administrators(@AuthenticationPrincipal AuthenticatedUser current,@PathVariable Long organizationId){
  requireMemberClient(current);
  return service.administrators(current.userId(),organizationId).stream().map(entry->{var row=entry.user();return new AdminResponse(String.valueOf(row.userId()),row.username(),row.displayName(),row.mobile(),row.status(),row.owner(),row.administrator(),entry.organizationNames());}).toList();
 }
 public record AdminResponse(String id,String username,String displayName,String mobile,String status,boolean owner,boolean administrator,List<String> organizationNames) { }
 @GetMapping("/organizations/{organizationId}/members") @RequirePermission("ORG_MEMBER_READ")
 public MemberPageResponse members(@AuthenticationPrincipal AuthenticatedUser current,@PathVariable Long organizationId,
  @RequestParam(required=false)String keyword,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int pageSize){
  var result=service.members(current.userId(),organizationId,keyword,page,pageSize);
  return new MemberPageResponse(result.items().stream().map(member->{var user=UserResponse.from(member.user());return new MemberResponse(user.id(),user.username(),user.displayName(),user.mobile(),user.type(),user.status(),user.createdAt(),user.updatedAt(),member.administrator(),member.owner());}).toList(),result.page(),result.pageSize(),result.total());
 }
 @PostMapping("/organizations/{organizationId}/members:batch") @ResponseStatus(HttpStatus.NO_CONTENT)
 public void addMembers(@AuthenticationPrincipal AuthenticatedUser current,@PathVariable Long organizationId,@Valid @RequestBody AddMembersRequest request){requireWeb(current);service.addMembers(current.userId(),organizationId,request.userIds(),request.administrator());}
 public record AddMembersRequest(@NotEmpty List<@NotNull Long> userIds,@NotNull Boolean administrator) { }
 @PostMapping("/organizations/{organizationId}/members:revoke") @ResponseStatus(HttpStatus.NO_CONTENT)
 public void revokeMembers(@AuthenticationPrincipal AuthenticatedUser current,@PathVariable Long organizationId,@Valid @RequestBody RevokeMembersRequest request){requireWeb(current);service.revokeMembers(current.userId(),organizationId,request.userIds(),request.administrator());}
 public record RevokeMembersRequest(@NotEmpty List<@NotNull Long> userIds,@NotNull Boolean administrator) { }
 /** Canonical fixed actions; legacy parameter endpoints above enforce the same action in service. */
 @PostMapping("/organizations/{organizationId}/members:associate") @RequirePermission("ORG_MEMBER_ASSOCIATE") @ResponseStatus(HttpStatus.NO_CONTENT)
 public void associateMembers(@AuthenticationPrincipal AuthenticatedUser current,@PathVariable Long organizationId,@Valid @RequestBody MemberIdsRequest request){service.addMembers(current.userId(),organizationId,request.userIds(),false);}
 @PostMapping("/organizations/{organizationId}/members:remove") @RequirePermission("ORG_MEMBER_REMOVE") @ResponseStatus(HttpStatus.NO_CONTENT)
 public void removeMembers(@AuthenticationPrincipal AuthenticatedUser current,@PathVariable Long organizationId,@Valid @RequestBody MemberIdsRequest request){service.revokeMembers(current.userId(),organizationId,request.userIds(),false);}
 @PostMapping("/organizations/{organizationId}/administrators:assign") @RequirePermission("ORG_ADMIN_ASSIGN") @ResponseStatus(HttpStatus.NO_CONTENT)
 public void assignAdministrators(@AuthenticationPrincipal AuthenticatedUser current,@PathVariable Long organizationId,@Valid @RequestBody MemberIdsRequest request){service.addMembers(current.userId(),organizationId,request.userIds(),true);}
 @PostMapping("/organizations/{organizationId}/administrators:revoke") @RequirePermission("ORG_ADMIN_REVOKE") @ResponseStatus(HttpStatus.NO_CONTENT)
 public void revokeAdministrators(@AuthenticationPrincipal AuthenticatedUser current,@PathVariable Long organizationId,@Valid @RequestBody MemberIdsRequest request){service.revokeMembers(current.userId(),organizationId,request.userIds(),true);}
 public record MemberIdsRequest(@NotEmpty List<@NotNull Long> userIds) { }
 private void requireMemberClient(AuthenticatedUser current){
  if(current==null || (current.clientType()!=com.lingdong.learning.auth.domain.AuthClientType.WEB
      && current.clientType()!=com.lingdong.learning.auth.domain.AuthClientType.MINIAPP))
   throw new com.lingdong.learning.common.security.SystemOperationAccessDeniedException("组织成员管理需要有效会话");
 }
 private void requireWeb(AuthenticatedUser current){
  if(current==null||current.clientType()!=com.lingdong.learning.auth.domain.AuthClientType.WEB)
   throw new com.lingdong.learning.common.security.SystemOperationAccessDeniedException("组织成员管理需要Web会话");
 }
 public record MemberPageResponse(List<MemberResponse> items,int page,int pageSize,long total) { }
 public record MemberResponse(@JsonSerialize(using=ToStringSerializer.class)Long id,String username,String displayName,String mobile,UserType type,UserStatus status,LocalDateTime createdAt,LocalDateTime updatedAt,boolean administrator,boolean owner) { }
}

