import {OwnerTransferPanel} from './OwnerTransferPanel';
import { configuredAccess, useMenuConfiguration } from '../../app/MenuConfiguration';
import { Button as PlainButton } from 'antd';
import {ApiRequestError} from '../../api/http';
import {ConfiguredAction} from '../../components/ConfiguredAction';
import { ConfiguredButton as Button } from '../../components/ConfiguredButton';
import { ConfiguredModal as Modal } from '../../components/ConfiguredModal';
import { ViewportTable as Table } from '../../components/ViewportTable';
import { useEffect, useMemo, useRef, useState } from 'react';
import { Button as NavigationButton, Alert, Card, Col, Descriptions, Form, Input, InputNumber, Radio, Row, Select, Space, Spin, Tabs, Tag, Tree, message } from 'antd';
import type { DataNode } from 'antd/es/tree';
import type { TreeProps } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { FormInstance } from 'antd';
import {
  ArrowRightLeft, Building2, Edit3, FolderPlus, Move, Plus, Power,
  PowerOff, ShieldCheck, Trash2, UserRoundCheck
} from 'lucide-react';
import type { CurrentUser } from '../../api/auth';
import {
  organizationApi, type CreateOrganizationChangeInput, type CreateOrganizationInput,
  type CreateOrganizationTypeInput, type OrganizationChangeType, type OrganizationNode,
  type OrganizationType, type ReorderOrganizationsInput
} from '../../api/organization';
import { OrganizationChangeReviewPanel } from './OrganizationChangeReviewPanel';
import { OrganizationNodeEditorDrawer } from './OrganizationNodeEditorDrawer';
import { ParentMobileManualRecoveryDrawer } from './ParentMobileManualRecoveryDrawer';
import { StudentAccountCancellationDrawer } from './StudentAccountCancellationDrawer';
import { StudentOrganizationLifecycleDrawer } from './StudentOrganizationLifecycleDrawer';
import { TeacherClassAssignmentDrawer } from './TeacherClassAssignmentDrawer';
import { ClassManagementPanel } from './ClassManagementPanel';
import { SchoolHierarchyPanel } from './SchoolHierarchyPanel';
import { OrganizationMembersDrawer } from './OrganizationMembersDrawer';

const typeColumns = [
  { title: '编码', dataIndex: 'code', key: 'code' },
  { title: '名称', dataIndex: 'name', key: 'name' },
  {
    title: '来源', dataIndex: 'builtIn', key: 'builtIn',
    render: (builtIn: boolean) => <Tag color={builtIn ? 'blue' : 'default'}>{builtIn ? '内置' : '自定义'}</Tag>
  },
  {
    title: '状态', dataIndex: 'status', key: 'status',
    render: (status: OrganizationType['status']) => <Tag color={status === 'ENABLED' ? 'green' : 'default'}>{status === 'ENABLED' ? '启用' : '停用'}</Tag>
  },
  { title: '排序', dataIndex: 'sortOrder', key: 'sortOrder', width: 76 }
];

type TreeDropInfo = Parameters<NonNullable<TreeProps['onDrop']>>[0];

interface OrganizationManagementPageProps {
  currentUser?: CurrentUser;
  organizationManagementEnabled?: boolean;
  studentOrganizationRelationshipEnabled?: boolean;
  parentMobileManualRecoveryEnabled?: boolean;
  studentAccountCancellationEnabled?: boolean;
  classManagementEnabled?: boolean;
}

interface ChangeFormValue {
  targetParentId?: string;
  reason: string;
}

export function OrganizationManagementPage({
  currentUser,
  organizationManagementEnabled = false,
  studentOrganizationRelationshipEnabled = false,
  parentMobileManualRecoveryEnabled = false,
  studentAccountCancellationEnabled = false,
  classManagementEnabled = false
}: OrganizationManagementPageProps) {
  const isSystemAdministrator = currentUser?.permissionCodes.includes('ORG_NODE_READ') === true;
  const showAdminTabs = currentUser?.roleCodes.includes('SYS_ADMIN') === true;
  const isSystemAuditor = currentUser?.permissionCodes.includes('ORG_NODE_CHANGE_REVIEW') === true;
  const isOrganizationAdministrator = currentUser?.permissionCodes.includes('CLASS_READ') === true;

  if (isOrganizationAdministrator && !isSystemAdministrator && !isSystemAuditor) {
    return <>{currentUser?.permissionCodes.includes('ORGANIZATION_OWNER_TRANSFER') && <OwnerTransferPanel />}<OrganizationOperationView
      permissionCodes={currentUser?.permissionCodes ?? []}
      studentOrganizationRelationshipEnabled={studentOrganizationRelationshipEnabled}
      parentMobileManualRecoveryEnabled={parentMobileManualRecoveryEnabled}
      studentAccountCancellationEnabled={studentAccountCancellationEnabled}
      classManagementEnabled={classManagementEnabled}
    /></>;
  }
  if ((isSystemAdministrator || isSystemAuditor) && !organizationManagementEnabled) {
    return <Alert type="info" showIcon message="组织管理功能当前未启用" />;
  }
  if (isSystemAuditor && !isSystemAdministrator) {
    return <Alert type="warning" showIcon message="当前身份没有组织架构访问权限" />;
  }
  if (!isSystemAdministrator) {
    return <Alert type="warning" showIcon message="当前身份不能访问组织管理" />;
  }
  return <>{currentUser?.permissionCodes.includes('ORGANIZATION_OWNER_TRANSFER') && <OwnerTransferPanel />}<PlatformOrganizationManagementView
    showAdminTabs={showAdminTabs}
    familyReadOnly={false}
    permissionCodes={currentUser?.permissionCodes ?? []}
    canReview={isSystemAuditor}
    studentOrganizationRelationshipEnabled={studentOrganizationRelationshipEnabled}
    parentMobileManualRecoveryEnabled={parentMobileManualRecoveryEnabled}
    studentAccountCancellationEnabled={studentAccountCancellationEnabled}
    classManagementEnabled={classManagementEnabled}
  /></>;
}

