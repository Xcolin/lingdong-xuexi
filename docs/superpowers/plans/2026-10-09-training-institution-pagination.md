# 培训机构、多机构学员关系与统一分页 Implementation Plan

> REQUIRED SUB-SKILL: superpowers:subagent-driven-development，独立模块分工；Maven由root串行协调。

Goal: 增加TRAINING_INSTITUTION机构根，多机构学生身份复用，普通学校每校一个行政班、培训机构多课程班；家庭学生独立建档，以机构班级邀请和确认建立关系，认证暂缓。家庭组织用户提供编辑入口。Web分页参照授权审计10/20/50，页容量切换实际作用于查询。

Architecture: 保留SCHOOL名称兼容已有API，将学校边界扩展为教育机构根。每条ENROLLMENT与CLASS按所属机构处理，所有查询继续真实数据范围与机构条件隔离；不重发学生凭据，不清业务数据。邀请代码定位单个班级，不提供公开学校搜索。

- [x] 机构层级、角色/成员/教师/任务/导入导出SQL兼容TRAINING_INSTITUTION，普通学校行为保留。
- [x] 家庭无学校学生建档、多机构入学、普通学校单班/培训多班、机构内转班退出；邀请与审核流程。
- [x] 前端培训机构根、家庭学生流程、邀请申请/审核、组织用户编辑。
- [x] 分页公共规则与后端查询页容量、双栏选择器分页统一，日志基准默认10/20/50。
- [x] 范围/跨机构/多班/凭据保留回归、构建、真实增量迁移与服务重启。

分工：institution_backend拥有组织层级/教师/数据范围/导入导出（不改student及UserAccess）；student_backend拥有student、UserAccess、邀请关系及其迁移；pagination_web拥有公共分页和非组织学员前端页面；root拥有相关组织/学生/家庭UI、组织类型迁移与整合验证。禁止历史dirty回退、真实数据删除、现有密码/首次码重置。

验证详情及全量历史断言限制见PROJECT_STATUS.md 2026-10-09记录；认证未启用。

