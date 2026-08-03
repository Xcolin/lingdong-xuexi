import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { OrganizationManagementPage } from './OrganizationManagementPage';

const organizationApi = vi.hoisted(() => ({
  listTypes: vi.fn(),
  createType: vi.fn(),
  listTree: vi.fn(),
  createOrganization: vi.fn()
}));

vi.mock('../../api/organization', () => ({ organizationApi }));

describe('组织管理页面', () => {
  beforeEach(() => {
    organizationApi.listTypes.mockResolvedValue([
      { id: '1874244142494646201', code: 'REGION', name: '区域', builtIn: true, status: 'ENABLED', sortOrder: 10 },
      { id: '1874244142494646202', code: 'SCHOOL', name: '学校', builtIn: true, status: 'ENABLED', sortOrder: 20 }
    ]);
    organizationApi.listTree.mockResolvedValue([
      {
        id: '1874244142494646203',
        parentId: null,
        code: 'REGION_EAST',
        name: '东部区域',
        typeCode: 'REGION',
        path: '/REGION_EAST/',
        sortOrder: 10,
        status: 'ENABLED',
        children: [
          {
            id: '1874244142494646204',
            parentId: '1874244142494646203',
            code: 'SCHOOL_EAST_1',
            name: '测试学校',
            typeCode: 'SCHOOL',
            path: '/REGION_EAST/SCHOOL_EAST_1/',
            sortOrder: 10,
            status: 'ENABLED',
            children: []
          }
        ]
      }
    ]);
    organizationApi.createType.mockResolvedValue({
      id: '1874244142494646205',
      code: 'COMMUNITY',
      name: '社区',
      builtIn: false,
      status: 'ENABLED',
      sortOrder: 100
    });
    organizationApi.createOrganization.mockResolvedValue({
      id: '1874244142494646206',
      parentId: null,
      code: 'SCHOOL_WEST_1',
      name: '西部测试学校',
      typeCode: 'SCHOOL',
      path: '/SCHOOL_WEST_1/',
      sortOrder: 100,
      status: 'ENABLED',
      children: []
    });
  });

  it('加载组织目录并创建组织类型', async () => {
    const user = userEvent.setup();
    render(<OrganizationManagementPage />);

    expect(await screen.findByText('区域')).toBeInTheDocument();
    expect(await screen.findByText('测试学校')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '新增组织类型' }));
    await user.type(screen.getByLabelText('类型编码'), 'COMMUNITY');
    await user.type(screen.getByLabelText('类型名称'), '社区');
    await user.click(screen.getByRole('button', { name: '创建类型' }));

    await waitFor(() => {
      expect(organizationApi.createType).toHaveBeenCalledWith({
        code: 'COMMUNITY',
        name: '社区',
        sortOrder: 100
      });
    });
  });

  it('创建根组织节点', async () => {
    const user = userEvent.setup();
    render(<OrganizationManagementPage />);

    expect(await screen.findByText('测试学校')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '新增组织节点' }));
    await user.type(screen.getByLabelText('组织编码'), 'SCHOOL_WEST_1');
    await user.type(screen.getByLabelText('组织名称'), '西部测试学校');
    await user.click(screen.getByLabelText('组织类型'));
    await user.click(await screen.findByText('学校（SCHOOL）'));
    await user.click(screen.getByRole('button', { name: '创建节点' }));

    await waitFor(() => {
      expect(organizationApi.createOrganization).toHaveBeenCalledWith({
        code: 'SCHOOL_WEST_1',
        name: '西部测试学校',
        typeCode: 'SCHOOL',
        parentId: undefined,
        sortOrder: 100
      });
    });
  });

  it('服务端拒绝访问时显示权限错误', async () => {
    organizationApi.listTypes.mockRejectedValue(Object.assign(new Error('无权执行此操作'), { status: 403 }));
    organizationApi.listTree.mockRejectedValue(Object.assign(new Error('无权执行此操作'), { status: 403 }));

    render(<OrganizationManagementPage />);

    expect(await screen.findByRole('alert')).toHaveTextContent('无权执行此操作');
  });
});
