import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { StudentOrganizationLifecycleDrawer } from './StudentOrganizationLifecycleDrawer';

const classAssignmentApi = vi.hoisted(() => ({
  listRelationshipClasses: vi.fn(),
  listRelationships: vi.fn(),
  transferStudent: vi.fn(),
  deactivateStudent: vi.fn()
}));

vi.mock('./classAssignmentApi', () => ({ classAssignmentApi }));

describe('学员机构关系抽屉', () => {
  beforeEach(() => {
    classAssignmentApi.listRelationships.mockResolvedValue([{
      studentId: '1874244142494646601',
      studentName: '小明',
      gradeCode: 'G4',
      enrollmentOrganizationId: '1874244142494646602',
      enrollmentOrganizationName: '示例学校',
      currentClassOrganizationId: '1874244142494646603',
      currentClassOrganizationName: '一班'
    }]);
    classAssignmentApi.listRelationshipClasses.mockResolvedValue([{
      id: '1874244142494646604', name: '二班', schoolOrganizationId:'1874244142494646602'
    }]);
    classAssignmentApi.transferStudent.mockResolvedValue({
      studentId: '1874244142494646601',
      enrollmentOrganizationId: '1874244142494646602',
      currentClassOrganizationId: '1874244142494646604',
      status: 'ACTIVE',
      changes: []
    });
  });

  it('加载活动学员并提交校内转班原因', async () => {
    const user = userEvent.setup();
    render(<StudentOrganizationLifecycleDrawer open onClose={vi.fn()} />);

    await user.click(await screen.findByLabelText('学生'));
    await user.click(await screen.findByText('小明 · 示例学校 · 一班'));
    await user.click(screen.getByLabelText('目标班级'));
    await user.click(await screen.findByText('二班'));
    await user.type(screen.getByLabelText('变更原因'), '调整学习班级');
    await user.click(screen.getByRole('button', { name: '确认转班' }));

    await waitFor(() => {
      expect(classAssignmentApi.transferStudent).toHaveBeenCalledWith(
        '1874244142494646601',
        '1874244142494646604',
        '调整学习班级'
      );
    });
  });
});

it('目标班级只显示所选学员所在学校，切换学校清除目标班级',async()=>{
 classAssignmentApi.listRelationships.mockResolvedValue([{studentId:'student-a',studentName:'甲',enrollmentOrganizationId:'school-a',enrollmentOrganizationName:'甲校'},{studentId:'student-b',studentName:'乙',enrollmentOrganizationId:'school-b',enrollmentOrganizationName:'乙校'}]);
 classAssignmentApi.listRelationshipClasses.mockResolvedValue([{id:'class-a',name:'甲校班级',schoolOrganizationId:'school-a'},{id:'class-b',name:'乙校班级',schoolOrganizationId:'school-b'}]);
 render(<StudentOrganizationLifecycleDrawer open onClose={()=>{}}/>);const actor=userEvent.setup();await actor.click(await screen.findByLabelText('学生'));await actor.click(await screen.findByText('甲 · 甲校 · 未分班'));await actor.click(screen.getByLabelText('目标班级'));expect(await screen.findByText('甲校班级')).toBeInTheDocument();expect(screen.queryByText('乙校班级')).not.toBeInTheDocument();
});