type OrganizationOperationCapabilities = { permissionCodes?: string[] } & Required<Pick<OrganizationManagementPageProps, 'studentOrganizationRelationshipEnabled' | 'parentMobileManualRecoveryEnabled' | 'studentAccountCancellationEnabled' | 'classManagementEnabled'>>;

function PlatformOrganizationManagementView({ showAdminTabs, familyReadOnly=false, permissionCodes, canReview, studentOrganizationRelationshipEnabled, parentMobileManualRecoveryEnabled, studentAccountCancellationEnabled, classManagementEnabled }: { showAdminTabs: boolean; familyReadOnly?:boolean; canReview: boolean } & OrganizationOperationCapabilities) {
  const configuration = useMenuConfiguration();
  const canUpdateNode = permissionCodes?.includes('ORG_NODE_UPDATE') === true && configuredAccess(configuration, 'ORG_NODE_UPDATE');
  const canReadChanges = permissionCodes?.some(code=>['ORG_NODE_CHANGE_SUBMIT','ORG_NODE_CHANGE_REVIEW'].includes(code)) === true;
  const [types, setTypes] = useState<OrganizationType[]>([]);
  const [organizationTree, setOrganizationTree] = useState<OrganizationNode[]>([]);
  const [selectedNode, setSelectedNode] = useState<OrganizationNode | null>(null);
  const [activeTab,setActiveTab]=useState('tree');
  const canReadMembers=!familyReadOnly&&(permissionCodes?.some(code=>['ORG_MEMBER_READ','ORG_MEMBER_ASSOCIATE','ORG_MEMBER_REMOVE','ORG_ADMIN_ASSIGN','ORG_ADMIN_REVOKE','IAM_USER_CREATE'].includes(code))===true);
  const [detailTab,setDetailTab]=useState(canReadMembers?'members':'details');
  useEffect(()=>{setDetailTab(canReadMembers?'members':'details');},[selectedNode?.id,canReadMembers]);
  const [treeSearch,setTreeSearch]=useState('');
  const [treeHeight,setTreeHeight]=useState(Math.max(180,window.innerHeight-330));
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [typeModalOpen, setTypeModalOpen] = useState(false);
  const [editingType, setEditingType] = useState<OrganizationType | null>(null);
  const [pendingTypeId, setPendingTypeId] = useState<string | null>(null);
  const typeMutationPending = useRef(false);
  const [editTypeForm] = Form.useForm<Pick<CreateOrganizationTypeInput, 'name' | 'sortOrder'>>();
  const canUpdateType = permissionCodes?.includes('ORG_TYPE_UPDATE') === true;
  const canDeleteType = permissionCodes?.includes('ORG_TYPE_DELETE') === true;
  const canChangeTypeStatus = permissionCodes?.includes('ORG_TYPE_STATUS_CHANGE') === true;
  const [nodeModalOpen, setNodeModalOpen] = useState(false);
  const [editorOpen, setEditorOpen] = useState(false);
  const [changeType, setChangeType] = useState<OrganizationChangeType | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [changeRefreshKey, setChangeRefreshKey] = useState(0);
  // 行政区划选项来自数据字典 ADMIN_DIVISION；加载失败时编辑表单保护既有值不提交该字段。
  const [divisionOptions, setDivisionOptions] = useState<Array<{ value: string; label: string }>>([]);
  const [divisionNames, setDivisionNames] = useState<Map<string, string>>(new Map());
  const [divisionOptionsLoaded, setDivisionOptionsLoaded] = useState(false);
  const [typeForm] = Form.useForm<CreateOrganizationTypeInput>();
  const [nodeForm] = Form.useForm<CreateOrganizationInput>();
  const [changeForm] = Form.useForm<ChangeFormValue>();
  const parentOptions = useMemo(() => flattenNodes(organizationTree), [organizationTree]);

  useEffect(() => { void loadOrganizationData(); }, []);
  useEffect(()=>{const resize=()=>setTreeHeight(Math.max(180,window.innerHeight-330));window.addEventListener('resize',resize);return()=>window.removeEventListener('resize',resize);},[]);

  function openCreate(parent?:OrganizationNode) {
    nodeForm.resetFields();
    nodeForm.setFieldsValue({parentId:parent?.id,sortOrder:100});
    setNodeModalOpen(true);
  }

  async function loadOrganizationData(): Promise<void> {
    setLoading(true);
    setErrorMessage(null);
    try {
      const [loadedTypes, loadedTree] = await Promise.all([permissionCodes?.includes('ORG_TYPE_READ') ? organizationApi.listTypes().catch(error=>{if(error instanceof ApiRequestError&&error.status===403)return [];throw error;}) : Promise.resolve([]), organizationApi.listTree()]);
      setTypes(loadedTypes);
      setOrganizationTree(loadedTree);
      setSelectedNode((current) => { const requested = new URLSearchParams(window.location.search).get('organizationId'); return (requested ? findNode(loadedTree, requested) : null) ?? (current ? findNode(loadedTree,current.id) : null) ?? loadedTree[0] ?? null; });
    } catch (error) {
      setErrorMessage(toMessage(error));
    } finally {
      setLoading(false);
    }
    try {
      const division = await loadDivisionChoices();
      setDivisionOptions(division.options);
      setDivisionNames(division.names);
      setDivisionOptionsLoaded(true);
    } catch {
      setDivisionOptionsLoaded(false);
    }
  }

  async function createOrganizationType(values: CreateOrganizationTypeInput): Promise<void> {
    await runSubmission(async () => {
      await organizationApi.createType({ ...values, sortOrder: values.sortOrder ?? 100 });
      message.success('组织类型已创建');
      setTypeModalOpen(false);
      typeForm.resetFields();
      await loadOrganizationData();
    });
  }

  async function mutateType(type: OrganizationType, action: () => Promise<unknown>, success: string, editing = false): Promise<void> {
    if ((type.builtIn && !(editing && showAdminTabs)) || typeMutationPending.current) return;
    typeMutationPending.current = true;
    setPendingTypeId(type.id);
    try {
      await action();
      message.success(success);
      await loadOrganizationData();
    } catch (error) { message.error(toMessage(error)); }
    finally { typeMutationPending.current = false; setPendingTypeId(null); }
  }

  async function saveType(values: Pick<CreateOrganizationTypeInput, 'name' | 'sortOrder'>): Promise<void> {
    if (!editingType || !canUpdateType) return;
    await mutateType(editingType, async () => {
      await organizationApi.updateType(editingType.id, {name:values.name.trim(),sortOrder:values.sortOrder});
      setEditingType(null);
    }, '组织类型已更新', true);
  }

  const managedTypeColumns: ColumnsType<OrganizationType> = [...typeColumns.map(column => column.key !== 'status' ? column : {
    ...column,
    render: (_status: OrganizationType['status'], type: OrganizationType) => <><Tag color={type.status==='ENABLED'?'green':'default'}>{type.status==='ENABLED'?'启用':'停用'}</Tag><ConfiguredAction actionKey="ORG_TYPE_STATUS_CHANGE" permissionCodes={permissionCodes??[]}><Radio.Group
      aria-label={`${type.name}状态`} size="small" optionType="button" buttonStyle="solid"
      value={type.status} disabled={type.builtIn || !canChangeTypeStatus || pendingTypeId !== null}
      options={[{label:'启用',value:'ENABLED'},{label:'停用',value:'DISABLED'}]}
      onChange={event=>void mutateType(type, ()=>organizationApi.changeTypeStatus(type.id,event.target.value), '组织类型状态已更新')}
    /></ConfiguredAction></>
  }), {
    title:'操作', key:'actions', render: (_: unknown, type: OrganizationType) => <Space>
      <Button actionKey="ORG_TYPE_UPDATE" hidden={!permissionCodes?.includes('ORG_TYPE_UPDATE')} size="small" disabled={(type.builtIn && !showAdminTabs) || !canUpdateType || pendingTypeId !== null} onClick={()=>{editTypeForm.setFieldsValue({name:type.name,sortOrder:type.sortOrder});setEditingType(type);}}>编辑</Button>
      <Button actionKey="ORG_TYPE_DELETE" hidden={!permissionCodes?.includes('ORG_TYPE_DELETE')} size="small" danger disabled={type.builtIn || !canDeleteType || pendingTypeId !== null} onClick={()=>Modal.confirm({actionPrefix:'ORG_TYPE_DELETE',title:'删除组织类型',content:`确认删除“${type.name}”吗？`,okText:'删除',cancelText:'取消',onOk:()=>mutateType(type,()=>organizationApi.deleteType(type.id),'组织类型已删除')})}>删除</Button>
    </Space>
  }];

  async function createOrganizationNode(values: CreateOrganizationInput): Promise<void> {
    await runSubmission(async () => {
      await organizationApi.createOrganization({ ...values, parentId: values.parentId || undefined, sortOrder: values.sortOrder ?? 100 });
      message.success('组织节点已创建');
      setNodeModalOpen(false);
      nodeForm.resetFields();
      await loadOrganizationData();
    });
  }

  async function runSubmission(action: () => Promise<void>): Promise<void> {
    setSubmitting(true);
    try { await action(); } catch (error) { message.error(toMessage(error)); } finally { setSubmitting(false); }
  }

  function openChange(type: OrganizationChangeType): void {
    changeForm.resetFields();
    setChangeType(type);
  }

  async function submitChange(values: ChangeFormValue): Promise<void> {
    if (!selectedNode || !changeType) return;
    const input: CreateOrganizationChangeInput = {
      organizationId: selectedNode.id, changeType, expectedVersion: selectedNode.versionNo,
      reason: values.reason.trim(), targetParentId: changeType === 'MOVE' ? values.targetParentId : undefined
    };
    await runSubmission(async () => {
      await organizationApi.createChange(input);
      message.success('组织变更申请已提交审核');
      setChangeType(null);
      setChangeRefreshKey((value) => value + 1);
    });
  }

  function confirmEnable(): void {
    if (!selectedNode) return;
    Modal.confirm({ actionPrefix: 'ORG_NODE_UPDATE',
      title: '重新启用组织节点', content: `确认重新启用“${selectedNode.name}”并重算其下级有效状态吗？`,
      okText: '确认启用', cancelText: '取消',
      onOk: async () => {
        await organizationApi.enableOrganization(selectedNode.id, selectedNode.versionNo);
        message.success('组织节点已重新启用');
        await loadOrganizationData();
      }
    });
  }

  /**
   * 拖拽落点三分支：放入目标内部 → 直接改父级；同级空隙 → 直接重排；
   * 跨父空隙 → 先改父级，再按落点空隙重排目标同级。根节点不可拖动。
   */
  async function handleTreeDrop(info: TreeDropInfo): Promise<void> {
    if (!canUpdateNode) return;
    const dragNode = findNode(organizationTree, String(info.dragNode.key));
    const dropNode = findNode(organizationTree, String(info.node.key));
    if (!dragNode || !dropNode || dragNode.id === dropNode.id) return;
    if (dragNode.parentId === null) {
      message.warning('根节点不可拖动');
      return;
    }
    const dropPosParts = String(info.node.pos).split('-');
    const relative = Number(info.dropPosition) - Number(dropPosParts[dropPosParts.length - 1]);
    try {
      if (!info.dropToGap) {
        const violation = dragMoveViolation(dragNode, dropNode, false);
        if (violation) {
          message.warning(violation);
          return;
        }
        await organizationApi.moveOrganization(dragNode.id, dropNode.id, dragNode.versionNo);
        message.success('组织节点已移动');
        await loadOrganizationData();
        return;
      }
      if (dropNode.parentId === null || dropNode.parentId === undefined) {
        message.warning('组织树只能保留一个根节点');
        return;
      }
      const parentChanged = dragNode.parentId !== dropNode.parentId;
      if (parentChanged) {
        const violation = dragMoveViolation(dragNode, dropNode, true);
        if (violation) {
          message.warning(violation);
          return;
        }
      }
      if (!parentChanged) {
        await reorderSiblings(dragNode, dropNode, relative);
        message.success('组织节点顺序已更新');
        await loadOrganizationData();
        return;
      }
      await organizationApi.moveOrganization(dragNode.id, String(dropNode.parentId), dragNode.versionNo);
      // 移动后版本号与同级集合已变化，重新拉树再按落点空隙重排目标同级。
      const freshTree = await organizationApi.listTree();
      const freshDrag = findNode(freshTree, dragNode.id);
      if (!freshDrag) throw new Error('组织移动结果未返回');
      const freshSiblings = siblingsOf(freshTree, freshDrag).filter((item) => item.id !== freshDrag.id);
      const targetIndex = freshSiblings.findIndex((item) => item.id === dropNode.id);
      if (targetIndex >= 0) {
        freshSiblings.splice(relative === -1 ? targetIndex : targetIndex + 1, 0, freshDrag);
        await organizationApi.reorderOrganizations({ parentId: freshDrag.parentId, items: toOrderItems(freshSiblings) });
      }
      message.success('组织节点已移动');
      await loadOrganizationData();
    } catch (error) {
      message.error(toMessage(error));
      await loadOrganizationData();
    }
  }

  async function reorderSiblings(dragNode: OrganizationNode, dropNode: OrganizationNode, relative: number): Promise<void> {
    const siblings = siblingsOf(organizationTree, dragNode).filter((item) => item.id !== dragNode.id);
    const dropIndex = siblings.findIndex((item) => item.id === dropNode.id);
    if (dropIndex < 0) throw new Error('组织树已变化，请刷新后重试');
    siblings.splice(relative === -1 ? dropIndex : dropIndex + 1, 0, dragNode);
    const input: ReorderOrganizationsInput = { parentId: dragNode.parentId, items: toOrderItems(siblings) };
    await organizationApi.reorderOrganizations(input);
  }

  return (
    <div className="page-stack management-page organization-management-page">
      {errorMessage && <Alert type="error" showIcon message={errorMessage} action={<PlainButton  size="small" onClick={() => void loadOrganizationData()}>重试</PlainButton>} />}
      {!showAdminTabs&&!familyReadOnly && <div className="page-heading"><Button actionKey="ORG_NODE_CREATE" hidden={!permissionCodes?.includes('ORG_NODE_CREATE')} type="primary" icon={<FolderPlus size={16}/>} onClick={()=>openCreate()}>新增组织节点</Button></div>}
      <Tabs className="page-sections" renderTabBar={(props,DefaultTabBar)=>showAdminTabs||canReadChanges?<DefaultTabBar {...props}/>:<></>} activeKey={(activeTab==='types'&&!showAdminTabs)||(activeTab==='changes'&&!canReadChanges)?'tree':activeTab} onChange={setActiveTab} tabBarExtraContent={familyReadOnly?null:<Space wrap>{activeTab==='types'?<Button actionKey="ORG_TYPE_CREATE" hidden={!permissionCodes?.includes('ORG_TYPE_CREATE')} type="primary" icon={<Plus size={16}/>} onClick={()=>setTypeModalOpen(true)}>新增组织类型</Button>:activeTab==='tree'?<Button actionKey="ORG_NODE_CREATE" hidden={!permissionCodes?.includes('ORG_NODE_CREATE')} type="primary" icon={<FolderPlus size={16}/>} onClick={()=>openCreate()}>新增组织节点</Button>:null}</Space>} items={[
        { key: 'tree', label: '组织架构', children: <Spin spinning={loading}><div className="management-split organization-split">
          <section className="content-panel management-tree-pane" aria-label="组织架构树">
              <div className="management-tree-search"><Input aria-label="搜索组织" allowClear placeholder="搜索组织名称或编码" value={treeSearch} onChange={event=>setTreeSearch(event.target.value)}/></div>
              {organizationTree.length ? <Tree key={treeSearch?'search':'all'} showLine blockNode height={treeHeight} draggable={canUpdateNode&&!familyReadOnly&&!treeSearch?{ icon: false }:false} defaultExpandAll {...(treeSearch?{expandedKeys:flattenNodes(searchOrganizationTree(organizationTree,treeSearch)).map(node=>node.id)}:{})} selectedKeys={selectedNode ? [selectedNode.id] : []} onSelect={(keys) => {if(keys.length){setSelectedNode(findNode(organizationTree,String(keys[0])));setDetailTab(canReadMembers?'members':'details');}}} onDrop={handleTreeDrop} treeData={toTreeData(searchOrganizationTree(organizationTree,treeSearch),divisionNames,new Map(types.map(type=>[type.code,type.name])))} className="organization-tree" /> : <div className="empty-state">{showAdminTabs?'暂无组织节点':'当前角色暂无可访问组织，请检查角色的数据范围和组织授权'}</div>}
          </section>
          <section className="content-panel management-detail-pane" aria-label="选中组织">
              {selectedNode ? <>
                <div className="management-detail-heading"><div><strong>{selectedNode.namePath ?? selectedNode.name}</strong><span className="management-context-code">{selectedNode.code}</span></div>{effectiveStatusTag(selectedNode)}</div>
                {!familyReadOnly&&<Space wrap className="management-context-actions">
                  {!['SCHOOL','TRAINING_INSTITUTION','CAMPUS','GRADE','CLASS'].includes(selectedNode.typeCode) && <Button actionKey="ORG_NODE_CREATE" hidden={!permissionCodes?.includes('ORG_NODE_CREATE')} onClick={()=>openCreate(selectedNode)}>新增下级</Button>}

                  <Button actionKey="ORG_NODE_UPDATE" hidden={!permissionCodes?.includes('ORG_NODE_UPDATE')} icon={<Edit3 size={15}/>} onClick={()=>setEditorOpen(true)}>编辑</Button>
                  {selectedNode.status==='DISABLED'?<Button actionKey="ORG_NODE_UPDATE" hidden={!permissionCodes?.includes('ORG_NODE_UPDATE')} onClick={confirmEnable}>重新启用</Button>:<Button actionKey="ORG_NODE_CHANGE_SUBMIT" hidden={!permissionCodes?.includes('ORG_NODE_CHANGE_SUBMIT')} onClick={()=>openChange('DISABLE')}>申请停用</Button>}
                  <Button actionKey="ORG_NODE_CHANGE_SUBMIT" hidden={!permissionCodes?.includes('ORG_NODE_CHANGE_SUBMIT')} onClick={()=>openChange('MOVE')}>申请移动</Button>
                  <Button actionKey="ORG_NODE_CHANGE_SUBMIT" hidden={!permissionCodes?.includes('ORG_NODE_CHANGE_SUBMIT')} danger onClick={()=>openChange('DELETE')}>申请删除</Button>
                </Space>}
                <Tabs className="management-detail-tabs" activeKey={detailTab} onChange={setDetailTab} items={[
                  ...(canReadMembers?[{key:'members',label:'组织用户',children:<OrganizationMembersDrawer key={selectedNode.id} organization={selectedNode} permissionCodes={permissionCodes} embedded onClose={()=>{}}/>}]:[]),
                  {key:'business',label:selectedNode.typeCode==='TRAINING_INSTITUTION'?'校区与课程分类':selectedNode.typeCode==='SCHOOL'?'校区与年级':selectedNode.typeCode==='CAMPUS'?'年级':selectedNode.typeCode==='GRADE'?'班级':selectedNode.typeCode==='CLASS'?'学生与教师':'下级组织',children:classManagementEnabled && permissionCodes?.includes('CLASS_READ') && ['SCHOOL','TRAINING_INSTITUTION','CAMPUS','GRADE','CLASS'].includes(selectedNode.typeCode)?<SchoolHierarchyPanel permissionCodes={permissionCodes} studentOrganizationRelationshipEnabled={studentOrganizationRelationshipEnabled} key={selectedNode.id} selectedNode={selectedNode} onSelect={id=>{setSelectedNode(findNode(organizationTree,id));setDetailTab('business');}} onChanged={loadOrganizationData} advancedActions={schoolId=><OrganizationOperationBody schoolId={schoolId} showHeading={false} contextual studentOrganizationRelationshipEnabled={studentOrganizationRelationshipEnabled} parentMobileManualRecoveryEnabled={parentMobileManualRecoveryEnabled} studentAccountCancellationEnabled={studentAccountCancellationEnabled} />} />:<Table rowKey="id" dataSource={selectedNode.children} pagination={false} columns={[{title:'名称',dataIndex:'name',render:(_:unknown,item:OrganizationNode)=><NavigationButton type="link" onClick={()=>setSelectedNode(item)}>{item.name}</NavigationButton>}]} />},
                  {key:'details',label:'组织信息',children:<Descriptions column={1} size="small">
                  <Descriptions.Item label="名称">{selectedNode.namePath ?? selectedNode.name}</Descriptions.Item>
                  <Descriptions.Item label="编码">{selectedNode.code}</Descriptions.Item>
                  <Descriptions.Item label="类型">{types.find(type=>type.code===selectedNode.typeCode)?.name ?? builtInOrganizationTypeNames[selectedNode.typeCode] ?? selectedNode.typeCode}</Descriptions.Item>
                  <Descriptions.Item label="行政区划">{selectedNode.adminDivisionCode ? `${divisionNames.get(selectedNode.adminDivisionCode) ?? selectedNode.adminDivisionCode}（${selectedNode.adminDivisionCode}）` : '未挂接'}</Descriptions.Item>
                  <Descriptions.Item label="自身状态">{ownStatusTag(selectedNode)}</Descriptions.Item>
                  <Descriptions.Item label="有效状态">{effectiveStatusTag(selectedNode)}</Descriptions.Item>
                  <Descriptions.Item label="版本">{selectedNode.versionNo}</Descriptions.Item>
                </Descriptions>}]} />
              </> : <div className="empty-state">请选择组织节点</div>}
          </section>
        </div></Spin> },
        { key: 'types', label: '组织类型', children: <Card title="组织类型" className="content-panel">
              <Table<OrganizationType> rowKey="id" columns={managedTypeColumns} dataSource={types} pagination={false} size="small" locale={{ emptyText: '暂无组织类型' }} />
            </Card> },
        { key: 'changes', label: '变更记录', children: <OrganizationChangeReviewPanel canReview={canReview} refreshKey={changeRefreshKey} /> },

      ].filter(item=>item.key==='tree'||(item.key==='types'?showAdminTabs:permissionCodes?.some(code=>['ORG_NODE_CHANGE_SUBMIT','ORG_NODE_CHANGE_REVIEW'].includes(code))))} />
      <OrganizationNodeEditorDrawer open={editorOpen} node={selectedNode} divisionOptions={divisionOptions} divisionOptionsLoaded={divisionOptionsLoaded} onClose={() => setEditorOpen(false)} onSaved={loadOrganizationData} />
      <Modal title="编辑组织类型" open={editingType !== null} footer={null} onCancel={()=>{if(!pendingTypeId)setEditingType(null);}} destroyOnHidden>
        <Form form={editTypeForm} layout="vertical" disabled={pendingTypeId !== null} onFinish={values=>void saveType(values)}>
          <Form.Item label="类型编码"><Input value={editingType?.code} disabled /></Form.Item>
          <Form.Item name="name" label="类型名称" rules={[{required:true,whitespace:true,message:'请输入类型名称'},{max:32}]}><Input /></Form.Item>
          <Form.Item name="sortOrder" label="排序" rules={[{required:true,message:'请输入排序值'}]}><InputNumber min={0} precision={0} /></Form.Item>
          <div className="form-actions"><PlainButton  disabled={pendingTypeId !== null} onClick={()=>setEditingType(null)}>取消</PlainButton><Button actionKey="ORG_TYPE_UPDATE" hidden={!permissionCodes?.includes('ORG_TYPE_UPDATE')} type="primary" htmlType="submit" loading={pendingTypeId !== null}>保存</Button></div>
        </Form>
      </Modal>
      <CreateTypeModal open={typeModalOpen} form={typeForm} submitting={submitting} onCancel={() => setTypeModalOpen(false)} onSubmit={createOrganizationType} />
      <CreateNodeModal open={nodeModalOpen} form={nodeForm} types={types} parentOptions={parentOptions} divisionOptions={divisionOptions} submitting={submitting} onCancel={() => setNodeModalOpen(false)} onSubmit={createOrganizationNode} />
      <Modal title={changeModalTitle(changeType)} open={changeType !== null} footer={null} width="min(520px, 92vw)" onCancel={() => setChangeType(null)} destroyOnHidden>
        <Form form={changeForm} layout="vertical" onFinish={submitChange}>
          {changeType === 'MOVE' && <Form.Item name="targetParentId" label="目标上级组织" rules={[{ required: true, message: '请选择目标上级组织' }]}><Select options={parentOptions.filter((item) => item.id !== selectedNode?.id).map((item) => ({ value: item.id, label: item.label }))} /></Form.Item>}
          <Form.Item name="reason" label="申请原因" rules={[{ required: true, whitespace: true, message: '请输入申请原因' }, { max: 500 }]}><Input.TextArea rows={4} maxLength={500} showCount /></Form.Item>
          <div className="form-actions"><PlainButton  onClick={() => setChangeType(null)}>取消</PlainButton><Button actionKey="ORG_NODE_CHANGE_SUBMIT" hidden={!permissionCodes?.includes('ORG_NODE_CHANGE_SUBMIT')} danger type="primary" htmlType="submit" loading={submitting}>提交审核</Button></div>
        </Form>
      </Modal>
    </div>
  );
}

