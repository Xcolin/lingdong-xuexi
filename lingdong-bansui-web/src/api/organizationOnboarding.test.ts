import { expect, it, vi } from 'vitest';
import { apiClient } from './http';
import { organizationOnboardingApi as api } from './organizationOnboarding';
vi.mock('./http',()=>({apiClient:{get:vi.fn(),post:vi.fn()}}));
it('重新提交带上版本和批次，个人列表无需已有组织',async()=>{
 await api.applications();
 await api.resubmit('a',{typeCode:'SCHOOL',name:'学校',regionId:'r',contactName:'联系人',versionNo:2,submissionBatch:1});
 expect(apiClient.get).toHaveBeenCalledWith('/organization-onboarding/applications');
 expect(apiClient.post).toHaveBeenCalledWith('/organization-onboarding/applications/a/resubmit',expect.objectContaining({versionNo:2,submissionBatch:1}));
});
it('家庭重试使用同一幂等键，审核使用领域事务端点',async()=>{
 const input={name:'家',idempotencyKey:'retry-key',regionId:'region'};
 await api.families(input);await api.families(input);
 await api.review('task','reject','名称重复');
 expect(apiClient.post).toHaveBeenLastCalledWith('/organization-onboarding/tasks/task/reject',{comment:'名称重复'});
 expect(vi.mocked(apiClient.post).mock.calls.filter(c=>c[0]==='/organization-onboarding/families').map(c=>c[1])).toEqual([input,input]);
});
