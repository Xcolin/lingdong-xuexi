import {render,screen,fireEvent,waitFor} from '@testing-library/react';
import {it,expect,vi} from 'vitest';
import {CreateUserDrawer} from './CreateUserDrawer';
import {usersApi} from '../../api/users';
import {organizationApi} from '../../api/organization';
import userEvent from '@testing-library/user-event';
vi.mock('../../api/users',()=>({usersApi:{create:vi.fn(),creationOrganizations:vi.fn(async()=>{const flatten=(nodes:any[]):any[]=>nodes.flatMap(n=>[{...n,name:n.namePath??n.name,disabled:n.status==='DISABLED'||n.effectiveStatus==='DISABLED'},...flatten(n.children??[])]);return flatten(await organizationApi.listTree());})}}));
vi.mock('../../api/organization',()=>({organizationApi:{listTree:vi.fn().mockResolvedValue([{id:'org',name:'示例学校',status:'ENABLED',effectiveStatus:'ENABLED',children:[]}])}}));
it('固定组织创建用户原子提交对应组织',async()=>{
 const onSaved=vi.fn();render(<CreateUserDrawer open onClose={()=>{}} onSaved={onSaved} organization={{id:'org',name:'示例学校'}}/>);
 fireEvent.change(screen.getByLabelText('邮箱'),{target:{value:'New@Example.com'}});fireEvent.change(screen.getByLabelText('手机号'),{target:{value:'13800000000'}});fireEvent.change(screen.getByLabelText('用户账号'),{target:{value:'newuser'}});fireEvent.change(screen.getByLabelText('用户名称'),{target:{value:'新用户'}});fireEvent.change(screen.getByLabelText('初始密码'),{target:{value:'Password12'}});fireEvent.change(screen.getByLabelText('确认密码'),{target:{value:'Password12'}});fireEvent.click(screen.getByRole('button',{name:'创建用户'}));await waitFor(()=>expect(usersApi.create).toHaveBeenCalledWith(expect.objectContaining({organizationId:'org',username:'newuser',displayName:'新用户',password:'Password12',email:'new@example.com',mobile:'13800000000'})));expect(vi.mocked(usersApi.create).mock.calls.at(-1)?.[0]).not.toHaveProperty('confirmPassword');await waitFor(()=>expect(onSaved).toHaveBeenCalledOnce());
});

it('教师账号提交机构类型和教师身份白名单',async()=>{
 vi.mocked(usersApi.create).mockClear();render(<CreateUserDrawer open onClose={()=>{}} onSaved={()=>{}} organization={{id:'campus',name:'东校区',typeCode:'CAMPUS'}}/>);
 const actor=userEvent.setup();await actor.click(screen.getByLabelText('用户类型'));await actor.click(screen.getByText('教师账号'));
 fireEvent.change(screen.getByLabelText('用户账号'),{target:{value:'teacher_one'}});fireEvent.change(screen.getByLabelText('用户名称'),{target:{value:'王老师'}});fireEvent.change(screen.getByLabelText('初始密码'),{target:{value:'Password12'}});fireEvent.change(screen.getByLabelText('确认密码'),{target:{value:'Password12'}});fireEvent.click(screen.getByRole('button',{name:'创建用户'}));
 await waitFor(()=>expect(usersApi.create).toHaveBeenCalledWith(expect.objectContaining({organizationId:'campus',type:'ORGANIZATION',defaultRoleCode:'TEACHER'})));
});

it('校区学生不重复选学校且必须填写年级',async()=>{
 vi.mocked(usersApi.create).mockClear();render(<CreateUserDrawer open onClose={()=>{}} onSaved={()=>{}} organization={{id:'campus',name:'东校区',typeCode:'CAMPUS'}}/>);await selectStudent();
 expect(screen.queryByLabelText('学生学校')).not.toBeInTheDocument();fireEvent.click(screen.getByRole('button',{name:'创建用户'}));await screen.findByText('请输入学生年级');expect(usersApi.create).not.toHaveBeenCalled();
});

vi.mock('antd',async()=>{const actual=await vi.importActual<typeof import('antd')>('antd');return {...actual,message:{...actual.message,success:vi.fn(),error:vi.fn()}};});