function OrganizationOperationView(props: OrganizationOperationCapabilities) {
  return <div className="page-stack management-page organization-management-page"><OrganizationOperationBody showHeading {...props} /></div>;
}

/** 机构业务面板：独立页（纯机构管理员）与平台组织视图页签共用，showHeading 控制页头布局。 */
function OrganizationOperationBody({ schoolId, permissionCodes, contextual = false, showHeading = true, studentOrganizationRelationshipEnabled, parentMobileManualRecoveryEnabled, studentAccountCancellationEnabled, classManagementEnabled }: { schoolId?:string; showHeading?: boolean; contextual?: boolean } & Partial<OrganizationOperationCapabilities>) {
  const [studentRelationshipOpen, setStudentRelationshipOpen] = useState(false);
  const [teacherClassOpen, setTeacherClassOpen] = useState(false);
  const [parentMobileOpen, setParentMobileOpen] = useState(false);
  const [studentCancellationOpen, setStudentCancellationOpen] = useState(false);
  const actions = <Space size={6} wrap={!contextual} className="organization-business-actions" aria-label="机构业务操作">
    {studentOrganizationRelationshipEnabled && <Button actionKey="STUDENT_ORGANIZATION_MANAGE" size="small" className="class-action-relationship" disabled={contextual&&!schoolId} icon={<ArrowRightLeft size={16} />} onClick={() => setStudentRelationshipOpen(true)}>学员关系</Button>}
    {parentMobileManualRecoveryEnabled && <Button actionKey="PARENT_MOBILE_MANUAL_RECOVERY_MANAGE" icon={<ShieldCheck size={16} />} onClick={() => setParentMobileOpen(true)}>家长换号核验</Button>}
    {studentAccountCancellationEnabled && <Button actionKey="STUDENT_ACCOUNT_CANCELLATION_MANAGE" danger icon={<Trash2 size={16} />} onClick={() => setStudentCancellationOpen(true)}>学生账号注销</Button>}
    <Button actionKey="TEACHER_CLASS_ASSIGN" size="small" className="class-action-teacher" title="配置教师班级" aria-label="配置教师班级" disabled={contextual&&!schoolId} icon={<UserRoundCheck size={16} />} onClick={() => setTeacherClassOpen(true)}>教师班级</Button>
  </Space>;
  return <>
    {showHeading ? <div className="page-heading"><h1>学校与班级</h1></div> : null}
    {actions}
    {classManagementEnabled && !contextual && <SchoolHierarchyPanel showTree permissionCodes={permissionCodes} studentOrganizationRelationshipEnabled={studentOrganizationRelationshipEnabled} />}
    <StudentOrganizationLifecycleDrawer schoolId={schoolId} open={studentRelationshipOpen} onClose={() => setStudentRelationshipOpen(false)} />
    <TeacherClassAssignmentDrawer schoolId={schoolId} open={teacherClassOpen} onClose={() => setTeacherClassOpen(false)} />
    <ParentMobileManualRecoveryDrawer open={parentMobileOpen} onClose={() => setParentMobileOpen(false)} />
    <StudentAccountCancellationDrawer open={studentCancellationOpen} onClose={() => setStudentCancellationOpen(false)} />
  </>;
}

