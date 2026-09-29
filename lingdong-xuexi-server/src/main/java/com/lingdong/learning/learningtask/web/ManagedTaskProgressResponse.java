package com.lingdong.learning.learningtask.web;

import com.lingdong.learning.learningtask.application.ManagedTaskProgressPage;
import com.lingdong.learning.learningtask.application.ManagedTaskProgressView;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 管理者任务进度分页响应，所有标识以字符串返回避免前端精度丢失。 */
public record ManagedTaskProgressResponse(
        List<Item> items,
        int page,
        int pageSize,
        long total
) {
    static ManagedTaskProgressResponse from(ManagedTaskProgressPage page) {
        return new ManagedTaskProgressResponse(
                page.items().stream().map(Item::from).toList(),
                page.page(), page.pageSize(), page.total());
    }

    public record Item(
            String assignmentId,
            String studentId,
            String studentName,
            String studentAccountMasked,
            String classOrganizationId,
            String className,
            String currentStatus,
            LocalDate scheduledDate,
            LocalDateTime claimedAt,
            LocalDateTime completedAt,
            LocalDateTime lastTransitionAt
    ) {
        static Item from(ManagedTaskProgressView view) {
            return new Item(
                    view.assignmentId().toString(), view.studentId().toString(),
                    view.studentName(), view.studentAccountMasked(),
                    view.classOrganizationId() == null ? null : view.classOrganizationId().toString(),
                    view.className(), view.currentStatus().name(), view.scheduledDate(),
                    view.claimedAt(), view.completedAt(), view.lastTransitionAt());
        }
    }
}
