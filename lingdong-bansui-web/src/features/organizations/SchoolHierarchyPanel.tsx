import { useEffect, useRef, useState } from 'react';
import { Button as NavigationButton, Alert, Form, Input, InputNumber, Select, Space, Tabs, Tree, message } from 'antd';
import { ConfiguredButton as Button } from '../../components/ConfiguredButton';
import { ConfiguredModal as Modal } from '../../components/ConfiguredModal';
import { ViewportTable as Table } from '../../components/ViewportTable';
import { classApi, type ClassOrganization } from '../../api/classes';
import type { OrganizationNode } from '../../api/organization';
import { classAssignmentApi } from './classAssignmentApi';
import type { StudentOption, TeacherOption } from '../learning-tasks/types';
import { StudentClassAssignmentDrawer } from './StudentClassAssignmentDrawer';
import { TeacherClassAssignmentDrawer } from './TeacherClassAssignmentDrawer';
import {subscribeTeacherProfileChanged} from '../users/teacherProfileChanged';
import {InstitutionEnrollmentReviewDrawer,ClassInvitationButton} from './InstitutionEnrollmentReviewDrawer';
import { subscribeStudentProfileChanged } from '../users/studentProfileChanged';

const isInstitutionRootType=(code?:string)=>code==='SCHOOL'||code==='TRAINING_INSTITUTION';
export function SchoolHierarchyPanel({ selectedNode, onSelect, onChanged, showTree = false, advancedActions, permissionCodes = [], studentOrganizationRelationshipEnabled = false }: {
  selectedNode?: OrganizationNode | null; onSelect?: (id: string) => void; onChanged?: () => Promise<void>;
  showTree?: boolean; advancedActions?: React.ReactNode|((schoolId?:string)=>React.ReactNode); permissionCodes?: string[]; studentOrganizationRelationshipEnabled?: boolean;
}) {
  const [schools, setSchools] = useState<ClassOrganization[]>([]);
  const [campuses, setCampuses] = useState<ClassOrganization[]>([]);
  const [createType, setCreateType] = useState<'CAMPUS'|'GRADE'|'CLASS'>('GRADE');
  const [grades, setGrades] = useState<ClassOrganization[]>([]);
  const [classes, setClasses] = useState<ClassOrganization[]>([]);
  const [selectedId, setSelectedId] = useState<string>();
  const [students, setStudents] = useState<StudentOption[]>([]);
  const [teachers, setTeachers] = useState<TeacherOption[]>([]);
  const [memberRefresh, setMemberRefresh] = useState(0);
  const memberRequests = useRef<{ key: string; students?: Promise<StudentOption[]>; teachers?: Promise<TeacherOption[]> }>();
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<ClassOrganization | null>(null);
  const [assigning, setAssigning] = useState<ClassOrganization | null>(null);
  const [gradeId, setGradeId] = useState<string>();
  const [reviewOpen,setReviewOpen]=useState(false);
  const [studentOpen, setStudentOpen] = useState(false);
  const [teacherOpen, setTeacherOpen] = useState(false);
  const [form] = Form.useForm<{ name: string; sortOrder: number }>();
  const ordered = (values: ClassOrganization[]) => [...values].sort((a,b)=>a.sortOrder-b.sortOrder || a.id.localeCompare(b.id,undefined,{numeric:true}));
  const items = [...schools, ...campuses, ...grades, ...classes];
  const node = selectedNode ?? items.find(item => item.id === selectedId) ?? schools[0] ?? campuses[0] ?? grades[0] ?? classes[0];
  const contextSchools = [...schools];
  const contextCampuses = [...campuses];
  const contextGrades = [...grades];
  const contextOnlyIds = new Set<string>();
  const placeholder = (id: string, name: string, typeCode: 'SCHOOL' | 'CAMPUS' | 'GRADE', parentId: string | null): ClassOrganization => ({id,name,typeCode,parentId,code:'',status:'DISABLED',effectiveStatus:'DISABLED',versionNo:0,sortOrder:0});
  for (const item of [...campuses, ...grades, ...classes]) {
    if (item.schoolOrganizationId && !contextSchools.some(school => school.id === item.schoolOrganizationId)) {
      contextSchools.push(placeholder(item.schoolOrganizationId,item.schoolName ?? '所属学校','SCHOOL',null));contextOnlyIds.add(item.schoolOrganizationId);
    }
    if (item.campusOrganizationId && !contextCampuses.some(campus => campus.id === item.campusOrganizationId)) {
      contextCampuses.push(placeholder(item.campusOrganizationId,item.campusName ?? '所属校区','CAMPUS',item.schoolOrganizationId ?? null));contextOnlyIds.add(item.campusOrganizationId);
    }
    if (item.typeCode === 'CLASS' && item.gradeOrganizationId && !contextGrades.some(grade => grade.id === item.gradeOrganizationId)) {
      contextGrades.push(placeholder(item.gradeOrganizationId,item.gradeName ?? '所属年级','GRADE',item.campusOrganizationId ?? item.schoolOrganizationId ?? null));contextOnlyIds.add(item.gradeOrganizationId);
    }
  }
  const schoolId = isInstitutionRootType(node?.typeCode) ? node?.id : items.find(item => item.id === node?.id)?.schoolOrganizationId ?? items.find(item => item.id === node?.parentId)?.schoolOrganizationId ?? (schools.some(item => item.id === node?.parentId) ? node?.parentId : undefined);
  const trainingInstitution=node?.typeCode==='TRAINING_INSTITUTION'||schools.find(item=>item.id===schoolId)?.typeCode==='TRAINING_INSTITUTION';
  const memberClassId = node?.typeCode === 'CLASS' ? node.id : undefined;
  const canReadStudents = permissionCodes.includes('STUDENT_READ');
  const canReadTeachers = permissionCodes.includes('TEACHER_CLASS_ASSIGN');
  useEffect(() => { void refresh(); }, []);
  useEffect(() => {
    if (!memberClassId || !canReadStudents) return;
    return subscribeStudentProfileChanged(() => setMemberRefresh(value => value + 1));
  }, [memberClassId, canReadStudents]);
  useEffect(()=>{if(!memberClassId||!canReadTeachers)return;return subscribeTeacherProfileChanged(()=>setMemberRefresh(value=>value+1));},[memberClassId,canReadTeachers]);
  useEffect(() => {
    let cancelled = false;
    setStudents([]); setTeachers([]); setError('');
    if (!memberClassId) { memberRequests.current = undefined; return; }
    const key = JSON.stringify([memberClassId, canReadStudents, canReadTeachers, memberRefresh]);
    // Reuse this load during StrictMode effect replay; scope/permission changes and explicit refreshes get a new load.
    if (memberRequests.current?.key !== key) {
      memberRequests.current = {
        key,
        students: canReadStudents ? classAssignmentApi.listClassMembers(memberClassId) : undefined,
        teachers: canReadTeachers ? classAssignmentApi.listTeachers(memberClassId) : undefined
      };
    }
    void memberRequests.current.students?.then(values=>{if(!cancelled)setStudents(values);}).catch(err=>{if(!cancelled)setError(err.message);});
    void memberRequests.current.teachers?.then(values=>{if(!cancelled)setTeachers(values.filter(item=>item.classIds.includes(memberClassId)));}).catch(err=>{if(!cancelled)setError(err.message);});
    return ()=>{cancelled=true;};
  }, [memberClassId, canReadStudents, canReadTeachers, memberRefresh]);
  async function refresh() {
    setLoading(true); setError('');
    try { const [s,ca,g,c] = await Promise.all([classApi.listSchools(), classApi.listCampuses(), classApi.listGrades(), classApi.listClasses()]); setSchools(s);setCampuses(ca);setGrades(g);setClasses(c); }
    catch (err) { setError(err instanceof Error ? err.message : '加载失败'); }
    finally { setLoading(false); }
  }
  async function saved() { await refresh(); setMemberRefresh(value => value + 1); await onChanged?.(); }
  function select(id: string) { setSelectedId(id); onSelect?.(id); }
  async function submit(values: { name: string; sortOrder: number }) {
    if (!node || submitting) return;
    setSubmitting(true);
    try {
      if (editing) {
        const input = {...values, versionNo: editing.versionNo};
        if (editing.typeCode === 'CAMPUS') await classApi.updateCampus(editing.id,input); else if (editing.typeCode === 'GRADE') await classApi.updateGrade(editing.id,input); else await classApi.updateClass(editing.id,input);
      } else if (createType === 'CAMPUS') await classApi.createCampus({...values,schoolOrganizationId:node.id});
      else if (createType === 'GRADE') await classApi.createGrade({...values,schoolOrganizationId:isInstitutionRootType(node.typeCode)?node.id:schoolId!,...(node.typeCode==='CAMPUS'?{campusOrganizationId:node.id}:{})});
      else await classApi.createClass({...values,gradeOrganizationId:node.id,schoolOrganizationId:schoolId ?? undefined});
      setCreating(false);setEditing(null);await saved();message.success('已保存');
    } catch (err) { message.error(err instanceof Error ? err.message : '保存失败'); } finally {setSubmitting(false);}
  }
  const rows = ordered(isInstitutionRootType(node?.typeCode) ? [...campuses,...grades].filter(item => item.parentId === node.id) : node?.typeCode === 'CAMPUS' ? grades.filter(item=>item.parentId===node.id) : classes.filter(item => item.parentId === node?.id));
  const legacy = isInstitutionRootType(node?.typeCode) ? classes.filter(item => item.parentId === node.id) : [];
  const columns = [
    { title: isInstitutionRootType(node?.typeCode) ? '校区 / 年级' : node?.typeCode === 'CAMPUS' ? '年级' : '班级', dataIndex: 'name', render: (_: unknown,item:ClassOrganization) => <NavigationButton type="link" onClick={() => select(item.id)}>{item.name}</NavigationButton> },
    { title:'状态',dataIndex:'effectiveStatus',render:(value:string)=>value==='ENABLED'?'启用':'停用' },
    { title:'操作',render:(_:unknown,item:ClassOrganization)=><Space><Button actionKey="CLASS_UPDATE" onClick={()=>{setEditing(item);form.setFieldsValue(item);setCreating(true);}}>编辑</Button><Button actionKey="CLASS_STATUS_CHANGE" onClick={()=>Modal.confirm({actionPrefix: 'CLASS_STATUS_CHANGE',title:'确认修改状态',onOk:async()=>{if(item.typeCode==='CAMPUS')await classApi.setCampusStatus(item.id,item.status!=='ENABLED',item.versionNo);else if(item.typeCode==='GRADE')await classApi.setGradeStatus(item.id,item.status!=='ENABLED',item.versionNo);else if(item.status==='ENABLED')await classApi.disableClass(item.id,item.versionNo);else await classApi.enableClass(item.id,item.versionNo);await saved();}})}>{item.status==='ENABLED'?'停用':'启用'}</Button></Space> }
  ];
  const body = <>
    {error && <Alert type="error" message={error} action={<NavigationButton onClick={()=>{void refresh();setMemberRefresh(value=>value+1);}}>重试</NavigationButton>}/>}
    {node && <><div className="section-heading"><h2>{node.name}</h2></div><div className="class-action-toolbar"><Space size={6}>
      {isInstitutionRootType(node.typeCode) && <Button actionKey="CLASS_CREATE" type="primary" disabled={node.effectiveStatus!=='ENABLED'} onClick={()=>{setCreateType('CAMPUS');setEditing(null);form.setFieldsValue({name:'',sortOrder:100});setCreating(true);}}>新增校区</Button>}
      {['SCHOOL','TRAINING_INSTITUTION','CAMPUS','GRADE'].includes(node.typeCode) && <Button actionKey="CLASS_CREATE" type="primary" disabled={node.effectiveStatus!=='ENABLED'||(node.typeCode==='CAMPUS'&&!schoolId)} onClick={()=>{setCreateType(node.typeCode==='GRADE'?'CLASS':'GRADE');setEditing(null);form.setFieldsValue({name:'',sortOrder:100});setCreating(true);}}>{node.typeCode==='GRADE'?(trainingInstitution?'新增课程班':'新增班级'):(trainingInstitution?'新增课程分类':'新增年级')}</Button>}
      {node.typeCode==='CLASS' && <>{studentOrganizationRelationshipEnabled && permissionCodes.includes('STUDENT_CLASS_ASSIGN') && <Button actionKey="STUDENT_CLASS_ASSIGN" className="class-action-add" size="small" disabled={node.effectiveStatus!=='ENABLED'} onClick={()=>setStudentOpen(true)}>添加学生</Button>}{permissionCodes.includes('TEACHER_CLASS_ASSIGN') && <Button actionKey="TEACHER_CLASS_ASSIGN" className="class-action-teacher" size="small" disabled={node.effectiveStatus!=='ENABLED'} onClick={()=>setTeacherOpen(true)}>添加教师</Button>}</>}
    </Space>
    <Space size={6}>{node.typeCode==='CLASS'&&permissionCodes.includes('STUDENT_ENROLLMENT_INVITE_CREATE')&&<ClassInvitationButton classId={node.id}/>}{schoolId&&permissionCodes.includes('STUDENT_ENROLLMENT_REVIEW')&&<Button actionKey="STUDENT_ENROLLMENT_REVIEW" className="class-action-review" size="small" title="审核学生入学申请" onClick={()=>setReviewOpen(true)}>入学审核</Button>}</Space>
    {typeof advancedActions==='function'?advancedActions(schoolId??undefined):advancedActions}</div>
    <InstitutionEnrollmentReviewDrawer open={reviewOpen} institutionId={schoolId??undefined} classId={node.typeCode==='CLASS'?node.id:undefined} onClose={()=>setReviewOpen(false)} onApproved={()=>void saved()}/>
    {node.typeCode==='CLASS' ? <Tabs key={node.id} items={[
      ...(permissionCodes.includes('STUDENT_READ') ? [{key:'students',label:'学生',children:<Table rowKey="id" dataSource={students} columns={[{title:'学生姓名',dataIndex:'studentName'}]} pagination={{}}/>}] : []),
      ...(permissionCodes.includes('TEACHER_CLASS_ASSIGN') ? [{key:'teachers',label:'教师',children:<Table rowKey="userId" dataSource={teachers} columns={[{title:'教师姓名',dataIndex:'displayName'}]} pagination={{}}/>}] : [])
    ]}/> : <Table rowKey="id" loading={loading} dataSource={rows} columns={columns} pagination={{}}/>}
    {legacy.length>0 && <><Alert type="warning" message="待归属年级：以下存量班级直属学校，请选择本校年级归并。"/><Table rowKey="id" dataSource={legacy} pagination={{}} columns={[{title:'待归属班级',dataIndex:'name'},{title:'操作',render:(_:unknown,item:ClassOrganization)=><Button actionKey="CLASS_UPDATE" onClick={()=>{setAssigning(item);setGradeId(undefined);}}>选择年级</Button>}]}/></>}
    </>}
    <Modal title={editing?'编辑':createType==='CAMPUS'?'新增校区':createType==='GRADE'?(trainingInstitution?'新增课程分类':'新增年级'):(trainingInstitution?'新增课程班':'新增班级')} open={creating} footer={null} onCancel={()=>setCreating(false)}><Form form={form} layout="vertical" onFinish={submit}><Form.Item label="上级">{editing ? items.find(item=>item.id===editing.parentId)?.name : node?.name}</Form.Item><Form.Item name="name" label="名称" rules={[{required:true,whitespace:true,max:50}]}><Input/></Form.Item><Form.Item name="sortOrder" label="排序" rules={[{required:true}]}><InputNumber min={0}/></Form.Item><Button actionKey={editing ? 'CLASS_UPDATE' : 'CLASS_CREATE'} type="primary" loading={submitting} htmlType="submit">保存</Button></Form></Modal>
    <Modal title="选择本校年级" open={!!assigning} onCancel={()=>setAssigning(null)} footer={null}><Select className="full-width" value={gradeId} onChange={setGradeId} options={grades.filter(item=>item.schoolOrganizationId===schoolId && item.effectiveStatus==='ENABLED').map(item=>({value:item.id,label:item.namePath??[item.schoolName,item.campusName,item.name].filter(Boolean).join(' / ')}))}/><Button actionKey="CLASS_UPDATE" loading={submitting} disabled={!gradeId || submitting} onClick={()=>void (async()=>{if(submitting)return;setSubmitting(true);try{await classApi.assignGrade(assigning!.id,gradeId!,assigning!.versionNo);setAssigning(null);await saved();}catch(err){message.error(err instanceof Error?err.message:'归并失败');}finally{setSubmitting(false);}})()}>确认归并</Button></Modal>
    <StudentClassAssignmentDrawer open={studentOpen} fixedClassId={node?.id} fixedClassName={items.find(item=>item.id===node?.id)?.namePath??node?.namePath??node?.name} schoolId={schoolId??undefined} onClose={()=>setStudentOpen(false)} onSaved={()=>void saved()}/>
    <TeacherClassAssignmentDrawer open={teacherOpen} fixedClassId={node?.id} fixedClassName={items.find(item=>item.id===node?.id)?.namePath??node?.namePath??node?.name} schoolId={schoolId??undefined} onClose={()=>setTeacherOpen(false)} onSaved={()=>void saved()}/>
  </>;
  function branch(item:ClassOrganization):{key:string;title:string;selectable:boolean;children:ReturnType<typeof branch>[]} {
    const children = ordered([...contextCampuses,...contextGrades,...classes].filter(child=>child.parentId===item.id));
    return {key:item.id,title:item.typeCode==='CLASS'&&contextSchools.some(s=>s.id===item.parentId)?`${item.name}（待归属年级）`:item.name,selectable:!contextOnlyIds.has(item.id),children:children.map(branch)};
  }
  return showTree ? <div className="management-split organization-split"><section className="content-panel management-tree-pane"><Tree key={[...contextSchools,...contextCampuses,...contextGrades,...classes].map(item=>item.id).join('|')} defaultExpandAll selectedKeys={node?[node.id]:[]} onSelect={keys=>keys.length&&select(String(keys[0]))} treeData={ordered(contextSchools).map(branch)}/></section><section className="content-panel management-detail-pane">{body}</section></div> : body;
}