function CreateTypeModal({ open, form, submitting, onCancel, onSubmit }: { open: boolean; form: FormInstance<CreateOrganizationTypeInput>; submitting: boolean; onCancel: () => void; onSubmit: (value: CreateOrganizationTypeInput) => Promise<void> }) {
  return <Modal title="新增组织类型" open={open} footer={null} width="min(520px, 92vw)" onCancel={onCancel} destroyOnHidden><Form form={form} layout="vertical" initialValues={{ sortOrder: 100 }} onFinish={onSubmit}>
    <Form.Item name="code" label="类型编码" rules={[{ required: true, message: '请输入类型编码' }, { max: 32 }, { pattern: /^[A-Z][A-Z0-9_]{2,31}$/, message: '类型编码仅允许3至32位大写字母、数字和下划线，且必须以字母开头' }]}><Input autoComplete="off" /></Form.Item>
    <Form.Item name="name" label="类型名称" rules={[{ required: true, message: '请输入类型名称' }, { max: 32 }]}><Input autoComplete="off" /></Form.Item>
    <Form.Item name="sortOrder" label="排序" rules={[{ required: true, message: '请输入排序值' }]}><InputNumber min={0} precision={0} className="full-width" /></Form.Item>
    <div className="form-actions"><PlainButton  onClick={onCancel}>取消</PlainButton><Button actionKey="ORG_TYPE_CREATE" type="primary" htmlType="submit" loading={submitting}>创建类型</Button></div>
  </Form></Modal>;
}

