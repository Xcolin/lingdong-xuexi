import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ClassManagementPanel } from './ClassManagementPanel';

const classApi = vi.hoisted(() => ({
  listSchools: vi.fn(),
  listClasses: vi.fn(),
  createClass: vi.fn(),
  updateClass: vi.fn(),
  disableClass: vi.fn(),
  enableClass: vi.fn()
}));

vi.mock('../../api/classes', () => ({ classApi }));

describe('班级管理面板', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    classApi.listSchools.mockResolvedValue([
      { id: '2088485801854763001', name: '东城学校', code: 'SCHOOL_EAST', typeCode: 'SCHOOL', status: 'ENABLED', effectiveStatus: 'ENABLED', sortOrder: 10, versionNo: 1 }
    ]);
    classApi.listClasses.mockResolvedValue([
      { id: '2088485801854763002', parentId: '2088485801854763001', name: '一年级一班', code: 'CLS_2088485801854763002', typeCode: 'CLASS', status: 'ENABLED', effectiveStatus: 'ENABLED', sortOrder: 10, versionNo: 2 },
      { id: '2088485801854763003', parentId: '2088485801854763001', name: '一年级二班', code: 'CLS_2088485801854763003', typeCode: 'CLASS', status: 'DISABLED', effectiveStatus: 'DISABLED', sortOrder: 20, versionNo: 3 }
    ]);
    classApi.createClass.mockResolvedValue(undefined);
    classApi.updateClass.mockResolvedValue(undefined);
    classApi.disableClass.mockResolvedValue(undefined);
    classApi.enableClass.mockResolvedValue(undefined);
  });

  it('加载并完成新增、编辑和启停二次确认', async () => {
    const user = userEvent.setup();
    render(<ClassManagementPanel />);

    expect(await screen.findByText('一年级一班')).toBeInTheDocument();
    expect(screen.getAllByText('东城学校')).toHaveLength(2);

    await user.click(screen.getByRole('button', { name: '新增班级' }));
    await user.click(screen.getByLabelText('所属学校'));
    await user.click(await screen.findByText('东城学校（SCHOOL_EAST）'));
    await user.type(screen.getByLabelText('班级名称'), '二年级一班');
    await user.click(screen.getByRole('button', { name: '创建班级' }));
    await waitFor(() => expect(classApi.createClass).toHaveBeenCalledWith({
      schoolOrganizationId: '2088485801854763001', name: '二年级一班', sortOrder: 100
    }));

    await user.click(screen.getAllByRole('button', { name: '编辑' })[0]);
    const nameInput = screen.getByLabelText('班级名称');
    await user.clear(nameInput);
    await user.type(nameInput, '一年级精英班');
    await user.click(screen.getByRole('button', { name: '保存修改' }));
    await waitFor(() => expect(classApi.updateClass).toHaveBeenCalledWith(
      '2088485801854763002', { name: '一年级精英班', sortOrder: 10, versionNo: 2 }
    ));

    await user.click(screen.getAllByRole('button', { name: '停用' })[0]);
    await user.click(await screen.findByRole('button', { name: '确认停用' }));
    await waitFor(() => expect(classApi.disableClass)
      .toHaveBeenCalledWith('2088485801854763002', 2));

    await user.click(screen.getAllByRole('button', { name: '启用' })[0]);
    await user.click(await screen.findByRole('button', { name: '确认启用' }));
    await waitFor(() => expect(classApi.enableClass)
      .toHaveBeenCalledWith('2088485801854763003', 3));
  });
});
