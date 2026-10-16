package com.lingdong.learning.student.web;
import org.junit.jupiter.api.Test;
import com.lingdong.learning.student.application.transfer.*;
import com.lingdong.learning.student.infrastructure.persistence.StudentMapper;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.student.domain.Student;
import com.lingdong.learning.organization.domain.Organization;
import static org.mockito.Mockito.*;import static org.junit.jupiter.api.Assertions.*;
class TransferApplicationPresentationTest {
 @Test void usesActualNamesAndStoredReviewMetadata(){
  var students=mock(StudentMapper.class);var organizations=mock(OrganizationMapper.class);
  when(students.findById(11L)).thenReturn(Student.create(11L,"fixture child","G1",41L));
  when(organizations.findById(21L)).thenReturn(Organization.create(21L,null,"ROOT","fixture","fixture school","SCHOOL","/21/",0,null));
  var date=java.time.LocalDate.now();var now=java.time.LocalDateTime.now();
  long fileId=2110374231123427328L;
  var stored=new TransferApplicationRepository.Stored(1,22,2,TransferApplication.submit(11,21,31,"destination","reason",date,java.util.List.of(fileId),date),false,null,"fixture review reason",now,now);
  var relations=mock(com.lingdong.learning.attachment.infrastructure.persistence.FileRelationMapper.class);
  when(relations.findActiveFilesByBusiness("STUDENT_TRANSFER",1L,"MATERIAL")).thenReturn(java.util.List.of(
   new com.lingdong.learning.attachment.domain.ManagedFileRecord(fileId,"hidden-key","fixture proof.pdf","pdf","application/pdf",10L,31L,"STUDENT_TRANSFER","MATERIAL",null,com.lingdong.learning.attachment.domain.FileStatus.AVAILABLE,now,now,now),
   new com.lingdong.learning.attachment.domain.ManagedFileRecord(fileId+1,"hidden-foreign-key","foreign proof.pdf","pdf","application/pdf",10L,31L,"STUDENT_TRANSFER","MATERIAL",null,com.lingdong.learning.attachment.domain.FileStatus.AVAILABLE,now,now,now)));
  var result=new TransferApplicationPresentation(students,organizations,relations).display(stored);
  assertEquals("fixture child",result.studentName());assertEquals("fixture school",result.institutionName());assertEquals("fixture review reason",result.reviewReason());assertEquals(now,result.reviewedAt());
  assertEquals(1,result.proofs().size());assertEquals(Long.toString(fileId),result.proofs().get(0).id());assertEquals("fixture proof.pdf",result.proofs().get(0).originalName());
 }
}
