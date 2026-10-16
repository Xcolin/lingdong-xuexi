import {ApiRequestError} from '../../api/http';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { OrganizationManagementPage, dragMoveViolation } from './OrganizationManagementPage';

const organizationApi = vi.hoisted(() => ({
  listAdminDivisions: vi.fn(async()=>[{code:'500000',name:'重庆市'}]),
  listTypes: vi.fn(),
  createType: vi.fn(),
  updateType: vi.fn(), changeTypeStatus: vi.fn(), deleteType: vi.fn(),
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
  listCampuses: vi.fn(), listGrades: vi.fn(), listSchools: vi.fn(), listClasses: vi.fn(), createClass: vi.fn(),
  updateClass: vi.fn(), disableClass: vi.fn(), enableClass: vi.fn()
}));

vi.mock('../../api/organization', () => ({ organizationApi }));
vi.mock('../../api/classes', () => ({ classApi }));
vi.mock('./OrganizationMembersDrawer',()=>({OrganizationMembersDrawer:({organization,embedded}:{organization:{id:string};embedded?:boolean})=><div data-testid="organization-members" data-organization-id={organization.id} data-embedded={String(embedded)}/> }));

describe('组织管理页面', () => {
  it('无类型目录权限时内置组织类型仍显示中文',async()=>{
    organizationApi.listTree.mockResolvedValue([
      {id:'family',code:'F',name:'家庭甲',typeCode:'FAMILY',parentId:null,status:'ENABLED',effectiveStatus:'ENABLED',versionNo:1,children:[]},
      {id:'training',code:'T',name:'培训甲',typeCode:'TRAINING_INSTITUTION',parentId:null,status:'ENABLED',effectiveStatus:'ENABLED',versionNo:1,children:[]}
    ]);
    render(<OrganizationManagementPage currentUser={{...systemAdministrator,roleCodes:['PARENT'],permissionCodes:['ORG_NODE_READ']}} organizationManagementEnabled/>);
    expect(await screen.findByText('培训机构')).toBeInTheDocument();
    expect(screen.getAllByText('家庭').length).toBeGreaterThan(0);
    expect(screen.queryByText('FAMILY')).not.toBeInTheDocument();
    expect(screen.queryByText('TRAINING_INSTITUTION')).not.toBeInTheDocument();
    await waitFor(()=>expect(organizationApi.listAdminDivisions).toHaveBeenCalled());
  });
  it('全局组织类型读取403不阻断家庭管理员的组织用户',async()=>{
    organizationApi.listTypes.mockRejectedValue(new ApiRequestError(403,'ACCESS_DENIED','无全局类型范围'));
    render(<OrganizationManagementPage currentUser={{...systemAdministrator,roleCodes:['PARENT','PARENT_ADMIN'],permissionCodes:['ORG_NODE_READ','ORG_TYPE_READ','ORG_MEMBER_READ']}} organizationManagementEnabled/>);
    expect(await screen.findByRole('tab',{name:'组织用户'})).toBeInTheDocument();
    expect(await screen.findByTestId('organization-members')).toBeInTheDocument();
  });
  it('家庭管理员有组织用户查询权限即可显示组织用户，无需用户目录权限',async()=>{
    render(<OrganizationManagementPage currentUser={{...systemAdministrator,roleCodes:['PARENT','PARENT_ADMIN'],permissionCodes:['ORG_NODE_READ','ORG_MEMBER_READ']}} organizationManagementEnabled/>);
    expect(await screen.findByRole('tab',{name:'组织用户'})).toHaveAttribute('aria-selected','true');
    expect(await screen.findByTestId('organization-members')).toBeInTheDocument();
  });
  it('普通家长查看家庭信息，不显示成员管理和写入操作',async()=>{
    organizationApi.listTree.mockResolvedValue([{id:'family',code:'FAMILY_ONE',name:'我的家庭',typeCode:'FAMILY',parentId:null,status:'ENABLED',effectiveStatus:'ENABLED',versionNo:1,children:[]}]);
    render(<OrganizationManagementPage currentUser={{...systemAdministrator,roleCodes:['PARENT'],permissionCodes:['ORG_NODE_READ']}} organizationManagementEnabled/>);
    expect(await screen.findByRole('tab',{name:'组织信息'})).toHaveAttribute('aria-selected','true');
    expect(screen.queryByRole('tab',{name:'组织用户'})).not.toBeInTheDocument();
    expect(screen.queryByRole('button',{name:'新增组织节点'})).not.toBeInTheDocument();
  });
  it('普通角色直接展示组织架构且隐藏管理选项卡',async()=>{
    render(<OrganizationManagementPage currentUser={{...systemAdministrator,roleCodes:['ALL_ROLE_TEST']}} organizationManagementEnabled/>);
    await screen.findByRole('tab',{name:'组织信息'});
    for(const name of ['组织架构','组织类型','变更记录']) expect(screen.queryByRole('tab',{name})).not.toBeInTheDocument();
    expect(organizationApi.listChanges).not.toHaveBeenCalled();
  });
  it('成员面板直接嵌入选中组织，新增下级自动选择当前上级', async () => {
    render(<OrganizationManagementPage currentUser={systemAdministrator} organizationManagementEnabled />);
    await userEvent.click(await screen.findByRole('tab',{name:'组织用户'}));
    expect(await screen.findByTestId('organization-members')).toHaveAttribute('data-embedded','true');
    expect(screen.queryByRole('button', {name:'组织用户'})).not.toBeInTheDocument();
    expect(screen.getByRole('tab', {name:'组织用户'})).toBeInTheDocument();
    await userEvent.click(await screen.findByRole('button',{name:'新增下级'}));
    expect(screen.getByRole('dialog')).toHaveTextContent('东部区域');
  });
  const systemAdministrator = {
    userId: '1874244142494646207', sessionId: '1874244142494646208',
    username: 'system_admin', displayName: '系统管理员', clientType: 'WEB' as const,
    roleCodes: ['SYS_ADMIN'], permissionCodes: ['ORG_NODE_READ', 'ORG_TYPE_READ','ORG_MEMBER_READ','ORG_NODE_CREATE','ORG_NODE_UPDATE']
  };

  it('系统管理员可编辑内置组织类型，仍禁止删除和启停',async()=>{
    render(<OrganizationManagementPage currentUser={{...systemAdministrator,permissionCodes:[...systemAdministrator.permissionCodes,'ORG_TYPE_UPDATE','ORG_TYPE_DELETE','ORG_TYPE_STATUS_CHANGE']}} organizationManagementEnabled />);
    await userEvent.click(await screen.findByRole('tab',{name:'组织类型'}));
    const row=screen.getAllByText('REGION').find(element=>element.closest('tr'))!.closest('tr')!;
    expect(within(row).getByRole('button',{name:'编辑'})).toBeEnabled();
    expect(within(row).getByRole('button',{name:'删除'})).toBeDisabled();
    expect(within(row).getByRole('radio',{name:'启用'})).toBeDisabled();
    expect(within(row).getByRole('radio',{name:'停用'})).toBeDisabled();
  });

  it('自定义类型停用失败保留原选中状态',async()=>{
    organizationApi.listTypes.mockResolvedValue([{id:'custom',code:'CUSTOM',name:'自定义组织',builtIn:false,status:'ENABLED',sortOrder:100}]);
    organizationApi.changeTypeStatus.mockRejectedValue(new Error('类型使用中'));
    render(<OrganizationManagementPage currentUser={{...systemAdministrator,permissionCodes:[...systemAdministrator.permissionCodes,'ORG_TYPE_STATUS_CHANGE']}} organizationManagementEnabled />);
    await userEvent.click(await screen.findByRole('tab',{name:'组织类型'}));
    await userEvent.click(screen.getByRole('radio',{name:'停用'}).closest('label')!);
    await waitFor(()=>expect(organizationApi.changeTypeStatus).toHaveBeenCalledWith('custom','DISABLED'));
    expect(screen.getByRole('radio',{name:'启用'})).toBeChecked();
    expect(screen.getByRole('radio',{name:'停用'})).not.toBeChecked();
  });

  it('只有组织读取权限的账号没有原生拖拽写入口',async()=>{
    const view=render(<OrganizationManagementPage currentUser={{...systemAdministrator,permissionCodes:['ORG_NODE_READ']}} organizationManagementEnabled/>);
    await screen.findByRole('tree');
    expect(view.container.querySelector('[draggable="true"]')).toBeNull();
  });
  it('自定义审核角色可访问变更记录，组织类型仍限定系统管理员',async()=>{
    render(<OrganizationManagementPage currentUser={{...systemAdministrator,roleCodes:['CUSTOM_AUDITOR'],permissionCodes:['ORG_NODE_READ','ORG_NODE_CHANGE_REVIEW']}} organizationManagementEnabled/>);
    expect(await screen.findByRole('tab',{name:'变更记录'})).toBeInTheDocument();
    expect(screen.queryByRole('tab',{name:'组织类型'})).toBeNull();
  });
  beforeEach(() => {
    vi.clearAllMocks();
    classApi.listCampuses.mockResolvedValue([]);
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
    classApi.listGrades.mockResolvedValue([]);
    classApi.listSchools.mockResolvedValue([{id:"s1",name:"可访问学校",typeCode:"SCHOOL",effectiveStatus:"ENABLED",children:[]}]);
    classApi.listClasses.mockResolvedValue([]);
  });

  it('加载组织目录并创建组织类型', async () => {
    const user = userEvent.setup();
    render(<OrganizationManagementPage currentUser={{...systemAdministrator,permissionCodes:[...systemAdministrator.permissionCodes,'ORG_TYPE_CREATE']}} organizationManagementEnabled />);

    await user.click(screen.getByRole('tab', { name: '组织类型' }));
    expect(await within(screen.getByRole('tabpanel',{name:'组织类型'})).findByText('区域')).toBeVisible();
    await user.click(screen.getByRole('tab', { name: '组织架构' }));
    expect((await screen.findAllByText('测试学校'))[0]).toBeInTheDocument();

    await user.click(screen.getByRole('tab',{name:'组织类型'}));
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

    expect((await screen.findAllByText('测试学校'))[0]).toBeInTheDocument();

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

    expect((await screen.findAllByText('测试学校'))[0]).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '家长换号核验' })).not.toBeInTheDocument();

    rerender(<OrganizationManagementPage
      currentUser={{
        userId: '1874244142494646207',
        sessionId: '1874244142494646208',
        username: 'school_admin',
        displayName: '学校管理员',
        clientType: 'WEB',
        roleCodes: ['ALL_ROLE_TEST'],
        permissionCodes: ['CLASS_READ']
      }}
      parentMobileManualRecoveryEnabled
    />);
    expect(await screen.findByRole('button', { name: '家长换号核验' })).toBeInTheDocument();
  });

  it('仅向机构管理员显示已启用的学生账号注销入口', async () => {
    const organizationAdministrator = {
      userId: '1874244142494646207',
      sessionId: '1874244142494646208',
      username: 'school_admin',
      displayName: '学校管理员',
      clientType: 'WEB' as const,
      roleCodes: ['ALL_ROLE_TEST'],
      permissionCodes: ['CLASS_READ']
    };
    const { rerender } = render(
      <OrganizationManagementPage currentUser={organizationAdministrator} />
    );

    expect(await screen.findByRole('heading', { name: '学校与班级' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '学生账号注销' })).not.toBeInTheDocument();

    rerender(
      <OrganizationManagementPage
      currentUser={organizationAdministrator}
        studentAccountCancellationEnabled
      />
    );
    expect(await screen.findByRole('button', { name: '学生账号注销' })).toBeInTheDocument();

    rerender(
      <OrganizationManagementPage
        currentUser={{ ...organizationAdministrator, roleCodes: ['TEACHER'], permissionCodes: [] }}
        studentAccountCancellationEnabled
      />
    );
    expect(screen.queryByRole('button', { name: '学生账号注销' })).not.toBeInTheDocument();
  });

  it('区分自身状态与上级停用造成的有效状态', async () => {
    render(<OrganizationManagementPage currentUser={systemAdministrator} organizationManagementEnabled />);

    expect((await screen.findAllByText('测试学校'))[0]).toBeInTheDocument();
    expect(screen.getByText('上级已停用')).toBeInTheDocument();
    expect(screen.queryByText('自身停用')).not.toBeInTheDocument();
  });

  it('只有审核权限时不展示组织类型或变更记录', async () => {
    organizationApi.listChanges.mockResolvedValue([{ changeId: '1874244142494646298', taskId: '1874244142494646299', organizationNameSnapshot: '待审核学校', changeType: 'DISABLE', executionStatus: 'PENDING', taskStatus: 'PENDING_REVIEW', submittedAt: '2026-08-15T10:00:00' }]);
    render(<OrganizationManagementPage
      currentUser={{ ...systemAdministrator, username: 'auditor', displayName: '系统审核员', roleCodes: ['ALL_ROLE_TEST'], permissionCodes: ['ORG_NODE_CHANGE_REVIEW'] }}
      organizationManagementEnabled
    />);

    expect(await screen.findByText('当前身份没有组织架构访问权限')).toBeInTheDocument();
    expect(organizationApi.listChanges).not.toHaveBeenCalled();
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
      username: 'school_admin', displayName: '学校管理员', roleCodes: ['ALL_ROLE_TEST'], permissionCodes: ['CLASS_READ']
    };
    const { rerender } = render(
      <OrganizationManagementPage currentUser={organizationAdministrator} />
    );

    expect(screen.queryByRole('heading', { name: '可访问学校' })).not.toBeInTheDocument();
    expect(classApi.listClasses).not.toHaveBeenCalled();

    rerender(
      <OrganizationManagementPage
        currentUser={organizationAdministrator}
        classManagementEnabled
      />
    );
    expect(await screen.findByRole('heading', { name: '可访问学校' })).toBeInTheDocument();
    expect(classApi.listClasses).toHaveBeenCalledTimes(1);

    rerender(
      <OrganizationManagementPage
        currentUser={systemAdministrator}
        organizationManagementEnabled
        classManagementEnabled
      />
    );
    expect(await screen.findByRole('tab', { name: '组织架构' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: '可访问学校' })).not.toBeInTheDocument();
  });

  it('平台组织视图把年级与低频业务整合到选中学校并保留能力开关', async () => {
    const user = userEvent.setup();
    render(<OrganizationManagementPage currentUser={{...systemAdministrator,permissionCodes:[...systemAdministrator.permissionCodes,'CLASS_READ']}} organizationManagementEnabled classManagementEnabled studentOrganizationRelationshipEnabled />);
    await user.click((await screen.findAllByText('测试学校'))[0]);
    expect(screen.queryByRole('tab',{name:'机构业务'})).not.toBeInTheDocument();
    expect(screen.getByRole('tab',{name:'组织用户'})).toHaveAttribute('aria-selected','true');
    await user.click(screen.getByRole('tab',{name:'校区与年级'}));
    expect(await screen.findByRole('button',{name:'新增年级'})).toBeInTheDocument();
    expect(classApi.listClasses).toHaveBeenCalledTimes(1);
    expect(await screen.findByRole('button',{name:'学员关系'})).toBeInTheDocument();
    expect(screen.getByRole('button',{name:'配置教师班级'})).toBeInTheDocument();
  });
  it('仅组织目录读取权限不调用班级接口', async () => {
    render(<OrganizationManagementPage currentUser={systemAdministrator} organizationManagementEnabled classManagementEnabled/>);
    await userEvent.click((await screen.findAllByText('测试学校'))[0]);
    expect(classApi.listClasses).not.toHaveBeenCalled();
    expect(classApi.listGrades).not.toHaveBeenCalled();
    expect(screen.getByRole('tab',{name:'组织信息'})).toBeInTheDocument();
  });

});

