package com.lingdong.learning.student.web;

import com.lingdong.learning.student.application.transfer.TransferMaterialService;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.RequirePermission;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.*;
import jakarta.validation.constraints.Positive;

@RestController
@org.springframework.validation.annotation.Validated
@RequestMapping("/api/v1/student-transfer-applications")
public class TransferMaterialController {
 private final TransferMaterialService materials;
 public TransferMaterialController(TransferMaterialService materials){this.materials=materials;}
 public record Uploaded(String id,String originalName,String contentType,long sizeBytes){}
 @DeleteMapping("/materials/{fileId}") @ResponseStatus(HttpStatus.NO_CONTENT) @RequirePermission("STUDENT_TRANSFER_REQUEST_CREATE")
 public void remove(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable @Positive long fileId){materials.retire(actor,fileId);}
 @PostMapping(value="/materials",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
 @ResponseStatus(HttpStatus.CREATED) @RequirePermission("STUDENT_TRANSFER_REQUEST_CREATE")
 public Uploaded upload(@AuthenticationPrincipal AuthenticatedUser actor,@RequestParam @Positive long studentId,
  @RequestParam @Positive long sourceInstitutionId,@RequestParam @Positive long familyOrganizationId,
  @RequestParam MultipartFile file) throws java.io.IOException {
  if(file.getSize()>10*1024*1024)throw new IllegalArgumentException("每份材料不能超过10MB");
  var stored=materials.upload(actor,studentId,sourceInstitutionId,familyOrganizationId,file.getOriginalFilename(),file.getContentType(),file.getBytes());
  return new Uploaded(stored.id().toString(),stored.originalName(),stored.contentType(),stored.sizeBytes());
 }
 @PostMapping(value="/institution-materials",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
 @ResponseStatus(HttpStatus.CREATED) @RequirePermission("STUDENT_TRANSFER_REQUEST_CREATE")
 public Uploaded uploadInstitution(@AuthenticationPrincipal AuthenticatedUser actor,@RequestParam @Positive long studentId,
  @RequestParam @Positive long sourceInstitutionId,@RequestParam MultipartFile file) throws java.io.IOException {
  if(file.getSize()>10*1024*1024)throw new IllegalArgumentException("每份材料不能超过10MB");
  var stored=materials.uploadInstitution(actor,studentId,sourceInstitutionId,file.getOriginalFilename(),file.getContentType(),file.getBytes());
  return new Uploaded(stored.id().toString(),stored.originalName(),stored.contentType(),stored.sizeBytes());
 }
 @GetMapping("/{applicationId}/materials/{fileId}") @RequirePermission("STUDENT_TRANSFER_VIEW")
 public ResponseEntity<byte[]> download(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable @Positive long applicationId,@PathVariable @Positive long fileId){
  var file=materials.read(actor,applicationId,fileId);
  return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.contentType()))
   .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(file.originalName(),java.nio.charset.StandardCharsets.UTF_8).build().toString())
   .header("X-Content-Type-Options","nosniff").cacheControl(CacheControl.noStore()).contentLength(file.content().length).body(file.content());
 }
}
