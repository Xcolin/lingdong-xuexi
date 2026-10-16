# 任务星期重复最小兼容实施计划（2026-10-14）

证据：V30 learn_task_recurrence.frequency_type VARCHAR(16) CHECK仅DAILY；LearningTaskRequest/学习任务草稿仅recurrenceEnabled/endDate；RecurringTaskGenerationService 每天推进并只认可DAILY。因此不能直接开放前端每周选项。

契约：新增 recurrenceFrequency=DAILY/WEEKLY/CUSTOM、recurrenceWeekdays（ISO星期1..7）。scheduledDate沿用起始日期，recurrenceEndDate结束日期。旧payload省略新字段默认DAILY；WEEKLY默认起始日期星期且只允许一个星期；CUSTOM必须非空、去重、1..7。禁用重复时新属性清空。第一条实例仍按scheduledDate发布，之后按星期计划生成；若选定星期不含起始日期，拒绝输入，避免第一条落在未选日。

持久化：learn_task定义追加频率/星期字段以保存草稿；learn_task_recurrence追加星期字段并放宽频率CHECK。不得修改已应用V30；另增前向migration，仅隔离clone候选验证，不部署真库。旧定义null按DAILY读取。Java record新增字段必须保留旧构造器（现有测试/调用）且MyBatis resultMap匹配。

步骤：先纯日期规则测试（旧DAILY、WEEKLY、CUSTOM、inclusive结束边界、停止/失效不生成）；再Request→Draft→Validator→Task→mapper→Response端到端字段；最后发布计划与生成器日期推进、客户端真实字段与编辑保留。scope授权保持当前部署版。校验frequency/weekday任何非法值拒绝，客户端不提供尚未支持选项。
