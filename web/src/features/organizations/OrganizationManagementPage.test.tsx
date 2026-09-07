import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { OrganizationManagementPage } from './OrganizationManagementPage';

const organizationApi = vi.hoisted(() => ({
  listTypes: vi.fn(),
  createType: vi.fn(),
  listTree: vi.fn(),
  createOrganization: vi.fn(),
  updateOrganization: vi.fn(),
  enableOrganization: vi.fn(),
  createChange: vi.fn(),
  listChanges: vi.fn(),
  approveChange: vi.fn(),
  rejectChange: vi.fn()
}));

const classApi = vi.hoisted(() => ({
  listSchools: vi.fn(), listClasses: vi.fn(), createClass: vi.fn(),
  updateClass: vi.fn(), disableClass: vi.fn(), enableClass: vi.fn()
}));

vi.mock('../../api/organization', () => ({ organizationApi }));
vi.mock('../../api/classes', () => ({ classApi }));

describe('组织管理页面', () => {
  const systemAdministrator = {
    userId: '1874244142494646207', sessionId: '1874244142494646208',
    username: 'system_admin', displayName: '系统管理员', clientType: 'WEB' as const,
    roleCodes: ['SYS_ADMIN'], permissionCodes: []
  };

  beforeEach(() => {
    vi.clearAllMocks();
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
        effectiveStatus: 'ENABLED',
        versionNo: 1,
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
            effectiveStatus: 'DISABLED',
            versionNo: 3,
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
      effectiveStatus: 'ENABLED',
      versionNo: 1,
      children: []
    });
    organizationApi.updateOrganization.mockResolvedValue(undefined);
    organizationApi.enableOrganization.mockResolvedValue(undefined);
    organizationApi.createChange.mockResolvedValue(undefined);
    organizationApi.listChanges.mockResolvedValue([]);
    organizationApi.approveChange.mockResolvedValue(undefined);
    organizationApi.rejectChange.mockResolvedValue(undefined);
    classApi.listSchools.mockResolvedValue([]);
    classApi.listClasses.mockResolvedValue([]);
  });

  it('加载组织目录并创建组织类型', async () => {
    const user = userEvent.setup();
    render(<OrganizationManagementPage currentUser={systemAdministrator} organizationManagementEnabled />);

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
    render(<OrganizationManagementPage currentUser={systemAdministrator} organizationManagementEnabled />);

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

    render(<OrganizationManagementPage currentUser={systemAdministrator} organizationManagementEnabled />);

    expect(await screen.findByRole('alert')).toHaveTextContent('无权执行此操作');
  });

  it('仅在能力开关启用时显示家长换号核验入口', async () => {
    const { rerender } = render(<OrganizationManagementPage currentUser={systemAdministrator} organizationManagementEnabled />);

    expect(await screen.findByText('测试学校')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '家长换号核验' })).not.toBeInTheDocument();

    rerender(<OrganizationManagementPage
      currentUser={{
        userId: '1874244142494646207',
        sessionId: '1874244142494646208',
        username: 'school_admin',
        displayName: '学校管理员',
        clientType: 'WEB',
        roleCodes: ['ORG_ADMIN'],
        permissionCodes: []
      }}
      parentMobileManualRecoveryEnabled
    />);
    expect(screen.getByRole('button', { name: '家长换号核验' })).toBeInTheDocument();
  });

  it('仅向机构管理员显示已启用的学生账号注销入口', async () => {
    const organizationAdministrator = {
      userId: '1874244142494646207',
      sessionId: '1874244142494646208',
      username: 'school_admin',
      displayName: '学校管理员',
      clientType: 'WEB' as const,
      roleCodes: ['ORG_ADMIN'],
      permissionCodes: []
    };
    const { rerender } = render(
      <OrganizationManagementPage currentUser={organizationAdministrator} />
    );

    expect(await screen.findByRole('heading', { name: '机构业务' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '学生账号注销' })).not.toBeInTheDocument();

    rerender(
      <OrganizationManagementPage
      currentUser={organizationAdministrator}
        studentAccountCancellationEnabled
      />
    );
    expect(screen.getByRole('button', { name: '学生账号注销' })).toBeInTheDocument();

    rerender(
      <OrganizationManagementPage
        currentUser={{ ...organizationAdministrator, roleCodes: ['TEACHER'] }}
        studentAccountCancellationEnabled
      />
    );
    expect(screen.queryByRole('button', { name: '学生账号注销' })).not.toBeInTheDocument();
  });

  it('区分自身状态与上级停用造成的有效状态', async () => {
    render(<OrganizationManagementPage currentUser={systemAdministrator} organizationManagementEnabled />);

    expect(await screen.findByText('测试学校')).toBeInTheDocument();
    expect(screen.getByText('上级已停用')).toBeInTheDocument();
    expect(screen.queryByText('自身停用')).not.toBeInTheDocument();
  });

  it('系统审核员只加载组织变更审核列表', async () => {
    organizationApi.listChanges.mockResolvedValue([{ changeId: '1874244142494646298', taskId: '1874244142494646299', organizationNameSnapshot: '待审核学校', changeType: 'DISABLE', executionStatus: 'PENDING', taskStatus: 'PENDING_REVIEW', submittedAt: '2026-08-15T10:00:00' }]);
    render(<OrganizationManagementPage
      currentUser={{ ...systemAdministrator, username: 'auditor', displayName: '系统审核员', roleCodes: ['SYS_AUDITOR'] }}
      organizationManagementEnabled
    />);

    expect(await screen.findByText('待审核学校')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '新增组织节点' })).not.toBeInTheDocument();
    expect(organizationApi.listTypes).not.toHaveBeenCalled();
    expect(organizationApi.listTree).not.toHaveBeenCalled();
  });

  it('组织管理功能停用时隐藏页面且不加载接口', async () => {
    render(<OrganizationManagementPage currentUser={systemAdministrator} organizationManagementEnabled={false} />);

    expect(screen.getByText('组织管理功能当前未启用')).toBeInTheDocument();
    expect(organizationApi.listTypes).not.toHaveBeenCalled();
    expect(organizationApi.listTree).not.toHaveBeenCalled();
    expect(organizationApi.listChanges).not.toHaveBeenCalled();
  });

  it('班级开关仅控制机构管理员班级面板且不影响平台组织视图', async () => {
    const organizationAdministrator = {
      ...systemAdministrator,
      username: 'school_admin', displayName: '学校管理员', roleCodes: ['ORG_ADMIN']
    };
    const { rerender } = render(
      <OrganizationManagementPage currentUser={organizationAdministrator} />
    );

    expect(screen.queryByRole('heading', { name: '班级管理' })).not.toBeInTheDocument();
    expect(classApi.listClasses).not.toHaveBeenCalled();

    rerender(
      <OrganizationManagementPage
        currentUser={organizationAdministrator}
        classManagementEnabled
      />
    );
    expect(await screen.findByRole('heading', { name: '班级管理' })).toBeInTheDocument();
    expect(classApi.listClasses).toHaveBeenCalledTimes(1);

    rerender(
      <OrganizationManagementPage
        currentUser={systemAdministrator}
        organizationManagementEnabled
        classManagementEnabled
      />
    );
    expect(await screen.findByRole('heading', { name: '组织管理' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: '班级管理' })).not.toBeInTheDocument();
  });
});
