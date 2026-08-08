package com.lingdong.learning.growthpoint.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.growthpoint.domain.GrowthReward;
import com.lingdong.learning.growthpoint.domain.GrowthRewardStatus;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthRewardMapper;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthPointAccountViewRow;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthPointQueryMapper;
import com.lingdong.learning.learningtask.application.CurrentStudentAccessService;
import com.lingdong.learning.student.domain.Student;
import com.lingdong.learning.student.infrastructure.persistence.ParentStudentMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** 家庭奖励配置服务，集中执行功能开关、端侧和主家长关系校验。 */
@Service
public class GrowthRewardService {
    private static final String FEATURE_CODE = "REWARD_EXCHANGE";

    private final GrowthRewardMapper rewardMapper;
    private final GrowthPointQueryMapper growthPointQueryMapper;
    private final ParentStudentMapper parentStudentMapper;
    private final CurrentStudentAccessService currentStudentAccessService;
    private final FeatureAccessService featureAccessService;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public GrowthRewardService(
            GrowthRewardMapper rewardMapper,
            GrowthPointQueryMapper growthPointQueryMapper,
            ParentStudentMapper parentStudentMapper,
            CurrentStudentAccessService currentStudentAccessService,
            FeatureAccessService featureAccessService,
            IdGenerator idGenerator,
            Clock clock
    ) {
        this.rewardMapper = rewardMapper;
        this.growthPointQueryMapper = growthPointQueryMapper;
        this.parentStudentMapper = parentStudentMapper;
        this.currentStudentAccessService = currentStudentAccessService;
        this.featureAccessService = featureAccessService;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public GrowthRewardPage findManaged(
            AuthenticatedUser currentUser, Long studentId, int page, int pageSize
    ) {
        requireAccessibleChild(currentUser, studentId);
        PageRequest pagination = requirePage(page, pageSize);
        return new GrowthRewardPage(
                rewardMapper.findManagedByStudentId(
                        studentId, pagination.offset(), pagination.pageSize()),
                pagination.page(), pagination.pageSize(),
                rewardMapper.countManagedByStudentId(studentId));
    }

    @Transactional(readOnly = true)
    public GrowthRewardPage findMine(
            AuthenticatedUser currentUser, int page, int pageSize
    ) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        Student student = requireMiniappStudent(currentUser);
        PageRequest pagination = requirePage(page, pageSize);
        LocalDateTime now = LocalDateTime.now(clock);
        return new GrowthRewardPage(
                rewardMapper.findAvailableByStudentId(
                        student.id(), now, pagination.offset(), pagination.pageSize()),
                pagination.page(), pagination.pageSize(),
                rewardMapper.countAvailableByStudentId(student.id(), now));
    }

