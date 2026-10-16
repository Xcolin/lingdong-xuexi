import { afterEach, describe, expect, it, vi } from 'vitest';
import { classAssignmentApi } from './classAssignmentApi';

describe('学员机构关系 API', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('将教师真实响应classOrganizationIds映射为班级列表使用的classIds', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({items:[{id:'teacher-1',displayName:'张老师',classOrganizationIds:['class-1'],schoolName:'学校'}],total:1,page:1,pageSize:100}),{status:200})));
    const teachers=await classAssignmentApi.listTeachers('class-1');
    expect(teachers).toEqual([{userId:'teacher-1',displayName:'张老师',classIds:['class-1'],organizationNames:['学校']}]);
    expect(teachers[0].classIds.includes('class-1')).toBe(true);
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

it('教师候选映射保留组织路径用于同名教师区分',async()=>{
 vi.stubGlobal('fetch',vi.fn().mockResolvedValue(new Response(JSON.stringify({items:[{id:'teacher-east',displayName:'王老师',classOrganizationIds:['class-1'],schoolName:'示例学校 / 东校区 / 三年级'}],total:1,page:1,pageSize:100}),{status:200})));
 expect(await classAssignmentApi.listTeachers('class-1')).toEqual([{userId:'teacher-east',displayName:'王老师',classIds:['class-1'],organizationNames:['示例学校 / 东校区 / 三年级']}]);vi.unstubAllGlobals();
});