function CreateNodeModal({ open, form, types, parentOptions, divisionOptions, submitting, onCancel, onSubmit }: { open: boolean; form: FormInstance<CreateOrganizationInput>; types: OrganizationType[]; parentOptions: Array<{ id: string; label: string }>; divisionOptions: Array<{ value: string; label: string }>; submitting: boolean; onCancel: () => void; onSubmit: (value: CreateOrganizationInput) => Promise<void> }) {
  return <Modal title="新增组织节点" open={open} footer={null} width="min(640px, 92vw)" onCancel={onCancel} destroyOnHidden><Form form={form} layout="vertical" initialValues={{ sortOrder: 100 }} onFinish={onSubmit}>
    <Form.Item name="code" label="组织编码" rules={[{ required: true, message: '请输入组织编码' }, { max: 64 }, { pattern: /^[A-Z][A-Z0-9_]{2,63}$/, message: '组织编码仅允许3至64位大写字母、数字和下划线，且必须以字母开头' }]}><Input autoComplete="off" /></Form.Item>
    <Form.Item name="name" label="组织名称" rules={[{ required: true, message: '请输入组织名称' }, { max: 100 }]}><Input autoComplete="off" /></Form.Item>
    <Form.Item name="typeCode" label="组织类型" rules={[{ required: true, message: '请选择组织类型' }]}><Select options={types.filter((item) => item.status === 'ENABLED').map((item) => ({ value: item.code, label: `${item.name}（${item.code}）` }))} /></Form.Item>
    <Form.Item name="parentId" label="上级组织"><Select allowClear placeholder="不选择则创建根节点" options={parentOptions.map((item) => ({ value: item.id, label: item.label }))} /></Form.Item>
    <Form.Item name="adminDivisionCode" label="行政区划"><Select allowClear showSearch optionFilterProp="label" placeholder="可选择该组织对应的行政区划" options={divisionOptions} /></Form.Item>
    <Form.Item name="sortOrder" label="排序" rules={[{ required: true, message: '请输入排序值' }]}><InputNumber min={0} precision={0} className="full-width" /></Form.Item>
    <div className="form-actions"><PlainButton  onClick={onCancel}>取消</PlainButton><Button actionKey="ORG_NODE_CREATE" type="primary" htmlType="submit" loading={submitting}>创建节点</Button></div>
  </Form></Modal>;
}

