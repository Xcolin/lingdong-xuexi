import { beforeEach, describe, expect, it, vi } from 'vitest';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { StrictMode } from 'react';
import userEvent from '@testing-library/user-event';
import { SchoolHierarchyPanel } from './SchoolHierarchyPanel';
import { CreateUserDrawer } from '../users/CreateUserDrawer';
import { usersApi } from '../../api/users';
import { notifyStudentProfileChanged } from '../users/studentProfileChanged';
vi.mock('../../api/users',()=>({usersApi:{create:vi.fn()}}));
vi.mock('../../api/organization',()=>({organizationApi:{listTree:vi.fn().mockResolvedValue([])}}));
const membership = vi.hoisted(()=>({listClassMembers:vi.fn(),listTeachers:vi.fn()}));
vi.mock('./classAssignmentApi',()=>({classAssignmentApi:membership}));
const api = vi.hoisted(()=>({listCampuses:vi.fn().mockResolvedValue([]),createCampus:vi.fn(),listSchools:vi.fn(),listGrades:vi.fn(),listClasses:vi.fn(),createGrade:vi.fn(),createClass:vi.fn()}));
vi.mock('../../api/classes',()=>({classApi:api}));
vi.mock('./StudentClassAssignmentDrawer',()=>({StudentClassAssignmentDrawer:({open,onSaved}:{open:boolean;onSaved:()=>void})=>open?<button onClick={onSaved}>保存学生关系</button>:null}));
vi.mock('./TeacherClassAssignmentDrawer',()=>({TeacherClassAssignmentDrawer:()=>null}));
const school={id:'s',parentId:null,name:'测试学校',code:'S',typeCode:'SCHOOL',status:'ENABLED',effectiveStatus:'ENABLED',versionNo:1,sortOrder:1};
const grade={...school,id:'g',parentId:'s',typeCode:'GRADE',name:'一年级',schoolOrganizationId:'s'};
describe('学校年级班级上下文',()=>{
 it('严格模式下创建学生成功仅刷新一次班级成员，卸载后清理监听',async()=>{
  const klass={...grade,id:'c',typeCode:'CLASS',name:'一班',children:[]};
  membership.listClassMembers.mockResolvedValueOnce([{id:'old',studentName:'原学生'}]).mockResolvedValue([{id:'new',studentName:'新创建学生'}]);membership.listTeachers.mockResolvedValue([]);
  vi.mocked(usersApi.create).mockResolvedValue({id:'new-user',username:'student_user',studentId:'new-student'} as any);
  const view=render(<StrictMode><SchoolHierarchyPanel selectedNode={klass as any} permissionCodes={['STUDENT_READ']}/><CreateUserDrawer open organization={klass as any} onClose={()=>{}} onSaved={()=>{}}/></StrictMode>);
  await screen.findByText('原学生');await userEvent.click(screen.getByLabelText('用户类型'));await userEvent.click(screen.getByText('学生账号'));fireEvent.change(screen.getByLabelText('用户账号'),{target:{value:'student_user'}});fireEvent.change(screen.getByLabelText('用户名称'),{target:{value:'新创建学生'}});fireEvent.click(screen.getByRole('button',{name:'创建用户'}));await screen.findByText('新创建学生');expect(membership.listClassMembers).toHaveBeenCalledTimes(2);
  view.unmount();act(()=>notifyStudentProfileChanged());expect(membership.listClassMembers).toHaveBeenCalledTimes(2);
 });
 it('创建通知不触发无学生读取权限的班级成员接口',async()=>{
  const klass={...grade,id:'c',typeCode:'CLASS',name:'一班'};render(<SchoolHierarchyPanel selectedNode={klass as any} permissionCodes={['CLASS_READ']}/>);await screen.findByRole('heading',{name:'一班'});act(()=>notifyStudentProfileChanged());expect(membership.listClassMembers).not.toHaveBeenCalled();
 });
 it('保存入班关系后只刷新一次，并显示更新后的学生',async()=>{
  membership.listClassMembers.mockResolvedValueOnce([{id:'old',studentName:'原学生'}]).mockResolvedValue([{id:'new',studentName:'新学生'}]);
  membership.listTeachers.mockResolvedValue([]);
  const klass={...grade,id:'c',typeCode:'CLASS',name:'一班',children:[]};
  render(<SchoolHierarchyPanel selectedNode={klass as any} permissionCodes={['STUDENT_READ','STUDENT_CLASS_ASSIGN']} studentOrganizationRelationshipEnabled/>);
  await screen.findByText('原学生');
  await userEvent.click(screen.getByRole('button',{name:'添加学生'}));
  await userEvent.click(screen.getByRole('button',{name:'保存学生关系'}));
  await screen.findByText('新学生');
  expect(membership.listClassMembers).toHaveBeenCalledTimes(2);
 });
 it('严格模式下班级成员只加载一次，组织列表加载不会重复刷新成员',async()=>{
  let resolveClasses!:(value:any[])=>void;
  api.listClasses.mockReturnValue(new Promise<any[]>(resolve=>{resolveClasses=resolve;}));
  membership.listClassMembers.mockResolvedValue([{id:'student',studentName:'班级学生'}]);
  membership.listTeachers.mockResolvedValue([]);
  const klass={...grade,id:'c',typeCode:'CLASS',name:'一班',children:[]};
  render(<StrictMode><SchoolHierarchyPanel selectedNode={klass as any} permissionCodes={['STUDENT_READ','TEACHER_CLASS_ASSIGN']}/></StrictMode>);
  await screen.findByText('班级学生');
  await act(async()=>resolveClasses([klass]));
  expect(membership.listClassMembers).toHaveBeenCalledTimes(1);
  expect(membership.listTeachers).toHaveBeenCalledTimes(1);
 });
 it('仅CLASS_READ用户选中班级不请求学生或教师权限接口',async()=>{
  const klass={...grade,id:'c',parentId:'g',typeCode:'CLASS',name:'一班'};
  api.listClasses.mockResolvedValue([klass]);
  render(<SchoolHierarchyPanel showTree permissionCodes={['CLASS_READ']}/>);
  await userEvent.click(await screen.findByText('一班'));
  expect(membership.listClassMembers).not.toHaveBeenCalled();
  expect(membership.listTeachers).not.toHaveBeenCalled();
 });
 it('年级局部范围用学校元数据合成不可选祖先，默认仍选实际年级',async()=>{
  api.listSchools.mockResolvedValue([]);api.listGrades.mockResolvedValue([{...grade,schoolName:'范围学校'}]);
  render(<SchoolHierarchyPanel showTree permissionCodes={['CLASS_READ']}/>);
  expect(await screen.findByRole('heading',{name:'一年级'})).toBeInTheDocument();
  expect(screen.getByText('范围学校')).toBeInTheDocument();
  expect(screen.queryByRole('button',{name:'新增年级'})).not.toBeInTheDocument();
 });
 it('班级局部范围仍可显示和选择班级而不会默认选到合成祖先',async()=>{
  api.listSchools.mockResolvedValue([]);api.listGrades.mockResolvedValue([]);
  api.listClasses.mockResolvedValue([{...grade,id:'only-class',parentId:'g',typeCode:'CLASS',name:'局部班级',schoolName:'范围学校',gradeOrganizationId:'g',gradeName:'一年级'}]);
  render(<SchoolHierarchyPanel showTree permissionCodes={['CLASS_READ']}/>);
  expect(await screen.findByRole('heading',{name:'局部班级'})).toBeInTheDocument();
  expect(screen.getByText('范围学校')).toBeInTheDocument();
  expect(screen.queryByRole('button',{name:'新增年级'})).not.toBeInTheDocument();
  expect(screen.queryByRole('button',{name:'新增班级'})).not.toBeInTheDocument();
 });
 it('快速切换班级后旧成员请求不会覆盖新班级',async()=>{
  let resolveA!:(value:any[])=>void;let resolveB!:(value:any[])=>void;
  const a=new Promise<any[]>(resolve=>{resolveA=resolve;});const b=new Promise<any[]>(resolve=>{resolveB=resolve;});
  membership.listClassMembers.mockImplementation((id:string)=>id==='a'?a:b);
  membership.listTeachers.mockImplementation((id:string)=>(id==='a'?a:b).then(()=>[{userId:id,displayName:`教师${id}`,classIds:[id]}]));
  const classA={...grade,id:'a',typeCode:'CLASS',name:'A班',children:[]};const classB={...classA,id:'b',name:'B班'};
  const props={permissionCodes:['STUDENT_READ','TEACHER_CLASS_ASSIGN']};
  const {rerender}=render(<SchoolHierarchyPanel {...props} selectedNode={classA as any}/>);
  await waitFor(()=>expect(membership.listClassMembers).toHaveBeenCalledWith('a'));
  rerender(<SchoolHierarchyPanel {...props} selectedNode={classB as any}/>);
  await waitFor(()=>expect(membership.listClassMembers).toHaveBeenCalledWith('b'));
  resolveB([{id:'sb',studentName:'B班学生'}]);
  expect(await screen.findByText('B班学生')).toBeInTheDocument();
  resolveA([{id:'sa',studentName:'A班学生'}]);
  await waitFor(()=>expect(screen.queryByText('A班学生')).not.toBeInTheDocument());
  expect(screen.getByRole('heading',{name:'B班'})).toBeInTheDocument();
  await userEvent.click(screen.getByRole('tab',{name:'教师'}));
  expect(await screen.findByText('教师b')).toBeInTheDocument();
  expect(screen.queryByText('教师a')).not.toBeInTheDocument();
 });
 beforeEach(()=>{vi.clearAllMocks();api.listCampuses.mockResolvedValue([]);api.listSchools.mockResolvedValue([school]);api.listGrades.mockResolvedValue([grade]);api.listClasses.mockResolvedValue([]);api.createGrade.mockResolvedValue(grade);});
 it('仅班级接口即可组成学校树，学校新增年级自动固定父级',async()=>{
  render(<SchoolHierarchyPanel showTree/>);
  await screen.findByRole('button',{name:'新增年级'});
  await userEvent.click(screen.getByRole('button',{name:'新增年级'}));
  await userEvent.type(screen.getByLabelText('名称'),'二年级');
  await userEvent.click(screen.getByRole('button',{name:'保存'}));
  await waitFor(()=>expect(api.createGrade).toHaveBeenCalledWith({name:'二年级',sortOrder:100,schoolOrganizationId:'s'}));
 });
 it('选择年级后新增班级自动固定年级和学校',async()=>{
  render(<SchoolHierarchyPanel showTree/>);
  await userEvent.click(await screen.findByRole('button',{name:'一年级'}));
  await userEvent.click(await screen.findByRole('button',{name:'新增班级'}));
  await userEvent.type(screen.getByLabelText('名称'),'一班');
  await userEvent.click(screen.getByRole('button',{name:'保存'}));
  await waitFor(()=>expect(api.createClass).toHaveBeenCalledWith({name:'一班',sortOrder:100,gradeOrganizationId:'g',schoolOrganizationId:'s'}));
  await waitFor(()=>expect(api.listGrades).toHaveBeenCalledTimes(2));
  expect(screen.getByRole('heading',{name:'一年级'})).toBeInTheDocument();
 });
});

