# 学校年级班级 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task.

**Goal:** 学校→年级→班级统一数据规则和直观操作。
**Architecture:** 复用sys_organization层级与权限范围，后端统一父子规则/学校解析，前端共享选中节点。
**Tech Stack:** Spring Boot/MyBatis/MySQL、React/Ant Design。

- [x] 后端组织创建、移动、班级/年级接口及存量同校归并，添加层级回归。
- [x] 所有教师学生任务/导入导出学校归属查询支持年级层，添加范围回归。
- [x] 前端左树右上下文列表，学校年级班级自动带父级与同步刷新，添加交互测试。
- [x] 复核权限边界，串行Maven回归、前端测试构建、打包重启真实环境，记录证据。

不清空业务数据，不修改现有密码和角色授权，不提交历史工作区修改。Maven串行避免target共享竞争。
