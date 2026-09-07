package com.lingdong.learning.auth.application;

import com.lingdong.learning.auth.infrastructure.persistence.StudentWechatBindingMapper;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.student.domain.ParentRelationship;
import com.lingdong.learning.student.infrastructure.persistence.ParentStudentMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** 当前主监护人查询并二次确认解绑学生微信。 */
@Service
public class StudentWechatBindingManagementService {
    private static final String FEATURE_CODE = "STUDENT_WECHAT_AUTH";
    private static final String CONFIRMATION = "确认解绑学生微信";

    private final StudentWechatBindingMapper bindingMapper;
    private final ParentStudentMapper relationshipMapper;
    private final FeatureAccessService featureAccessService;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public StudentWechatBindingManagementService(
            StudentWechatBindingMapper bindingMapper,
            ParentStudentMapper relationshipMapper,
            FeatureAccessService featureAccessService,
            IdGenerator idGenerator,
            Clock clock
    ) {
        this.bindingMapper = bindingMapper;
        this.relationshipMapper = relationshipMapper;
        this.featureAccessService = featureAccessService;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    public List<StudentWechatBindingSummary> list(AuthenticatedUser currentUser) {
        requireParent(currentUser);
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        return bindingMapper.findPrimaryStudentsByParentUserId(currentUser.userId()).stream()
                .map(row -> new StudentWechatBindingSummary(
                        row.studentId(), row.studentName(), maskAccount(row.studentAccount()),
                        row.bindingId() != null, row.boundAt()))
                .toList();
    }

    @Transactional
    public void unbind(AuthenticatedUser currentUser, Long studentId, String confirmation) {
        requireParent(currentUser);
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        if (!CONFIRMATION.equals(confirmation)) {
            throw new IllegalArgumentException("请输入正确的学生微信解绑确认语句");
        }
        ParentRelationship primary = relationshipMapper.findActivePrimaryByStudentIdForUpdate(studentId);
        if (primary == null || !currentUser.userId().equals(primary.parentUserId())) {
            throw new StudentWechatBindingUnavailableException();
        }
        StudentWechatBinding binding = bindingMapper.findByStudentIdForUpdate(studentId);
        if (binding == null) {
            throw new StudentWechatBindingUnavailableException();
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (bindingMapper.deleteById(binding.id()) != 1) {
            throw new StudentWechatBindingUnavailableException();
        }
        StudentWechatBindingAudit audit = new StudentWechatBindingAudit(
                idGenerator.nextId(), binding.id(), binding.studentId(), binding.studentUserId(),
                StudentWechatBindingAuditEvent.UNBIND, currentUser.userId(), currentUser.clientType(), now);
        if (bindingMapper.insertAudit(audit) != 1) {
            throw new IllegalStateException("学生微信解绑审计保存失败");
        }
    }

    private void requireParent(AuthenticatedUser currentUser) {
        if (currentUser == null || !currentUser.roleCodes().contains("PARENT")) {
            throw new StudentWechatBindingUnavailableException();
        }
    }

    private String maskAccount(String account) {
        if (account == null || account.length() != 8) {
            return "********";
        }
        return account.substring(0, 2) + "****" + account.substring(6);
    }
}
