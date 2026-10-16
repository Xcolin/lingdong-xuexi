package com.lingdong.learning.student.application.transfer;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;import static org.junit.jupiter.api.Assertions.*;
import com.lingdong.learning.student.infrastructure.persistence.*;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.student.domain.Student;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
class TransferApplicationOptionsTest {
 @Test void onlyCurrentEnrollmentAndAuthorizedFamilyCanProduceOptions(){
  var access=mock(TransferApplicationAccess.class);var students=mock(StudentMapper.class);var enrollments=mock(StudentOrganizationMapper.class);var organizations=mock(OrganizationMapper.class);
  var service=new TransferApplicationOptionsService(access,students,enrollments,organizations,mock(FeatureAccessService.class));
  var actor=new AuthenticatedUser(31L,1L,"fixture","fixture",AuthClientType.MINIAPP,java.util.List.of());
  doThrow(new IllegalArgumentException("denied")).when(access).requireFamilyContext(actor,11,22,"STUDENT_TRANSFER_REQUEST_CREATE");
  assertThrows(IllegalArgumentException.class,()->service.familyOptions(actor,11,22));verifyNoInteractions(students,enrollments,organizations);
  doNothing().when(access).requireFamilyContext(actor,11,22,"STUDENT_TRANSFER_REQUEST_CREATE");when(students.findById(11L)).thenReturn(Student.create(11L,"fixture child","G1",41L));
  when(enrollments.findActiveEnrollmentOrganizationIds(11L)).thenReturn(java.util.List.of(21L,23L));
  when(organizations.findById(21L)).thenReturn(Organization.create(21L,null,"ROOT","fixture_school","fixture school","SCHOOL","/21/",1,null));
  when(organizations.findById(23L)).thenReturn(Organization.create(23L,null,"ROOT","fixture_wrong","not institution","FAMILY","/23/",0,null));
  when(enrollments.findActiveClassOrganizationIdsInEnrollment(11L,21L)).thenReturn(java.util.List.of(24L));
  when(organizations.findById(24L)).thenReturn(Organization.create(24L,21L,"21","fixture_class","fixture class","CLASS","/21/24/",1,null));
  var result=service.familyOptions(actor,11,22);assertEquals("11",result.studentId());assertEquals("fixture child",result.studentName());assertEquals(1,result.institutions().size());assertEquals("21",result.institutions().get(0).id());assertEquals(java.util.List.of("fixture class"),result.institutions().get(0).classNames());
 }
 @Test void institutionOptionsExposeOnlyTheAuthorizedSource(){
  var access=mock(TransferApplicationAccess.class);var students=mock(StudentMapper.class);var enrollments=mock(StudentOrganizationMapper.class);var organizations=mock(OrganizationMapper.class);var service=new TransferApplicationOptionsService(access,students,enrollments,organizations,mock(FeatureAccessService.class));var actor=new AuthenticatedUser(33L,1L,"fixture","fixture",AuthClientType.MINIAPP,java.util.List.of());
  when(access.requireInstitutionOptions(actor,11,21)).thenThrow(new IllegalArgumentException("denied"));assertThrows(IllegalArgumentException.class,()->service.institutionOptions(actor,11,21));verifyNoInteractions(students,enrollments,organizations);
  doReturn(31L).when(access).requireInstitutionOptions(actor,11,21);when(students.findById(11L)).thenReturn(Student.create(11L,"fixture child","G1",41L));when(organizations.findById(21L)).thenReturn(Organization.create(21L,null,"ROOT","fixture_school","fixture school","SCHOOL","/21/",1,null));when(enrollments.findActiveClassOrganizationIdsInEnrollment(11L,21L)).thenReturn(java.util.List.of());
  var result=service.institutionOptions(actor,11,21);assertEquals(1,result.institutions().size());assertEquals("21",result.institutions().get(0).id());verify(enrollments,never()).findActiveEnrollmentOrganizationIds(anyLong());
 }
}