it('学校同时显示直属年级和校区，校区新增年级固定父级',async()=>{
 const campus={...school,id:'campus',parentId:'s',typeCode:'CAMPUS',name:'东校区',schoolOrganizationId:'s'};
 api.listCampuses.mockResolvedValue([campus]);api.listSchools.mockResolvedValue([school]);api.listGrades.mockResolvedValue([grade]);api.listClasses.mockResolvedValue([]);
 render(<SchoolHierarchyPanel showTree/>);await screen.findByRole('button',{name:'新增校区'});expect(screen.getAllByText('一年级').length).toBeGreaterThan(0);
 await userEvent.click(screen.getByRole('button',{name:'东校区'}));await userEvent.click(screen.getByRole('button',{name:'新增年级'}));await userEvent.type(screen.getByLabelText('名称'),'三年级');await userEvent.click(screen.getByRole('button',{name:'保存'}));await waitFor(()=>expect(api.createGrade).toHaveBeenCalledWith({name:'三年级',sortOrder:100,schoolOrganizationId:'s',campusOrganizationId:'campus'}));
});

it('校区年级局部范围显示不可选校区学校祖先且保留实际年级',async()=>{
 api.listCampuses.mockResolvedValue([]);api.listSchools.mockResolvedValue([]);api.listGrades.mockResolvedValue([{...grade,parentId:'campus',campusOrganizationId:'campus',campusName:'范围校区',schoolName:'范围学校'}]);api.listClasses.mockResolvedValue([]);
 render(<SchoolHierarchyPanel showTree/>);await screen.findByRole('heading',{name:'一年级'});expect(screen.getByText('范围校区')).toBeInTheDocument();expect(screen.getByText('范围学校')).toBeInTheDocument();expect(screen.queryByRole('button',{name:'新增校区'})).not.toBeInTheDocument();
});
it('学校新增校区自动固定学校父级',async()=>{
 api.listCampuses.mockResolvedValue([]);api.listSchools.mockResolvedValue([school]);api.listGrades.mockResolvedValue([]);api.listClasses.mockResolvedValue([]);api.createCampus.mockResolvedValue({...school,id:'campus',parentId:'s',typeCode:'CAMPUS'});
 render(<SchoolHierarchyPanel showTree/>);await userEvent.click(await screen.findByRole('button',{name:'新增校区'}));await userEvent.type(screen.getByLabelText('名称'),'北校区');await userEvent.click(screen.getByRole('button',{name:'保存'}));await waitFor(()=>expect(api.createCampus).toHaveBeenCalledWith({name:'北校区',sortOrder:100,schoolOrganizationId:'s'}));
});

