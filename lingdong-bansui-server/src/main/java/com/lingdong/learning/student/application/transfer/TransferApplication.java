package com.lingdong.learning.student.application.transfer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** Pure proposal state. Relationship mutation belongs exclusively to a later transactional executor. */
public record TransferApplication(long studentId, long sourceInstitutionId, long applicantId,
        String targetDescription, String reason, LocalDate effectiveDate, List<Long> attachmentIds,
        Status status, LocalDateTime completedAt) {
    public enum Status { PENDING, APPROVED, REJECTED, WITHDRAWN, COMPLETED }
    public TransferApplication {
        if (studentId <= 0 || sourceInstitutionId <= 0 || applicantId <= 0) throw new IllegalArgumentException("标识无效");
        targetDescription = text(targetDescription, 200);
        reason = text(reason, 200);
        Objects.requireNonNull(effectiveDate, "预计日期不能为空");
        Objects.requireNonNull(status, "状态不能为空");
        attachmentIds = List.copyOf(Objects.requireNonNull(attachmentIds));
        if (attachmentIds.size() > 3 || attachmentIds.stream().anyMatch(id -> id <= 0)
                || attachmentIds.stream().distinct().count() != attachmentIds.size()) throw new IllegalArgumentException("附件引用无效");
        if ((status == Status.COMPLETED) != (completedAt != null)) throw new IllegalArgumentException("完成时间与状态不符");
    }
    public static TransferApplication submit(long studentId, long sourceId, long applicantId,
            String target, String reason, LocalDate date, List<Long> files, LocalDate today) {
        if (date == null || date.isBefore(Objects.requireNonNull(today))) throw new IllegalArgumentException("预计日期不能早于今天");
        return new TransferApplication(studentId, sourceId, applicantId, target, reason, date, files, Status.PENDING, null);
    }
    public TransferApplication review(long reviewerId, long reviewerInstitutionId, boolean approve) {
        require(Status.PENDING);
        if (reviewerId <= 0 || reviewerId == applicantId || reviewerInstitutionId != sourceInstitutionId) throw new IllegalArgumentException("审核人或机构不合法");
        return transition(approve ? Status.APPROVED : Status.REJECTED, null);
    }
    public TransferApplication withdraw(long actorId) {
        require(Status.PENDING);
        if (actorId != applicantId) throw new IllegalArgumentException("仅申请人可撤销");
        return transition(Status.WITHDRAWN, null);
    }
    /** Call only after relationship changes have succeeded in the same database transaction. */
    public TransferApplication complete(LocalDateTime now) {
        require(Status.APPROVED);
        if (Objects.requireNonNull(now).toLocalDate().isBefore(effectiveDate)) throw new IllegalStateException("未到执行日期");
        return transition(Status.COMPLETED, now);
    }
    private void require(Status expected) { if (status != expected) throw new IllegalStateException("申请状态不允许该操作"); }
    private TransferApplication transition(Status next, LocalDateTime at) {
        return new TransferApplication(studentId, sourceInstitutionId, applicantId, targetDescription, reason, effectiveDate, attachmentIds, next, at);
    }
    private static String text(String value, int max) {
        if (value == null || value.isBlank() || value.strip().codePointCount(0, value.strip().length()) > max) throw new IllegalArgumentException("文本不能为空或超过上限");
        return value.strip();
    }
}
