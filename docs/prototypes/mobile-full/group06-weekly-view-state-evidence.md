# 06-10 weekly detail evidence

Route /pages/growth-reviews/growth-reviews, separate period=WEEK view selected through the explicit growth weekly report tab. Current self identity must be MINIAPP STUDENT, not SYS_AUDITOR, with GROWTH_REVIEW_READ_SELF permission; periodicGrowthReportEnabled must be true before WEEK list requests. DAY and WEEK tabs use distinct list period and reject detail responses with unexpected periodType. Session/hide revision guards reject stale responses.

Existing backend supports WEEK in self list and detail with self permission and period feature guard; no backend changes required. Empty WEEK lists show no weekly report, even if daily records exist. Weekly metrics, dates, categories and trends use existing real DTO. No weekly supplement editor is shown. Teacher comments, class name, star count and narrative highlights are not in this DTO and are not invented. No native pixel acceptance claimed.
