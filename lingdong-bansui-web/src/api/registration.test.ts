import {beforeEach,it,expect,vi} from 'vitest';
import {registrationApi} from './registration';
import {apiClient,authSessionStore} from './http';
beforeEach(()=>{sessionStorage.clear();vi.restoreAllMocks();});
it('注册使用既有会话存储',async()=>{const session={sessionId:'s',accessToken:'a',refreshToken:'r',accessExpiresAt:'x',refreshExpiresAt:'y'};vi.spyOn(apiClient,'publicPost').mockResolvedValue({session});await registrationApi.register({channel:'EMAIL',destination:'a@b.com'});expect(authSessionStore.get()).toEqual(session);expect(apiClient.publicPost).toHaveBeenCalledWith('/auth/registrations',expect.objectContaining({clientType:'WEB',channel:'EMAIL'}));});
