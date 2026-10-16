import {beforeEach,expect,it,vi} from 'vitest';
import {apiClient,authSessionStore,organizationContextStore} from './http';
const session={sessionId:'1',accessToken:'token',refreshToken:'refresh',accessExpiresAt:'',refreshExpiresAt:''};
beforeEach(()=>{sessionStorage.clear();vi.stubGlobal('fetch',vi.fn().mockImplementation(()=>Promise.resolve(new Response('{}',{status:200}))));authSessionStore.save(session);});
it('选择组织只附加业务请求，个人入驻查询仍可查看全部本人组织',async()=>{organizationContextStore.select('123');await apiClient.get('/organizations');expect(new Headers(vi.mocked(fetch).mock.calls[0][1]?.headers).get('X-Organization-Id')).toBe('123');await apiClient.get('/organization-onboarding/my-organizations');expect(new Headers(vi.mocked(fetch).mock.calls[1][1]?.headers).has('X-Organization-Id')).toBe(false);});
it('不同登录会话不能继承之前组织，退出清除上下文',async()=>{organizationContextStore.select('123');authSessionStore.save({...session,sessionId:'2'});await apiClient.get('/organizations');expect(new Headers(vi.mocked(fetch).mock.calls[0][1]?.headers).has('X-Organization-Id')).toBe(false);authSessionStore.clear();expect(sessionStorage.getItem('lingdong.web.selected-organization')).toBeNull();});
