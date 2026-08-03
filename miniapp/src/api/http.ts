const API_PREFIX = '/api/v1';
const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? '';

export interface MiniappRequestOptions {
  method?: UniApp.RequestOptions['method'];
  data?: UniApp.RequestOptions['data'];
  header?: Record<string, string>;
}

export interface ApiErrorBody {
  code?: string;
  message?: string;
  traceId?: string;
  lockedUntil?: string;
}

/** 保留服务端错误码，供登录页切换验证码、锁定等明确状态。 */
export class ApiError extends Error {
  readonly statusCode: number;
  readonly code: string;
  readonly traceId?: string;
  readonly lockedUntil?: string;

  constructor(statusCode: number, body: ApiErrorBody = {}) {
    super(body.message || '请求未能完成');
    this.name = 'ApiError';
    this.statusCode = statusCode;
    this.code = body.code || 'REQUEST_FAILED';
    this.traceId = body.traceId;
    this.lockedUntil = body.lockedUntil;
  }
}

/**
 * 小程序端请求基础封装。
 * 小程序会话由调用方显式传入，不复用 Web 端存储和路由状态。
 */
export function request<T>(path: string, options: MiniappRequestOptions = {}): Promise<T> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${apiBaseUrl}${API_PREFIX}${path}`,
      method: options.method ?? 'GET',
      data: options.data,
      header: options.header,
      success: (response) => {
        if (response.statusCode >= 200 && response.statusCode < 300) {
          resolve(response.data as T);
          return;
        }
        reject(new ApiError(response.statusCode, (response.data || {}) as ApiErrorBody));
      },
      fail: () => reject(new Error('网络请求未能完成'))
    });
  });
}