async function selectStudent(){const actor=userEvent.setup();await actor.click(screen.getByLabelText('用户类型'));await actor.click(screen.getByText('学生账号'));fireEvent.change(screen.getByLabelText('用户账号'),{target:{value:'student_user'}});fireEvent.change(screen.getByLabelText('用户名称'),{target:{value:'学生'}});}
it('学生创建响应的账号和初始登录码持续显示供保存',async()=>{
 vi.mocked(usersApi.create).mockResolvedValueOnce({id:'created-user',username:'student_user',studentId:'created-student',initialLoginCode:'5821'} as any);render(<CreateUserDrawer open onClose={()=>{}} onSaved={()=>{}} organization={{id:'class',name:'一班',typeCode:'CLASS'}}/>);await selectStudent();fireEvent.click(screen.getByRole('button',{name:'创建用户'}));await screen.findByText('学生账号：student_user');expect(screen.getByText('初始登录码：5821')).toBeInTheDocument();const actor=userEvent.setup();await actor.click(screen.getByRole('button',{name:'知道了'}));await waitFor(()=>expect(screen.queryByText('初始登录码：5821')).not.toBeInTheDocument());
});
it.each(['CLASS','GRADE'])('%s创建学生由组织推导学校和年级，无需重复选择',async(typeCode)=>{
 vi.mocked(usersApi.create).mockClear();vi.mocked(organizationApi.listTree).mockResolvedValue([{id:'grade',name:'四年级',typeCode:'GRADE',parentId:'school',children:[]}] as any);render(<CreateUserDrawer open onClose={()=>{}} onSaved={()=>{}} organization={{id:'class',name:'组织',typeCode,parentId:'grade'}}/>);await selectStudent();expect(screen.queryByLabelText('学生学校')).not.toBeInTheDocument();expect(screen.queryByLabelText('学生年级')).not.toBeInTheDocument();fireEvent.click(screen.getByRole('button',{name:'创建用户'}));await waitFor(()=>expect(usersApi.create).toHaveBeenCalledWith(expect.objectContaining({organizationId:'class',type:'STUDENT',username:'student_user'})));expect(vi.mocked(usersApi.create).mock.calls.at(-1)?.[0]).not.toHaveProperty('password');
});
it('学校创建学生必须填写年级',async()=>{
 vi.mocked(usersApi.create).mockClear();render(<CreateUserDrawer open onClose={()=>{}} onSaved={()=>{}} organization={{id:'school',name:'学校',typeCode:'SCHOOL'}}/>);await selectStudent();expect(screen.queryByLabelText('学生学校')).not.toBeInTheDocument();fireEvent.click(screen.getByRole('button',{name:'创建用户'}));await screen.findByText('请输入学生年级');expect(usersApi.create).not.toHaveBeenCalled();fireEvent.change(screen.getByLabelText('学生年级'),{target:{value:'四年级'}});fireEvent.click(screen.getByRole('button',{name:'创建用户'}));await waitFor(()=>expect(usersApi.create).toHaveBeenCalledWith(expect.objectContaining({organizationId:'school',gradeCode:'四年级'})));
});
it('直属学校的历史班级创建学生必须填写并提交年级',async()=>{
 vi.mocked(usersApi.create).mockClear();vi.mocked(organizationApi.listTree).mockResolvedValue([{id:'school',name:'学校',typeCode:'SCHOOL',parentId:null,children:[]}] as any);render(<CreateUserDrawer open onClose={()=>{}} onSaved={()=>{}} organization={{id:'legacy-class',name:'历史班级',typeCode:'CLASS',parentId:'school'}}/>);await selectStudent();expect(screen.queryByLabelText('学生学校')).not.toBeInTheDocument();fireEvent.click(screen.getByRole('button',{name:'创建用户'}));await screen.findByText('请输入学生年级');expect(usersApi.create).not.toHaveBeenCalled();fireEvent.change(screen.getByLabelText('学生年级'),{target:{value:'四年级'}});fireEvent.click(screen.getByRole('button',{name:'创建用户'}));await waitFor(()=>expect(usersApi.create).toHaveBeenCalledWith(expect.objectContaining({organizationId:'legacy-class',gradeCode:'四年级'})));
});
it.each([undefined,'四年级'])('班级范围缺少祖先时年级可选并保留填写值 %s',async(gradeCode)=>{
 vi.mocked(usersApi.create).mockClear();vi.mocked(organizationApi.listTree).mockResolvedValue([]);render(<CreateUserDrawer open onClose={()=>{}} onSaved={()=>{}} organization={{id:'scoped-class',name:'班级',typeCode:'CLASS',parentId:'invisible-grade'}}/>);await selectStudent();expect(screen.queryByLabelText('学生学校')).not.toBeInTheDocument();expect(screen.getByLabelText('学生年级')).toBeInTheDocument();if(gradeCode)fireEvent.change(screen.getByLabelText('学生年级'),{target:{value:gradeCode}});fireEvent.click(screen.getByRole('button',{name:'创建用户'}));await waitFor(()=>expect(usersApi.create).toHaveBeenCalledWith(expect.objectContaining({organizationId:'scoped-class',gradeCode})));
});
it('非教育组织创建学生必须选择学校并填写年级',async()=>{
 vi.mocked(usersApi.create).mockClear();vi.mocked(organizationApi.listTree).mockResolvedValue([{id:'school',name:'学生学校选项',typeCode:'SCHOOL',status:'ENABLED',effectiveStatus:'ENABLED',children:[]}] as any);render(<CreateUserDrawer open onClose={()=>{}} onSaved={()=>{}} organization={{id:'company',name:'机构',typeCode:'COMPANY'}}/>);await selectStudent();fireEvent.click(screen.getByRole('button',{name:'创建用户'}));await screen.findByText('请选择学生学校');await screen.findByText('请输入学生年级');expect(usersApi.create).not.toHaveBeenCalled();const actor=userEvent.setup();await actor.click(screen.getByLabelText('学生学校'));await actor.click(screen.getByText('学生学校选项'));fireEvent.change(screen.getByLabelText('学生年级'),{target:{value:'四年级'}});fireEvent.click(screen.getByRole('button',{name:'创建用户'}));await waitFor(()=>expect(usersApi.create).toHaveBeenCalledWith(expect.objectContaining({organizationId:'company',studentSchoolOrganizationId:'school',gradeCode:'四年级'})));
});

