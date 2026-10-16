import { Button as PlainButton } from 'antd';
import {useEffect,useRef,useState} from 'react';
import {Alert,Drawer,Form,Input,Modal,Space,Tag,message} from 'antd';
import {ViewportTable as Table} from '../../components/ViewportTable';
import {useMenuConfiguration} from '../../app/MenuConfiguration';
import {ConfiguredButton as Button} from '../../components/ConfiguredButton';
import {organizationApi,type OrganizationNode,type OrganizationMemberPage} from '../../api/organization';
import {UserPickerModal} from '../users/UserPickerModal';
import {InstitutionEnrollmentModal} from './InstitutionEnrollmentModal';
import {DEFAULT_PAGE_SIZE,createPagination} from '../../components/pagination';
import {EditUserDrawer} from '../users/EditUserDrawer';
import type {ManagedUser} from '../../api/users';
import {CreateUserDrawer} from '../users/CreateUserDrawer';
export function OrganizationMembersDrawer({organization,onClose,embedded=false,permissionCodes}:{organization:OrganizationNode;onClose:()=>void;embedded?:boolean;permissionCodes?:string[]}){
 const configuration=useMenuConfiguration();
 const effectivePermissions=permissionCodes??configuration?.permissionCodes??[];

 const [enrollmentUser,setEnrollmentUser]=useState<ManagedUser>();
 const [pageSize,setPageSize]=useState(DEFAULT_PAGE_SIZE);
 const [editingUser,setEditingUser]=useState<ManagedUser>();
 const [page,setPage]=useState<OrganizationMemberPage>({items:[],page:1,pageSize:DEFAULT_PAGE_SIZE,total:0});const [keyword,setKeyword]=useState('');const [query,setQuery]=useState('');const [loading,setLoading]=useState(false);const [busy,setBusy]=useState(false);const [error,setError]=useState<string>();const [picker,setPicker]=useState<'members'|'administrators'>();const [create,setCreate]=useState(false);const request=useRef(0);const saving=useRef(false);const enabled=organization.effectiveStatus==='ENABLED'&&organization.status==='ENABLED';
 async function load(current=1,search=query,size=pageSize){if(!effectivePermissions.includes('ORG_MEMBER_READ')){setPage({items:[],page:1,pageSize,total:0});return;}const serial=++request.current;setLoading(true);setError(undefined);try{const data=await organizationApi.members(organization.id,{page:current,pageSize:size,keyword:search});if(serial===request.current)setPage(data);}catch(e){if(serial===request.current)setError(e instanceof Error?e.message:'组织用户加载失败');}finally{if(serial===request.current)setLoading(false);}}
 useEffect(()=>{void load(1,'');return()=>{request.current++;};},[organization.id,effectivePermissions.join(',')]);
 async function associate(ids:string[],administrator:boolean){if(!effectivePermissions.includes(administrator?'ORG_ADMIN_ASSIGN':'ORG_MEMBER_ASSOCIATE')||saving.current)return;saving.current=true;setBusy(true);setError(undefined);try{await organizationApi.associateMembers(organization.id,ids,administrator);message.success(administrator?'组织管理员已添加':'组织用户已关联');await load(page.page);}finally{saving.current=false;setBusy(false);}}
 async function promote(id:string){try{await associate([id],true);}catch(e){setError(e instanceof Error?e.message:'设置管理员失败');}}
 async function revoke(ids:string[],administrator:boolean){if(!effectivePermissions.includes(administrator?'ORG_ADMIN_REVOKE':'ORG_MEMBER_REMOVE')||saving.current)return;saving.current=true;setBusy(true);setError(undefined);try{await organizationApi.revokeMembers(organization.id,ids,administrator);message.success(administrator?'组织管理员已取消':'组织用户已取消关联');await load(page.page);}catch(e){setError(e instanceof Error?e.message:'撤销操作失败');}finally{saving.current=false;setBusy(false);}}
 function confirmRevoke(id:string,name:string,administrator:boolean){
  Modal.confirm({title:administrator?'取消组织管理员':'取消关联组织用户',
   content:administrator?`确认取消“${name}”在当前组织的管理员身份吗？取消后保留成员身份。`:`确认取消“${name}”与当前组织的关联吗？`,
   okText:administrator?'取消管理员':'取消关联',okButtonProps:{danger:true},cancelText:'返回',
   onOk:()=>revoke([id],administrator)});
 }
 const content=<div className="organization-members">
 {error&&<Alert type="error" message={error} action={<PlainButton  disabled={busy} onClick={()=>void load(page.page)}>重试</PlainButton>}/>}
 <div className="content-panel" style={{padding:12,marginBottom:12}}><Form layout="inline" onFinish={()=>{setQuery(keyword);void load(1,keyword);}}><Form.Item label="账号或名称"><Input value={keyword} onChange={e=>setKeyword(e.target.value)} style={{width:230}} disabled={busy}/></Form.Item><Form.Item><Space><Button actionKey="ORG_MEMBER_READ" htmlType="submit" type="primary" disabled={busy}>查询</Button><PlainButton  disabled={busy} onClick={()=>{setQuery('');setKeyword('');void load(1,'');}}>重置</PlainButton></Space></Form.Item></Form></div>
 <div className="content-panel" style={{padding:12,marginBottom:12}}><Space wrap><Button actionKey="ORG_MEMBER_ASSOCIATE" hidden={!effectivePermissions.includes('ORG_MEMBER_ASSOCIATE')} disabled={!enabled||busy} onClick={()=>setPicker('members')}>关联已有用户</Button><Button actionKey="ORG_ADMIN_ASSIGN" hidden={!effectivePermissions.includes('ORG_ADMIN_ASSIGN')} disabled={!enabled||busy} onClick={()=>setPicker('administrators')}>添加组织管理员</Button><Button actionKey="IAM_USER_CREATE" hidden={!effectivePermissions.includes('IAM_USER_CREATE')} type="primary" disabled={!enabled||busy} onClick={()=>setCreate(true)}>新增用户</Button></Space></div>
 <Table rowKey="id" size="small" loading={loading} dataSource={page.items} columns={[{title:'账号',dataIndex:'username',width:130,ellipsis:true},{title:'名称',dataIndex:'displayName',width:120,ellipsis:true},{title:'身份',dataIndex:'administrator',width:110,render:(value,user)=><Space>{user.owner&&<Tag color="gold">负责人</Tag>}{value?<Tag color="green">组织管理员</Tag>:<Tag>用户</Tag>}</Space>},{title:'状态',dataIndex:'status',width:70,render:status=>status==='ENABLED'?'启用':status==='DISABLED'?'停用':status==='LOCKED'?'锁定':'已注销'},{title:'操作',width:420,fixed:'right',render:(_,user)=><Space size={4} style={{flexWrap:'nowrap',whiteSpace:'nowrap'}}>{effectivePermissions.includes('IAM_USER_UPDATE')&&user.status!=='CANCELLED'&&<Button actionKey="IAM_USER_UPDATE" size="small" type="text" onClick={()=>setEditingUser(user)}>编辑</Button>}{organization.typeCode==='FAMILY'&&user.type==='STUDENT'&&effectivePermissions.includes('STUDENT_ENROLLMENT_REQUEST_CREATE')&&<Button actionKey="STUDENT_ENROLLMENT_REQUEST_CREATE" size="small" type="text" title="申请关联学校或培训机构" onClick={()=>setEnrollmentUser(user)}>关联申请</Button>}{user.administrator?<Button actionKey="ORG_ADMIN_REVOKE" hidden={!effectivePermissions.includes('ORG_ADMIN_REVOKE')} size="small" type="text" danger aria-label={`取消管理员-${user.displayName}`} title={user.owner?'请先转交负责人':undefined} disabled={busy||user.owner} onClick={()=>confirmRevoke(user.id,user.displayName,true)}>取消管理员</Button>:<Button actionKey="ORG_ADMIN_ASSIGN" hidden={!effectivePermissions.includes('ORG_ADMIN_ASSIGN')} size="small" type="text" aria-label={`设为管理员-${user.displayName}`} disabled={!enabled||busy||user.status!=='ENABLED'} onClick={()=>void promote(user.id)}>设为管理员</Button>}{<Button actionKey="ORG_MEMBER_REMOVE" hidden={!effectivePermissions.includes('ORG_MEMBER_REMOVE')} size="small" type="text" danger aria-label={`取消关联-${user.displayName}`} title={user.owner?'请先转交负责人':undefined} disabled={busy||user.owner} onClick={()=>confirmRevoke(user.id,user.displayName,false)}>取消关联</Button>}</Space>}]} pagination={createPagination({current:page.page,pageSize,total:page.total,onChange:(current,size)=>{setPageSize(size);void load(current,query,size);}})}/>
 {picker&&<UserPickerModal open title={picker==='administrators'?'添加组织管理员':'关联已有用户'} targetOrganizationId={organization.id} administratorMode={picker==='administrators'} actionPrefix={picker==='administrators'?'ORG_ADMIN_ASSIGN':'ORG_MEMBER_ASSOCIATE'} onClose={()=>setPicker(undefined)} onSubmit={ids=>associate(ids,picker==='administrators')} removeActionKey={picker==='administrators'?'ORG_ADMIN_REVOKE':'ORG_MEMBER_REMOVE'} onRemove={async users=>{await organizationApi.revokeMembers(organization.id,users.map(user=>user.id),picker==='administrators');await load(page.page);}}/>}
 {enrollmentUser&&<InstitutionEnrollmentModal user={enrollmentUser} familyOrganizationId={organization.id} onClose={()=>setEnrollmentUser(undefined)}/>}
 {editingUser&&<EditUserDrawer user={editingUser} onClose={()=>setEditingUser(undefined)} onSaved={()=>load(page.page)}/>}
 <CreateUserDrawer open={create} organization={organization} actionPrefix="IAM_USER_CREATE" onClose={()=>setCreate(false)} onSaved={()=>load(page.page)}/>
 </div>;
 return embedded?content:<Drawer open title={`组织用户 · ${organization.namePath ?? organization.name}`} width="min(960px,96vw)" onClose={busy?undefined:onClose} closable={!busy} maskClosable={!busy} keyboard={!busy}>{content}</Drawer>;
}

