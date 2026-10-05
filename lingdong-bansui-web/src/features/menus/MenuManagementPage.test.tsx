import { fireEvent, render, screen, within, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { menuApi, type MenuNode } from '../../api/menus';
import { iamApi } from '../../api/iam';
import { ApiRequestError } from '../../api/http';
import { MenuManagementPage } from './MenuManagementPage';

vi.mock('../../api/menus', () => ({ menuApi: { list: vi.fn(), create: vi.fn(), update: vi.fn(), reorder: vi.fn(), move: vi.fn(), createButtons: vi.fn() } }));
vi.mock('../../api/iam', () => ({ iamApi: { listPermissions: vi.fn() } }));
const node = (id: string, name: string, parentId: string | null, sortOrder: number): MenuNode => ({ id, code: id, name, parentId, sortOrder, type: parentId ? 'PAGE' : 'DIRECTORY', route: parentId ? '/users' : null, icon: null, permissionCode: null, status: 'ENABLED', version: '9007199254740993' });
const nodes = [node('dir', '系统', null, 0), node('a', '用户', 'dir', 1), node('b', '组织', 'dir', 2)];
function renderPage(manage = true) { return render(<MenuManagementPage currentUser={{userId:'u',sessionId:'s',username:'admin',displayName:'管理员',clientType:'WEB',roleCodes:manage ? ['SYS_ADMIN'] : ['SYS_AUDITOR'],permissionCodes:manage ? ['MENU_READ','MENU_MANAGE'] : ['MENU_READ']}} />); }
function tree(){return within(screen.getByRole('tree'));}
describe('菜单配置', () => {
  it.each(['/menu-management','/dashboard'])('含嵌套核心页面%s的祖先目录不能停用，编辑状态也锁定', async (route) => {
    vi.mocked(menuApi.list).mockResolvedValue([
      {...nodes[0],name:'权限管理'},
      {...nodes[0],id:'nested',code:'nested',parentId:'dir',name:'子目录'},
      {...nodes[1],id:'core',code:'MENU_MANAGEMENT',name:'菜单管理',parentId:'nested',route}
    ]);
    renderPage();await tree().findByText('权限管理');
    expect(screen.getByRole('button',{name:'停用-权限管理'})).toBeDisabled();
    fireEvent.click(screen.getByRole('button',{name:'编辑-权限管理'}));
    expect(within(screen.getByRole('dialog')).getByLabelText('状态')).toBeDisabled();
    expect(menuApi.update).not.toHaveBeenCalled();
  });
  it('新增页面必须填写编码和页面访问URL', async () => {
    renderPage();await tree().findByText('系统');
    fireEvent.click(screen.getByRole('button',{name:'新增下级'}));
    const drawer=screen.getByRole('dialog');
    fireEvent.change(within(drawer).getByLabelText('名称'),{target:{value:'新页面'}});
    fireEvent.click(within(drawer).getByRole('button',{name:/保\s*存/}));
    expect(await within(drawer).findByText('请输入编码')).toBeInTheDocument();
    expect(await within(drawer).findByText('请输入页面访问URL')).toBeInTheDocument();
    expect(menuApi.create).not.toHaveBeenCalled();
  });
  it('未知页面URL不能保存，防止创建后菜单静默消失', async () => {
    vi.mocked(menuApi.list).mockResolvedValue([{...nodes[1], route:'/unknown-page',name:'未知页面',parentId:null}]);
    renderPage(); await tree().findByText('未知页面');
    fireEvent.click(screen.getByRole('button',{name:'编辑-未知页面'}));
    const drawer=screen.getByRole('dialog');
    expect(within(drawer).getByLabelText('编码')).toBeDisabled();
    fireEvent.click(within(drawer).getByRole('button',{name:/保\s*存/}));
    expect(await within(drawer).findByText('请输入已实现页面的访问URL')).toBeInTheDocument();
    expect(menuApi.update).not.toHaveBeenCalled();
  });
  it('页面访问URL可手填，保存时去除首尾空格', async () => {
    renderPage(); await tree().findByText('用户');fireEvent.click(tree().getByText('用户'));
    fireEvent.click(screen.getByRole('button',{name:'编辑-用户'}));
    const drawer=screen.getByRole('dialog');
    const input=within(drawer).getByRole('textbox',{name:'页面访问URL'});
    expect(input).toHaveValue('/users');
    fireEvent.change(input,{target:{value:' /organizations '}});
    fireEvent.click(within(drawer).getByRole('button',{name:/保\s*存/}));
    await waitFor(()=>expect(menuApi.update).toHaveBeenCalledWith('a',expect.objectContaining({route:'/organizations'})));
  });
  it('选中节点后在右侧集中操作，新增下级预填当前父级', async () => {
    renderPage(); await tree().findByText('系统');
    expect(screen.getAllByRole('button',{name:/编辑-/})).toHaveLength(1);
    fireEvent.click(screen.getByRole('button',{name:'新增下级'}));
    expect(screen.getByRole('dialog')).toHaveTextContent('系统');
  });
  it('编辑原展示按钮时统一启用独立权限且保留组件编码', async () => {
    const button: MenuNode = {...node('detail','查看详情','a',10),type:'BUTTON',code:'system-tasks.detail',route:null,grantable:false};
    vi.mocked(menuApi.list).mockResolvedValue([...nodes,button]);
    renderPage(); await tree().findByText('用户');
    fireEvent.click(tree().getByText('用户').closest('.ant-tree-treenode')!.querySelector('.ant-tree-switcher')!);
    await tree().findByText('查看详情');
    fireEvent.click(tree().getByText('查看详情')); fireEvent.click(screen.getByRole('button',{name:'编辑-查看详情'}));
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button',{name:/保\s*存/}));
    await waitFor(()=>expect(menuApi.update).toHaveBeenCalledWith('detail',expect.objectContaining({code:'system-tasks.detail',permissionCode:'system-tasks.detail',grantable:true})));
  });
  beforeEach(() => { vi.clearAllMocks(); vi.mocked(menuApi.list).mockResolvedValue(nodes); vi.mocked(iamApi.listPermissions).mockResolvedValue([]); vi.mocked(menuApi.reorder).mockResolvedValue(undefined); vi.mocked(menuApi.update).mockResolvedValue(nodes[1]); });
  it('只读用户看树但不显示修改操作', async () => { renderPage(false); await waitFor(()=>expect(tree().getByText('用户')).toBeInTheDocument()); expect(screen.queryByRole('button',{name:'新增菜单'})).not.toBeInTheDocument(); expect(screen.queryByRole('button',{name:'编辑-用户'})).not.toBeInTheDocument(); });
  it('筛选匹配项保留父级并移除不匹配兄弟', async () => { renderPage(); await tree().findByText('用户'); fireEvent.change(screen.getByLabelText('名称'),{target:{value:'用户'}}); fireEvent.click(screen.getByRole('button',{name:/查\s*询/})); await waitFor(()=>expect(tree().queryByText('组织')).not.toBeInTheDocument()); expect(tree().getByText('系统')).toBeInTheDocument(); });
  it('使用拖动树并移除上下移操作，页面支持批量新增按钮', async () => { renderPage(); await tree().findByText('组织'); expect(screen.queryByRole('button',{name:'上移-组织'})).not.toBeInTheDocument(); expect(screen.queryByRole('button',{name:'下移-组织'})).not.toBeInTheDocument(); expect(screen.getByRole('tree')).toBeInTheDocument(); fireEvent.click(tree().getByText('用户')); fireEvent.click(screen.getByRole('button',{name:'批量新增按钮-用户'})); expect(screen.getByRole('dialog')).toHaveTextContent('批量新增按钮'); });
  it('停用携带完整节点及版本', async () => { renderPage(); await tree().findByText('用户'); fireEvent.click(tree().getByText('用户')); fireEvent.click(screen.getByRole('button',{name:'停用-用户'})); await waitFor(() => expect(menuApi.update).toHaveBeenCalledWith('a', expect.objectContaining({status:'DISABLED',version:'9007199254740993',parentId:'dir',route:'/users'}))); });
  it('版本冲突后刷新树并保留错误提示', async () => {
    vi.mocked(menuApi.update).mockRejectedValueOnce(new ApiRequestError(409,'CONFLICT','菜单已变更'));
    vi.mocked(menuApi.list).mockResolvedValueOnce(nodes).mockResolvedValue([{...nodes[0]}, {...nodes[1], name:'最新用户',version:'9007199254740994'},nodes[2]]);
    renderPage(); await tree().findByText('用户'); fireEvent.click(tree().getByText('用户')); fireEvent.click(screen.getByRole('button',{name:'停用-用户'}));
    await tree().findByText('最新用户'); expect(screen.getByRole('alert')).toHaveTextContent('菜单已变更');
  });
  it('抽屉中排除自身和子孙父级候选', async () => { renderPage(); await tree().findByText('系统'); fireEvent.click(screen.getByRole('button',{name:'编辑-系统'})); const drawer = screen.getByRole('dialog'); expect(within(drawer).getByLabelText('编码')).toBeDisabled(); fireEvent.mouseDown(within(drawer).getByLabelText('父级')); expect(document.querySelector('.ant-select-dropdown [title="系统"]')).not.toBeInTheDocument(); });
  it('新增目录提交完整字段', async () => { renderPage(); await tree().findByText('系统'); fireEvent.click(screen.getByRole('button',{name:'新增菜单'})); const drawer=screen.getByRole('dialog'); fireEvent.change(within(drawer).getByLabelText('编码'),{target:{value:'extra'}}); fireEvent.change(within(drawer).getByLabelText('名称'),{target:{value:'其他'}}); fireEvent.click(within(drawer).getByRole('button',{name:/保\s*存/})); await waitFor(()=>expect(menuApi.create).toHaveBeenCalledWith({code:'extra',name:'其他',type:'DIRECTORY',parentId:null,route:null,icon:null,permissionCode:null,grantable:false,sortOrder:0,status:'ENABLED'})); });
  it('编辑保留不可变编码与版本', async () => { renderPage(); await tree().findByText('系统'); fireEvent.click(screen.getByRole('button',{name:'编辑-系统'})); const drawer=screen.getByRole('dialog'); fireEvent.change(within(drawer).getByLabelText('名称'),{target:{value:'系统管理'}}); fireEvent.click(within(drawer).getByRole('button',{name:/保\s*存/})); await waitFor(()=>expect(menuApi.update).toHaveBeenCalledWith('dir',expect.objectContaining({code:'dir',name:'系统管理',version:'9007199254740993'}))); });
  it('关键入口禁止停用和移动父级', async () => { vi.mocked(menuApi.list).mockResolvedValue([{...nodes[1],code:'dashboard',name:'工作台',route:'/dashboard',parentId:null}]); renderPage(); await tree().findByText('工作台'); expect(screen.getByRole('button',{name:'停用-工作台'})).toBeDisabled(); fireEvent.click(screen.getByRole('button',{name:'编辑-工作台'})); const drawer=screen.getByRole('dialog'); expect(within(drawer).getByLabelText('父级')).toBeDisabled(); expect(within(drawer).getByLabelText('页面访问URL')).toBeDisabled(); });
});


