package com.lingdong.learning.attendance.application;

import com.lingdong.learning.attendance.domain.AttendanceStatus;
import com.lingdong.learning.attendance.infrastructure.persistence.*;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.feature.application.FeatureDisabledException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

/** 使用真实 Flyway、MyBatis、RBAC 和事务验证考勤隔离与原子性。 */
@SpringBootTest
@ActiveProfiles("test")
class AttendancePersistenceTest {
    @Autowired AttendanceService service;
    @Autowired AttendanceMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired Clock clock;
    @Autowired org.mybatis.spring.SqlSessionTemplate sqlSession;
    private long next;
    private long school, classroom, otherClass, teacher, parent, studentUser, student, second;
    private LocalDate date;
    private TransactionTemplate tx;

    @BeforeEach void init() {
        next = 8920000000000002000L;
        date = LocalDate.now(clock.withZone(ZoneId.of("Asia/Shanghai")));
        tx = new TransactionTemplate(transactions);
    }

    @Test void createsCorrectsAndReplaysWithoutDuplicateHistory() {
        tx.executeWithoutResult(status -> {
            seed();
            var initial = service.batch(user(teacher, "TEACHER"), classroom, date,
                    List.of(entry(student, AttendanceStatus.NORMAL, null))).get(0);
            assertThat(initial.id().toString()).hasSize(19);
            assertThat(service.batch(user(teacher, "TEACHER"), classroom, date,
                    List.of(entry(student, AttendanceStatus.NORMAL, null))).get(0).versionNo()).isZero();
            assertThat(mapper.findActions(initial.id())).hasSize(1);
            var corrected = service.batch(user(teacher, "TEACHER"), classroom, date,
                    List.of(entry(student, AttendanceStatus.LEAVE, 0L))).get(0);
            assertThat(corrected.versionNo()).isEqualTo(1);
            assertThat(mapper.findActions(initial.id())).extracting(AttendanceActionRow::actionType)
                    .containsExactly("CREATE", "CORRECT");
            assertThat(mapper.findActions(initial.id()).get(1).beforeStatus()).isEqualTo(AttendanceStatus.NORMAL);
            assertThatThrownBy(() -> service.batch(user(teacher, "TEACHER"), classroom, date,
                    List.of(entry(student, AttendanceStatus.ABSENT, 0L)))).isInstanceOf(IllegalStateException.class);
            status.setRollbackOnly();
        });
    }

    @Test void appliesTeacherParentStudentAndEmptyOrganizationScopesInSql() {
        tx.executeWithoutResult(status -> {
            seed();
            var rows = service.batch(user(teacher, "TEACHER"), classroom, date,
                    List.of(entry(student, AttendanceStatus.NORMAL, null), entry(second, AttendanceStatus.LATE, null)));
            assertThat(page(user(teacher, "TEACHER")).total()).isEqualTo(2);
            assertThat(page(user(parent, "PARENT")).items()).extracting(AttendanceRow::studentId).containsExactly(student);
            assertThat(page(user(studentUser, "STUDENT")).items()).extracting(AttendanceRow::studentId).containsExactly(student);
            var otherRecord = rows.stream().filter(r -> r.studentId().equals(second)).findFirst().orElseThrow();
            assertThatThrownBy(() -> service.details(user(parent, "PARENT"), otherRecord.id())).isInstanceOf(ResourceNotFoundException.class);
            assertThat(service.classes(user(parent, "PARENT"), false)).extracting(AttendanceClassRow::classOrganizationId).containsExactly(classroom);
            var empty = new AttendanceScope(teacher,"ORGANIZATION",false,List.of());
            assertThat(mapper.findPage(new AttendanceQuery(empty,null,null,null,null,date,date,20,0))).isEmpty();
            assertThat(mapper.findVisible(empty, rows.get(0).id())).isNull();
            assertThatThrownBy(() -> service.roster(user(teacher, "TEACHER"), otherClass, date)).isInstanceOf(ResourceNotFoundException.class);
            status.setRollbackOnly();
        });
    }

    @Test void disabledClassesKeepHistoryAndLateEnrollmentIsNotRetroactive() {
        tx.executeWithoutResult(status -> {
            seed();
            service.batch(user(teacher,"TEACHER"),classroom,date,List.of(entry(student,AttendanceStatus.NORMAL,null)));
            jdbc.update("update edu_student_organization set effective_from = ? where student_id = ?",date.plusDays(1).atStartOfDay(), second);
            assertThat(service.roster(user(teacher,"TEACHER"),classroom,date)).extracting(AttendanceService.RosterEntry::studentId).containsExactly(student);
            jdbc.update("update sys_organization set status='DISABLED',effective_status='DISABLED' where id=?",classroom);
            assertThat(page(user(teacher,"TEACHER")).total()).isEqualTo(1);
            assertThat(service.classes(user(teacher,"TEACHER"),false)).hasSize(1);
            assertThat(service.classes(user(teacher,"TEACHER"),true)).isEmpty();
            assertThatThrownBy(() -> service.batch(user(teacher,"TEACHER"),classroom,date,
                    List.of(entry(student,AttendanceStatus.LEAVE,0L)))).isInstanceOf(ResourceNotFoundException.class);
            status.setRollbackOnly();
        });
    }

