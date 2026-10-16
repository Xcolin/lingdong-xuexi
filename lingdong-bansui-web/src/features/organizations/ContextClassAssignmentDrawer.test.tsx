import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { StudentClassAssignmentDrawer } from './StudentClassAssignmentDrawer';
import { TeacherClassAssignmentDrawer } from './TeacherClassAssignmentDrawer';
const api=vi.hoisted(()=>({listClasses:vi.fn(),listStudents:vi.fn(),listTeachers:vi.fn()}));
vi.mock('./classAssignmentApi',()=>({classAssignmentApi:api}));
describe('班级成员添加上下文',()=>{
 beforeEach(()=>{vi.clearAllMocks();api.listStudents.mockResolvedValue([]);api.listTeachers.mockResolvedValue([]);});
 it('学生添加固定真实班级名称与ID，候选请求带当前班级且不请求班级选择列表',async()=>{
  render(<StudentClassAssignmentDrawer open fixedClassId="class-1" fixedClassName="一年级一班" onClose={()=>{}}/>);
  expect(await screen.findByText('一年级一班')).toBeInTheDocument();
  expect(api.listStudents).toHaveBeenCalledWith('class-1');
  expect(api.listClasses).not.toHaveBeenCalled();
 });
 it('教师添加自动加载当前班级候选并显示真实名称',async()=>{
  render(<TeacherClassAssignmentDrawer open fixedClassId="class-1" fixedClassName="一年级一班" onClose={()=>{}}/>);
  expect(await screen.findByText('一年级一班')).toBeInTheDocument();
  await waitFor(()=>expect(api.listTeachers).toHaveBeenCalledWith('class-1'));
  expect(api.listClasses).not.toHaveBeenCalled();
 });
});

it('同名教师按完整校区路径区分，选择仍使用教师ID',async()=>{
 api.listTeachers.mockResolvedValue([{userId:'teacher-east',displayName:'王老师',classIds:[],organizationNames:['示例学校 / 东校区 / 三年级']},{userId:'teacher-west',displayName:'王老师',classIds:[],organizationNames:['示例学校 / 西校区 / 三年级']}]);
 render(<TeacherClassAssignmentDrawer open fixedClassId="class-1" fixedClassName="一班" onClose={()=>{}}/>);await waitFor(()=>expect(api.listTeachers).toHaveBeenCalledWith('class-1'));const actor=userEvent.setup();await actor.click(screen.getByLabelText('教师'));expect(await screen.findByText('王老师 · 示例学校 / 东校区 / 三年级')).toBeInTheDocument();expect(screen.getByText('王老师 · 示例学校 / 西校区 / 三年级')).toBeInTheDocument();
});

it('学校上下文教师班级请求和选项限定本校',async()=>{
 api.listClasses.mockResolvedValue([{id:'class-a',name:'本校一班',schoolOrganizationId:'school-a'},{id:'class-b',name:'外校一班',schoolOrganizationId:'school-b'}]);
 render(<TeacherClassAssignmentDrawer open schoolId="school-a" onClose={()=>{}}/>);await waitFor(()=>expect(api.listClasses).toHaveBeenCalledWith('school-a','TEACHER_CLASS_ASSIGN'));
 const actor=userEvent.setup();await actor.click(screen.getByLabelText('班级'));expect(await screen.findByText('本校一班')).toBeInTheDocument();expect(screen.queryByText('外校一班')).not.toBeInTheDocument();
});
