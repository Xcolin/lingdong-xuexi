package com.lingdong.learning.student.application;

import com.lingdong.learning.auth.infrastructure.security.StudentLoginCodeHasher;
import com.lingdong.learning.student.domain.StudentCredential;
import com.lingdong.learning.student.infrastructure.persistence.StudentCredentialMapper;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserType;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StudentIdentityProvisioningServiceTest {
    @Autowired private StudentIdentityProvisioningService provisioningService;
    @Autowired private UserMapper userMapper;
    @Autowired private UserRoleMapper userRoleMapper;
    @Autowired private StudentCredentialMapper credentialMapper;
    @Autowired private StudentLoginCodeHasher loginCodeHasher;

    @Test
    void issuesStudentUserRoleAndProtectedCredentialInOneTransaction() {
        IssuedStudentCredential issued = provisioningService.issue("无机构学生");

        User user = userMapper.findById(issued.studentUserId());
        StudentCredential credential = credentialMapper.findByStudentUserId(issued.studentUserId());
        assertThat(issued.studentAccount()).matches("\\d{8}");
        assertThat(issued.plainLoginCode()).matches("\\d{4}");
        assertThat(user.username()).isEqualTo(issued.studentAccount());
        assertThat(user.displayName()).isEqualTo("无机构学生");
        assertThat(user.type()).isEqualTo(UserType.STUDENT);
        assertThat(user.passwordHash()).isNull();
        assertThat(userRoleMapper.hasRoleCode(user.id(), "STUDENT")).isTrue();
        assertThat(credential.codeHash()).doesNotContain(issued.plainLoginCode());
        assertThat(loginCodeHasher.matches(
                issued.plainLoginCode(), credential.codeHash(), credential.codeSalt(), credential.keyVersion()
        )).isTrue();
        assertThat(credential.id()).isBetween(1_000_000_000_000_000_000L, Long.MAX_VALUE);
    }
}