describe('拖拽防环判定', () => {
  const leaf = (id: string, parentId: string | null): any => ({ id, parentId, children: [] });
  // A(根) → B → C；A 的另一子节点 D
  const C = leaf('C', 'B');
  const B: any = { id: 'B', parentId: 'A', children: [C] };
  const D = leaf('D', 'A');
  const A: any = { id: 'A', parentId: null, children: [B, D] };

  it('空隙换父到非自身后代：合法（从下级拖出与同级平级）', () => {
    expect(dragMoveViolation(C, D, true)).toBeNull();
  });
  it('空隙换父到自己的后代之下：违规', () => {
    expect(dragMoveViolation(A, C, true)).toBe('不能移动到自身或其下级组织');
  });
  it('空隙同父重排：合法', () => {
    expect(dragMoveViolation(B, D, true)).toBeNull();
  });
  it('拖入自己的后代内部：违规', () => {
    expect(dragMoveViolation(A, C, false)).toBe('不能移动到自身或其下级组织');
  });
  it('把祖先拖入后代内部：违规', () => {
    expect(dragMoveViolation(C, A, false)).toBe('不能移动到自身或其下级组织');
  });
  it('拖入无祖先关系的节点内部：合法', () => {
    expect(dragMoveViolation(D, B, false)).toBeNull();
  });
  it('拖到自身：违规', () => {
    expect(dragMoveViolation(B, B, false)).toBe('不能移动到自身或其下级组织');
  });
});