export function searchOrganizationTree(nodes:OrganizationNode[],query:string):OrganizationNode[] {
  const keyword=query.trim().toLowerCase();
  if(!keyword)return nodes;
  return nodes.flatMap(node=>{
    if(`${node.name} ${node.code}`.toLowerCase().includes(keyword))return [node];
    const children=searchOrganizationTree(node.children,query);
    return children.length?[{...node,children}]:[];
  });
}
const organizationTypeColors: Record<string,string> = {
  COUNTRY:'geekblue', CITY:'cyan', REGION:'purple', SCHOOL:'blue',
  TRAINING_INSTITUTION:'orange', CAMPUS:'cyan', GRADE:'gold', CLASS:'green', FAMILY:'magenta'
};
const builtInOrganizationTypeNames: Record<string,string> = {
  COUNTRY:'国家', CITY:'城市', REGION:'区域', SCHOOL:'学校',
  TRAINING_INSTITUTION:'培训机构', CAMPUS:'校区', GRADE:'年级', CLASS:'班级', FAMILY:'家庭'
};

function toTreeData(nodes: OrganizationNode[], divisionNames: Map<string, string>,typeNames:Map<string,string>): DataNode[] {
  return nodes.map((node) => ({ key: node.id, title: <div className="management-tree-label"><Building2 size={15} aria-hidden="true" /><span title={node.name}>{node.name}</span><Tag color={organizationTypeColors[node.typeCode] ?? 'default'}>{typeNames.get(node.typeCode)??builtInOrganizationTypeNames[node.typeCode]??node.typeCode}</Tag>{node.status === 'DISABLED' ? <Tag color="red">自身停用</Tag> : node.effectiveStatus === 'DISABLED' ? <Tag color="orange">上级已停用</Tag> : null}</div>, children: toTreeData(node.children, divisionNames,typeNames) }));
}

