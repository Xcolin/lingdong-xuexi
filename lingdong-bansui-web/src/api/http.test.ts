import { afterEach, describe, expect, it, vi } from 'vitest';
import { apiClient, authSessionStore } from './http';

const SESSION_KEY = 'lingdong-learning.web.session';

describe('HTTP 认证会话', () => {
  it('下载保留服务端中文文件名且拒绝路径字符', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('pdf', {
      headers: { 'Content-Disposition': "attachment; filename*=UTF-8''%E5%A4%8D%E7%9B%98.pdf" }
    })));
    expect((await apiClient.getDownload('/report')).fileName).toBe('复盘.pdf');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('pdf', {
      headers: { 'Content-Disposition': "attachment; filename*=UTF-8''..%2Fsecret.pdf" }
    })));
    expect((await apiClient.getDownload('/report')).fileName).toBeUndefined();
  });
  afterEach(() => {
    sessionStorage.clear();
    vi.unstubAllGlobals();
  });

  it('密码登录后保存会话并携带访问凭证', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      sessionId: '1874244142494646201',
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
      accessExpiresAt: '2026-08-01T00:00:00',
      refreshExpiresAt: '2026-08-08T00:00:00'
    }), { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);

    await apiClient.loginByPassword({
      username: 'system-admin',
      password: 'TestPassword123',
      deviceId: 'web-browser',
      deviceName: '灵动伴随管理端'
    });

    expect(authSessionStore.get()).toMatchObject({
      accessToken: 'access-token',
      refreshToken: 'refresh-token'
    });
    expect(sessionStorage.getItem(SESSION_KEY)).toContain('access-token');
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/auth/sessions/password', expect.objectContaining({
      method: 'POST'
    }));
  });

  it('认证请求收到一次 401 后刷新令牌并重放请求', async () => {
    authSessionStore.save({
      sessionId: '1874244142494646201',
      accessToken: 'expired-token',
      refreshToken: 'refresh-token',
      accessExpiresAt: '2026-08-01T00:00:00',
      refreshExpiresAt: '2026-08-08T00:00:00'
    });
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ code: 'AUTH_REQUIRED' }), { status: 401 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        sessionId: '1874244142494646201',
        accessToken: 'renewed-token',
        refreshToken: 'renewed-refresh-token',
        accessExpiresAt: '2026-08-01T01:00:00',
        refreshExpiresAt: '2026-08-08T00:00:00'
      }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ username: 'system-admin' }), { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);

    const currentUser = await apiClient.get<{ username: string }>('/auth/me');

    expect(currentUser.username).toBe('system-admin');
    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/auth/me');
    expect(new Headers(fetchMock.mock.calls[0][1]?.headers).get('Authorization')).toBe('Bearer expired-token');
    expect(fetchMock.mock.calls[2][0]).toBe('/api/v1/auth/me');
    expect(new Headers(fetchMock.mock.calls[2][1]?.headers).get('Authorization')).toBe('Bearer renewed-token');
    expect(authSessionStore.get()?.accessToken).toBe('renewed-token');
  });

  it('刷新失败时清除会话并返回认证错误', async () => {
    authSessionStore.save({
      sessionId: '1874244142494646201',
      accessToken: 'expired-token',
      refreshToken: 'refresh-token',
      accessExpiresAt: '2026-08-01T00:00:00',
      refreshExpiresAt: '2026-08-08T00:00:00'
    });
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ code: 'AUTH_REQUIRED' }), { status: 401 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ code: 'AUTH_REQUIRED' }), { status: 401 }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(apiClient.get('/auth/me')).rejects.toMatchObject({ status: 401 });

    expect(authSessionStore.get()).toBeNull();
    expect(sessionStorage.getItem(SESSION_KEY)).toBeNull();
  });
});
