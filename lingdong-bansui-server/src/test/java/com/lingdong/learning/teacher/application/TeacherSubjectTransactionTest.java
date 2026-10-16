package com.lingdong.learning.teacher.application;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.lingdong.learning.teacher.infrastructure.persistence.*;
import com.lingdong.learning.user.infrastructure.persistence.*;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.datascope.application.*;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import java.util.List;
class TeacherSubjectTransactionTest {
 @Test void auditFailureRollsBackMetadataThroughSpringTransactionProxy() throws Exception {
  var ds=new org.h2.jdbcx.JdbcDataSource();ds.setURL("jdbc:h2:mem:subject_tx_"+System.nanoTime()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
  var db=new org.springframework.jdbc.core.JdbcTemplate(ds);db.execute("CREATE TABLE sys_user(id BIGINT PRIMARY KEY)");db.execute("CREATE TABLE sys_organization(id BIGINT PRIMARY KEY)");
  try(var c=ds.getConnection()){org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(c,new org.springframework.core.io.ClassPathResource("db/migration/V135__teacher_subject_metadata.sql"));}
  db.update("INSERT INTO sys_user VALUES(1),(4)");db.update("INSERT INTO sys_organization VALUES(2)");
  var permissions=mock(PermissionDecisionService.class);when(permissions.isAllowed(anyLong(),any(),anyString())).thenReturn(true);
  var access=mock(TeacherManagementAccessService.class);var scopes=mock(OrganizationDataScopeService.class);when(scopes.resolve(1L)).thenReturn(OrganizationDataScope.all(false));
  var teachers=mock(TeacherManagementMapper.class);when(teachers.findAccessibleById(4L,true,List.of())).thenReturn(mock(TeacherDirectoryRow.class));
  var members=mock(UserOrganizationMapper.class);when(members.exists(4L,2L)).thenReturn(true);
  var users=mock(UserMapper.class);when(users.findByIdForUpdate(4L)).thenReturn(mock(com.lingdong.learning.user.domain.User.class));
  var dictionary=mock(TeacherSubjectDictionary.class);when(dictionary.validate("MATH")).thenReturn("MATH");
  var store=new TeacherSubjectStore(db);var target=new TeacherSubjectService(permissions,access,scopes,teachers,members,users,store,dictionary);
  var interceptor=new org.springframework.transaction.interceptor.TransactionInterceptor(new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds),new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource());
  var factory=new org.springframework.aop.framework.ProxyFactory(target);factory.setProxyTargetClass(true);factory.addAdvice(interceptor);var service=(TeacherSubjectService)factory.getProxy();
  // Force an actual database audit failure after metadata insertion, rather than mocking the write.
  db.update("INSERT INTO edu_teacher_subject_audit(teacher_user_id,institution_organization_id,version_no,operator_user_id) VALUES(4,2,1,1)");
  var actor=new AuthenticatedUser(1L,2L,"actor","actor",AuthClientType.MINIAPP,List.of());
  assertThatThrownBy(()->service.update(actor,4L,2L,"MATH",0)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM edu_teacher_subject_metadata",Integer.class)).isZero();
  assertThat(db.queryForObject("SELECT COUNT(*) FROM edu_teacher_subject_audit",Integer.class)).isEqualTo(1);
  db.update("DELETE FROM edu_teacher_subject_audit");
  assertThat(service.update(actor,4L,2L,"MATH",0).versionNo()).isEqualTo(1);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM edu_teacher_subject_metadata",Integer.class)).isEqualTo(1);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM edu_teacher_subject_audit",Integer.class)).isEqualTo(1);
  when(users.findByIdForUpdate(4L)).thenAnswer(invocation->{
   db.queryForObject("SELECT id FROM sys_user WHERE id=4 FOR UPDATE",Long.class);
   return mock(com.lingdong.learning.user.domain.User.class);
  });
  var start=new java.util.concurrent.CountDownLatch(1);var workers=java.util.concurrent.Executors.newFixedThreadPool(2);
  try {
   java.util.concurrent.Callable<Boolean> write=()->{start.await();try{service.update(actor,4L,2L,"MATH",1);return true;}catch(IllegalStateException conflict){return false;}};
   var first=workers.submit(write);var second=workers.submit(write);start.countDown();
   assertThat(List.of(first.get(10,java.util.concurrent.TimeUnit.SECONDS),second.get(10,java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
   assertThat(db.queryForObject("SELECT version_no FROM edu_teacher_subject_metadata",Integer.class)).isEqualTo(2);
   assertThat(db.queryForObject("SELECT COUNT(*) FROM edu_teacher_subject_audit",Integer.class)).isEqualTo(2);
  } finally {workers.shutdownNow();}
 }
}
