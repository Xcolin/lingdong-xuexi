package com.lingdong.learning.organization.web;
import com.lingdong.learning.organization.application.*;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.RequirePermission;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
@RestController @RequestMapping("/api/v1/classes/course-categories")
public class CourseCategoryController {
 private final CourseCategoryService service;private final CourseCategoryDictionary dictionaries;
 public CourseCategoryController(CourseCategoryService service,CourseCategoryDictionary dictionaries){this.service=service;this.dictionaries=dictionaries;}
 public record Request(@NotNull @Positive Long schoolOrganizationId,@Positive Long campusOrganizationId,@NotBlank @Size(max=50) String name,@Min(0) Integer sortOrder,@Positive Integer versionNo,@NotNull CourseCategoryService.Metadata metadata){}
 @GetMapping("/options") @RequirePermission(anyOf={"CLASS_READ","CLASS_CREATE","CLASS_UPDATE"}) public CourseCategoryDictionary.Options options(){return dictionaries.options();}
 @PostMapping @RequirePermission("CLASS_CREATE") @ResponseStatus(org.springframework.http.HttpStatus.CREATED) public ClassOrganizationResponse create(@AuthenticationPrincipal AuthenticatedUser user,@Valid @RequestBody Request r){return ClassOrganizationResponse.from(service.create(user,r.schoolOrganizationId(),r.campusOrganizationId(),r.name(),r.sortOrder(),r.metadata()));}
 @PutMapping("/{id}") @RequirePermission("CLASS_UPDATE") public ClassOrganizationResponse update(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable Long id,@Valid @RequestBody Request r){return ClassOrganizationResponse.from(service.update(user,id,r.name(),r.sortOrder(),r.versionNo(),r.metadata()));}
 @GetMapping("/{id}") @RequirePermission("CLASS_READ") public CourseCategoryService.Metadata read(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable Long id){return service.read(user,id);}
}
