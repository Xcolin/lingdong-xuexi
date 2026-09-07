import { beforeEach, describe, expect, it, vi } from 'vitest';
import { apiClient } from '../../api/http';
import { exceptionReportApi } from './api';

vi.mock('../../api/http', () => ({
  apiClient: { get: vi.fn(), post: vi.fn() }
}));

describe('异常报备接口', () => {
  beforeEach(() => vi.mocked(apiClient.get).mockResolvedValue({ items: [], page: 1, pageSize: 20, total: 0 }));

  it('编码台账组合筛选条件并忽略空值', async () => {
    await exceptionReportApi.list({
      classOrganizationId: '8910000000000001043',
      exceptionType: 'MENTAL_STATE',
      status: 'SUBMITTED',
      page: 2,
      pageSize: 20
    });

    expect(apiClient.get).toHaveBeenCalledWith(
      '/exception-reports?classOrganizationId=8910000000000001043&exceptionType=MENTAL_STATE&status=SUBMITTED&page=2&pageSize=20'
    );
  });
});