it('新增用户必须选择组织，未选择不发创建请求',async()=>{vi.mocked(usersApi.create).mockClear();vi.mocked(organizationApi.listTree).mockResolvedValue([{id:'org',name:'示例学校',status:'ENABLED',effectiveStatus:'ENABLED',children:[]}] as any);render(<CreateUserDrawer open onClose={()=>{}} onSaved={()=>{}}/>);fireEvent.change(screen.getByLabelText('用户账号'),{target:{value:'missing_org'}});fireEvent.change(screen.getByLabelText('用户名称'),{target:{value:'未选择组织'}});await waitFor(()=>expect(screen.getByRole('button',{name:'创建用户'})).not.toBeDisabled());fireEvent.click(screen.getByRole('button',{name:'创建用户'}));await screen.findByText('请选择所属组织');expect(usersApi.create).not.toHaveBeenCalled();});

it('切换学生账号清除通用密码，切换回来或重新打开不保留密码',async()=>{
 const actor=userEvent.setup();const props={open:true,onClose:()=>{},onSaved:()=>{},organization:{id:'org',name:'示例学校'}};
 const view=render(<CreateUserDrawer {...props}/>);fireEvent.change(screen.getByLabelText('初始密码'),{target:{value:'Password12'}});
 await actor.click(screen.getByLabelText('用户类型'));await actor.click(screen.getByText('学生账号'));
 expect(screen.queryByLabelText('初始密码')).not.toBeInTheDocument();
 await actor.click(screen.getByLabelText('用户类型'));await actor.click(screen.getByText('家长账号'));
 expect((screen.getByLabelText('初始密码') as HTMLInputElement).value).toMatch(/^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d]{8}$/);fireEvent.change(screen.getByLabelText('初始密码'),{target:{value:'Password12'}});
 view.rerender(<CreateUserDrawer {...props} open={false}/>);view.rerender(<CreateUserDrawer {...props}/>);
 expect((screen.getByLabelText('初始密码') as HTMLInputElement).value).toMatch(/^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d]{8}$/);
});

it('新增用户默认生成八位字母数字密码及一致确认值',async()=>{
 render(<CreateUserDrawer open onClose={()=>{}} onSaved={()=>{}} organization={{id:'org',name:'学校'}}/>);
 await waitFor(()=>expect((screen.getByLabelText('初始密码') as HTMLInputElement).value).toMatch(/^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d]{8}$/));
 expect(screen.getByLabelText('确认密码')).toHaveValue((screen.getByLabelText('初始密码') as HTMLInputElement).value);
});

it.each([
 ['FAMILY','家长账号',['平台账号','机构账号','教师账号']],
 ['SCHOOL','机构账号',['平台账号','家长账号']],
 ['REGION','平台账号',['家长账号','机构账号','教师账号','学生账号']]
])('固定%s组织设置适用默认类型并隐藏无效账号',async(typeCode,defaultLabel,excluded)=>{
 render(<CreateUserDrawer open onClose={()=>{}} onSaved={()=>{}} organization={{id:'typed-org',name:'限定组织',typeCode}}/>);
 await waitFor(()=>expect(screen.getByLabelText('用户类型').closest('.ant-select')).toHaveTextContent(defaultLabel));
 await userEvent.click(screen.getByLabelText('用户类型'));
 for(const label of excluded)expect(screen.queryByText(label)).not.toBeInTheDocument();
});

it('家庭学生独立创建无需选择学校或年级',async()=>{
 vi.mocked(usersApi.create).mockClear();vi.mocked(usersApi.create).mockResolvedValue({id:'family-student-user',username:'student_user',studentId:'student-id'} as any);render(<CreateUserDrawer open organization={{id:'family',name:'家庭',typeCode:'FAMILY'}} onClose={()=>{}} onSaved={()=>{}}/>);await selectStudent();expect(screen.queryByLabelText('学生学校')).not.toBeInTheDocument();expect(screen.queryByLabelText('学生年级')).not.toBeInTheDocument();fireEvent.click(screen.getByRole('button',{name:'创建用户'}));await waitFor(()=>expect(usersApi.create).toHaveBeenCalledWith(expect.objectContaining({organizationId:'family',type:'STUDENT',username:'student_user'})));
});
