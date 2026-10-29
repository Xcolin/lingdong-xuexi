package com.lingdong.learning.responsibility;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.student.application.StudentManagementAccessService;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

/** Real isolated SQL, migration, Spring transaction proxy and CAS; no external database. */
class ResponsibilityTransactionTest {
    static class FailingStore extends ResponsibilityStore {
        boolean failHistory;
        FailingStore(JdbcTemplate jdbc){super(jdbc);}
        @Override public void history(long s,long o,Long from,long to,String reason,String text,long actor,int before,int after){
            super.history(s,o,from,to,reason,text,actor,before,after);
            if(failHistory)throw new IllegalStateException("injected after real history insert");
        }
    }
    @Configuration @EnableTransactionManagement static class Config {
        @Bean javax.sql.DataSource dataSource(){return new DriverManagerDataSource("jdbc:h2:mem:responsibility_"+System.nanoTime()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");}
        @Bean org.springframework.transaction.PlatformTransactionManager transactionManager(javax.sql.DataSource ds){return new DataSourceTransactionManager(ds);}
        @Bean JdbcTemplate jdbc(javax.sql.DataSource ds){return new JdbcTemplate(ds);}
        @Bean FailingStore store(JdbcTemplate jdbc){return new FailingStore(jdbc);}
        @Bean ResponsibilityService service(FailingStore store,StudentManagementAccessService access,PermissionDecisionService permissions){return new ResponsibilityService(store,access,permissions);}
    }
    final AuthenticatedUser actor=new AuthenticatedUser(1L,2L,"operator","管理员",AuthClientType.MINIAPP,List.of());
    AnnotationConfigApplicationContext fixture() throws Exception {
        var c=new AnnotationConfigApplicationContext();
        c.getBeanFactory().registerSingleton("access",mock(StudentManagementAccessService.class));
        c.getBeanFactory().registerSingleton("permissions",mock(PermissionDecisionService.class));
        c.register(Config.class);c.refresh();var db=c.getBean(JdbcTemplate.class);
        db.execute("CREATE TABLE sys_user(id BIGINT PRIMARY KEY,display_name VARCHAR(60),status VARCHAR(16))");
        db.execute("CREATE TABLE sys_organization(id BIGINT PRIMARY KEY,organization_type VARCHAR(32),organization_path VARCHAR(100),organization_name VARCHAR(60),status VARCHAR(16),effective_status VARCHAR(16))");
        db.execute("CREATE TABLE edu_student(id BIGINT PRIMARY KEY,student_user_id BIGINT,status VARCHAR(16))");
        db.execute("CREATE TABLE sys_organization_admin(user_id BIGINT,organization_id BIGINT)");
        db.execute("CREATE TABLE sys_role(id BIGINT PRIMARY KEY,status VARCHAR(16))");
        db.execute("CREATE TABLE sys_user_role(user_id BIGINT,organization_id BIGINT,role_id BIGINT)");
        db.execute("CREATE TABLE sys_user_organization(user_id BIGINT,organization_id BIGINT)");
        db.execute("CREATE TABLE edu_student_organization(id BIGINT PRIMARY KEY,student_id BIGINT,organization_id BIGINT,relation_type VARCHAR(20),status VARCHAR(16),effective_from TIMESTAMP,effective_to TIMESTAMP)");
        db.execute("CREATE TABLE edu_class_academic_metadata(class_organization_id BIGINT,head_teacher_user_id BIGINT)");
        db.execute("CREATE TABLE edu_teacher_class(teacher_user_id BIGINT,class_organization_id BIGINT,status VARCHAR(16),effective_from TIMESTAMP,effective_to TIMESTAMP)");
        db.execute("CREATE TABLE edu_parent_student(parent_user_id BIGINT,student_id BIGINT,status VARCHAR(16))");
        try(var connection=c.getBean(javax.sql.DataSource.class).getConnection()){ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/migration/V151__student_institution_responsibility.sql"));}
        db.update("INSERT INTO sys_user VALUES(1,'管理员','ENABLED'),(2,'班主任甲','ENABLED'),(3,'新老师','ENABLED'),(4,'班主任乙','ENABLED')");
        db.update("INSERT INTO edu_student VALUES(10,1000,'ENABLED')");
        db.update("INSERT INTO sys_organization VALUES(20,'SCHOOL','/20/','甲校','ENABLED','ENABLED'),(21,'TRAINING_INSTITUTION','/21/','乙机构','ENABLED','ENABLED'),(30,'CLASS','/20/30/','甲班','ENABLED','ENABLED'),(31,'CLASS','/21/31/','乙班','ENABLED','ENABLED')");
        db.update("INSERT INTO sys_organization_admin VALUES(1,20),(3,20),(1,21),(4,21)");
        db.update("INSERT INTO sys_role VALUES(50,'ENABLED'),(51,'ENABLED')");
        db.update("INSERT INTO sys_user_role VALUES(1,20,50),(3,20,51),(1,21,50),(4,21,50)");
        db.update("INSERT INTO edu_student_organization VALUES(100,10,20,'ENROLLMENT','ACTIVE',CURRENT_TIMESTAMP,NULL),(101,10,21,'ENROLLMENT','ACTIVE',CURRENT_TIMESTAMP,NULL),(102,10,30,'CLASS','ACTIVE',CURRENT_TIMESTAMP,NULL),(103,10,31,'CLASS','ACTIVE',CURRENT_TIMESTAMP,NULL)");
        db.update("INSERT INTO edu_class_academic_metadata VALUES(30,2),(31,4)");
        db.update("INSERT INTO edu_teacher_class VALUES(2,30,'ACTIVE',CURRENT_TIMESTAMP,NULL),(4,31,'ACTIVE',CURRENT_TIMESTAMP,NULL)");
        var access=c.getBean(StudentManagementAccessService.class);when(access.organizationAllowed(actor,20L)).thenReturn(true);when(access.organizationAllowed(actor,21L)).thenReturn(true);
        var permissions=c.getBean(PermissionDecisionService.class);when(permissions.isAllowed(eq(1L),eq(PermissionClient.MINIAPP),anyString())).thenReturn(true);
        return c;
    }
    private void selected(long id,String type){
        var request=new org.springframework.mock.web.MockHttpServletRequest();
        request.setAttribute(com.lingdong.learning.common.security.SelectedOrganizationContext.ATTRIBUTE,
            com.lingdong.learning.organization.domain.Organization.create(id,null,"ROOT","scope","scope",type,"/"+id+"/",0,null));
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(request));
    }
    @org.junit.jupiter.api.AfterEach void clearSelected(){org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();}
    @Test void guardianRequiresSharedEnabledFamilyAndSelectedFamilyScope() throws Exception {
        try(var c=fixture()){
            var db=c.getBean(JdbcTemplate.class);var service=c.getBean(ResponsibilityService.class);
            var permissions=c.getBean(PermissionDecisionService.class);
            when(permissions.isAllowed(1L,PermissionClient.MINIAPP,"STUDENT_RESPONSIBILITY_READ")).thenReturn(false);
            db.update("INSERT INTO edu_parent_student VALUES(1,10,'ACTIVE')");
            db.update("INSERT INTO sys_organization VALUES(40,'FAMILY','/40/','甲家','ENABLED','ENABLED'),(41,'FAMILY','/41/','乙家','ENABLED','ENABLED')");
            db.update("INSERT INTO sys_user_organization VALUES(1,40),(1000,40),(1,41)");
            selected(41,"FAMILY");
            assertThat(c.getBean(FailingStore.class).guardian(1,10)).isFalse();
            clearSelected();
            assertThat(service.current(actor,10,20).source()).isEqualTo("HEAD_TEACHER");
            selected(40,"FAMILY");
            assertThat(service.current(actor,10,20).source()).isEqualTo("HEAD_TEACHER");
            selected(41,"FAMILY");
            assertThatThrownBy(()->service.current(actor,10,20)).isInstanceOf(com.lingdong.learning.common.security.SystemOperationAccessDeniedException.class);
            selected(20,"SCHOOL");
            assertThatThrownBy(()->service.current(actor,10,20)).isInstanceOf(com.lingdong.learning.common.security.SystemOperationAccessDeniedException.class);
            clearSelected();
            db.update("DELETE FROM sys_user_organization WHERE user_id=1 AND organization_id=40");
            assertThatThrownBy(()->service.current(actor,10,20)).isInstanceOf(com.lingdong.learning.common.security.SystemOperationAccessDeniedException.class);
            db.update("INSERT INTO sys_user_organization VALUES(1,40)");
            db.update("DELETE FROM sys_user_organization WHERE user_id=1000 AND organization_id=40");
            assertThatThrownBy(()->service.current(actor,10,20)).isInstanceOf(com.lingdong.learning.common.security.SystemOperationAccessDeniedException.class);
            db.update("INSERT INTO sys_user_organization VALUES(1000,40)");
            db.update("UPDATE sys_organization SET effective_status='DISABLED' WHERE id=40");
            assertThatThrownBy(()->service.current(actor,10,20)).isInstanceOf(com.lingdong.learning.common.security.SystemOperationAccessDeniedException.class);
        }
    }
    @Test void firstWriteCasIsolationAndRollback() throws Exception {
        try(var c=fixture()){
            var service=c.getBean(ResponsibilityService.class);var db=c.getBean(JdbcTemplate.class);var store=c.getBean(FailingStore.class);
            assertThat(service.current(actor,10,20).responsibleUser().id()).isEqualTo("2");
            assertThat(service.current(actor,10,21).responsibleUser().id()).isEqualTo("4");
            store.failHistory=true;
            var request=new ResponsibilityService.TransferRequest(20L,3L,"RESIGNATION_HANDOVER",null,0);
            assertThatThrownBy(()->service.transfer(actor,10,request)).isInstanceOf(IllegalStateException.class);
            assertThat(db.queryForObject("SELECT COUNT(*) FROM edu_student_institution_responsibility",Integer.class)).isZero();
            assertThat(db.queryForObject("SELECT COUNT(*) FROM edu_student_responsibility_transfer",Integer.class)).isZero();
            store.failHistory=false;
            assertThat(service.transfer(actor,10,request).versionNo()).isEqualTo(1);
            assertThat(service.current(actor,10,21).versionNo()).isZero();
            assertThat(service.history(actor,10,21,1,20).items()).isEmpty();
            assertThat(service.history(actor,10,20,1,20).items()).hasSize(1);
            assertThatThrownBy(()->service.transfer(actor,10,request)).isInstanceOf(ResponsibilityConflictException.class);
            assertThat(service.history(actor,10,20,1,20).items()).hasSize(1);
            assertThat(service.transfer(actor,10,new ResponsibilityService.TransferRequest(20L,1L,"OTHER","接手",1)).versionNo()).isEqualTo(2);
            store.failHistory=true;
            assertThatThrownBy(()->service.transfer(actor,10,new ResponsibilityService.TransferRequest(20L,3L,"TEMPORARY_TAKEOVER",null,2))).isInstanceOf(IllegalStateException.class);
            assertThat(store.row(10,20).userId()).isEqualTo(1L);
            assertThat(store.row(10,20).version()).isEqualTo(2);
            assertThat(store.historyPage(10,20,20,0)).hasSize(2);
            db.update("UPDATE sys_user SET status='DISABLED' WHERE id=1");
            assertThat(store.row(10,20).version()).isEqualTo(2);
            assertThat(store.activeAdministrator(1,20)).isFalse();
        }
    }
    @Test void concurrentFirstInsertHasOneWinnerAndOneHistory() throws Exception {
        try(var c=fixture()){
            var service=c.getBean(ResponsibilityService.class);var ready=new java.util.concurrent.CountDownLatch(2);var start=new java.util.concurrent.CountDownLatch(1);
            var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
            try{
                java.util.concurrent.Callable<String> operation=()->{ready.countDown();if(!start.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("timeout");try{service.transfer(actor,10,new ResponsibilityService.TransferRequest(20L,3L,"TEMPORARY_TAKEOVER",null,0));return "OK";}catch(ResponsibilityConflictException ex){return "CONFLICT";}};
                var a=pool.submit(operation);var b=pool.submit(operation);assertThat(ready.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();start.countDown();
                assertThat(List.of(a.get(10,java.util.concurrent.TimeUnit.SECONDS),b.get(10,java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder("OK","CONFLICT");
                assertThat(service.history(actor,10,20,1,20).items()).hasSize(1);
            }finally{start.countDown();pool.shutdownNow();}
        }
    }
    @Test void stableFallbackOnlyUsesLiveAssignmentAndHistoryRetainsDepartedUsers() throws Exception {
        try(var c=fixture()){
            var db=c.getBean(JdbcTemplate.class);var service=c.getBean(ResponsibilityService.class);var store=c.getBean(FailingStore.class);
            db.update("INSERT INTO sys_organization VALUES(32,'CLASS','/20/32/','新班','ENABLED','ENABLED')");
            db.update("INSERT INTO edu_student_organization VALUES(104,10,32,'CLASS','ACTIVE',CURRENT_TIMESTAMP,NULL)");
            db.update("UPDATE edu_student_organization SET effective_from='2026-01-01 00:00:00' WHERE relation_type='CLASS'");
            db.update("INSERT INTO edu_class_academic_metadata VALUES(32,3)");
            db.update("INSERT INTO edu_teacher_class VALUES(3,32,'ACTIVE','2025-01-01 00:00:00',NULL)");
            assertThat(service.current(actor,10,20).className()).isEqualTo("新班");
            db.update("UPDATE edu_teacher_class SET status='INACTIVE' WHERE class_organization_id=32");
            assertThat(service.current(actor,10,20).responsibleUser().id()).isEqualTo("2");
            db.update("UPDATE edu_teacher_class SET effective_to='2020-01-01 00:00:00' WHERE class_organization_id=30");
            assertThat(service.current(actor,10,20).source()).isEqualTo("NONE");
            service.transfer(actor,10,new ResponsibilityService.TransferRequest(20L,3L,"WORK_ADJUSTMENT",null,0));
            db.update("DELETE FROM sys_organization_admin WHERE user_id=3 AND organization_id=20");
            assertThat(service.current(actor,10,20).availability()).isEqualTo("UNAVAILABLE");
            assertThat(service.options(actor,10,20)).extracting(ResponsibilityStore.Person::id).doesNotContain("3");
            assertThat(store.historyPage(10,20,20,0)).hasSize(1);
            assertThatThrownBy(()->service.transfer(actor,10,new ResponsibilityService.TransferRequest(20L,3L,"OTHER","同一人",1))).isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test void revokedOrDisabledScopedRoleCannotBeCandidateAndHistoryIsPaged() throws Exception {
        try(var c=fixture()){
            var db=c.getBean(JdbcTemplate.class);var service=c.getBean(ResponsibilityService.class);var store=c.getBean(FailingStore.class);
            assertThat(store.candidates(20)).extracting(ResponsibilityStore.Person::id).contains("3");
            db.update("UPDATE sys_role SET status='DISABLED' WHERE id=51");
            assertThat(store.activeAdministrator(3,20)).isFalse();
            assertThat(service.options(actor,10,20)).extracting(ResponsibilityStore.Person::id).doesNotContain("3");
            assertThatThrownBy(()->service.transfer(actor,10,new ResponsibilityService.TransferRequest(20L,3L,"OTHER","角色停用",0))).isInstanceOf(IllegalArgumentException.class);
            db.update("UPDATE sys_role SET status='ENABLED' WHERE id=51");
            db.update("DELETE FROM sys_user_role WHERE user_id=3 AND organization_id=20");
            assertThat(store.candidates(20)).extracting(ResponsibilityStore.Person::id).doesNotContain("3");
            db.update("INSERT INTO sys_user_role VALUES(3,20,51)");
            service.transfer(actor,10,new ResponsibilityService.TransferRequest(20L,3L,"OTHER","首次",0));
            service.transfer(actor,10,new ResponsibilityService.TransferRequest(20L,1L,"OTHER","再次",1));
            var first=service.history(actor,10,20,1,1);var second=service.history(actor,10,20,2,1);
            assertThat(first.total()).isEqualTo(2);assertThat(first.page()).isEqualTo(1);assertThat(first.pageSize()).isEqualTo(1);
            assertThat(first.items()).extracting(ResponsibilityStore.Transfer::afterVersionNo).containsExactly(2);
            assertThat(second.items()).extracting(ResponsibilityStore.Transfer::afterVersionNo).containsExactly(1);
            assertThat(service.history(actor,10,20,3,1).items()).isEmpty();
            assertThat(service.history(actor,10,21,1,1).total()).isZero();
            assertThatThrownBy(()->service.history(actor,10,20,0,20)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(()->service.history(actor,10,20,1,101)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
