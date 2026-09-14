package com.lingdong.learning.growthpoint.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthReviewMapper;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.student.infrastructure.persistence.ParentStudentMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 准备已授权的不可变报告内容；调用方仍须检查导出开关、创建权限并持久化作业。 */
@Service
public class GrowthReviewExportPreparationService {
    private final GrowthReviewMapper mapper;
    private final GrowthReviewQueryService queries;
    private final ParentStudentMapper relationships;
    private final PermissionDecisionService permissions;
    private final int maxReports;

    public GrowthReviewExportPreparationService(GrowthReviewMapper mapper, GrowthReviewQueryService queries,
            ParentStudentMapper relationships, PermissionDecisionService permissions,
            @Value("${lingdong.growth-review-export.max-reports:100}") int maxReports) {
        if (maxReports < 1 || maxReports > 1000) throw new IllegalArgumentException("复盘导出批次保护上限必须为1至1000");
        this.mapper = mapper;
        this.queries = queries;
        this.relationships = relationships;
        this.permissions = permissions;
        this.maxReports = maxReports;
    }

    public record Prepared(Long requesterId, Long studentId, List<GrowthReviewDetailView> reports) {
        public Prepared { reports = List.copyOf(reports); }
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Prepared prepare(AuthenticatedUser user, GrowthReviewExportSelection selection) {
        if (selection == null) throw new IllegalArgumentException("复盘选取条件不能为空");
        requireRead(user, selection.studentId());
        if (selection.reviewId() != null) {
            return new Prepared(user.userId(), selection.studentId(),
                    List.of(queries.findChildReview(user, selection.studentId(), selection.reviewId())));
        }
        var candidates = mapper.findExportCandidates(user.userId(), selection.studentId(), selection.periodType(),
                selection.dateFrom(), selection.dateTo(), maxReports + 1);
        if (candidates.isEmpty()) throw new IllegalArgumentException("所选区间没有完整复盘报告");
        if (candidates.size() > maxReports) throw new IllegalArgumentException("报告数量超过批次保护上限，请缩小日期区间");
        var reports = new ArrayList<GrowthReviewDetailView>(candidates.size());
        for (var candidate : candidates) {
            var report = queries.findChildReview(user, selection.studentId(), candidate.reviewId());
            // 外层事务隔离配置异常时也不能静默返回跨版本拼接的报告。
            if (!Objects.equals(report.snapshotId(), candidate.snapshotId())
                    || report.contentVersion() != candidate.contentVersion()) {
                throw new IllegalStateException("复盘版本已变化，请重新发起导出");
            }
            reports.add(report);
        }
        return new Prepared(user.userId(), selection.studentId(), reports);
    }

    private void requireRead(AuthenticatedUser user, Long studentId) {
        if (user == null || user.clientType() != AuthClientType.WEB || !user.roleCodes().contains("PARENT")
                || user.roleCodes().contains("SYS_AUDITOR")
                || !permissions.isAllowed(user.userId(), PermissionClient.WEB, "GROWTH_REVIEW_READ_CHILD")) {
            throw new SystemOperationAccessDeniedException("当前账号无权准备孩子复盘导出");
        }
        if (!relationships.existsActiveByParentAndStudent(user.userId(), studentId)) {
            throw new ResourceNotFoundException("复盘对象不存在或不可访问");
        }
    }
}
