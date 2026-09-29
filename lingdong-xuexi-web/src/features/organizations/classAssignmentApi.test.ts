import { afterEach, describe, expect, it, vi } from 'vitest';
import { classAssignmentApi } from './classAssignmentApi';

describe('学员机构关系 API', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('从学生机构关系专用接口读取班级候选', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify([]), { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);

    await classAssignmentApi.listRelationshipClasses();

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/students/organization-relationship-classes',
      expect.any(Object)
    );
  });
});
