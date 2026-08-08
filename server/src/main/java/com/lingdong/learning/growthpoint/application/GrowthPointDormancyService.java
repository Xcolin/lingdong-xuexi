package com.lingdong.learning.growthpoint.application;

import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.growthpoint.domain.GrowthPointAccount;
import com.lingdong.learning.growthpoint.domain.GrowthPointLedger;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthPointAccountMapper;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthPointDormancyStateRow;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthPointLedgerMapper;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthPointLifecycleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** 对单个学生重新核验有效活跃后，幂等生成提醒并清空可用积分。 */
@Service
public class GrowthPointDormancyService {
    private final GrowthPointLifecycleMapper lifecycleMapper;
    private final GrowthPointAccountMapper accountMapper;
    private final GrowthPointLedgerMapper ledgerMapper;
    private final IdGenerator idGenerator;

    public GrowthPointDormancyService(
            GrowthPointLifecycleMapper lifecycleMapper,
            GrowthPointAccountMapper accountMapper,
            GrowthPointLedgerMapper ledgerMapper,
            IdGenerator idGenerator
    ) {
        this.lifecycleMapper = lifecycleMapper;
        this.accountMapper = accountMapper;
        this.ledgerMapper = ledgerMapper;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public GrowthPointDormancyResult processStudent(Long studentId, LocalDateTime now) {
        GrowthPointDormancyStateRow state = lifecycleMapper.findDormancyStateForUpdate(studentId);
        if (state == null) {
            requireSingleWrite(lifecycleMapper.insertDormancyState(studentId));
            return new GrowthPointDormancyResult(false, false, 0L, false);
        }

        LocalDateTime latestActivityAt = lifecycleMapper.findLatestEffectiveActivityAt(studentId);
        if (latestActivityAt != null && latestActivityAt.isAfter(state.lastActivityAt())) {
            requireSingleWrite(lifecycleMapper.resetDormancyCycle(
                    studentId, latestActivityAt, latestActivityAt.plusDays(27),
                    latestActivityAt.plusDays(30), now, state.versionNo()));
            return new GrowthPointDormancyResult(false, false, 0L, true);
        }

        boolean reminderCreated = false;
        int currentVersion = state.versionNo();
        Long noticeId = lifecycleMapper.findDormancyNoticeId(studentId, state.lastActivityAt());
        if (!now.isBefore(state.reminderDueAt()) && noticeId == null) {
            noticeId = idGenerator.nextId();
            Long primaryParentUserId = lifecycleMapper.findPrimaryParentUserId(studentId);
            String deliveryStatus = primaryParentUserId == null ? "NO_RECIPIENT" : "PENDING";
            requireSingleWrite(lifecycleMapper.insertDormancyNotice(
                    noticeId, studentId, primaryParentUserId, state.lastActivityAt(),
                    state.clearDueAt(), deliveryStatus, now));
            if (state.lastReminderCreatedAt() == null) {
                requireSingleWrite(lifecycleMapper.markDormancyReminderCreated(
                        studentId, now, currentVersion));
                currentVersion++;
            }
            reminderCreated = true;
        }

        if (now.isBefore(state.clearDueAt()) || state.lastClearedAt() != null) {
            return new GrowthPointDormancyResult(reminderCreated, false, 0L, false);
        }
        if (noticeId == null) {
            throw new IllegalStateException("沉睡清零缺少对应提醒周期记录");
        }

        GrowthPointAccount account = accountMapper.findByStudentIdForUpdate(studentId);
        if (account == null) {
            throw new IllegalStateException("学生积分账户不可用");
        }
        long clearedPoints = account.availablePoints();
        if (clearedPoints > 0) {
            requireSingleWrite(accountMapper.clearAvailablePoints(
                    account.id(), clearedPoints, account.versionNo(), now));
            requireSingleWrite(ledgerMapper.insert(GrowthPointLedger.dormancyClear(
                    idGenerator.nextId(), account.id(), studentId, noticeId, clearedPoints, now)));
        }
        requireSingleWrite(lifecycleMapper.markDormancyCleared(studentId, now, currentVersion));
        return new GrowthPointDormancyResult(reminderCreated, true, clearedPoints, false);
    }

    private void requireSingleWrite(int affectedRows) {
        if (affectedRows != 1) {
            throw new IllegalStateException("积分生命周期状态已变化，请重试");
        }
    }
}
