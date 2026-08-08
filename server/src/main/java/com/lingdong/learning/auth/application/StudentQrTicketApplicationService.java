package com.lingdong.learning.auth.application;

import com.lingdong.learning.auth.domain.StudentQrTicket;
import com.lingdong.learning.auth.domain.StudentQrTicketStatus;
import com.lingdong.learning.auth.infrastructure.persistence.StudentQrTicketMapper;
import com.lingdong.learning.auth.infrastructure.security.SessionTokenService;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.student.domain.Student;
import com.lingdong.learning.student.domain.StudentStatus;
import com.lingdong.learning.student.infrastructure.persistence.ParentStudentMapper;
import com.lingdong.learning.student.infrastructure.persistence.StudentMapper;
import com.lingdong.learning.student.infrastructure.persistence.StudentOrganizationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;

/** 按学生对象范围签发并以数据库行锁消费一次性扫码票据。 */
@Service
public class StudentQrTicketApplicationService {
    public static final String FEATURE_CODE = "STUDENT_QR_LOGIN";
    private static final String QR_PREFIX = "lingdong-learning://student-login?ticket=";
    private static final String PARENT_ROLE = "PARENT";
    private static final String ORGANIZATION_ADMIN_ROLE = "ORG_ADMIN";

    private final StudentMapper studentMapper;
    private final ParentStudentMapper parentStudentMapper;
    private final StudentOrganizationMapper studentOrganizationMapper;
    private final StudentQrTicketMapper ticketMapper;
    private final SessionTokenService tokenService;
    private final FeatureAccessService featureAccessService;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public StudentQrTicketApplicationService(
            StudentMapper studentMapper,
            ParentStudentMapper parentStudentMapper,
            StudentOrganizationMapper studentOrganizationMapper,
            StudentQrTicketMapper ticketMapper,
            SessionTokenService tokenService,
            FeatureAccessService featureAccessService,
            IdGenerator idGenerator,
            Clock clock
    ) {
        this.studentMapper = studentMapper;
        this.parentStudentMapper = parentStudentMapper;
        this.studentOrganizationMapper = studentOrganizationMapper;
        this.ticketMapper = ticketMapper;
        this.tokenService = tokenService;
        this.featureAccessService = featureAccessService;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Transactional
    public IssuedStudentQrTicket issue(AuthenticatedUser currentUser, Long studentId) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        Objects.requireNonNull(currentUser, "当前登录用户不能为空");
        if (studentId == null) {
            throw new IllegalArgumentException("学生标识不能为空");
        }
        Student student = studentMapper.findByIdForUpdate(studentId);
        if (student == null || !hasObjectScope(currentUser, studentId)) {
            throw new ResourceNotFoundException("学生档案不存在或无权访问");
        }
        if (student.status() != StudentStatus.ENABLED || student.studentUserId() == null) {
            throw new IllegalStateException("学生账号当前不可生成登录二维码");
        }

        String rawToken = tokenService.newToken();
        LocalDateTime expiresAt = LocalDateTime.now(clock).plusMinutes(5);
        StudentQrTicket ticket = new StudentQrTicket(
                idGenerator.nextId(), student.id(), student.studentUserId(), tokenService.hash(rawToken),
                StudentQrTicketStatus.ACTIVE, expiresAt, null, currentUser.userId(), null, null);
        ticketMapper.revokeActiveByStudentId(student.id());
        if (ticketMapper.insert(ticket) != 1) {
            throw new IllegalStateException("学生登录二维码生成失败");
        }
        return new IssuedStudentQrTicket(ticket.id(), QR_PREFIX + rawToken, expiresAt);
    }

    @Transactional
    public Long consume(String qrContent) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        String rawToken = parseRawToken(qrContent);
        StudentQrTicket ticket = ticketMapper.findByTokenHashForUpdate(tokenService.hash(rawToken));
        LocalDateTime now = LocalDateTime.now(clock);
        if (ticket == null || ticket.status() != StudentQrTicketStatus.ACTIVE
                || !ticket.expiresAt().isAfter(now)) {
            throw new StudentQrTicketInvalidException();
        }
        if (ticketMapper.markConsumed(ticket.id(), now) != 1) {
            throw new StudentQrTicketInvalidException();
        }
        return ticket.studentUserId();
    }

    /** 为已要求图形验证码的扫码流程解析仍有效的票据，但不提前消费。 */
    public Long resolveActiveStudentUserId(String qrContent) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        String rawToken = parseRawToken(qrContent);
        StudentQrTicket ticket = ticketMapper.findByTokenHash(tokenService.hash(rawToken));
        LocalDateTime now = LocalDateTime.now(clock);
        if (ticket == null || ticket.status() != StudentQrTicketStatus.ACTIVE
                || !ticket.expiresAt().isAfter(now)) {
            throw new StudentQrTicketInvalidException();
        }
        return ticket.studentUserId();
    }

    private String parseRawToken(String qrContent) {
        if (qrContent == null || !qrContent.startsWith(QR_PREFIX)) {
            throw new StudentQrTicketInvalidException();
        }
        String rawToken = qrContent.substring(QR_PREFIX.length());
        if (rawToken.length() < 40 || rawToken.length() > 100) {
            throw new StudentQrTicketInvalidException();
        }
        return rawToken;
    }

    private boolean hasObjectScope(AuthenticatedUser currentUser, Long studentId) {
        boolean primaryParent = currentUser.roleCodes().contains(PARENT_ROLE)
                && parentStudentMapper.existsActivePrimaryByParentAndStudent(currentUser.userId(), studentId);
        boolean directOrganizationAdministrator = currentUser.roleCodes().contains(ORGANIZATION_ADMIN_ROLE)
                && studentOrganizationMapper.existsActiveByOrganizationAdministratorAndStudent(
                currentUser.userId(), studentId);
        return primaryParent || directOrganizationAdministrator;
    }
}
