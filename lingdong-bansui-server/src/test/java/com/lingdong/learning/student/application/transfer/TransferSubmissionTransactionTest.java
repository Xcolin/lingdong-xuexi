package com.lingdong.learning.student.application.transfer;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.interceptor.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.attachment.domain.*;
import com.lingdong.learning.attachment.infrastructure.persistence.ManagedFileMapper;
import com.lingdong.learning.attachment.application.*;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.feature.application.FeatureAccessService;
import java.time.*;import java.util.List;
import static org.mockito.Mockito.*;import static org.junit.jupiter.api.Assertions.*;
class TransferSubmissionTransactionTest {
 @Test void attachmentAssociationFailureRollsBackProposalAndRetrySucceeds(){
  var ds=new DriverManagerDataSource("jdbc:h2:mem:transfer_submission;MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");var j=new JdbcTemplate(ds);
  j.execute("CREATE TABLE edu_student(id BIGINT PRIMARY KEY)");j.execute("CREATE TABLE sys_organization(id BIGINT PRIMARY KEY)");j.execute("CREATE TABLE sys_user(id BIGINT PRIMARY KEY)");j.update("INSERT INTO edu_student VALUES (11)");j.update("INSERT INTO sys_organization VALUES (21),(22)");j.update("INSERT INTO sys_user VALUES (31)");
  new ResourceDatabasePopulator(new ClassPathResource("db/migration/V129__student_transfer_applications.sql"),new ClassPathResource("db/migration/V132__transfer_submission_identity.sql")).execute(ds);
  j.execute("CREATE TABLE sys_file(id BIGINT PRIMARY KEY,uploader_id BIGINT,module_code VARCHAR(64),file_category VARCHAR(64),status VARCHAR(20))");
  j.execute("CREATE TABLE sys_file_relation(file_id BIGINT,status VARCHAR(20))");
  j.update("INSERT INTO sys_file VALUES(41,31,'STUDENT_TRANSFER','MATERIAL','AVAILABLE')");
  var repository=new TransferApplicationRepository(j,new ObjectMapper());var access=mock(TransferApplicationAccess.class);var files=mock(ManagedFileMapper.class);var attachments=mock(AttachmentFileApplicationService.class);var ids=mock(IdGenerator.class);when(ids.nextId()).thenReturn(77L);
  when(files.findById(41L)).thenReturn(new ManagedFileRecord(41L,"fixture","fixture.png","png","image/png",1L,31L,"STUDENT_TRANSFER","MATERIAL",null,FileStatus.AVAILABLE,null,null,null));
  var clock=Clock.fixed(Instant.parse("2026-10-14T00:00:00Z"),ZoneOffset.UTC);var service=new TransferApplicationService(repository,access,files,attachments,ids,clock,mock(FeatureAccessService.class));
  var factory=new ProxyFactory(service);factory.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds),new MatchAlwaysTransactionAttributeSource()));var transactional=(TransferApplicationService)factory.getProxy();
  var actor=new AuthenticatedUser(31L,1L,"fixture","fixture",AuthClientType.MINIAPP,List.of());
  when(attachments.attachToBusiness(any())).thenThrow(new IllegalStateException("fixture attachment failure"));
  assertThrows(IllegalStateException.class,()->transactional.submit(actor,11,21,22,"destination","reason",LocalDate.now(clock),List.of(41L)));
  assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM edu_transfer_application",Integer.class));
  reset(attachments);var stored=transactional.submit(actor,11,21,22,"destination","reason",LocalDate.now(clock),List.of(41L));
  assertEquals(77,stored.id());assertEquals(TransferApplication.Status.PENDING,stored.application().status());assertEquals(List.of(41L),stored.application().attachmentIds());
  verify(attachments).attachToBusiness(new AttachFileToBusinessCommand(41L,"STUDENT_TRANSFER",77L,"MATERIAL","TRANSFER_PARTICIPANTS"));
  assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM edu_transfer_application",Integer.class));
 }
}
