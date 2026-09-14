import { beforeEach, describe, expect, it, vi } from 'vitest';
import { apiClient } from '../../api/http';
import { growthReviewExportApi } from './exportApi';

vi.mock('../../api/http', () => ({ apiClient: { get: vi.fn(), post: vi.fn(), getBlob: vi.fn() } }));
const studentId = '1874244142494650102';
const id = '1874244142494650180';

describe('复盘专用导出接口', () => {
  it('模板选项带孩子范围，不调用管理员配置接口', async () => {
    await growthReviewExportApi.options(studentId);
    expect(apiClient.get).toHaveBeenCalledWith(`/growth-review-export-jobs/options?studentId=${studentId}`);
  });
  beforeEach(() => vi.clearAllMocks());

  it('单份创建保留字符串标识并使用专用路径', async () => {
    const input = { studentId, reviewId: id, templateId: '1874244142494650105', mode: 'SIMPLE' as const, reason: '复盘留存' };
    await growthReviewExportApi.create(input);
    expect(apiClient.post).toHaveBeenCalledWith('/growth-review-export-jobs', input);
  });

  it('区间创建不填单份标识', async () => {
    const input = { studentId, periodType: 'WEEK' as const, dateFrom: '2026-08-01', dateTo: '2026-08-31',
      templateId: '1874244142494650105', mode: 'DETAILED' as const, reason: '周报留存' };
    await growthReviewExportApi.create(input);
    expect(apiClient.post).toHaveBeenCalledWith('/growth-review-export-jobs', input);
    expect(input).not.toHaveProperty('reviewId');
  });

  it('历史查询始终携带学生范围和分页', async () => {
    await growthReviewExportApi.list(studentId);
    expect(apiClient.get).toHaveBeenLastCalledWith(`/growth-review-export-jobs?studentId=${studentId}&page=1&pageSize=20`);
    await growthReviewExportApi.list(studentId, { status: 'SUCCEEDED', page: 2, pageSize: 10 });
    expect(apiClient.get).toHaveBeenLastCalledWith(`/growth-review-export-jobs?studentId=${studentId}&page=2&pageSize=10&status=SUCCEEDED`);
  });

  it('详情与下载不通过统一 XLSX 接口', async () => {
    const blob = new Blob(['pdf'], { type: 'application/pdf' });
    vi.mocked(apiClient.getBlob).mockResolvedValue(blob);
    await growthReviewExportApi.detail(id);
    expect(apiClient.get).toHaveBeenCalledWith(`/growth-review-export-jobs/${id}`);
    expect(await growthReviewExportApi.download(id)).toBe(blob);
    expect(apiClient.getBlob).toHaveBeenCalledWith(`/growth-review-export-jobs/${id}/download`);
  });
});
