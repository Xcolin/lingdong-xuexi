# 伙伴首页真实动态方案证据（2026-10-14）

任务定义 GET /learning-tasks 不代表孩子提交或完成。现有 GET /task-reviews 响应 TaskReviewResponse 包含 sourceOrganizationId/studentId/latestCheckIn.submittedAt，SQL TaskReviewMapper.findPage 已限制 current_reviewer_id、selectedStudentScope、PENDING_REVIEW、SUBMITTED，并按提交时间升序分页。可以只读收集完整分页后展示真实待审核提交事件，不能标记任务完成或积分到账。

兑换 GET /parent-reward-exchanges/students/:id 受 MINIAPP_REWARD_EXCHANGE_REVIEW_CHILD 和 parent actor 保护；requestedAt/reviewedAt/verifiedAt 是真实事件时间，但首页当前家庭/机构作用域还需选中学生集合，未自动跨孩子聚合。审批任务历史不在 task-reviews 待办列表内，不能从 latest task definition 推断。

本轮实现保守的 task submission 动态 API adapter：调用现有授权接口、不修改后端或授权；严格要求 selected studentIds，机构另要求匹配 sourceOrganizationId，家庭仅 FAMILY；只显示当前 scope 的待审核提交，完整分页排序再截取。没有 scope 时零请求零数据。无 TASK_ASSIGNMENT_REVIEW 或功能关闭时不调用。历史完成、审核及兑换动态仍不宣称覆盖。