    @Transactional(readOnly = true)
    public GrowthRewardAccountSummary findMyAccountSummary(AuthenticatedUser currentUser) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        Student student = requireMiniappStudent(currentUser);
        GrowthPointAccountViewRow row = growthPointQueryMapper.findAccountByStudentId(student.id());
        if (row == null) {
            throw new ResourceNotFoundException("学生奖励积分账户不存在或不可访问");
        }
        return new GrowthRewardAccountSummary(row.studentId(), row.availablePoints(), row.updatedAt());
    }

    @Transactional
    public GrowthReward create(
            AuthenticatedUser currentUser, Long studentId, SaveGrowthRewardCommand command
    ) {
        requireAccessibleChild(currentUser, studentId);
        NormalizedReward normalized = normalize(command);
        LocalDateTime now = LocalDateTime.now(clock);
        GrowthReward reward = new GrowthReward(
                idGenerator.nextId(), studentId, currentUser.userId(), normalized.rewardName(),
                normalized.requiredPoints(), normalized.description(), normalized.expiresAt(),
                normalized.status(), 0, now, now, null);
        if (rewardMapper.insert(reward) != 1) {
            throw new IllegalStateException("家庭奖励新增失败");
        }
        return reward;
    }

    @Transactional
    public GrowthReward update(
            AuthenticatedUser currentUser, Long rewardId, SaveGrowthRewardCommand command
    ) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        GrowthReward existing = requireMutableReward(rewardId);
        requireAccessibleChild(currentUser, existing.studentId());
        NormalizedReward normalized = normalize(command);
        LocalDateTime now = LocalDateTime.now(clock);
        GrowthReward updated = new GrowthReward(
                existing.id(), existing.studentId(), existing.createdByParentId(), normalized.rewardName(),
                normalized.requiredPoints(), normalized.description(), normalized.expiresAt(),
                normalized.status(), existing.versionNo() + 1, existing.createdAt(), now, null);
        if (rewardMapper.update(updated, existing.versionNo()) != 1) {
            throw new IllegalStateException("家庭奖励已被其他操作修改");
        }
        return updated;
    }

    @Transactional
    public void delete(AuthenticatedUser currentUser, Long rewardId) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        GrowthReward existing = requireMutableReward(rewardId);
        requireAccessibleChild(currentUser, existing.studentId());
        if (rewardMapper.softDelete(
                existing.id(), existing.versionNo(), LocalDateTime.now(clock)) != 1) {
            throw new IllegalStateException("家庭奖励已被其他操作修改");
        }
    }

    private GrowthReward requireMutableReward(Long rewardId) {
        if (rewardId == null) {
            throw notFound();
        }
        GrowthReward reward = rewardMapper.findByIdForUpdate(rewardId);
        if (reward == null || reward.status() == GrowthRewardStatus.DELETED) {
            throw notFound();
        }
        return reward;
    }

    private void requireAccessibleChild(AuthenticatedUser currentUser, Long studentId) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        requireWebParent(currentUser);
        if (studentId == null || !parentStudentMapper.existsActivePrimaryByParentAndStudent(
                currentUser.userId(), studentId)) {
            throw notFound();
        }
    }

    private Student requireMiniappStudent(AuthenticatedUser currentUser) {
        if (currentUser == null || currentUser.clientType() != AuthClientType.MINIAPP) {
            throw new SystemOperationAccessDeniedException("仅小程序学生可查看本人家庭奖励");
        }
        return currentStudentAccessService.require(currentUser);
    }

    private void requireWebParent(AuthenticatedUser currentUser) {
        if (currentUser == null || currentUser.clientType() != AuthClientType.WEB
                || !currentUser.roleCodes().contains("PARENT")) {
            throw new SystemOperationAccessDeniedException("仅 Web 端主家长可管理家庭奖励");
        }
    }

    private NormalizedReward normalize(SaveGrowthRewardCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("奖励内容不能为空");
        }
        String rewardName = normalizeRequired(command.rewardName(), 30, "奖励名称");
        if (command.requiredPoints() == null || command.requiredPoints() <= 0) {
            throw new IllegalArgumentException("所需积分必须大于零");
        }
        String description = normalizeOptional(command.description(), 200, "奖励说明");
        LocalDateTime now = LocalDateTime.now(clock);
        if (command.expiresAt() != null && !command.expiresAt().isAfter(now)) {
            throw new IllegalArgumentException("有效期必须晚于当前时间");
        }
        if (command.status() != GrowthRewardStatus.ONLINE
                && command.status() != GrowthRewardStatus.OFFLINE) {
            throw new IllegalArgumentException("奖励状态不合法");
        }
        return new NormalizedReward(
                rewardName, command.requiredPoints(), description, command.expiresAt(), command.status());
    }

    private String normalizeRequired(String value, int maximumLength, String fieldName) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty() || normalized.length() > maximumLength) {
            throw new IllegalArgumentException(fieldName + "不合法");
        }
        return normalized;
    }

    private String normalizeOptional(String value, int maximumLength, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException(fieldName + "不合法");
        }
        return normalized;
    }

    private ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("家庭奖励不存在或不可访问");
    }

    private PageRequest requirePage(int page, int pageSize) {
        if (page < 1 || page > 1_000_000) {
            throw new IllegalArgumentException("页码不合法");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("每页数量不合法");
        }
        return new PageRequest(page, pageSize, Math.multiplyExact(page - 1, pageSize));
    }

    private record PageRequest(int page, int pageSize, int offset) {
    }

    private record NormalizedReward(
            String rewardName,
            Long requiredPoints,
            String description,
            LocalDateTime expiresAt,
            GrowthRewardStatus status
    ) {
    }
}