it('班级用户新增教师成功后教师列表刷新一次并清理监听',async()=>{
 api.listCampuses.mockResolvedValue([]);
 api.listSchools.mockResolvedValue([school]);api.listGrades.mockResolvedValue([grade]);api.listClasses.mockResolvedValue([{...grade,id:'c',parentId:'g',typeCode:'CLASS',name:'一班'}]);
 membership.listClassMembers.mockResolvedValue([]);membership.listTeachers.mockReset();membership.listTeachers.mockResolvedValueOnce([]).mockResolvedValue([{userId:'new-teacher',displayName:'新教师',classIds:['c']}]);
 const view=render(<StrictMode><SchoolHierarchyPanel selectedNode={{...grade,id:'c',parentId:'g',typeCode:'CLASS',name:'一班',children:[]} as any} permissionCodes={['TEACHER_CLASS_ASSIGN']}/></StrictMode>);
 await waitFor(()=>expect(membership.listTeachers).toHaveBeenCalledTimes(1));const {notifyTeacherProfileChanged}=await import('../users/teacherProfileChanged');act(()=>notifyTeacherProfileChanged());await screen.findByText('新教师');expect(membership.listTeachers).toHaveBeenCalledTimes(2);
 view.unmount();act(()=>notifyTeacherProfileChanged());expect(membership.listTeachers).toHaveBeenCalledTimes(2);
});

