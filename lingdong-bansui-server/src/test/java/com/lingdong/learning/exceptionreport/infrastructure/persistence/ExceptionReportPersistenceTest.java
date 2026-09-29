package com.lingdong.learning.exceptionreport.infrastructure.persistence;

import com.lingdong.learning.exceptionreport.domain.ExceptionReportStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证异常报备 MyBatis 查询在数据库层落实教师本人和班级双重范围。 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ExceptionReportPersistenceTest {
    @Autowired private ExceptionReportMapper mapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void teacherQueryReturnsOnlyOwnReportsInsideActiveClasses() {
        long schoolId = 8910000000000001101L;
        long classId = 8910000000000001102L;
        long teacherId = 8910000000000001103L;
        long otherTeacherId = 8910000000000001104L;
        long studentUserId = 8910000000000001105L;
        long studentId = 8910000000000001106L;
        long ownReportId = 8910000000000001107L;

        insertUser(teacherId, "exception_teacher_1103", "报备教师");
        insertUser(otherTeacherId, "exception_teacher_1104", "同班教师");
        insertUser(studentUserId, "26010116", "测试学生账号");
        jdbcTemplate.update("""
                insert into sys_organization (
                    id, organization_code, organization_name, organization_type,
                    organization_path, sort_order, status
                ) values (?, 'EXCEPTION_SCHOOL_1101', '异常报备学校', 'SCHOOL',
                    '/EXCEPTION_SCHOOL_1101/', 1, 'ENABLED')
                """, schoolId);
        jdbcTemplate.update("""
                insert into sys_organization (
                    id, parent_id, organization_code, organization_name, organization_type,
                    organization_path, sort_order, status
                ) values (?, ?, 'EXCEPTION_CLASS_1102', '异常报备班级', 'CLASS',
                    '/EXCEPTION_SCHOOL_1101/EXCEPTION_CLASS_1102/', 1, 'ENABLED')
                """, classId, schoolId);
        jdbcTemplate.update("""
                insert into edu_student (id, student_name, student_user_id, status)
                values (?, '测试学生', ?, 'ENABLED')
                """, studentId, studentUserId);
        jdbcTemplate.update("""
                insert into edu_student_organization (
                    id, student_id, organization_id, relation_type, status
                ) values (?, ?, ?, 'CLASS', 'ACTIVE')
                """, 8910000000000001108L, studentId, classId);
        insertTeacherClass(8910000000000001109L, teacherId, classId);
        insertTeacherClass(8910000000000001110L, otherTeacherId, classId);
        insertReport(ownReportId, studentId, classId, teacherId, "own-report-1107");
        insertReport(8910000000000001111L, studentId, classId, otherTeacherId, "other-report-1111");

        ExceptionReportQuery query = new ExceptionReportQuery(teacherId, true, false, List.of(),
                null, null, null, null, 20, 0);

        assertThat(mapper.findPage(query)).extracting(ExceptionReportRow::id)
                .containsExactly(ownReportId);
        assertThat(mapper.count(query)).isEqualTo(1);
    }

    private void insertUser(long id, String username, String displayName) {
        jdbcTemplate.update("""
                insert into sys_user (id, username, display_name, user_type, status)
                values (?, ?, ?, 'ORGANIZATION', 'ENABLED')
                """, id, username, displayName);
    }

    private void insertTeacherClass(long id, long teacherId, long classId) {
        jdbcTemplate.update("""
                insert into edu_teacher_class (id, teacher_user_id, class_organization_id, status)
                values (?, ?, ?, 'ACTIVE')
                """, id, teacherId, classId);
    }

    private void insertReport(long id, long studentId, long classId, long reporterId, String key) {
        jdbcTemplate.update("""
                insert into edu_exception_report (
                    id, student_id, class_organization_id, reporter_user_id, exception_type,
                    content, status, idempotency_key, version_no, reported_at
                ) values (?, ?, ?, ?, 'LEARNING_STATUS', '课堂注意力明显下降',
                    'SUBMITTED', ?, 0, CURRENT_TIMESTAMP)
                """, id, studentId, classId, reporterId, key);
    }
}
