package com.lingdong.learning.student.web;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.RequirePermission;
import com.lingdong.learning.student.application.AcademicMetadataService;
import com.lingdong.learning.student.infrastructure.persistence.AcademicMetadataStore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/classes/{classId}/academic-metadata")
public class AcademicClassMetadataController {
 private final AcademicMetadataService service;
 public AcademicClassMetadataController(AcademicMetadataService service){this.service=service;}
 public record Update(@Positive Long headTeacherUserId,@NotNull @Min(0) Integer versionNo){}
 public record Response(String classId,String headTeacherUserId,int versionNo,String headTeacherName){
  public Response(String classId,String headTeacherUserId,int versionNo){this(classId,headTeacherUserId,versionNo,null);}
  public static Response from(AcademicMetadataService.ClassView view){var value=view.metadata();return new Response(Long.toString(value.classId()),value.headTeacherUserId()==null?null:value.headTeacherUserId().toString(),value.versionNo(),view.headTeacherName());}
  public static Response from(AcademicMetadataStore.ClassMetadata value){return new Response(Long.toString(value.classId()),value.headTeacherUserId()==null?null:value.headTeacherUserId().toString(),value.versionNo());}
 }
 public record TeacherOption(String userId,String displayName,java.util.List<String> classOrganizationIds){}
 @GetMapping("/head-teacher-options") @RequirePermission("CLASS_UPDATE")
 public java.util.List<TeacherOption> headTeacherOptions(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable long classId){return service.headTeacherOptions(actor,classId).stream().map(value->new TeacherOption(Long.toString(value.userId()),value.displayName(),java.util.List.of(Long.toString(value.classId())))).toList();}
 @GetMapping @RequirePermission("CLASS_READ")
 public Response read(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable long classId){return Response.from(service.classView(actor,classId));}
 @PutMapping @RequirePermission("CLASS_UPDATE")
 public Response update(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable long classId,@Valid @RequestBody Update request){return Response.from(service.setHeadTeacher(actor,classId,request.headTeacherUserId(),request.versionNo()));}
}
