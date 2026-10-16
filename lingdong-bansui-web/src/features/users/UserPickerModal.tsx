import {lookupOrganizationTree} from '../../api/organizationLookup';
import {DEFAULT_PAGE_SIZE,createPaginationControl} from '../../components/pagination';
import {useEffect,useRef,useState} from 'react';
import {ChevronsLeft,ChevronsRight} from 'lucide-react';
import {Button as PlainButton,Alert,Input,Modal,Pagination,Space,Table,TreeSelect} from 'antd';
import {ConfiguredButton as Button} from '../../components/ConfiguredButton';
import {usersApi,type PickerUser,type UserPickerPage,type UserPickerQuery} from '../../api/users';
import {organizationApi,type OrganizationNode} from '../../api/organization';
import {iamApi} from '../../api/iam';

interface Props {
 open:boolean; title:string; actionPrefix:string; roleId?:string; requireOrganization?:boolean;
 targetOrganizationId?:string; administratorMode?:boolean; onClose:()=>void;
 onSubmit:(ids:string[],organizationId:string|null)=>Promise<void>;
 onRemove:(users:PickerUser[])=>Promise<void>; removeActionKey?:string;
}
const emptyPage=():UserPickerPage=>({items:[],page:1,pageSize:DEFAULT_PAGE_SIZE,total:0});
interface PickerRow extends PickerUser {rowKey:string;grantOrganizationId?:string|null;}
function organizationTree(nodes:OrganizationNode[],forGrant=false):{title:string;value:string;disabled:boolean;children:ReturnType<typeof organizationTree>}[] {
 return [...nodes].sort((a,b)=>a.sortOrder-b.sortOrder||a.id.localeCompare(b.id,undefined,{numeric:true})).map(node=>({title:node.name,value:node.id,disabled:forGrant&&(node.status!=='ENABLED'||node.effectiveStatus!=='ENABLED'),children:organizationTree(node.children??[],forGrant)}));
}
function organizationPaths(nodes:OrganizationNode[]):Map<string,string> {
 const result=new Map<string,string>();for(const node of nodes){const path=node.name;result.set(node.id,path);for(const [id,name] of organizationPaths(node.children??[]))result.set(id,name);}return result;
}
function schoolTree(nodes:OrganizationNode[],targetId:string):OrganizationNode[] {
 function path(items:OrganizationNode[],ancestors:OrganizationNode[]=[]):OrganizationNode[]|undefined{for(const node of items){if(node.id===targetId)return [...ancestors,node];const found=path(node.children??[],[...ancestors,node]);if(found)return found;}}
 const chain=path(nodes);if(!chain)return [];
 const school=chain.find(node=>['SCHOOL','TRAINING_INSTITUTION'].includes(node.typeCode));if(school)return [school];
 if(chain[0].typeCode==='CAMPUS')return nodes.filter(node=>node.typeCode==='CAMPUS'&&node.parentId===chain[0].parentId);
 return [chain[chain.length-1]];
}
export function UserPickerModal({open,title,actionPrefix,roleId,requireOrganization=false,targetOrganizationId,administratorMode=false,onClose,onSubmit,onRemove,removeActionKey}:Props){
 const operationCode = /^[A-Z][A-Z0-9_]*$/.test(actionPrefix) ? actionPrefix : roleId ? 'IAM_USER_ROLE_ASSIGN' : administratorMode ? 'ORG_ADMIN_ASSIGN' : 'ORG_MEMBER_ASSOCIATE';
 const removeCode = removeActionKey ?? (roleId ? 'IAM_USER_ROLE_ASSIGN' : administratorMode ? 'ORG_ADMIN_REVOKE' : 'ORG_MEMBER_REMOVE');
 const [organizations,setOrganizations]=useState<OrganizationNode[]>([]);
 const [grantOrg,setGrantOrg]=useState<string>();const [filterOrg,setFilterOrg]=useState<string>();
 const [keyword,setKeyword]=useState('');const group='SCHOOL' as const;
 const [candidates,setCandidates]=useState<UserPickerPage>(emptyPage);const [linked,setLinked]=useState<UserPickerPage>(emptyPage);
 const [leftSelected,setLeftSelected]=useState<string[]>([]);const [rightSelected,setRightSelected]=useState<string[]>([]);
 const [loading,setLoading]=useState({CANDIDATES:false,LINKED:false});const [ready,setReady]=useState({CANDIDATES:false,LINKED:false});
 const [errors,setErrors]=useState<{CANDIDATES?:string;LINKED?:string;save?:string;tree?:string}>({});const [busy,setBusy]=useState(false);
 const requests=useRef({CANDIDATES:0,LINKED:0,tree:0});const saving=useRef(false);
 const [applied,setApplied]=useState<{keyword:string;filterOrganizationId?:string}>({keyword:''});
 const canQuery=!requireOrganization||!!grantOrg;
 const pickerOrganizations=targetOrganizationId?schoolTree(organizations,targetOrganizationId):organizations;
 const paths=organizationPaths(pickerOrganizations);
 const rightRows:PickerRow[]=linked.items.flatMap(user=>roleId?(user.assignments??[]).map(assignment=>({...user,assignments:[assignment],grantOrganizationId:assignment.organizationId,rowKey:JSON.stringify([user.id,assignment.organizationId])})):[{...user,rowKey:user.id}]);
 async function load(side:'CANDIDATES'|'LINKED',page=1,search=applied.keyword,filter=applied.filterOrganizationId,scope=grantOrg,selectedGroup=group,size=side==='CANDIDATES'?candidates.pageSize:linked.pageSize){
  if(side==='CANDIDATES'&&requireOrganization&&!scope)return;
  const serial=++requests.current[side];setLoading(value=>({...value,[side]:true}));setReady(value=>({...value,[side]:false}));setErrors(value=>({...value,[side]:undefined}));
  const setPage=side==='CANDIDATES'?setCandidates:setLinked;setPage({...emptyPage(),page,pageSize:size});
  try{
   const query:UserPickerQuery={side,page,pageSize:size,keyword:search,filterOrganizationId:filter,grantOrganizationId:side==='CANDIDATES'?scope:undefined};
   const data=roleId?await iamApi.roleUserPicker(roleId,query):await organizationApi.memberPicker(targetOrganizationId!,{...query,administrator:administratorMode,group:selectedGroup});
   if(serial===requests.current[side]){setPage(data);setReady(value=>({...value,[side]:true}));}
  }catch(error){if(serial===requests.current[side])setErrors(value=>({...value,[side]:error instanceof Error?error.message:'用户加载失败'}));}
  finally{if(serial===requests.current[side])setLoading(value=>({...value,[side]:false}));}
 }
 function reload(search=applied.keyword,filter=applied.filterOrganizationId,scope=grantOrg,selectedGroup=group){setLeftSelected([]);setRightSelected([]);void load('CANDIDATES',1,search,filter,scope,selectedGroup);void load('LINKED',1,search,filter,scope,selectedGroup);}
 useEffect(()=>{
  if(open){setGrantOrg(undefined);setFilterOrg(undefined);setKeyword('');setApplied({keyword:''});setLeftSelected([]);setRightSelected([]);setCandidates(emptyPage());setLinked(emptyPage());setReady({CANDIDATES:false,LINKED:false});setLoading({CANDIDATES:false,LINKED:false});setErrors({});setOrganizations([]);
   const serial=++requests.current.tree;void usersApi.scopeOrganizations(operationCode).then(lookupOrganizationTree).then(nodes=>{if(serial===requests.current.tree)setOrganizations(nodes);}).catch(error=>{if(serial===requests.current.tree)setErrors(value=>({...value,tree:error instanceof Error?error.message:'组织加载失败'}));});
   if(!requireOrganization)void load('CANDIDATES',1,'','','','SCHOOL');
   void load('LINKED',1,'','','','SCHOOL');
  }
  return()=>{requests.current.CANDIDATES++;requests.current.LINKED++;requests.current.tree++;};
 },[open,targetOrganizationId,roleId,administratorMode,requireOrganization]);
 const querying=loading.CANDIDATES||loading.LINKED;
 const complete=ready.CANDIDATES&&ready.LINKED;
 async function mutate(remove:boolean){
  if(saving.current||querying||(remove?!ready.LINKED:!complete||!canQuery))return;
  const ids=remove?rightSelected:leftSelected;if(!ids.length)return;
  saving.current=true;setBusy(true);setErrors(value=>({...value,save:undefined}));
  try{if(remove)await onRemove(rightRows.filter(user=>ids.includes(user.rowKey)));else await onSubmit(ids,grantOrg??null);reload();}
  catch(error){setErrors(value=>({...value,save:error instanceof Error?error.message:'保存失败'}));}
  finally{saving.current=false;setBusy(false);}
 }
 const shortOrganizationName=(name:string)=>name.split(/\s*\/\s*|·/).at(-1)?.trim()||name;
 const tableHeight=Math.max(180,Math.min(680,window.innerHeight-340));
 const columns=[{title:'账号',dataIndex:'username',width:120},{title:'名称',dataIndex:'displayName',width:120},{title:'所属组织',dataIndex:'organizationNames',render:(names:string[]|undefined)=>(names??[]).map(shortOrganizationName).join('、')||'未归属学校'}];
 return <Modal title={title} centered open={open} className="user-picker-modal" width="min(1480px,96vw)" onCancel={busy?undefined:onClose} closable={!busy} maskClosable={!busy} keyboard={!busy} footer={<PlainButton disabled={busy} onClick={onClose}>完成</PlainButton>}>
 {Object.values(errors).filter(Boolean).map((error,index)=><Alert key={index} type="error" message={error} style={{marginBottom:8}}/>)}
 <Space wrap className="user-picker-filters" style={{marginBottom:16}}>
 {requireOrganization&&<TreeSelect aria-label="授权组织" placeholder="选择授权组织" style={{width:340,maxWidth:'100%'}} showSearch treeNodeFilterProp="title" treeData={organizationTree(pickerOrganizations,true)} treeDefaultExpandAll disabled={busy||querying} value={grantOrg} onChange={id=>{setGrantOrg(id);reload(applied.keyword,applied.filterOrganizationId,id);}}/>}
 <TreeSelect aria-label="筛选组织（含下级）" placeholder="筛选组织（含下级）" style={{width:340,maxWidth:'100%'}} showSearch treeNodeFilterProp="title" allowClear treeData={organizationTree(pickerOrganizations)} treeDefaultExpandAll disabled={busy||querying} value={filterOrg} onChange={id=>{setFilterOrg(id);setApplied({keyword:applied.keyword,filterOrganizationId:id});reload(applied.keyword,id??'');}}/>

 <Input aria-label="关联用户查询" placeholder="账号或名称" style={{width:200}} value={keyword} disabled={busy} onChange={event=>setKeyword(event.target.value)} onPressEnter={()=>{setApplied({keyword,filterOrganizationId:filterOrg});reload(keyword,filterOrg);}}/>
 <Button actionKey={operationCode} disabled={busy||querying} onClick={()=>{setApplied({keyword,filterOrganizationId:filterOrg});reload(keyword,filterOrg);}}>查询</Button>
 </Space>
 {requireOrganization&&!grantOrg&&<Alert type="info" message="新增关联前请选择授权组织。"/>}
 <div className="user-picker-columns">
 <section role="region" aria-label="待加入" className="user-picker-column"><h3>待加入</h3><Table rowKey="id" size="small" loading={loading.CANDIDATES} dataSource={candidates.items} scroll={{x:420,y:tableHeight}} columns={columns} rowSelection={{selectedRowKeys:leftSelected,preserveSelectedRowKeys:true,onChange:keys=>setLeftSelected(keys.map(String)),getCheckboxProps:user=>({disabled:busy||querying||!complete||user.status!=='ENABLED'})}} pagination={false}/><Pagination size="small" {...createPaginationControl({current:candidates.page,pageSize:candidates.pageSize,total:candidates.total,disabled:busy||querying||!canQuery,onChange:(page,size)=>{setLeftSelected([]);void load('CANDIDATES',page,applied.keyword,applied.filterOrganizationId,grantOrg,group,size);}})}/></section>
 <div className="user-picker-actions" role="group" aria-label="用户关联操作"><Button actionKey={operationCode} type="primary" icon={<ChevronsRight size={16} aria-hidden="true"/>} loading={busy} disabled={!leftSelected.length||querying||!complete||!canQuery} onClick={()=>void mutate(false)}>加入所选</Button><Button actionKey={removeCode} danger icon={<ChevronsLeft size={16} aria-hidden="true"/>} loading={busy} disabled={!rightSelected.length||querying||!ready.LINKED} onClick={()=>void mutate(true)}>移除所选</Button></div>
 <section role="region" aria-label="已加入" className="user-picker-column"><h3>已加入</h3><Table rowKey="rowKey" size="small" loading={loading.LINKED} dataSource={rightRows} scroll={{x:roleId?570:420,y:tableHeight}} columns={[...columns,...(roleId?[{title:'授权组织',dataIndex:'grantOrganizationId',width:150,render:(id:string|null)=>id===null?'全部范围':paths.get(id)??id}]:[])]} rowSelection={{selectedRowKeys:rightSelected,onChange:keys=>setRightSelected(keys.map(String)),getCheckboxProps:()=>({disabled:busy||querying||!ready.LINKED})}} pagination={false}/><Pagination size="small" {...createPaginationControl({current:linked.page,pageSize:linked.pageSize,total:linked.total,disabled:busy||querying,onChange:(page,size)=>{setRightSelected([]);void load('LINKED',page,applied.keyword,applied.filterOrganizationId,grantOrg,group,size);}})}/></section>
 </div>
 </Modal>;
}
