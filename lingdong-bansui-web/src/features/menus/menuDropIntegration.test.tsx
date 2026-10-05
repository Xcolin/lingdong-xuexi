import { fireEvent,render,screen,waitFor } from '@testing-library/react';
import { describe,it,vi,expect,beforeEach } from 'vitest';
import { menuApi } from '../../api/menus';
import { MenuManagementPage } from './MenuManagementPage';
const refresh=vi.fn();
vi.mock('../../components/ConfiguredButton',()=>({ConfiguredButton:({actionKey,...props}:any)=><button {...props}/>}));
vi.mock('../../app/MenuConfiguration',()=>({useMenuConfiguration:()=>({refresh})}));
vi.mock('../../api/menus',()=>({menuApi:{list:vi.fn(),reorder:vi.fn(),move:vi.fn()}}));
vi.mock('antd',async()=>{
 const actual=await vi.importActual<typeof import('antd')>('antd');
 return {...actual,Tree:(props:any)=><><actual.Tree {...props}/><button disabled={!props.draggable} onClick={()=>props.onDrop({dragNode:{key:'b'},node:{key:'a',pos:'0-0-0'},dropToGap:true,dropPosition:-1})}>模拟同级拖动</button><button disabled={!props.draggable} onClick={()=>props.onDrop({dragNode:{key:'a'},node:{key:'other',pos:'0-1'},dropToGap:false,dropPosition:1})}>模拟跨目录拖动</button></>};
});
const nodes=[{id:'dir',code:'dir',name:'目录',parentId:null,type:'DIRECTORY',sortOrder:0,version:'1'},{id:'other',code:'other',name:'另一目录',parentId:null,type:'DIRECTORY',sortOrder:10,version:'2'},{id:'a',code:'AAA',name:'页面A',parentId:'dir',type:'PAGE',sortOrder:0,version:'9007199254740993'},{id:'b',code:'BBB',name:'页面B',parentId:'dir',type:'PAGE',sortOrder:10,version:'9007199254740994'}].map(n=>({...n,route:null,icon:null,permissionCode:null,status:'ENABLED'}));
function page(){render(<MenuManagementPage currentUser={{roleCodes:['SYS_ADMIN'],permissionCodes:['MENU_MANAGE']} as any}/>);}
describe('菜单拖动接口接线',()=>{
 beforeEach(()=>{vi.clearAllMocks();vi.mocked(menuApi.list).mockResolvedValue(nodes as any);vi.mocked(menuApi.reorder).mockResolvedValue(undefined);vi.mocked(menuApi.move).mockResolvedValue(nodes[2] as any);});
 it('间隙拖动提交完整兄弟顺序及版本并刷新导航',async()=>{page();await screen.findByText('页面A');fireEvent.click(screen.getByRole('button',{name:'模拟同级拖动'}));await waitFor(()=>expect(refresh).toHaveBeenCalledOnce());expect(menuApi.reorder).toHaveBeenCalledWith({parentId:'dir',ids:['b','a'],versions:{a:'9007199254740993',b:'9007199254740994'}});});
 it('拖入目录调用位置接口并刷新导航',async()=>{page();await screen.findByText('页面A');fireEvent.click(screen.getByRole('button',{name:'模拟跨目录拖动'}));await waitFor(()=>expect(refresh).toHaveBeenCalledOnce());expect(menuApi.move).toHaveBeenCalledWith('a',{parentId:'other',index:0,version:'9007199254740993'});});
});
