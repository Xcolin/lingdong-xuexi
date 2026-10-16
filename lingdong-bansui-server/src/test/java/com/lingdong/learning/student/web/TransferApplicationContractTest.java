package com.lingdong.learning.student.web;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lingdong.learning.student.application.transfer.*;
import com.lingdong.learning.common.security.RequirePermission;
import jakarta.validation.Validation;
import java.time.LocalDate;import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TransferApplicationContractTest {
 @Test void allNumericIdentifiersAreJsonStrings() throws Exception {
  long id=2110374231123427328L;var date=LocalDate.of(2026,10,14);
  var domain=TransferApplication.submit(id,id+1,id+2,"destination","reason",date,List.of(id+3),date);
  var response=TransferApplicationController.Response.from(new TransferApplicationRepository.Stored(id+4,id+5,1,domain));
  var tree=new ObjectMapper().registerModule(new JavaTimeModule()).valueToTree(response);
  for(String key:List.of("id","studentId","sourceInstitutionId","familyOrganizationId","applicantUserId"))assertTrue(tree.get(key).isTextual(),key);
  assertEquals(Long.toString(id+3),tree.get("attachmentIds").get(0).textValue());
 }
 @Test void rejectsEmptyInputInvalidVersionsAndExcessMaterials(){
  try(var factory=Validation.buildDefaultValidatorFactory()){var validator=factory.getValidator();
   assertFalse(validator.validate(new TransferApplicationController.Submit(null,null,null,"","",null,null)).isEmpty());
   assertFalse(validator.validate(new TransferApplicationController.Version(0)).isEmpty());
   assertFalse(validator.validate(new TransferApplicationController.InstitutionSubmit(null,0L,"","",null,null)).isEmpty());
   assertFalse(validator.validate(new TransferApplicationController.InstitutionSubmit(1L,2L,"target","reason",LocalDate.now(),List.of(1L,2L,3L,4L))).isEmpty());
   assertFalse(validator.validate(new TransferApplicationController.Submit(1L,2L,3L,"target","reason",LocalDate.now(),List.of(1L,2L,3L,4L))).isEmpty());
  }
 }
 @Test void everyHttpActionDeclaresSpecificPermission(){
  var methods=java.util.Arrays.stream(TransferApplicationController.class.getDeclaredMethods()).filter(m->List.of("list","submit","read","approve","reject","rejectBlocked","withdraw").contains(m.getName())).toList();
  assertEquals(7,methods.size());for(var method:methods){var permission=method.getAnnotation(RequirePermission.class);assertNotNull(permission);assertTrue(permission.value().startsWith("STUDENT_TRANSFER_"));}
 }
 @Test void institutionSubmissionHasNoClientSuppliedFamilyAndDeclaresCreatePermission() throws Exception {
  var dto=Class.forName("com.lingdong.learning.student.web.TransferApplicationController$InstitutionSubmit");
  assertFalse(java.util.Arrays.stream(dto.getRecordComponents()).anyMatch(c->c.getName().equals("familyOrganizationId")));
  var method=TransferApplicationController.class.getDeclaredMethod("submitInstitution",com.lingdong.learning.auth.application.AuthenticatedUser.class,dto);
  assertEquals("STUDENT_TRANSFER_REQUEST_CREATE",method.getAnnotation(RequirePermission.class).value());
  assertArrayEquals(new String[]{"/institution-submissions"},method.getAnnotation(org.springframework.web.bind.annotation.PostMapping.class).value());
 }
}
