package com.lingdong.learning.learningtask.infrastructure.persistence;

import com.lingdong.learning.student.application.StudentDirectoryQuery;
import com.lingdong.learning.student.infrastructure.persistence.StudentMapper;
import com.lingdong.learning.student.infrastructure.persistence.StudentOrganizationMapper;
import com.lingdong.learning.datascope.application.OrganizationDataScope;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SchoolGradeClassQueryPersistenceTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired TeacherClassMapper teachers;
    @Autowired LearningTaskOptionMapper options;
    @Autowired StudentMapper students;
    @Autowired StudentOrganizationMapper studentOrganizations;
    @Autowired org.apache.ibatis.session.SqlSession sqlSession;

    @Test
    void teacherCandidatesIncludeUnassignedSameSchoolAndExcludeOutsideSchoolEvenWithAllScope() {
        organization(95001, null, "SCHOOL", "/95001/");
        organization(95002, 95001L, "GRADE", "/95001/95002/");
        organization(95003, 95002L, "CLASS", "/95001/95002/95003/");
        organization(96001, null, "SCHOOL", "/96001/");
        for (long id : List.of(97001L, 97002L, 97003L)) {
            jdbc.update("insert into sys_user(id,username,display_name,user_type,status) values(?,?,?,'ORGANIZATION','ENABLED')", id, "candidate_" + id, "Teacher " + id);
            jdbc.update("insert into sys_user_role(id,user_id,role_id) select ?,?,id from sys_role where role_code='TEACHER'", id, id);
            jdbc.update("insert into sys_user_organization(id,user_id,organization_id) values(?,?,?)", id, id, id == 97003 ? 96001 : 95001);
            // 候选教师查询要求启用中的职业档案（LearningTaskOptionMapper.findScopedTeacherOptions）。
            jdbc.update("insert into edu_teacher_profile(user_id,status) values(?,'ENABLED')", id);
        }
        jdbc.update("insert into edu_teacher_class(id,teacher_user_id,class_organization_id,status) values(97002,97002,95003,'ACTIVE')");
        var rows = options.findScopedTeacherOptions(List.of(95001L,95002L,95003L,96001L),95003L,null);
        assertThat(rows).extracting(TeacherOptionRow::userId).containsExactly(97001L,97002L);
        assertThat(rows).filteredOn(row -> row.userId().equals(97001L)).singleElement()
                .satisfies(row -> assertThat(row.classOrganizationId()).isNull());
        assertThat(rows).filteredOn(row -> row.userId().equals(97002L)).singleElement()
                .satisfies(row -> assertThat(row.classOrganizationId()).isEqualTo(95003L));
        jdbc.update("insert into sys_organization_admin(id,user_id,organization_id) values(98001,97001,95001)");
        jdbc.update("insert into sys_organization_admin(id,user_id,organization_id) values(98002,97001,96001)");
        assertThat(options.findTeacherOptionsForAdministrator(97001L,95003L,null))
                .extracting(TeacherOptionRow::userId).containsExactly(97001L,97002L);
        assertThat(options.findScopedTeacherOptions(List.of(95001L,95002L),95003L,null))
                .allSatisfy(row -> assertThat(row.classOrganizationId()).isNull());
        organization(95004, 95001L, "GRADE", "/95001/95004/");
        jdbc.update("update sys_user_organization set organization_id=95004 where user_id=97001");
        sqlSession.clearCache();
        assertThat(options.findScopedTeacherOptions(List.of(95001L,95002L,95003L,95004L,96001L),95003L,null))
                .extracting(TeacherOptionRow::userId).containsExactly(97002L);
        jdbc.update("update sys_user_organization set organization_id=95001 where user_id=97001");
        jdbc.update("update sys_organization set effective_status='DISABLED' where id=95001");
        sqlSession.clearCache();
        assertThat(options.findScopedTeacherOptions(List.of(95001L,95002L,95003L,96001L),95003L,null)).isEmpty();
    }

    @Test
    void gradedAndLegacyClassesResolveSchoolAndKeepStudentScopeAcrossTwoSchools() {
        organization(91001, null, "SCHOOL", "/91001/");
        organization(91002, 91001L, "GRADE", "/91001/91002/");
        organization(91003, 91002L, "CLASS", "/91001/91002/91003/");
        organization(91004, 91001L, "CLASS", "/91001/91004/");
        organization(92001, null, "SCHOOL", "/92001/");
        organization(92002, 92001L, "GRADE", "/92001/92002/");
        organization(92003, 92002L, "CLASS", "/92001/92002/92003/");
        jdbc.update("insert into sys_user(id, username, display_name, user_type, status) values(93001, 'graded_query_teacher', 'Teacher', 'TEACHER', 'ENABLED')");
        for (long classId : List.of(91003L, 91004L, 92003L)) {
            jdbc.update("insert into edu_teacher_class(id, teacher_user_id, class_organization_id, status) values(?, 93001, ?, 'ACTIVE')", classId, classId);
        }
        var summaries = teachers.findActiveClassSummaries(93001L);
        assertThat(summaries).filteredOn(row -> row.classId().equals(91003L)).singleElement()
                .satisfies(row -> assertThat(row.schoolId()).isEqualTo(91001L));
        assertThat(summaries).filteredOn(row -> row.classId().equals(91004L)).singleElement()
                .satisfies(row -> assertThat(row.schoolId()).isEqualTo(91001L));
        assertThat(summaries).filteredOn(row -> row.classId().equals(92003L)).singleElement()
                .satisfies(row -> assertThat(row.schoolId()).isEqualTo(92001L));
        student(94001, 91003);
        student(94002, 92003);
        var schoolScope = new OrganizationDataScope(false, false, List.of("/91001/"));
        assertThat(studentOrganizations.findClassStudentOptions(91003L, schoolScope, true))
                .singleElement().satisfies(row -> {
                    assertThat(row.id()).isEqualTo(94001L);
                    assertThat(row.studentName()).isEqualTo("Student 94001");
                    assertThat(row.currentClassId()).isEqualTo(91003L);
                    assertThat(row.currentClassName()).isEqualTo("Node 91003");
                    assertThat(row.studentAccountMasked()).isNull();
                    assertThat(row.relationshipRole()).isNull();
                });
        assertThat(studentOrganizations.findClassStudentOptions(91003L, schoolScope, false))
                .extracting(row -> row.id()).containsExactly(94001L);
        assertThat(studentOrganizations.findClassStudentOptions(92003L, schoolScope, false)).isEmpty();
        jdbc.update("update edu_student_organization set organization_id=92003 where student_id=94001 and relation_type='CLASS'");
        sqlSession.clearCache();
        assertThat(studentOrganizations.findClassStudentOptions(91003L, schoolScope, true)).isEmpty();
        assertThat(studentOrganizations.findClassStudentOptions(91003L, schoolScope, false))
                .singleElement().satisfies(row -> {
                    assertThat(row.currentClassId()).isNull();
                    assertThat(row.currentClassName()).isNull();
                });
        jdbc.update("update edu_student_organization set organization_id=91003 where student_id=94001 and relation_type='CLASS'");
        sqlSession.clearCache();
        assertThat(options.findScopedStudentOptions(List.of(91003L), 91001L, null))
                .extracting(StudentOptionRow::id).containsExactly(94001L);
        assertThat(options.findScopedStudentOptions(List.of(91003L), 92001L, null)).isEmpty();
        var query = new StudentDirectoryQuery(null, 93001L, false, false, true, 0, 20,
                true, false, List.of("/91001/"));
        assertThat(students.findPage(query)).extracting(row -> row.id()).containsExactly(94001L);
        assertThat(students.count(query)).isEqualTo(1);
        jdbc.update("update sys_organization set effective_status='DISABLED' where id=91003");
        sqlSession.clearCache();
        assertThat(teachers.findActiveClassSummaries(93001L)).extracting(row -> row.classId())
                .containsExactlyInAnyOrder(91004L, 92003L);
    }

    private void organization(long id, Long parent, String type, String path) {
        jdbc.update("insert into sys_organization(id,parent_id,parent_scope_key,organization_code,organization_name,organization_type,organization_path,sort_order,status,effective_status,version_no) values(?,?,?,?,?,?,?,0,'ENABLED','ENABLED',1)",
                id, parent, parent == null ? "ROOT" : parent.toString(), "QUERY-" + id, "Node " + id, type, path);
    }

    private void student(long id, long classId) {
        jdbc.update("insert into edu_student(id,student_name,status) values(?,?,'ENABLED')", id, "Student " + id);
        jdbc.update("insert into edu_student_organization(id,student_id,organization_id,relation_type,status) values(?,?,?,'CLASS','ACTIVE')", id, id, classId);
        jdbc.update("insert into edu_student_organization(id,student_id,organization_id,relation_type,status) values(?,?,?,'ENROLLMENT','ACTIVE')", id + 1000, id, classId == 91003 ? 91001 : 92001);
    }
}
