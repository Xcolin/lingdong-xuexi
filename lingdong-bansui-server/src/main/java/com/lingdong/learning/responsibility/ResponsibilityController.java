package com.lingdong.learning.responsibility;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.RequirePermission;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** Permissions and both read alternatives are checked together with live data scope in the service. */
@RestController
@RequestMapping("/api/v1/students/{studentId}")
public class ResponsibilityController {
    private final ResponsibilityService service;
    public ResponsibilityController(ResponsibilityService service){this.service=service;}
    @GetMapping("/institution-responsibility")
    @RequirePermission(anyOf={"STUDENT_RESPONSIBILITY_READ","PARENT_RELATIONSHIP_READ"})
    public ResponsibilityService.Current current(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long studentId,@RequestParam long institutionOrganizationId){return service.current(user,studentId,institutionOrganizationId);}
    @GetMapping("/institution-responsibility-transfer-options")
    @RequirePermission("STUDENT_RESPONSIBILITY_TRANSFER")
    public List<ResponsibilityStore.Person> options(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long studentId,@RequestParam long institutionOrganizationId){return service.options(user,studentId,institutionOrganizationId);}
    @PostMapping("/institution-responsibility-transfer")
    @RequirePermission("STUDENT_RESPONSIBILITY_TRANSFER")
    public ResponsibilityService.Current transfer(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long studentId,@Valid @RequestBody ResponsibilityService.TransferRequest request){return service.transfer(user,studentId,request);}
    @GetMapping("/institution-responsibility-transfers")
    @RequirePermission(anyOf={"STUDENT_RESPONSIBILITY_READ","PARENT_RELATIONSHIP_READ"})
    public ResponsibilityService.HistoryPage history(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long studentId,@RequestParam long institutionOrganizationId,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize){return service.history(user,studentId,institutionOrganizationId,page,pageSize);}
}
