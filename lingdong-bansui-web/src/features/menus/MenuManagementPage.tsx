import { ConfiguredButton as Button } from '../../components/ConfiguredButton';
import { Alert, Drawer, Form, Input, InputNumber, Select, Space, Tag, TreeSelect } from 'antd';
import { useEffect, useState } from 'react';
import type { CurrentUser } from '../../api/auth';
import { iamApi, type Permission } from '../../api/iam';
import { menuApi, type CreateMenuInput, type MenuNode } from '../../api/menus';
import { menuActionCatalog, menuPageCatalog } from '../../app/menuCatalog';
import { useMenuConfiguration } from '../../app/MenuConfiguration';
import { ViewportTable } from '../../components/ViewportTable';

type TreeRow = MenuNode & { children?: TreeRow[] };
type ParentChoice = {title:string;value:string;selectable:boolean;children?:ParentChoice[]};
type Filter = { name: string; type?: MenuNode['type']; status?: MenuNode['status'] };
const typeNames = { DIRECTORY: '目录', PAGE: '页面', BUTTON: '按钮' };
const protectedNode = (node?: MenuNode) => node?.route === '/dashboard' || node?.route === '/menu-management' || node?.code === 'menu-management' || node?.code.startsWith('menu-management.');
const sorted = (nodes: MenuNode[]) => [...nodes].sort((a,b) => a.sortOrder-b.sortOrder || a.code.localeCompare(b.code));
export function menuTree(nodes: MenuNode[], filter: Filter, parentId: string | null = null): TreeRow[] {
  return sorted(nodes.filter(n => n.parentId === parentId)).flatMap(n => {
    const children = menuTree(nodes, filter, n.id);
    const matches = n.name.includes(filter.name.trim()) && (!filter.type || n.type === filter.type) && (!filter.status || n.status === filter.status);
    return matches || children.length ? [{...n, ...(children.length ? {children} : {})}] : [];
  });
}
export function MenuManagementPage({ currentUser }: { currentUser: CurrentUser }) {
  const canManage = currentUser.roleCodes.includes('SYS_ADMIN') && !currentUser.roleCodes.includes('SYS_AUDITOR') && currentUser.permissionCodes.includes('MENU_MANAGE');
  const configuration = useMenuConfiguration();
  const [nodes,setNodes] = useState<MenuNode[]>([]);
  const [permissions,setPermissions] = useState<Permission[]>([]);
  const [loading,setLoading] = useState(true);
  const [busy,setBusy] = useState(false);
  const [error,setError] = useState<string>();
  const [draft,setDraft] = useState<Filter>({name:''});
  const [filter,setFilter] = useState<Filter>({name:''});
  const [open,setOpen] = useState(false);
  const [expanded,setExpanded] = useState<string[]>([]);
  const [editing,setEditing] = useState<MenuNode>();
  const [form] = Form.useForm<CreateMenuInput>();
  const type = Form.useWatch('type',form) ?? 'DIRECTORY';
  const selectedCode = Form.useWatch('code',form);
  useEffect(() => { void load(); },[]);
  async function load() {
    setLoading(true); setError(undefined);
    try { const [menus,available] = await Promise.all([menuApi.list(),canManage ? iamApi.listPermissions().catch(() => []) : Promise.resolve([])]); setNodes(menus); setPermissions(available); setExpanded(menus.filter(n=>n.type==='DIRECTORY').map(n=>n.id)); }
    catch(e) { setError(e instanceof Error ? e.message : '菜单加载失败'); }
    finally { setLoading(false); }
  }
  async function mutate(action: () => Promise<unknown>) {
    if (!canManage || busy) return;
    setBusy(true); setError(undefined);
    try { await action(); await load(); await configuration?.refresh(); return true; }
    catch(e) { setError(e instanceof Error ? e.message : '操作失败，请刷新后重试'); return false; }
    finally { setBusy(false); }
  }
  function edit(node?: MenuNode) {
    setEditing(node); form.resetFields();
    form.setFieldsValue(node ?? {code:'',name:'',type:'DIRECTORY',parentId:null,route:null,icon:null,permissionCode:null,sortOrder:0,status:'ENABLED'});
    setOpen(true);
  }
  async function save(values: CreateMenuInput) {
    const input = {...values,name:values.name.trim(),code:values.code.trim(),parentId:values.parentId ?? null,route:type === 'PAGE' ? values.route ?? null : null,icon:values.icon || null,permissionCode:values.permissionCode || null};
    const saved = await mutate(() => editing ? menuApi.update(editing.id,{...input,version:editing.version}) : menuApi.create(input));
    if (saved) setOpen(false);
  }
  async function move(node: MenuNode, delta: number) {
    const siblings = sorted(nodes.filter(n => n.parentId === node.parentId));
    const index = siblings.findIndex(n => n.id === node.id); const target = index+delta;
    if(target < 0 || target >= siblings.length) return;
    [siblings[index],siblings[target]] = [siblings[target],siblings[index]];
    await mutate(() => menuApi.reorder({parentId:node.parentId,ids:siblings.map(n=>n.id),versions:Object.fromEntries(siblings.map(n=>[n.id,n.version]))}));
  }
  const excluded = new Set<string>();
  function exclude(id: string) { if(excluded.has(id)) return; excluded.add(id); nodes.filter(n=>n.parentId===id).forEach(n=>exclude(n.id)); }
  if(editing) exclude(editing.id);
  const actionRoute = menuActionCatalog.find(a=>a.code === selectedCode)?.route;
  const parentNodes = nodes.filter(n => !excluded.has(n.id) && (type === 'BUTTON' ? n.type === 'PAGE' && n.route === actionRoute : n.type === 'DIRECTORY'));
  // Keep ancestor directories in the selector for page-owned buttons, but prevent selecting them.
  const parentTree = (parentId: string | null): ParentChoice[] => sorted(nodes.filter(n=>n.parentId===parentId && n.type !== 'BUTTON' && !excluded.has(n.id))).map(n=>({title:n.name,value:n.id,selectable:parentNodes.some(p=>p.id===n.id),children:parentTree(n.id)}));
  const availablePages = menuPageCatalog.filter(p=>p.route===editing?.route || !nodes.some(n=>n.type==='PAGE' && n.route===p.route));
  const availableActions = menuActionCatalog.filter(a=>a.code===editing?.code || !nodes.some(n=>n.code===a.code));
  return <div className="page-stack">
    {error && <Alert type="error" showIcon message={error} action={<Button actionKey="menu-management.menu-management-page.1" onClick={()=>void load()}>重试</Button>} />}
    {canManage && <div className="page-heading"><Button actionKey="menu-management.menu-management-page.2" type="primary" onClick={()=>edit()}>新增菜单</Button></div>}
    <div className="content-panel" style={{padding:16}}>
      <Form layout="inline" onFinish={()=>setFilter({...draft})} style={{gap:12}}>
        <Form.Item label="名称"><Input aria-label="名称" style={{width:180}} value={draft.name} onChange={e=>setDraft({...draft,name:e.target.value})} /></Form.Item>
        <Form.Item label="类型"><Select aria-label="类型" allowClear style={{width:180}} value={draft.type} options={Object.entries(typeNames).map(([value,label])=>({value,label}))} onChange={value=>setDraft({...draft,type:value})}/></Form.Item>
        <Form.Item label="状态"><Select aria-label="状态" allowClear style={{width:180}} value={draft.status} options={[{value:'ENABLED',label:'启用'},{value:'DISABLED',label:'停用'}]} onChange={value=>setDraft({...draft,status:value})}/></Form.Item>
        <Form.Item><Space><Button actionKey="menu-management.menu-management-page.3" type="primary" htmlType="submit">查询</Button><Button actionKey="menu-management.menu-management-page.4" onClick={()=>{setDraft({name:''});setFilter({name:''});}}>重置</Button></Space></Form.Item>
      </Form>
    </div>
    <div className="content-panel" style={{padding:16}}>
      <ViewportTable<TreeRow> rowKey="id" size="small" loading={loading} dataSource={menuTree(nodes,filter)} pagination={false} scroll={{x:1350}} expandable={{expandedRowKeys:filter.name || filter.type || filter.status ? nodes.map(n=>n.id) : expanded,onExpandedRowsChange:keys=>setExpanded(keys.map(String))}} columns={[
        {title:'名称',dataIndex:'name',width:220}, {title:'编码',dataIndex:'code',width:260,ellipsis:true}, {title:'类型',dataIndex:'type',width:70,render:(value: MenuNode['type'])=>typeNames[value]},
        {title:'绑定路由',dataIndex:'route',width:200,ellipsis:true}, {title:'权限',dataIndex:'permissionCode',width:180,ellipsis:true}, {title:'排序',dataIndex:'sortOrder',width:65},
        {title:'状态',dataIndex:'status',width:75,render:(status)=> <Tag color={status==='ENABLED'?'green':'default'}>{status==='ENABLED'?'启用':'停用'}</Tag>},
        ...(canManage ? [{title:'操作',key:'actions',width:260,fixed:'right' as const,render:(_:unknown,node:MenuNode)=> {
          const siblings=sorted(nodes.filter(n=>n.parentId===node.parentId)); const index=siblings.findIndex(n=>n.id===node.id);
          return <Space size={4}><Button actionKey="menu-management.menu-management-page.5" size="small" aria-label={`编辑-${node.name}`} disabled={busy} onClick={()=>edit(node)}>编辑</Button><Button actionKey="menu-management.menu-management-page.6" size="small" aria-label={`上移-${node.name}`} disabled={busy || index===0} onClick={()=>void move(node,-1)}>上移</Button><Button actionKey="menu-management.menu-management-page.7" size="small" aria-label={`下移-${node.name}`} disabled={busy || index===siblings.length-1} onClick={()=>void move(node,1)}>下移</Button><Button actionKey="menu-management.menu-management-page.8" size="small" aria-label={`${node.status==='ENABLED'?'停用':'启用'}-${node.name}`} disabled={busy || protectedNode(node)} onClick={()=>void mutate(()=>{const source=nodes.find(item=>item.id===node.id)!;const {id,...input}=source;return menuApi.update(id,{...input,status:node.status==='ENABLED'?'DISABLED':'ENABLED'});})}>{node.status==='ENABLED'?'停用':'启用'}</Button></Space>;
        }}] : [])
      ]}/>
    </div>
    <Drawer title={editing?'编辑菜单':'新增菜单'} open={open} width={480} onClose={()=>setOpen(false)} extra={<Button actionKey="menu-management.menu-management-page.9" type="primary" loading={busy} onClick={()=>form.submit()}>保存</Button>}>
      <Form form={form} layout="vertical" onFinish={values=>void save(values)}>
        <Form.Item label="类型" name="type" rules={[{required:true}]}><Select disabled={!!editing} options={Object.entries(typeNames).map(([value,label])=>({value,label}))} onChange={()=>form.setFieldsValue({code:'',route:null,parentId:null})}/></Form.Item>
        <Form.Item label="编码" name="code" rules={[{required:true,message:'请输入编码'},{validator:(_,value)=>nodes.some(n=>n.code===value && n.id!==editing?.id)?Promise.reject(new Error('编码已存在')):Promise.resolve()}]}>{type==='BUTTON' && !editing ? <Select showSearch optionFilterProp="label" options={availableActions.map(a=>({value:a.code,label:a.name}))} onChange={code=>{const action=availableActions.find(a=>a.code===code);if(action) form.setFieldsValue({name:action.name,route:action.route});}}/> : <Input disabled={!!editing}/>}</Form.Item>
        <Form.Item label="名称" name="name" rules={[{required:true,whitespace:true,message:'请输入名称'}]}><Input maxLength={100}/></Form.Item>
        <Form.Item label="父级" name="parentId" rules={type==='BUTTON'?[{required:true,message:'请选择所属页面'}]:[]}><TreeSelect allowClear treeDefaultExpandAll disabled={protectedNode(editing)} treeData={parentTree(null)} /></Form.Item>
        {type==='PAGE' && <Form.Item label="页面绑定" name="route" rules={[{required:true,message:'请选择已有页面'}]}><Select disabled={protectedNode(editing)} showSearch optionFilterProp="label" options={availablePages.map(p=>({value:p.route,label:p.name}))} onChange={route=>{const page=availablePages.find(p=>p.route===route);if(page) form.setFieldsValue({icon:page.icon});}}/></Form.Item>}
        {type==='BUTTON' && <Form.Item label="所属路由" name="route"><Input disabled/></Form.Item>}
        <Form.Item label="图标" name="icon"><Input/></Form.Item>
        <Form.Item label="权限" name="permissionCode"><Select allowClear showSearch optionFilterProp="label" options={permissions.map(p=>({value:p.code,label:`${p.name} (${p.code})`}))}/></Form.Item>
        <Form.Item label="排序号" name="sortOrder" rules={[{required:true}]}><InputNumber min={0} precision={0} style={{width:'100%'}}/></Form.Item>
        <Form.Item label="状态" name="status" rules={[{required:true}]}><Select disabled={protectedNode(editing)} options={[{value:'ENABLED',label:'启用'},{value:'DISABLED',label:'停用'}]}/></Form.Item>
      </Form>
    </Drawer>
  </div>;
}


