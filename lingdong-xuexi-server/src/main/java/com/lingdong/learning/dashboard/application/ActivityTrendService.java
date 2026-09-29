package com.lingdong.learning.dashboard.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.dashboard.infrastructure.persistence.ActivityTrendMapper;
import com.lingdong.learning.dashboard.infrastructure.persistence.ActivityTrendRow;
import com.lingdong.learning.datascope.application.OrganizationDataScopeService;
import com.lingdong.learning.feature.application.FeatureAccessService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/** 学员活跃度服务端聚合（R-002）：仅机构管理员按授权组织树读取，看板接线属角色看板任务。 */
@Service
public class ActivityTrendService {
    private final ActivityTrendMapper mapper;
    private final FeatureAccessService features;
    private final OrganizationDataScopeService scopes;
    private final Clock clock;

    public ActivityTrendService(ActivityTrendMapper mapper, FeatureAccessService features,
            OrganizationDataScopeService scopes, Clock clock) {
        this.mapper = mapper;
        this.features = features;
        this.scopes = scopes;
        this.clock = clock;
    }

    public record TrendPoint(LocalDate date, long activeStudents) {
        static TrendPoint from(ActivityTrendRow row) {
            return new TrendPoint(row.activityDate(), row.activeStudents());
        }
    }

    public record Trends(List<TrendPoint> items) {
    }

    /** 缺省区间为近 30 天；行为事实与授权范围口径见 docs/design/09 第 2.7 节。 */
    public Trends activityTrends(AuthenticatedUser user, LocalDate start, LocalDate end) {
        features.requireEnabled("LEARNING_TASK_MANAGEMENT", null);
        if (user == null || !user.roleCodes().contains("ORG_ADMIN")) {
            throw denied();
        }
        var scope = scopes.resolve(user.userId());
        if (!scope.allOrganizations() && scope.rootPaths().isEmpty()) {
            throw denied();
        }
        LocalDate effectiveEnd = end == null ? LocalDate.now(clock) : end;
        LocalDate effectiveStart = start == null ? effectiveEnd.minusDays(29) : start;
        if (effectiveStart.isAfter(effectiveEnd)) {
            throw new IllegalArgumentException("开始日期不能晚于结束日期");
        }
        return new Trends(mapper.findDailyActive(scope.allOrganizations(), scope.rootPaths(),
                effectiveStart, effectiveEnd).stream().map(TrendPoint::from).toList());
    }

    private SystemOperationAccessDeniedException denied() {
        return new SystemOperationAccessDeniedException("无权查看学员活跃度趋势");
    }
}
