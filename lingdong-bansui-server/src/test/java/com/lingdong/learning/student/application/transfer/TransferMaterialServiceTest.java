package com.lingdong.learning.student.application.transfer;
import org.junit.jupiter.api.Test;
import com.lingdong.learning.attachment.application.*;
import com.lingdong.learning.attachment.infrastructure.persistence.ManagedFileMapper;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.feature.application.FeatureAccessService;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class TransferMaterialServiceTest {
 @Test void permissionDenialOccursBeforeAnyStorageAndValidUploadUsesIndependentModule(){
  var access=mock(TransferApplicationAccess.class);var applications=mock(TransferApplicationService.class);var content=mock(ManagedAttachmentContentService.class);var files=mock(ManagedFileMapper.class);var features=mock(FeatureAccessService.class);
  var service=new TransferMaterialService(access,applications,content,files,features,mock(com.lingdong.learning.attachment.infrastructure.persistence.FileRelationMapper.class));var actor=new AuthenticatedUser(31L,1L,"fixture","fixture",AuthClientType.MINIAPP,java.util.List.of());
  doThrow(new IllegalArgumentException("denied")).when(access).requireFamily(actor,11,21,22,"STUDENT_TRANSFER_REQUEST_CREATE");
  byte[] pdf="%PDF-1.7\nfixture".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
  assertThrows(IllegalArgumentException.class,()->service.upload(actor,11,21,22,"proof.pdf","application/pdf",pdf));verifyNoInteractions(content);
  doNothing().when(access).requireFamily(actor,11,21,22,"STUDENT_TRANSFER_REQUEST_CREATE");
  service.upload(actor,11,21,22,"proof.pdf","application/pdf",pdf);
  verify(content).store(31L,"STUDENT_TRANSFER","MATERIAL","proof.pdf","application/pdf",pdf);
 }
 @Test void unrelatedFileAndLostApplicationAccessNeverReadStoredBytes(){
  var access=mock(TransferApplicationAccess.class);var applications=mock(TransferApplicationService.class);var content=mock(ManagedAttachmentContentService.class);var files=mock(ManagedFileMapper.class);var service=new TransferMaterialService(access,applications,content,files,mock(FeatureAccessService.class),mock(com.lingdong.learning.attachment.infrastructure.persistence.FileRelationMapper.class));
  var actor=new AuthenticatedUser(31L,1L,"fixture","fixture",AuthClientType.MINIAPP,java.util.List.of());var date=java.time.LocalDate.now();
  when(applications.read(actor,1)).thenReturn(new TransferApplicationRepository.Stored(1,22,1,TransferApplication.submit(11,21,31,"target","reason",date,java.util.List.of(51L),date)));
  assertThrows(com.lingdong.learning.common.web.ResourceNotFoundException.class,()->service.read(actor,1,52));verifyNoInteractions(files,content);
  when(applications.read(actor,1)).thenThrow(new IllegalArgumentException("denied"));
  assertThrows(IllegalArgumentException.class,()->service.read(actor,1,51));verifyNoInteractions(files,content);
 }
 @Test void institutionUploadValidatesRealContextBeforeStoring(){
  var applications=mock(TransferApplicationService.class);var content=mock(ManagedAttachmentContentService.class);var service=new TransferMaterialService(mock(TransferApplicationAccess.class),applications,content,mock(ManagedFileMapper.class),mock(FeatureAccessService.class),mock(com.lingdong.learning.attachment.infrastructure.persistence.FileRelationMapper.class));var actor=new AuthenticatedUser(33L,1L,"fixture","fixture",AuthClientType.MINIAPP,java.util.List.of());byte[] pdf="%PDF-1.7\nfixture".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
  when(applications.institutionContext(actor,11,21)).thenThrow(new IllegalArgumentException("denied"));assertThrows(IllegalArgumentException.class,()->service.uploadInstitution(actor,11,21,"proof.pdf","application/pdf",pdf));verifyNoInteractions(content);
  doReturn(22L).when(applications).institutionContext(actor,11,21);service.uploadInstitution(actor,11,21,"proof.pdf","application/pdf",pdf);verify(content).store(33L,"STUDENT_TRANSFER","MATERIAL","proof.pdf","application/pdf",pdf);
 }
}