    @Test void dynamicOrganizationRoleIsBoundToItsSchool() {
        tx.executeWithoutResult(status -> {
            seed();
            long manager = addUser("考勤管理员");
            long role = id();
            jdbc.update("insert into sys_role(id,role_code,role_name,role_type,data_scope,built_in,status) values(?,?,'考勤员','CUSTOM','SCHOOL',0,'ENABLED')",role,"ATT_CUSTOM_"+role);
            assign(manager,role,school);
            jdbc.update("insert into sys_user_organization(id,user_id,organization_id) values(?,?,?)",id(),manager,school);
            for(long permission : List.of(1874244142494646680L,1874244142494646681L)) {
                jdbc.update("insert into sys_role_permission(id,role_id,permission_id,effect) values(?,?,?,'ALLOW')",id(),role,permission);
            }
            var principal = user(manager,"ATT_CUSTOM");
            service.batch(principal,classroom,date,List.of(entry(student,AttendanceStatus.LEAVE,null)));
            assertThat(page(principal).total()).isEqualTo(1);
            assertThatThrownBy(() -> service.roster(principal,otherClass,date)).isInstanceOf(ResourceNotFoundException.class);
            status.setRollbackOnly();
        });
    }

    @Test void revocationAndFeatureClosureImmediatelyDenyServiceCalls() {
        tx.executeWithoutResult(status -> {
            seed();
            jdbc.update("insert into sys_user_permission(id,user_id,permission_id,effect) values(?,?,?,'DENY')",id(),teacher,1874244142494646680L);
            assertThatThrownBy(() -> page(user(teacher,"TEACHER"))).isInstanceOf(SystemOperationAccessDeniedException.class);
            assertThatThrownBy(() -> service.batch(user(parent,"PARENT"),classroom,date,List.of(entry(student,AttendanceStatus.LEAVE,null))))
                    .isInstanceOf(SystemOperationAccessDeniedException.class);
            assertThatThrownBy(() -> page(user(teacher,"SYS_AUDITOR"))).isInstanceOf(SystemOperationAccessDeniedException.class);
            jdbc.update("update sys_feature_toggle set status='DISABLED' where feature_code='ATTENDANCE_MANAGEMENT'");
            // JDBC 直接改表绕过了 MyBatis 写操作的一级缓存失效；模拟下一请求的新会话。
            sqlSession.clearCache();
            assertThatThrownBy(() -> service.classes(user(teacher,"TEACHER"),false)).isInstanceOf(FeatureDisabledException.class);
            status.setRollbackOnly();
        });
    }

    @Test void failingSecondEntryRollsBackFirstEntryAndItsAction() {
        long original = jdbc.queryForObject("select count(*) from attendance_record",Long.class);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            seed();
            service.batch(user(teacher,"TEACHER"),classroom,date,List.of(
                    entry(student,AttendanceStatus.NORMAL,null),entry(8999999999999999999L,AttendanceStatus.NORMAL,null)));
        })).isInstanceOf(ResourceNotFoundException.class);
        assertThat(jdbc.queryForObject("select count(*) from attendance_record",Long.class)).isEqualTo(original);
        assertThat(jdbc.queryForObject("select count(*) from attendance_record_action a left join attendance_record r on r.id=a.record_id where r.id is null",Long.class)).isZero();
    }

    private AttendanceService.Page page(AuthenticatedUser u) { return service.page(u,null,null,null,null,date,date,1,20); }
    private AttendanceEntry entry(long id,AttendanceStatus state,Long version) { return new AttendanceEntry(id,state,null,null,version); }
    private AuthenticatedUser user(long id,String role) { return new AuthenticatedUser(id,1L,"test","测试",AuthClientType.WEB,List.of(role)); }
    private long id() { return ++next; }
    private long addUser(String name) {
        long value=id();
        jdbc.update("insert into sys_user(id,username,display_name,user_type,status) values(?,?,?,'ORGANIZATION','ENABLED')",value,"attendance_"+value,name);
        return value;
    }
    private void assign(long user,long role,Long org) {
        jdbc.update("insert into sys_user_role(id,user_id,role_id,organization_id,organization_scope_key) values(?,?,?,?,?)",id(),user,role,org,org==null?"GLOBAL":org.toString());
    }
    private long organization(Long parent,String type,String path) {
        long value=id();
        jdbc.update("insert into sys_organization(id,parent_id,organization_code,organization_name,organization_type,organization_path,status) values(?,?,?,?,?,?,'ENABLED')",value,parent,"ATT_"+value,"考勤组织"+value,type,path);
        return value;
    }
    private long addStudent(Long user) {
        long value=id();
        jdbc.update("insert into edu_student(id,student_name,student_user_id,status) values(?,'测试学生',?,'ENABLED')",value,user);
        jdbc.update("insert into edu_student_organization(id,student_id,organization_id,relation_type,status,effective_from) values(?,?,?,'CLASS','ACTIVE',?)",id(),value,classroom,date.minusDays(10).atStartOfDay());
        return value;
    }
    private void seed() {
        school=organization(null,"SCHOOL","/ATT_TEST/");
        classroom=organization(school,"CLASS","/ATT_TEST/CLASS/");
        otherClass=organization(null,"CLASS","/ATT_OTHER/");
        teacher=addUser("考勤教师"); parent=addUser("考勤家长"); studentUser=addUser("考勤学生");
        assign(teacher,1874244142494646276L,classroom);
        assign(parent,1874244142494646277L,null);
        assign(studentUser,1874244142494646278L,null);
        jdbc.update("insert into edu_teacher_class(id,teacher_user_id,class_organization_id,status) values(?,?,?,'ACTIVE')",id(),teacher,classroom);
        student=addStudent(studentUser); second=addStudent(null);
        jdbc.update("insert into edu_parent_student(id,parent_user_id,student_id,relation_role,status,primary_scope_key) values(?,?,?,'SECONDARY_GUARDIAN','ACTIVE','SECONDARY')",id(),parent,student);
    }
}
