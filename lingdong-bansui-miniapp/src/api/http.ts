const API_PREFIX = '/api/v1';
// H5 走开发代理(相对路径);小程序/原生端没有相对路径概念,开发态默认连本机后端,
// 生产构建必须用 VITE_API_BASE_URL 注入正式域名;真机预览需改为开发机的局域网地址。
let apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? '';
// #ifndef H5
if (!apiBaseUrl && import.meta.env.DEV) apiBaseUrl = 'http://127.0.0.1:8080';
// #endif

/** 同时支持业务相对路径和服务端返回的完整 API 路径。 */
export function apiUrl(path: string): string {
  return path.startsWith(API_PREFIX)
    ? `${apiBaseUrl}${path}`
    : `${apiBaseUrl}${API_PREFIX}${path}`;
}

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

/** 单请求超时兜底：任何网络异常都不允许让页面永久停留在加载态。 */
const REQUEST_TIMEOUT_MS = 15000;

/**
 * 小程序端请求基础封装。
 * 小程序会话由调用方显式传入，不复用 Web 端存储和路由状态。
 */
export function request<T>(path: string, options: MiniappRequestOptions = {}): Promise<T> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: apiUrl(path),
      method: options.method ?? 'GET',
      data: options.data,
      header: options.header,
      timeout: REQUEST_TIMEOUT_MS,
      success: (response) => {
        if (response.statusCode >= 200 && response.statusCode < 300) {
          resolve(response.data as T);
          return;
        }
        reject(new ApiError(response.statusCode, (response.data || {}) as ApiErrorBody));
      },
      fail: () => reject(new Error('网络请求未能完成，请检查网络后重试'))
    });
  });
}