/** 从数据字典 ADMIN_DIVISION 读取启用的行政区划选项与编码-名称映射。 */
async function loadDivisionChoices(): Promise<{ options: Array<{ value: string; label: string }>; names: Map<string, string> }> {
  const items = await organizationApi.listAdminDivisions();
  return {
    options: items.map((item) => ({ value: item.code, label: `${item.name}（${item.code}）` })),
    names: new Map(items.map((item) => [item.code, item.name]))
  };
}

/** 同级兄弟节点：根层级返回整棵树的顶层列表。 */
function siblingsOf(tree: OrganizationNode[], node: OrganizationNode): OrganizationNode[] {
  if (node.parentId === null) return tree;
  const parent = findNode(tree, node.parentId);
  return parent ? parent.children : [];
}

function toOrderItems(nodes: OrganizationNode[]): Array<{ organizationId: string; expectedVersion: number }> {
  return nodes.map((node) => ({ organizationId: node.id, expectedVersion: node.versionNo }));
}

/** 判断子树中是否包含指定节点（用于阻止把组织移动到自身后代下）。 */
function containsNode(node: OrganizationNode, id: string): boolean {
  if (node.id === id) return true;
  return node.children.some((child) => containsNode(child, id));
}

/**
 * 拖拽防环判定：返回违规提示文案，合法返回 null。
 * 拖入内部时目标与拖动节点存在祖先-后代关系（含自身）即成环；
 * 空隙换父时仅当新父位于拖动节点自己的子树内才成环——
 * 新父子树包含拖动节点只是其当前位置的正常情况（例如从下级拖出），不能据此拦截。
 */
