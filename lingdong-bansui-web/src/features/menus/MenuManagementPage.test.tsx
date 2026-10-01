import { fireEvent, render, screen, within, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { menuApi, type MenuNode } from '../../api/menus';
import { iamApi } from '../../api/iam';
import { MenuManagementPage } from './MenuManagementPage';

vi.mock('../../api/menus', () => ({ menuApi: { list: vi.fn(), create: vi.fn(), update: vi.fn(), reorder: vi.fn() } }));
vi.mock('../../api/iam', () => ({ iamApi: { listPermissions: vi.fn() } }));
const node = (id: string, name: string, parentId: string | null, sortOrder: number): MenuNode => ({ id, code: id, name, parentId, sortOrder, type: parentId ? 'PAGE' : 'DIRECTORY', route: parentId ? '/users' : null, icon: null, permissionCode: null, status: 'ENABLED', version: '9007199254740993' });
const nodes = [node('dir', '系统', null, 0), node('a', '用户', 'dir', 1), node('b', '组织', 'dir', 2)];
function renderPage(manage = true) { return render(<MenuManagementPage currentUser={{userId:'u',sessionId:'s',username:'admin',displayName:'管理员',clientType:'WEB',roleCodes:manage ? ['SYS_ADMIN'] : ['SYS_AUDITOR'],permissionCodes:manage ? ['MENU_READ','MENU_MANAGE'] : ['MENU_READ']}} />); }
describe('菜单配置', () => {
  beforeEach(() => { vi.clearAllMocks(); vi.mocked(menuApi.list).mockResolvedValue(nodes); vi.mocked(iamApi.listPermissions).mockResolvedValue([]); vi.mocked(menuApi.reorder).mockResolvedValue(undefined); vi.mocked(menuApi.update).mockResolvedValue(nodes[1]); });
  it('只读用户看树但不显示修改操作', async () => { renderPage(false); expect(await screen.findByText('用户')).toBeInTheDocument(); expect(screen.queryByRole('button',{name:'新增菜单'})).not.toBeInTheDocument(); expect(screen.queryByRole('button',{name:'编辑-用户'})).not.toBeInTheDocument(); });
  it('筛选匹配项保留父级并移除不匹配兄弟', async () => { renderPage(); await screen.findByText('用户'); fireEvent.change(screen.getByLabelText('名称'),{target:{value:'用户'}}); fireEvent.click(screen.getByRole('button',{name:/查\s*询/})); await waitFor(()=>expect(screen.queryByText('组织')).not.toBeInTheDocument()); expect(screen.getByText('系统')).toBeInTheDocument(); });
  it('上移发送全部兄弟和精确字符串版本', async () => { renderPage(); await screen.findByText('组织'); fireEvent.click(screen.getByRole('button',{name:'上移-组织'})); await waitFor(() => expect(menuApi.reorder).toHaveBeenCalledWith({parentId:'dir',ids:['b','a'],versions:{a:'9007199254740993',b:'9007199254740993'}})); });
  it('停用携带完整节点及版本', async () => { renderPage(); await screen.findByText('用户'); fireEvent.click(screen.getByRole('button',{name:'停用-用户'})); await waitFor(() => expect(menuApi.update).toHaveBeenCalledWith('a', expect.objectContaining({status:'DISABLED',version:'9007199254740993',parentId:'dir',route:'/users'}))); });
  it('抽屉中排除自身和子孙父级候选', async () => { renderPage(); await screen.findByText('系统'); fireEvent.click(screen.getByRole('button',{name:'编辑-系统'})); const drawer = screen.getByRole('dialog'); expect(within(drawer).getByLabelText('编码')).toBeDisabled(); fireEvent.mouseDown(within(drawer).getByLabelText('父级')); expect(screen.queryByTitle('系统')).not.toBeInTheDocument(); });
  it('新增目录提交完整字段', async () => { renderPage(); await screen.findByText('系统'); fireEvent.click(screen.getByRole('button',{name:'新增菜单'})); const drawer=screen.getByRole('dialog'); fireEvent.change(within(drawer).getByLabelText('编码'),{target:{value:'extra'}}); fireEvent.change(within(drawer).getByLabelText('名称'),{target:{value:'其他'}}); fireEvent.click(within(drawer).getByRole('button',{name:/保\s*存/})); await waitFor(()=>expect(menuApi.create).toHaveBeenCalledWith({code:'extra',name:'其他',type:'DIRECTORY',parentId:null,route:null,icon:null,permissionCode:null,sortOrder:0,status:'ENABLED'})); });
  it('编辑保留不可变编码与版本', async () => { renderPage(); await screen.findByText('系统'); fireEvent.click(screen.getByRole('button',{name:'编辑-系统'})); const drawer=screen.getByRole('dialog'); fireEvent.change(within(drawer).getByLabelText('名称'),{target:{value:'系统管理'}}); fireEvent.click(within(drawer).getByRole('button',{name:/保\s*存/})); await waitFor(()=>expect(menuApi.update).toHaveBeenCalledWith('dir',expect.objectContaining({code:'dir',name:'系统管理',version:'9007199254740993'}))); });
  it('关键入口禁止停用和移动父级', async () => { vi.mocked(menuApi.list).mockResolvedValue([{...nodes[1],code:'dashboard',name:'工作台',route:'/dashboard',parentId:null}]); renderPage(); await screen.findByText('工作台'); expect(screen.getByRole('button',{name:'停用-工作台'})).toBeDisabled(); fireEvent.click(screen.getByRole('button',{name:'编辑-工作台'})); const drawer=screen.getByRole('dialog'); expect(within(drawer).getByLabelText('父级')).toBeDisabled(); expect(within(drawer).getByLabelText('页面绑定')).toBeDisabled(); });
});


