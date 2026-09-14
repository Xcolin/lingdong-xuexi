import { beforeEach, describe, expect, it, vi } from 'vitest';
import { apiClient } from '../../api/http';
import { growthReviewSubscriptionApi } from './subscriptionApi';

vi.mock('../../api/http', () => ({ apiClient: { get: vi.fn(), put: vi.fn() } }));
const studentId = '1874244142494661102';
const path = `/growth-review-subscriptions/students/${studentId}`;

describe('本人周报订阅请求契约', () => {
  beforeEach(() => vi.resetAllMocks());

  it('读取本人偏好时完整保留学生字符串标识', async () => {
    const view = { studentId, enabled: false, version: 0 };
    vi.mocked(apiClient.get).mockResolvedValue(view);
    expect(await growthReviewSubscriptionApi.get(studentId)).toEqual(view);
    expect(apiClient.get).toHaveBeenCalledWith(path);
  });

  it('开启只提交状态与版本，不透传客户端身份或微信授权', async () => {
    const input = { enabled: true, version: 0, parentUserId: 'forged', consent: true };
    await growthReviewSubscriptionApi.set(studentId, input);
    expect(apiClient.put).toHaveBeenCalledWith(path, { enabled: true, version: 0 });
  });

  it('取消直接提交当前版本，不依赖额外读取或开启资格接口', async () => {
    const view = { studentId, enabled: false, version: 3 };
    vi.mocked(apiClient.put).mockResolvedValue(view);
    expect(await growthReviewSubscriptionApi.set(studentId, { enabled: false, version: 2 })).toEqual(view);
    expect(apiClient.put).toHaveBeenCalledWith(path, { enabled: false, version: 2 });
    expect(apiClient.get).not.toHaveBeenCalled();
  });

  it('版本冲突原样返回，不自动重试覆盖其他页面的修改', async () => {
    const conflict = new Error('订阅状态已变更');
    vi.mocked(apiClient.put).mockRejectedValue(conflict);
    await expect(growthReviewSubscriptionApi.set(studentId, { enabled: true, version: 1 })).rejects.toBe(conflict);
    expect(apiClient.put).toHaveBeenCalledTimes(1);
  });
});
