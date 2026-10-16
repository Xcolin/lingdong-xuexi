package com.lingdong.learning.student.web;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.RequirePermission;
import com.lingdong.learning.student.application.StudentNumberService;
import com.lingdong.learning.student.infrastructure.persistence.AcademicMetadataStore;
import jakarta.validation.Valid;import jakarta.validation.constraints.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/students/{studentId}/institutions/{institutionId}/student-number")
public class StudentNumberController {
 private final StudentNumberService service;
 public StudentNumberController(StudentNumberService service){this.service=service;}
 public record Update(@Size(max=64) String studentNumber,@NotNull @Min(0) Integer versionNo){}
 public record Response(String studentId,String institutionId,String studentNumber,int versionNo){public static Response from(AcademicMetadataStore.EnrollmentMetadata value){return new Response(Long.toString(value.studentId()),Long.toString(value.institutionId()),value.studentNumber(),value.versionNo());}}
 @GetMapping @RequirePermission("STUDENT_ORGANIZATION_MANAGE")
 public Response read(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable long studentId,@PathVariable long institutionId){return Response.from(service.read(actor,studentId,institutionId));}
 @PutMapping @RequirePermission("STUDENT_ORGANIZATION_MANAGE")
 public Response update(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable long studentId,@PathVariable long institutionId,@Valid @RequestBody Update request){return Response.from(service.update(actor,studentId,institutionId,request.studentNumber(),request.versionNo()));}
}
