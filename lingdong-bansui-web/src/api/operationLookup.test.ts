import { beforeEach, expect, it, vi } from 'vitest';
import { apiClient } from './http';
import { organizationApi } from './organization';
import { usersApi } from './users';
import { classApi } from './classes';
import { teachersApi } from './teachers';
import { rewardApi } from '../features/rewards/api';
import { growthPointApi } from '../features/growth-points/api';
import { growthReviewApi } from '../features/growth-reviews/api';
vi.mock('./http', () => ({ apiClient: { get: vi.fn(), post: vi.fn() } }));
beforeEach(()=>{vi.clearAllMocks();vi.mocked(apiClient.get).mockResolvedValue([]);vi.mocked(apiClient.post).mockResolvedValue(undefined);});
it('组织普通成员和管理员使用四个独立API，不借通用IAM权限操作',async()=>{
 await organizationApi.associateMembers('org',['user'],false);
 await organizationApi.associateMembers('org',['user'],true);
 await organizationApi.revokeMembers('org',['user'],false);
 await organizationApi.revokeMembers('org',['user'],true);
 expect(vi.mocked(apiClient.post).mock.calls).toEqual([
 ['/organizations/org/members:associate',{userIds:['user']}],
 ['/organizations/org/administrators:assign',{userIds:['user']}],
 ['/organizations/org/members:remove',{userIds:['user']}],
 ['/organizations/org/administrators:revoke',{userIds:['user']}]
 ]);
 await organizationApi.memberRelations('org',['user'],true);
 expect(apiClient.get).toHaveBeenLastCalledWith('/organizations/org/member-relations?userIds=user&administrator=true');
});
it('选择器使用其调用动作最小lookup而非全局组织和教师读取接口',async()=>{
 await usersApi.creationOrganizations('STUDENT');
 await usersApi.scopeOrganizations('ORG_ADMIN_ASSIGN');
 await classApi.lookup('TEACHER_CLASS_ASSIGN','CLASS',{schoolOrganizationId:'school'});
 await teachersApi.lookup('LEARNING_TASK_CREATE',{classOrganizationId:'class',page:1,pageSize:100});
 expect(vi.mocked(apiClient.get).mock.calls.map(call=>call[0])).toEqual([
 '/users/creation-organizations?type=STUDENT',
 '/users/scope-organizations?permission=ORG_ADMIN_ASSIGN',
 '/classes/lookup?permission=TEACHER_CLASS_ASSIGN&kind=CLASS&schoolOrganizationId=school',
 '/organization-teachers/lookup?permission=LEARNING_TASK_CREATE&page=1&pageSize=100&classOrganizationId=class'
 ]);
});
it('奖励/积分/复盘共用真实关系孩子lookup并保留各自组件类型',async()=>{
 vi.mocked(apiClient.get).mockResolvedValue([{id:'child',studentName:'孩子',gradeCode:null,schoolOrganizationId:null,status:'ENABLED',relationshipRole:'PRIMARY_GUARDIAN'}]);
 expect((await rewardApi.listStudents())[0].id).toBe('child');
 expect(await growthPointApi.listStudents()).toEqual([{studentId:'child',studentName:'孩子'}]);
 expect(await growthReviewApi.listStudents()).toEqual([{studentId:'child',studentName:'孩子'}]);
 expect(vi.mocked(apiClient.get).mock.calls.every(call=>call[0]==='/parent/student-options')).toBe(true);
});
