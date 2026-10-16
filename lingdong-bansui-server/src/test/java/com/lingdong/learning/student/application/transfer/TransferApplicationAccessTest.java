package com.lingdong.learning.student.application.transfer;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.student.infrastructure.persistence.*;
import com.lingdong.learning.student.domain.Student;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.organization.domain.*;
import com.lingdong.learning.datascope.application.OrganizationDataScopeService;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import java.util.List;
class TransferApplicationAccessTest {
 @Test void executionLocksGuardianAndRechecksOriginalClientAndReviewerAuthority(){
  var p=mock(PermissionDecisionService.class);var g=mock(ParentStudentMapper.class);var f=mock(InstitutionEnrollmentMapper.class);var s=mock(StudentMapper.class);var e=mock(StudentOrganizationMapper.class);var o=mock(OrganizationMapper.class);var d=mock(OrganizationDataScopeService.class);var access=new TransferApplicationAccess(p,g,f,s,e,o,d);
  var date=java.time.LocalDate.now();var stored=new TransferApplicationRepository.Stored(1,22,2,TransferApplication.submit(11,21,31,"target","reason",date,List.of(),date).review(32,21,true));var identity=new TransferApplicationRepository.ExecutionIdentity(31,"MINIAPP",32L,"WEB");
  when(s.findById(11L)).thenReturn(Student.create(11L,"fixture","G1",41L));
  var source=mock(Organization.class);when(source.typeCode()).thenReturn("SCHOOL");when(source.status()).thenReturn(OrganizationStatus.ENABLED);when(source.effectiveStatus()).thenReturn(OrganizationEffectiveStatus.ENABLED);when(o.findById(21L)).thenReturn(source);
  var family=mock(Organization.class);when(family.typeCode()).thenReturn("FAMILY");when(family.status()).thenReturn(OrganizationStatus.ENABLED);when(family.effectiveStatus()).thenReturn(OrganizationEffectiveStatus.ENABLED);when(o.findById(22L)).thenReturn(family);
  when(e.findActiveEnrollmentOrganizationIds(11L)).thenReturn(List.of(21L));when(f.familyMember(31L,22L)).thenReturn(true);when(f.familyMember(41L,22L)).thenReturn(true);when(g.existsActivePrimaryByParentAndStudent(31L,11L)).thenReturn(true);
  var guardian=new com.lingdong.learning.student.domain.ParentRelationship(1L,31L,11L,com.lingdong.learning.student.domain.ParentRelationshipRole.PRIMARY_GUARDIAN,"ACTIVE","PRIMARY",null,null);when(g.findActivePrimaryByStudentIdForUpdate(11L)).thenReturn(guardian);
  when(p.isAllowed(31L,PermissionClient.MINIAPP,"STUDENT_TRANSFER_REQUEST_CREATE")).thenReturn(true);when(p.isAllowed(32L,PermissionClient.WEB,"STUDENT_TRANSFER_REVIEW")).thenReturn(true);when(d.canAccess(32L,21L)).thenReturn(true);
  assertNull(access.executionIssue(stored,identity));verify(g).findActivePrimaryByStudentIdForUpdate(11L);
  var delegated=new TransferApplicationRepository.Stored(2,22,2,TransferApplication.submit(11,21,33,"target","reason",date,List.of(),date).review(32,21,true));var delegationIdentity=new TransferApplicationRepository.ExecutionIdentity(33,"WEB",32L,"WEB","INSTITUTION",31L);
  when(p.isAllowed(33L,PermissionClient.WEB,"STUDENT_TRANSFER_REQUEST_CREATE")).thenReturn(true);when(p.isAllowed(33L,PermissionClient.WEB,"STUDENT_ORGANIZATION_MANAGE")).thenReturn(true);when(d.canAccess(33L,21L)).thenReturn(true);
  assertNull(access.executionIssue(delegated,delegationIdentity));
  when(d.canAccess(33L,21L)).thenReturn(false);assertEquals("DELEGATE_AUTHORITY_CHANGED",access.executionIssue(delegated,delegationIdentity));when(d.canAccess(33L,21L)).thenReturn(true);
  when(g.findActivePrimaryByStudentIdForUpdate(11L)).thenReturn(null);assertEquals("GUARDIAN_CHANGED",access.executionIssue(stored,identity));when(g.findActivePrimaryByStudentIdForUpdate(11L)).thenReturn(guardian);
  when(p.isAllowed(32L,PermissionClient.WEB,"STUDENT_TRANSFER_REVIEW")).thenReturn(false);assertEquals("REVIEW_AUTHORITY_CHANGED",access.executionIssue(stored,identity));
  when(p.isAllowed(32L,PermissionClient.WEB,"STUDENT_TRANSFER_REVIEW")).thenReturn(true);when(d.canAccess(32L,21L)).thenReturn(false);assertEquals("REVIEW_AUTHORITY_CHANGED",access.executionIssue(stored,identity));
 }
 @Test void explicitPermissionAndBothFamilyMembershipsAndPrimaryGuardianAreRequired(){
  var p=mock(PermissionDecisionService.class);var g=mock(ParentStudentMapper.class);var f=mock(InstitutionEnrollmentMapper.class);var s=mock(StudentMapper.class);var e=mock(StudentOrganizationMapper.class);var o=mock(OrganizationMapper.class);var d=mock(OrganizationDataScopeService.class);var access=new TransferApplicationAccess(p,g,f,s,e,o,d);
  var actor=new AuthenticatedUser(31L,1L,"fixture","fixture",AuthClientType.MINIAPP,List.of());
  Runnable check=()->access.requireFamily(actor,11,21,22,"STUDENT_TRANSFER_REQUEST_CREATE");
  assertThrows(SystemOperationAccessDeniedException.class,check::run);verifyNoInteractions(s,g,f);
  when(p.isAllowed(31L,PermissionClient.MINIAPP,"STUDENT_TRANSFER_REQUEST_CREATE")).thenReturn(true);
  when(s.findById(11L)).thenReturn(Student.create(11L,"fixture","G1",41L));
  var source=mock(Organization.class);when(source.typeCode()).thenReturn("SCHOOL");when(source.status()).thenReturn(OrganizationStatus.ENABLED);when(source.effectiveStatus()).thenReturn(OrganizationEffectiveStatus.ENABLED);when(o.findById(21L)).thenReturn(source);when(e.findActiveEnrollmentOrganizationIds(11L)).thenReturn(List.of(21L));
  var family=mock(Organization.class);when(family.typeCode()).thenReturn("FAMILY");when(family.status()).thenReturn(OrganizationStatus.ENABLED);when(family.effectiveStatus()).thenReturn(OrganizationEffectiveStatus.ENABLED);when(o.findById(22L)).thenReturn(family);
  assertThrows(SystemOperationAccessDeniedException.class,check::run);
  when(f.familyMember(31L,22L)).thenReturn(true);when(f.familyMember(41L,22L)).thenReturn(true);
  assertThrows(SystemOperationAccessDeniedException.class,check::run);
  when(g.existsActivePrimaryByParentAndStudent(31L,11L)).thenReturn(true);assertDoesNotThrow(check::run);
  when(e.findActiveEnrollmentOrganizationIds(11L)).thenReturn(List.of());assertThrows(SystemOperationAccessDeniedException.class,check::run);
  var application=TransferApplication.submit(11,21,31,"destination","reason",java.time.LocalDate.now(),List.of(),java.time.LocalDate.now());
  var stored=new TransferApplicationRepository.Stored(1,22,1,application);
  when(p.isAllowed(31L,PermissionClient.MINIAPP,"STUDENT_TRANSFER_VIEW")).thenReturn(true);
  assertDoesNotThrow(()->access.requireRead(actor,stored));
  when(g.existsActivePrimaryByParentAndStudent(31L,11L)).thenReturn(false);
  assertThrows(SystemOperationAccessDeniedException.class,()->access.requireRead(actor,stored));
  when(d.canAccess(31L,21L)).thenReturn(true);assertDoesNotThrow(()->access.requireRead(actor,stored));
  assertTrue(access.requireList(actor,22));assertFalse(access.requireList(actor,21));
  when(f.familyMember(31L,22L)).thenReturn(false);assertThrows(SystemOperationAccessDeniedException.class,()->access.requireList(actor,22));
  when(d.canAccess(31L,21L)).thenReturn(false);assertThrows(SystemOperationAccessDeniedException.class,()->access.requireList(actor,21));
  when(p.isAllowed(31L,PermissionClient.MINIAPP,"STUDENT_TRANSFER_VIEW")).thenReturn(false);
  assertThrows(SystemOperationAccessDeniedException.class,()->access.requireRead(actor,stored));
  assertThrows(SystemOperationAccessDeniedException.class,()->access.requireList(actor,22));
 }
 @Test void institutionDelegationRequiresBothPermissionsSourceScopeAndUniquePrimaryGuardian(){
  var p=mock(PermissionDecisionService.class);var g=mock(ParentStudentMapper.class);var f=mock(InstitutionEnrollmentMapper.class);var s=mock(StudentMapper.class);var e=mock(StudentOrganizationMapper.class);var o=mock(OrganizationMapper.class);var d=mock(OrganizationDataScopeService.class);var access=new TransferApplicationAccess(p,g,f,s,e,o,d);
  var actor=new AuthenticatedUser(33L,1L,"fixture","fixture",AuthClientType.MINIAPP,List.of());
  assertThrows(SystemOperationAccessDeniedException.class,()->access.requireInstitutionSubmission(actor,11,21));verifyNoInteractions(s,g);
  when(p.isAllowed(33L,PermissionClient.MINIAPP,"STUDENT_TRANSFER_REQUEST_CREATE")).thenReturn(true);
  assertThrows(SystemOperationAccessDeniedException.class,()->access.requireInstitutionSubmission(actor,11,21));verifyNoInteractions(s,g);
  when(p.isAllowed(33L,PermissionClient.MINIAPP,"STUDENT_ORGANIZATION_MANAGE")).thenReturn(true);
  assertThrows(SystemOperationAccessDeniedException.class,()->access.requireInstitutionSubmission(actor,11,21));verifyNoInteractions(s,g);
  when(d.canAccess(33L,21L)).thenReturn(true);var student=Student.create(11L,"fixture","G1",41L);when(s.findByIdForUpdate(11L)).thenReturn(student);when(s.findById(11L)).thenReturn(student);
  var source=mock(Organization.class);when(source.typeCode()).thenReturn("SCHOOL");when(source.status()).thenReturn(OrganizationStatus.ENABLED);when(source.effectiveStatus()).thenReturn(OrganizationEffectiveStatus.ENABLED);when(o.findById(21L)).thenReturn(source);when(e.findActiveEnrollmentOrganizationIds(11L)).thenReturn(List.of(21L));
  when(g.findActiveByStudentIdForUpdate(11L)).thenReturn(List.of());assertThrows(SystemOperationAccessDeniedException.class,()->access.requireInstitutionSubmission(actor,11,21));
  var guardian=new com.lingdong.learning.student.domain.ParentRelationship(1L,31L,11L,com.lingdong.learning.student.domain.ParentRelationshipRole.PRIMARY_GUARDIAN,"ACTIVE","PRIMARY",null,null);
  when(g.findActiveByStudentIdForUpdate(11L)).thenReturn(List.of(guardian,guardian));assertThrows(SystemOperationAccessDeniedException.class,()->access.requireInstitutionSubmission(actor,11,21));
  when(g.findActiveByStudentIdForUpdate(11L)).thenReturn(List.of(guardian));assertEquals(31L,access.requireInstitutionSubmission(actor,11,21));
  when(g.findActiveByStudentId(11L)).thenReturn(List.of(guardian));clearInvocations(s,g);assertEquals(31L,access.requireInstitutionOptions(actor,11,21));verify(s,never()).findByIdForUpdate(anyLong());verify(g,never()).findActiveByStudentIdForUpdate(anyLong());
  when(e.findActiveEnrollmentOrganizationIds(11L)).thenReturn(List.of());assertThrows(SystemOperationAccessDeniedException.class,()->access.requireInstitutionOptions(actor,11,21));
 }
}
