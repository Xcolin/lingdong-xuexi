package com.lingdong.learning.student.application.transfer;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.attachment.infrastructure.persistence.ManagedFileMapper;
import com.lingdong.learning.attachment.application.AttachmentFileApplicationService;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import java.time.*;import java.util.*;
class TransferApplicationServiceTest {
 @Test void missingOrForeignMaterialCannotCreateProposal(){
  var repository=mock(TransferApplicationRepository.class);var access=mock(TransferApplicationAccess.class);var files=mock(ManagedFileMapper.class);var attachments=mock(AttachmentFileApplicationService.class);var ids=mock(IdGenerator.class);var features=mock(FeatureAccessService.class);var clock=Clock.fixed(Instant.parse("2026-10-14T00:00:00Z"),ZoneOffset.UTC);var service=new TransferApplicationService(repository,access,files,attachments,ids,clock,features);var actor=new AuthenticatedUser(31L,1L,"fixture","fixture",AuthClientType.MINIAPP,List.of());
  assertThrows(SystemOperationAccessDeniedException.class,()->service.submit(actor,11,21,22,"destination","reason",LocalDate.now(clock),List.of(41L)));
  verifyNoInteractions(repository,attachments,ids);
 }
 @Test void permissionRejectionPrecedesFileLookupAndWrites(){
  var repository=mock(TransferApplicationRepository.class);var access=mock(TransferApplicationAccess.class);var files=mock(ManagedFileMapper.class);var attachments=mock(AttachmentFileApplicationService.class);var ids=mock(IdGenerator.class);var features=mock(FeatureAccessService.class);var clock=Clock.systemUTC();var service=new TransferApplicationService(repository,access,files,attachments,ids,clock,features);var actor=new AuthenticatedUser(31L,1L,"fixture","fixture",AuthClientType.MINIAPP,List.of());
  doThrow(new SystemOperationAccessDeniedException("denied")).when(access).requireFamily(actor,11,21,22,"STUDENT_TRANSFER_REQUEST_CREATE");
  assertThrows(SystemOperationAccessDeniedException.class,()->service.submit(actor,11,21,22,"destination","reason",LocalDate.now(clock),List.of()));verifyNoInteractions(repository,files,attachments,ids);
 }
}
