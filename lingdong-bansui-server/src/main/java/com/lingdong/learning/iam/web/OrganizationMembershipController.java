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
 @GetMapping("/roles/{roleId}/users") @RequirePermission("IAM_USER_ROLE_ASSIGN")
 public List<RoleUserAssignment> roleUsers(@AuthenticationPrincipal AuthenticatedUser current,@PathVariable Long roleId){return service.roleUsers(current.userId(),roleId);}
 @GetMapping("/organizations/{organizationId}/member-relations") @RequirePermission("IAM_USER_LIST")
 public List<OrganizationMembershipService.MemberRelation> memberRelations(@AuthenticationPrincipal AuthenticatedUser current,
  @PathVariable Long organizationId,@RequestParam List<Long> userIds){return service.memberRelations(current.userId(),organizationId,userIds);}
 @GetMapping("/organizations/{organizationId}/members") @RequirePermission("IAM_USER_LIST")
 public MemberPageResponse members(@AuthenticationPrincipal AuthenticatedUser current,@PathVariable Long organizationId,
  @RequestParam(required=false)String keyword,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int pageSize){
  var result=service.members(current.userId(),organizationId,keyword,page,pageSize);
  return new MemberPageResponse(result.items().stream().map(member->{var user=UserResponse.from(member.user());return new MemberResponse(user.id(),user.username(),user.displayName(),user.mobile(),user.type(),user.status(),user.createdAt(),user.updatedAt(),member.administrator());}).toList(),result.page(),result.pageSize(),result.total());
 }
 @PostMapping("/organizations/{organizationId}/members:batch") @RequirePermission("IAM_USER_ORGANIZATION_ASSIGN") @ResponseStatus(HttpStatus.NO_CONTENT)
 public void addMembers(@AuthenticationPrincipal AuthenticatedUser current,@PathVariable Long organizationId,@Valid @RequestBody AddMembersRequest request){service.addMembers(current.userId(),organizationId,request.userIds(),request.administrator());}
 public record AddMembersRequest(@NotEmpty List<@NotNull Long> userIds,@NotNull Boolean administrator) { }
 public record MemberPageResponse(List<MemberResponse> items,int page,int pageSize,long total) { }
 public record MemberResponse(@JsonSerialize(using=ToStringSerializer.class)Long id,String username,String displayName,String mobile,UserType type,UserStatus status,LocalDateTime createdAt,LocalDateTime updatedAt,boolean administrator) { }
}
