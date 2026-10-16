# 03-04/03-05 组织层级与新增节点契约核查

当前 miniapp organization-classes API 提供 /classes/grades 查询及创建班级时 schoolOrganizationId、gradeOrganizationId、name、sortOrder。现有班级维护页可进入班级创建，并不等于原图完整校区/年级/课程分类层级浏览。

缺口：需经授权的只读组织树，包含 id、parentId、typeCode、name、status、sortOrder、可创建子节点类型、可编辑权限；新增节点需明确 parentId、typeCode、name、sortOrder，以及重复名称与非法父子类型错误。建议契约需先核对服务端已有组织管理控制器后确定真实路径，不能将本记录中的概念当成已存在接口。

03-04 需要层级展开、选中节点、进入班级列表；03-05 需要按允许类型填写校区/年级/课程分类并提交。当前页面不以静态树伪造数据，无接口则应明确无数据/权限/暂不可用，新增表单不得假报保存成功。服务端须校验当前账号管理组织范围，不能相信前端传入 parentId。

工作台已清除重复班级/教师/任务入口；相关审核、考勤、异常、人工恢复、注销与账号设置仍通过唯一入口保留。TRAINING_INSTITUTION 已映射培训机构。尚无可用完整统计，未填原图示例数字。
