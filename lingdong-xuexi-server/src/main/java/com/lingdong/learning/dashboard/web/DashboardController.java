package com.lingdong.learning.dashboard.web;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.dashboard.application.ActivityTrendService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** 机构管理员看板统计入口（Web 完整、小程序摘要）；活跃度口径见 docs/design/09 第 2.7 节。 */
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {
    private final ActivityTrendService activityTrendService;

    public DashboardController(ActivityTrendService activityTrendService) {
        this.activityTrendService = activityTrendService;
    }

    @GetMapping("/activity-trends")
    public ActivityTrendService.Trends activityTrends(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) LocalDate start,
            @RequestParam(required = false) LocalDate end) {
        return activityTrendService.activityTrends(user, start, end);
    }
}
