package com.lingdong.learning.growthpoint.application;

import com.lingdong.learning.growthpoint.infrastructure.persistence.AnonymousRankMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;

/** 真实迁移和 MyBatis/H2 校验排名口径，不读取全局账户余额。 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AnonymousRankPersistenceTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired AnonymousRankMapper mapper;
    @Autowired org.mybatis.spring.SqlSessionTemplate sqlSession;
    private long next = 1874244142494690000L;
    private long classroom, otherClass, reviewer;
    private long id() { return ++next; }
    @BeforeEach void seed() {
        reviewer = id();
        jdbc.update("insert into sys_user(id,username,display_name,user_type,status) values(?,'rank_reviewer','测试','ORGANIZATION','ENABLED')", reviewer);
        classroom = organization(); otherClass = organization();
    }
    @Test void ranksNetClassPointsWithCompetitionTiesAndIncludesZeroPoints() {
        long a = student(classroom), b = student(classroom), c = student(classroom);
        reward(a, classroom, "TEACHER", 20);
        long original = reward(a, null, "FAMILY", 10);
        jdbc.update("insert into growth_point_ledger(id,account_id,student_id,source_assignment_id,source_type,change_type,amount,available_delta,reviewer_user_id,occurred_at,correction_of_id) select ?,account_id,student_id,source_assignment_id,'FAMILY','CORRECTION',-10,-10,reviewer_user_id,CURRENT_TIMESTAMP,id from growth_point_ledger where id=?", id(),original);
        reward(b, classroom, "ORGANIZATION", 20);
        reward(a, null, "FAMILY", 30);
        reward(c, otherClass, "TEACHER", 30);
        assertThat(mapper.list(classroom)).extracting(AnonymousRankMapper.Row::rank).containsExactly(1L,1L,3L);
        assertThat(mapper.list(classroom)).extracting(AnonymousRankMapper.Row::points).containsExactly(20L,20L,0L);
    }
    @Test void excludesFormerMembersDisabledStudentsAndDisabledClasses() {
        long a = student(classroom), b = student(classroom);
        reward(a, classroom, "TEACHER", 10);
        jdbc.update("update edu_student_organization set status='INACTIVE' where student_id=?",a);
        jdbc.update("update edu_student set status='DISABLED' where id=?",b);
        assertThat(mapper.list(classroom)).isEmpty();
        student(classroom);
        jdbc.update("update sys_organization set effective_status='DISABLED' where id=?",classroom);
        assertThat(mapper.list(classroom)).isEmpty();
    }
    @Test void exposesOnlyRankAndPointsAndNeverMixesAnotherClass() {
        student(classroom);
        reward(student(otherClass), otherClass, "TEACHER", 30);
        assertThat(mapper.list(classroom)).containsExactly(new AnonymousRankMapper.Row(1L,0L));
        assertThat(AnonymousRankMapper.Row.class.getRecordComponents()).extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("rank", "points");
    }
    @Test void classOptionsExcludeOtherChildrenFormerAndDisabledClasses() {
        long child = student(classroom);
        student(otherClass);
        assertThat(mapper.classes(child)).extracting(AnonymousRankMapper.ClassOption::classId)
                .containsExactly(String.valueOf(classroom));
        jdbc.update("update sys_organization set effective_status='DISABLED' where id=?",classroom);
        sqlSession.clearCache();
        assertThat(mapper.classes(child)).isEmpty();
        jdbc.update("update sys_organization set effective_status='ENABLED' where id=?",classroom);
        jdbc.update("update edu_student_organization set status='INACTIVE' where student_id=?",child);
        sqlSession.clearCache();
        assertThat(mapper.classes(child)).isEmpty();
    }
    private long organization() {
        long value = id();
        jdbc.update("insert into sys_organization(id,organization_code,organization_name,organization_type,organization_path,status) values(?,?,?,'CLASS',?,'ENABLED')",value,"RANK_"+value,"测试班级"+value,"/"+value+"/");
        return value;
    }
    private long student(long group) {
        long value = id();
        jdbc.update("insert into edu_student(id,student_name,status) values(?,'不应输出的姓名','ENABLED')",value);
        jdbc.update("insert into edu_student_organization(id,student_id,organization_id,relation_type,status) values(?,?,?,'CLASS','ACTIVE')",id(),value,group);
        jdbc.update("insert into growth_point_account(id,student_id,total_points,available_points) values(?,?,900,900)",value,value);
        return value;
    }
    private long reward(long student, Long group, String source, int points) {
        long task=id(), assignment=id(), ledger=id();
        jdbc.update("insert into learn_task(id,source_type,source_organization_id,creator_user_id,title,difficulty_level,base_points,duration_minutes,scheduled_date,reviewer_user_id) values(?,?,?,?,'测试',1,10,10,CURRENT_DATE,?)",task,source,group,reviewer,reviewer);
        jdbc.update("insert into learn_task_assignment(id,task_id,student_id,source_type,source_organization_id,current_reviewer_id,scheduled_date,due_at) values(?,?,?,?,?,?,CURRENT_DATE,CURRENT_TIMESTAMP)",assignment,task,student,source,group,reviewer);
        jdbc.update("insert into growth_point_ledger(id,account_id,student_id,source_assignment_id,source_task_id,source_type,source_organization_id,change_type,amount,available_delta,reviewer_user_id,occurred_at,base_points_snapshot,decay_percent,streak_days) values(?,?,?,?,?,?,?,'TASK_REWARD',?,?,?,CURRENT_TIMESTAMP,30,0,1)",ledger,student,student,assignment,task,source,group,points,points,reviewer);
        return ledger;
    }
}
