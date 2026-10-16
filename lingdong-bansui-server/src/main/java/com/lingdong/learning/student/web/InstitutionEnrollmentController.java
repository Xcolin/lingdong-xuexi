package com.lingdong.learning.student.web;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.student.application.InstitutionEnrollmentService;
import com.lingdong.learning.student.infrastructure.persistence.InstitutionEnrollmentMapper;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController
@RequestMapping("/api/v1/institution-enrollment")
public class InstitutionEnrollmentController {
    private final InstitutionEnrollmentService service;
    public InstitutionEnrollmentController(InstitutionEnrollmentService service){this.service=service;}
    public record InvitationRequest(Long classOrganizationId,Integer validDays){}
    public record ResolveRequest(String code){@Override public String toString(){return "ResolveRequest[code=REDACTED]";}}
    public record EnrollmentRequest(Long studentUserId,String code,Long familyOrganizationId){@Override public String toString(){return "EnrollmentRequest[code=REDACTED]";}}
    public record ReviewRequest(String reason){}
    @PostMapping("/invitations") public InstitutionEnrollmentService.CreatedInvitation create(@AuthenticationPrincipal AuthenticatedUser actor,@RequestBody InvitationRequest request){return service.createInvitation(actor,request.classOrganizationId(),request.validDays());}
    @PostMapping("/invitations/resolve") public InstitutionEnrollmentService.ResolvedInvitation resolve(@AuthenticationPrincipal AuthenticatedUser actor,@RequestBody ResolveRequest request){return service.resolve(actor,request.code());}
    @PostMapping("/requests") public InstitutionEnrollmentMapper.Request request(@AuthenticationPrincipal AuthenticatedUser actor,@RequestBody EnrollmentRequest request){return service.request(actor,request.studentUserId(),request.code(),request.familyOrganizationId());}
    @GetMapping("/requests") public List<InstitutionEnrollmentMapper.Request> list(@AuthenticationPrincipal AuthenticatedUser actor,@RequestParam(required=false) Long institutionOrganizationId,@RequestParam(required=false) Long classOrganizationId,@RequestParam(required=false) Long familyOrganizationId,@RequestParam(required=false) String status){return service.list(actor,institutionOrganizationId,classOrganizationId,familyOrganizationId,status);}
    @PostMapping("/requests/{id}/approve") public InstitutionEnrollmentMapper.Request approve(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable Long id){return service.review(actor,id,true,null);}
    @PostMapping("/requests/{id}/reject") public InstitutionEnrollmentMapper.Request reject(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable Long id,@RequestBody ReviewRequest request){return service.review(actor,id,false,request.reason());}
}
