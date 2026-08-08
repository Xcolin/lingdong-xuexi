const API_PREFIX = '/api/v1';
const SESSION_STORAGE_KEY = 'lingdong-learning.web.session';

export interface AuthSession {
  sessionId: string;
  accessToken: string;
  refreshToken: string;
  accessExpiresAt: string;
  refreshExpiresAt: string;
}

export interface PasswordLoginInput {
  username: string;
  password: string;
  deviceId: string;
  deviceName: string;
}

export class ApiRequestError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string | null,
    message: string
  ) {
    super(message);
    this.name = 'ApiRequestError';
  }
}

export const authSessionStore = {
  get(): AuthSession | null {
    const serialized = sessionStorage.getItem(SESSION_STORAGE_KEY);
    if (!serialized) {
      return null;
    }
    try {
      return JSON.parse(serialized) as AuthSession;
    } catch {
      sessionStorage.removeItem(SESSION_STORAGE_KEY);
      return null;
    }
  },
  save(session: AuthSession): void {
    sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session));
  },
  clear(): void {
    sessionStorage.removeItem(SESSION_STORAGE_KEY);
  }
};

let refreshInFlight: Promise<AuthSession> | null = null;

interface RequestOptions {
  authenticated?: boolean;
  retryAfterRefresh?: boolean;
}

async function request<T>(
  path: string,
  init: RequestInit = {},
  options: RequestOptions = {}
): Promise<T> {
  const authenticated = options.authenticated ?? true;
  const retryAfterRefresh = options.retryAfterRefresh ?? true;
  const headers = new Headers(init.headers);
  const session = authSessionStore.get();

  if (init.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }
  if (authenticated && session) {
    headers.set('Authorization', `Bearer ${session.accessToken}`);
  }

  const response = await fetch(`${API_PREFIX}${path}`, { ...init, headers });
  if (response.status === 401 && authenticated && retryAfterRefresh && session) {
    await refreshSession();
    return request<T>(path, init, { authenticated, retryAfterRefresh: false });
  }
  if (!response.ok) {
    throw await toApiRequestError(response);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return response.json() as Promise<T>;
}

async function requestBlob(path: string, retryAfterRefresh = true): Promise<Blob> {
  const session = authSessionStore.get();
  const headers = new Headers();
  if (session) headers.set('Authorization', `Bearer ${session.accessToken}`);
  const response = await fetch(`${API_PREFIX}${path}`, { headers });
  if (response.status === 401 && retryAfterRefresh && session) {
    await refreshSession();
    return requestBlob(path, false);
  }
  if (!response.ok) throw await toApiRequestError(response);
  return response.blob();
}

async function refreshSession(): Promise<AuthSession> {
  if (!refreshInFlight) {
    const currentSession = authSessionStore.get();
    if (!currentSession) {
      throw new ApiRequestError(401, 'AUTH_REQUIRED', '登录状态已失效');
    }
    // 多个并发请求只触发一次刷新，避免刷新凭证轮换后相互失效。
    refreshInFlight = request<AuthSession>('/auth/sessions/refresh', {
      method: 'POST',
      body: JSON.stringify({ refreshToken: currentSession.refreshToken })
    }, { authenticated: false })
      .then((session) => {
        authSessionStore.save(session);
        return session;
      })
      .catch((error: unknown) => {
        authSessionStore.clear();
        throw error;
      })
      .finally(() => {
        refreshInFlight = null;
      });
  }
  return refreshInFlight;
}

async function toApiRequestError(response: Response): Promise<ApiRequestError> {
  const payload = await response.json().catch(() => null) as { code?: string; message?: string } | null;
  return new ApiRequestError(
    response.status,
    payload?.code ?? null,
    payload?.message ?? '请求未能完成'
  );
}

export const apiClient = {
  get<T>(path: string): Promise<T> {
    return request<T>(path);
  },
  post<T>(path: string, body: unknown): Promise<T> {
    return request<T>(path, { method: 'POST', body: JSON.stringify(body) });
  },
  put<T>(path: string, body: unknown): Promise<T> {
    return request<T>(path, { method: 'PUT', body: JSON.stringify(body) });
  },
  patch<T>(path: string, body: unknown): Promise<T> {
    return request<T>(path, { method: 'PATCH', body: JSON.stringify(body) });
  },
  delete(path: string): Promise<void> {
    return request<void>(path, { method: 'DELETE' });
  },
  getBlob(path: string): Promise<Blob> {
    return requestBlob(path);
  },
  async loginByPassword(input: PasswordLoginInput): Promise<AuthSession> {
    const session = await request<AuthSession>('/auth/sessions/password', {
      method: 'POST',
      body: JSON.stringify(input)
    }, { authenticated: false });
    authSessionStore.save(session);
    return session;
  }
};
