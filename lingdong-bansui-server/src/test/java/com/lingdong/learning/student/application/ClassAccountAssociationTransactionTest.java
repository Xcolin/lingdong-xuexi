package com.lingdong.learning.student.application;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import com.lingdong.learning.auth.application.*;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.student.infrastructure.persistence.StudentOrganizationMapper;
import com.lingdong.learning.feature.application.FeatureAccessService;
import java.util.List;
class ClassAccountAssociationTransactionTest {
 @Test void realJdbcWritesRollbackOnRelationshipFailureButAuthenticationFailurePersists(){
  var ds=new DriverManagerDataSource("jdbc:h2:mem:account_tx_"+System.nanoTime()+";DB_CLOSE_DELAY=-1","sa","");var db=new JdbcTemplate(ds);db.execute("CREATE TABLE proof_state(id BIGINT PRIMARY KEY)");db.execute("CREATE TABLE class_link(id BIGINT PRIMARY KEY)");
  var access=mock(StudentManagementAccessService.class);var orgs=mock(OrganizationMapper.class);var proof=mock(StudentCodeLoginApplicationService.class);var relations=mock(StudentOrganizationMapper.class);var lifecycle=mock(StudentOrganizationLifecycleService.class);var actor=new AuthenticatedUser(1L,2L,"actor","actor",AuthClientType.MINIAPP,List.of());var cmd=new StudentCodeLoginCommand("child","1234","device","preview",null,null,"127.0.0.1");
  when(orgs.findById(2L)).thenReturn(Organization.create(2L,null,"ROOT","S","school","SCHOOL","/2/",1,null));when(orgs.findById(3L)).thenReturn(Organization.create(3L,2L,"S","C","class","CLASS","/2/3/",1,null));when(access.organizationAllowed(actor,3L)).thenReturn(true);when(relations.existsActiveEnrollmentInScope(5L,2L,null)).thenReturn(true);
  var target=new ClassAccountAssociationService(access,orgs,proof,relations,lifecycle,mock(FeatureAccessService.class));var factory=new ProxyFactory(target);factory.setProxyTargetClass(true);factory.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds),new AnnotationTransactionAttributeSource()));var service=(ClassAccountAssociationService)factory.getProxy();
  when(proof.verifyForBinding(cmd)).thenAnswer(i->{db.update("INSERT INTO proof_state VALUES(1)");return new VerifiedStudentIdentity(5L,6L,"child");});when(lifecycle.transferClass(eq(actor),eq(5L),any())).thenAnswer(i->{db.update("INSERT INTO class_link VALUES(1)");throw new IllegalStateException("after real link write");});
  assertThatThrownBy(()->service.associate(actor,3L,cmd)).isInstanceOf(IllegalStateException.class);assertThat(db.queryForObject("SELECT COUNT(*) FROM proof_state",Integer.class)).isZero();assertThat(db.queryForObject("SELECT COUNT(*) FROM class_link",Integer.class)).isZero();
  doAnswer(i->{db.update("INSERT INTO proof_state VALUES(2)");throw new StudentAuthenticationFailedException();}).when(proof).verifyForBinding(cmd);assertThatThrownBy(()->service.associate(actor,3L,cmd)).isInstanceOf(StudentAuthenticationFailedException.class);assertThat(db.queryForObject("SELECT COUNT(*) FROM proof_state",Integer.class)).isEqualTo(1);assertThat(db.queryForObject("SELECT COUNT(*) FROM class_link",Integer.class)).isZero();
 }
}
