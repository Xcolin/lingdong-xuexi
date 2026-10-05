import { ConfiguredButton as Button } from '../../components/ConfiguredButton';
import { Alert, Descriptions, Drawer, Form, Input, InputNumber, Select, Space, Spin, Tag, Tree, TreeSelect } from 'antd';
import { useEffect, useRef, useState, type ReactNode } from 'react';
import type { CurrentUser } from '../../api/auth';
import { ApiRequestError } from '../../api/http';
import { menuApi, type CreateMenuInput, type MenuNode } from '../../api/menus';
import { menuPageCatalog } from '../../app/menuCatalog';
import { useMenuConfiguration } from '../../app/MenuConfiguration';
import { menuDrop, protectedMenu, sortedMenus } from './menuDrag';
import { BatchMenuButtons } from './BatchMenuButtons';

type TreeRow = MenuNode & { children?: TreeRow[] };
type ParentChoice = {title:string;value:string;selectable:boolean;children?:ParentChoice[]};
type Filter = { name: string; type?: MenuNode['type']; status?: MenuNode['status'] };
const typeNames = { DIRECTORY: '目录', PAGE: '页面', BUTTON: '按钮' };
const protectedNode = protectedMenu;
const sorted = sortedMenus;
export function menuTree(nodes: MenuNode[], filter: Filter, parentId: string | null = null): TreeRow[] {
  return sorted(nodes.filter(n => n.parentId === parentId)).flatMap(n => {
    const children = menuTree(nodes, filter, n.id);
    const matches = n.name.includes(filter.name.trim()) && (!filter.type || n.type === filter.type) && (!filter.status || n.status === filter.status);
    return matches || children.length ? [{...n, ...(children.length ? {children} : {})}] : [];
  });
}
export function MenuManagementPage({ currentUser }: { currentUser: CurrentUser }) {
  const canManage = currentUser.permissionCodes.includes('MENU_MANAGE');
  const configuration = useMenuConfiguration();
  const [nodes,setNodes] = useState<MenuNode[]>([]);
  const [selectedId,setSelectedId]=useState<string>();
  const [batch,setBatch] = useState<{pageId?:string}|null>(null);
  const [treeHeight,setTreeHeight] = useState(Math.max(180,window.innerHeight-380));
  const [loading,setLoading] = useState(true);
  const [busy,setBusy] = useState(false);
  const saving = useRef(false);
  const [error,setError] = useState<string>();
  const [draft,setDraft] = useState<Filter>({name:''});
  const [filter,setFilter] = useState<Filter>({name:''});
  const [open,setOpen] = useState(false);
  const [expanded,setExpanded] = useState<string[]>([]);
  const [editing,setEditing] = useState<MenuNode>();
  const [form] = Form.useForm<CreateMenuInput>();
  const type = Form.useWatch('type',form) ?? 'DIRECTORY';
  const filtered = !!(filter.name || filter.type || filter.status);
  const selected=nodes.find(node=>node.id===selectedId);
  function protectedStatus(node?: MenuNode): boolean {
    if (!node) return false;
    if (protectedNode(node)) return true;
    if (node.type !== 'DIRECTORY') return false;
    const byId = new Map(nodes.map(item => [item.id, item]));
    return nodes.filter(protectedNode).some(core => {
      const seen = new Set<string>();
      let parentId = core.parentId;
      while (parentId && !seen.has(parentId)) {
        if (parentId === node.id) return true;
        seen.add(parentId);
        parentId = byId.get(parentId)?.parentId ?? null;
      }
      return false;
    });
  }
  useEffect(()=>{const resize=()=>setTreeHeight(Math.max(180,window.innerHeight-380));window.addEventListener('resize',resize);return()=>window.removeEventListener('resize',resize);},[]);
  useEffect(() => { void load(); },[]);
  async function load() {
    setLoading(true); setError(undefined);
    try { const menus=await menuApi.list(); setNodes(menus); setSelectedId(current=>menus.some(node=>node.id===current)?current:sorted(menus)[0]?.id); setExpanded(menus.filter(n=>n.type==='DIRECTORY').map(n=>n.id)); }
    catch(e) { setError(e instanceof Error ? e.message : '菜单加载失败'); }
    finally { setLoading(false); }
  }
  async function mutate(action: () => Promise<unknown>) {
    if (!canManage || saving.current) return false;
    saving.current = true;
    setBusy(true); setError(undefined);
    try { await action(); await load(); await configuration?.refresh(); return true; }
    catch(e) {
      if(e instanceof ApiRequestError && e.status===409) {
        await load();
        setOpen(false); setEditing(undefined);
        try { await configuration?.refresh(); } catch { /* Retry remains available. */ }
      }
      setError(e instanceof Error ? e.message : '操作失败，请刷新后重试'); return false;
    }
    finally { saving.current = false; setBusy(false); }
  }
  function edit(node?: MenuNode,parent?:MenuNode) {
    setEditing(node); form.resetFields();
    form.setFieldsValue(node ?? {code:'',name:'',type:parent?(parent.type==='PAGE'?'BUTTON':'PAGE'):'DIRECTORY',parentId:parent?.id??null,route:null,icon:null,permissionCode:null,sortOrder:0,status:'ENABLED'});
    setOpen(true);
  }
  async function save(values: CreateMenuInput) {
    const input = {...values,name:values.name.trim(),code:values.code.trim(),parentId:values.parentId ?? null,route:type === 'PAGE' ? values.route?.trim() ?? null : null,icon:values.icon || null,permissionCode:type==='DIRECTORY'?null:values.code.trim(),grantable:type!=='DIRECTORY'};
    const saved = await mutate(() => editing ? menuApi.update(editing.id,{...input,version:editing.version}) : menuApi.create(input));
    if (saved) setOpen(false);
  }
  async function drop(dragId:string,targetId:string,position:number) {
    if(!canManage || busy || filtered)return;
    try { const result=menuDrop(nodes,dragId,targetId,position);if(result) await mutate(()=>result.kind==='order'?menuApi.reorder(result.input):menuApi.move(result.id,result.input)); }
    catch(cause){setError(cause instanceof Error?cause.message:'无法移动菜单');}
  }
  const excluded = new Set<string>();
  function exclude(id: string) { if(excluded.has(id)) return; excluded.add(id); nodes.filter(n=>n.parentId===id).forEach(n=>exclude(n.id)); }
  if(editing) exclude(editing.id);
  const parentNodes = nodes.filter(n => !excluded.has(n.id) && (type === 'BUTTON' ? n.type === 'PAGE' : n.type === 'DIRECTORY'));
  // Keep ancestor directories in the selector for page-owned buttons, but prevent selecting them.
  const parentTree = (parentId: string | null): ParentChoice[] => sorted(nodes.filter(n=>n.parentId===parentId && n.type !== 'BUTTON' && !excluded.has(n.id))).map(n=>({title:n.name,value:n.id,selectable:parentNodes.some(p=>p.id===n.id),children:parentTree(n.id)}));
  function renderTree(rows:TreeRow[]):{key:string;title:ReactNode;children?:ReturnType<typeof renderTree>}[] {
    return rows.map(node=>({key:node.id,title:<div className="management-tree-label">
      <span className="menu-tree-name" title={node.name}>{node.name}</span><Tag>{typeNames[node.type]}</Tag>{node.status==='DISABLED'&&<Tag>停用</Tag>}
    </div>,...(node.children?{children:renderTree(node.children)}:{})}));
  }
  return <div className="page-stack management-page menu-management-page">
    {error && <Alert type="error" showIcon message={error} action={<Button actionKey="menu-management.menu-management-page.1" onClick={()=>void load()}>重试</Button>} />}
    {canManage && <div className="page-heading"><Space><Button actionKey="MENU_MANAGE" type="primary" onClick={()=>edit()}>新增菜单</Button><Button actionKey="menu-management.batch-buttons.open" onClick={()=>{setError(undefined);setBatch({});}}>批量新增按钮</Button></Space></div>}
    <div className="content-panel management-query">
      <Form layout="inline" onFinish={()=>{setFilter({...draft});const match=sorted(nodes).find(node=>node.name.includes(draft.name.trim())&&(!draft.type||node.type===draft.type)&&(!draft.status||node.status===draft.status));if(match)setSelectedId(match.id);}} style={{gap:12}}>
        <Form.Item label="名称"><Input aria-label="名称" style={{width:180}} value={draft.name} onChange={e=>setDraft({...draft,name:e.target.value})} /></Form.Item>
        <Form.Item label="类型"><Select aria-label="类型" allowClear style={{width:180}} value={draft.type} options={Object.entries(typeNames).map(([value,label])=>({value,label}))} onChange={value=>setDraft({...draft,type:value})}/></Form.Item>
        <Form.Item label="状态"><Select aria-label="状态" allowClear style={{width:180}} value={draft.status} options={[{value:'ENABLED',label:'启用'},{value:'DISABLED',label:'停用'}]} onChange={value=>setDraft({...draft,status:value})}/></Form.Item>
        <Form.Item><Space><Button actionKey="menu-management.menu-management-page.3" type="primary" htmlType="submit">查询</Button><Button actionKey="menu-management.menu-management-page.4" onClick={()=>{setDraft({name:''});setFilter({name:''});}}>重置</Button></Space></Form.Item>
      </Form>
    </div>
    <div className="management-split menu-split">
    <section className="content-panel management-tree-pane menu-tree-panel" aria-label="菜单树">
      {loading && <Spin size="small" />}<Tree blockNode showLine selectedKeys={selectedId?[selectedId]:[]} onSelect={keys=>{if(keys.length)setSelectedId(String(keys[0]));}} height={treeHeight} disabled={busy}
        draggable={canManage&&!busy&&!filtered?{nodeDraggable:node=>!protectedNode(nodes.find(n=>n.id===String(node.key)))}:false}
        expandedKeys={filtered?nodes.map(n=>n.id):expanded} autoExpandParent={filtered} onExpand={keys=>setExpanded(keys.map(String))}
        treeData={renderTree(menuTree(nodes,filter))}
        onDrop={info=>void drop(String(info.dragNode.key),String(info.node.key),info.dropToGap?info.dropPosition-Number(info.node.pos.split('-').pop()):0)}
        allowDrop={info=>{try{menuDrop(nodes,String(info.dragNode.key),String(info.dropNode.key),info.dropPosition);return true;}catch{return false;}}}/>
    </section>
    <section className="content-panel management-detail-pane" aria-label="选中菜单">
      {selected?<><div className="management-detail-heading"><div><strong>{selected.name}</strong><span className="management-context-code">{selected.code}</span></div><Tag color={selected.status==='ENABLED'?'green':'default'}>{selected.status==='ENABLED'?'启用':'停用'}</Tag></div>
      {canManage&&<Space wrap className="management-context-actions">
        <Button actionKey="menu-management.menu-management-page.5" type="primary" aria-label={`编辑-${selected.name}`} disabled={busy} onClick={()=>edit(selected)}>编辑</Button>
        {selected.type==='DIRECTORY'&&<Button actionKey="menu-management.node.add-child" disabled={busy} onClick={()=>edit(undefined,selected)}>新增下级</Button>}
        {selected.type==='PAGE'&&<Button actionKey="menu-management.batch-buttons.open" aria-label={`批量新增按钮-${selected.name}`} disabled={busy} onClick={()=>{setError(undefined);setBatch({pageId:selected.id});}}>批量新增按钮</Button>}
        <Button actionKey="menu-management.menu-management-page.8" danger={selected.status==='ENABLED'} aria-label={`${selected.status==='ENABLED'?'停用':'启用'}-${selected.name}`} disabled={busy||protectedStatus(selected)} onClick={()=>void mutate(()=>{const {id,...input}=selected;return menuApi.update(id,{...input,status:selected.status==='ENABLED'?'DISABLED':'ENABLED'});})}>{selected.status==='ENABLED'?'停用':'启用'}</Button>
      </Space>}
      <Descriptions column={1} size="small" items={[
        {key:'type',label:'类型',children:typeNames[selected.type]},
        {key:'parent',label:'上级',children:nodes.find(node=>node.id===selected.parentId)?.name??'根节点'},
        {key:'code',label:'编码',children:selected.code},
        ...(selected.type==='PAGE'?[{key:'route',label:'页面访问URL',children:selected.route}]:[]),
        ...(selected.type!=='DIRECTORY'?[{key:'permission',label:'权限编码',children:selected.permissionCode??selected.code}]:[]),
        {key:'order',label:'排序号',children:selected.sortOrder},
        {key:'count',label:'直接下级',children:nodes.filter(node=>node.parentId===selected.id).length}
      ]}/></>:<div className="empty-state">请选择菜单节点</div>}
    </section>
    </div>
    {batch && <BatchMenuButtons pages={nodes.filter(n=>n.type==='PAGE').map(n=>({id:n.id,name:n.name}))} existingCodes={nodes.map(n=>n.code)} initialPageId={batch.pageId} busy={busy} error={error}
      onClose={()=>setBatch(null)} onSubmit={(pageId,buttons)=>mutate(()=>menuApi.createButtons(pageId,buttons))}/>}
    <Drawer title={editing?'编辑菜单':'新增菜单'} open={open} width="min(480px, 92vw)" closable={!busy} maskClosable={!busy} keyboard={!busy} onClose={()=>setOpen(false)} extra={<Button actionKey="menu-management.menu-management-page.9" type="primary" loading={busy} onClick={()=>form.submit()}>保存</Button>}>
      <Form form={form} layout="vertical" onFinish={values=>void save(values)}>
        <Form.Item label="类型" name="type" rules={[{required:true}]}><Select disabled={!!editing} options={Object.entries(typeNames).map(([value,label])=>({value,label}))} onChange={()=>form.setFieldsValue({code:'',route:null,parentId:null})}/></Form.Item>
        <Form.Item label="编码" name="code" rules={[{required:true,message:'请输入编码'},{validator:(_,value)=>nodes.some(n=>n.code===value&&n.id!==editing?.id)?Promise.reject(new Error('编码已存在')):Promise.resolve()},...(!editing&&type!=='DIRECTORY'?[{pattern:/^[A-Z][A-Z0-9_]{2,127}$/,message:'编码即权限编码，使用大写字母、数字、下划线'}]:[])]}><Input disabled={!!editing} maxLength={128}/></Form.Item>
        <Form.Item label="名称" name="name" rules={[{required:true,whitespace:true,message:'请输入名称'}]}><Input maxLength={100}/></Form.Item>
        <Form.Item label="父级" name="parentId" rules={type==='BUTTON'?[{required:true,message:'请选择所属页面'}]:[]}><TreeSelect allowClear treeDefaultExpandAll disabled={protectedNode(editing)} treeData={parentTree(null)} /></Form.Item>
        {type==='PAGE' && <Form.Item label="页面访问URL" name="route" rules={[{required:true,whitespace:true,message:'请输入页面访问URL'},{validator:(_,value)=>!value || menuPageCatalog.some(page=>page.route===value.trim())?Promise.resolve():Promise.reject(new Error('请输入已实现页面的访问URL'))}]}><Input disabled={protectedNode(editing)} placeholder="例如 /organizations" maxLength={128} autoComplete="off" /></Form.Item>}
        <Form.Item label="图标" name="icon"><Input/></Form.Item>
        <Form.Item label="排序号" name="sortOrder" rules={[{required:true}]}><InputNumber min={0} precision={0} style={{width:'100%'}}/></Form.Item>
        <Form.Item label="状态" name="status" rules={[{required:true}]}><Select disabled={protectedStatus(editing)} options={[{value:'ENABLED',label:'启用'},{value:'DISABLED',label:'停用'}]}/></Form.Item>
      </Form>
    </Drawer>
  </div>;
}


