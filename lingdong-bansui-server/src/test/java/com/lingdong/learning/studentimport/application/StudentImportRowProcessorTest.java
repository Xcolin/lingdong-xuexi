package com.lingdong.learning.studentimport.application;

import com.lingdong.learning.student.application.CreatedStudent;
import com.lingdong.learning.student.application.StudentApplicationService;
import com.lingdong.learning.student.application.StudentClassAssignmentService;
import com.lingdong.learning.student.domain.Student;
import com.lingdong.learning.studentimport.domain.StudentImportCredentialStatus;
import com.lingdong.learning.studentimport.domain.StudentImportExecutionRecord;
import com.lingdong.learning.studentimport.domain.StudentImportExecutionStatus;
import com.lingdong.learning.studentimport.domain.StudentImportRowRecord;
import com.lingdong.learning.studentimport.domain.StudentImportRowStatus;
import com.lingdong.learning.studentimport.infrastructure.persistence.StudentImportRowMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudentImportRowProcessorTest {
    @Test
    void createsStudentBindsOptionalClassAndStoresOnlyEncryptedCredential() {
        StudentApplicationService studentService = mock(StudentApplicationService.class);
        StudentClassAssignmentService classService = mock(StudentClassAssignmentService.class);
        StudentCredentialCipher cipher = mock(StudentCredentialCipher.class);
        StudentImportRowMapper rowMapper = mock(StudentImportRowMapper.class);
        StudentImportRowProcessor processor = new StudentImportRowProcessor(
                studentService, classService, cipher, rowMapper, org.mockito.Mockito.mock(com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper.class), org.mockito.Mockito.mock(com.lingdong.learning.studentimport.application.StudentImportAccessService.class));
        Student student = Student.create(1874244142494647051L, "张同学", "G3",
                1874244142494647052L);
        when(studentService.createStudent(any(), any())).thenReturn(
                new CreatedStudent(student, "12345678", "2468"));
        when(cipher.encrypt("2468", "execution:1874244142494647053:row:2"))
                .thenReturn(new EncryptedStudentCredential(
                        new byte[] {1}, new byte[] {2}, "v1"));
        when(rowMapper.markSucceeded(any(), any(), any(), any(), any(), any())).thenReturn(1);

        processor.process(execution(), row(), new StudentImportWorkbookRow(2, "张同学", "G3"));

        verify(studentService).createStudent(any(), any());
        verify(classService).assign(any(), org.mockito.ArgumentMatchers.eq(student.id()), any());
        verify(rowMapper).markSucceeded(row().id(), student.id(), "12345678",
                new byte[] {1}, new byte[] {2}, "v1");
    }

    @Test
    void propagatesClassFailureBeforeRowCanBeMarkedSucceeded() {
        StudentApplicationService studentService = mock(StudentApplicationService.class);
        StudentClassAssignmentService classService = mock(StudentClassAssignmentService.class);
        StudentCredentialCipher cipher = mock(StudentCredentialCipher.class);
        StudentImportRowMapper rowMapper = mock(StudentImportRowMapper.class);
        StudentImportRowProcessor processor = new StudentImportRowProcessor(
                studentService, classService, cipher, rowMapper, org.mockito.Mockito.mock(com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper.class), org.mockito.Mockito.mock(com.lingdong.learning.studentimport.application.StudentImportAccessService.class));
        Student student = Student.create(1874244142494647051L, "张同学", null,
                1874244142494647052L);
        when(studentService.createStudent(any(), any())).thenReturn(
                new CreatedStudent(student, "12345678", "2468"));
        when(classService.assign(any(), any(), any())).thenThrow(new IllegalStateException("班级停用"));

        assertThatThrownBy(() -> processor.process(
                execution(), row(), new StudentImportWorkbookRow(2, "张同学", null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("班级停用");
        verify(rowMapper, org.mockito.Mockito.never()).markSucceeded(
                any(), any(), any(), any(), any(), any());
    }

    private StudentImportExecutionRecord execution() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 3, 17, 30);
        return new StudentImportExecutionRecord(
                1874244142494647053L, "SIM-1", 1L, 1874244142494647054L,
                1874244142494647055L, 1874244142494647056L,
                StudentImportExecutionStatus.RUNNING, 1L, 1, 0, 0, 0,
                null, null, null, StudentImportCredentialStatus.NONE,
                null, null, now, now, null, now, now);
    }

    private StudentImportRowRecord row() {
        return new StudentImportRowRecord(
                1874244142494647057L, 1874244142494647053L, 2,
                StudentImportRowStatus.PENDING, null, null, null, null,
                null, null, null, 0, null, null);
    }
}