export function dragMoveViolation(dragNode: OrganizationNode, dropNode: OrganizationNode, dropToGap: boolean): string | null {
  if (!dropToGap) {
    if (dropNode.id === dragNode.id || containsNode(dropNode, dragNode.id) || containsNode(dragNode, dropNode.id)) {
      return '不能移动到自身或其下级组织';
    }
    return null;
  }
  const newParentId = dropNode.parentId;
  if (newParentId == null) return null;
  return containsNode(dragNode, newParentId) ? '不能移动到自身或其下级组织' : null;
}

function flattenNodes(nodes: OrganizationNode[], level = 0): Array<{ id: string; label: string }> {
  return nodes.flatMap((node) => [{ id: node.id, label: `${node.namePath ?? node.name}（${node.code}）` }, ...flattenNodes(node.children, level + 1)]);
}

function findNode(nodes: OrganizationNode[], id: string): OrganizationNode | null {
  for (const node of nodes) { if (node.id === id) return node; const child = findNode(node.children, id); if (child) return child; }
  return null;
}

function ownStatusTag(node: OrganizationNode) { return <Tag color={node.status === 'ENABLED' ? 'green' : 'red'}>{node.status === 'ENABLED' ? '自身启用' : '自身停用'}</Tag>; }
function effectiveStatusTag(node: OrganizationNode) { return <Tag color={node.effectiveStatus === 'ENABLED' ? 'green' : 'orange'}>{node.effectiveStatus === 'ENABLED' ? '可开展业务' : node.status === 'DISABLED' ? '自身停用' : '上级已停用'}</Tag>; }
function changeModalTitle(type: OrganizationChangeType | null): string { return type === 'DISABLE' ? '申请停用组织' : type === 'MOVE' ? '申请移动组织' : type === 'DELETE' ? '申请删除组织' : '组织变更申请'; }
function toMessage(error: unknown): string { return error instanceof Error ? error.message : '请求未能完成'; }
