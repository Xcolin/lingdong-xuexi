package com.lingdong.learning.student.application.transfer;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;import static org.junit.jupiter.api.Assertions.*;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.attachment.infrastructure.persistence.ManagedFileMapper;
import com.lingdong.learning.attachment.application.AttachmentFileApplicationService;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.feature.application.FeatureAccessService;
import java.time.*;
class TransferInstitutionSubmissionTest {
 @Test void validatesInstitutionBeforeLookingUpFamilyAndRecordsActualOperator(){
  var repo=mock(TransferApplicationRepository.class);var access=mock(TransferApplicationAccess.class);var files=mock(ManagedFileMapper.class);var attachments=mock(AttachmentFileApplicationService.class);var ids=mock(IdGenerator.class);var clock=Clock.fixed(Instant.parse("2026-10-15T00:00:00Z"),ZoneOffset.UTC);
  var service=new TransferApplicationService(repo,access,files,attachments,ids,clock,mock(FeatureAccessService.class));var actor=new AuthenticatedUser(33L,1L,"fixture operator","fixture operator",AuthClientType.MINIAPP,java.util.List.of());
  when(access.requireInstitutionSubmission(actor,11,21)).thenThrow(new IllegalArgumentException("fixture denied"));
  assertThrows(IllegalArgumentException.class,()->service.submitInstitution(actor,11,21,"destination","reason",LocalDate.now(clock),java.util.List.of()));verifyNoInteractions(repo,files,attachments,ids);
  doReturn(31L).when(access).requireInstitutionSubmission(actor,11,21);when(repo.uniqueGuardianFamily(11,31)).thenReturn(22L);when(ids.nextId()).thenReturn(77L);
  var application=TransferApplication.submit(11,21,33,"destination","reason",LocalDate.now(clock),java.util.List.of(),LocalDate.now(clock));when(repo.find(77)).thenReturn(java.util.Optional.of(new TransferApplicationRepository.Stored(77,22,1,application)));
  var result=service.submitInstitution(actor,11,21,"destination","reason",LocalDate.now(clock),java.util.List.of());assertEquals(33,result.application().applicantId());
  verify(repo).insert(77,22,application);verify(repo).recordInstitutionSubmission(77,31,"MINIAPP");verify(repo,never()).recordApplicantClient(anyLong(),anyString());verifyNoInteractions(attachments);
 }
 @Test void optionsContextRechecksScopeBeforeResolvingFamilyWithoutWriteLocks(){
  var repo=mock(TransferApplicationRepository.class);var access=mock(TransferApplicationAccess.class);var service=new TransferApplicationService(repo,access,mock(ManagedFileMapper.class),mock(AttachmentFileApplicationService.class),mock(IdGenerator.class),Clock.systemUTC(),mock(FeatureAccessService.class));var actor=new AuthenticatedUser(33L,1L,"fixture","fixture",AuthClientType.MINIAPP,java.util.List.of());
  when(access.requireInstitutionOptions(actor,11,21)).thenThrow(new IllegalArgumentException("denied"));assertThrows(IllegalArgumentException.class,()->service.institutionContext(actor,11,21));verifyNoInteractions(repo);
  doReturn(31L).when(access).requireInstitutionOptions(actor,11,21);when(repo.uniqueGuardianFamily(11,31)).thenReturn(22L);assertEquals(22L,service.institutionContext(actor,11,21));verify(access,never()).requireInstitutionSubmission(any(),anyLong(),anyLong());
 }
}
