import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, expect, it, vi } from 'vitest';
import { OrganizationOnboardingPage } from './OrganizationOnboardingPage';
import { organizationOnboardingApi as api } from '../../api/organizationOnboarding';
import type { CurrentUser } from '../../api/auth';
vi.mock('../../api/organizationOnboarding',()=>({organizationOnboardingApi:{myOrganizations:vi.fn(),applications:vi.fn(),regions:vi.fn(),families:vi.fn(),submit:vi.fn(),context:vi.fn(),mobileCode:vi.fn(),verifyMobile:vi.fn()}}));
const currentUser={userId:'1',userType:'FAMILY',roleCodes:[],permissionCodes:['ORGANIZATION_ONBOARDING_READ','ORGANIZATION_ONBOARDING_SUBMIT']} as unknown as CurrentUser;
it('入驻页统一顶栏并保留退出与中文组织身份',async()=>{
 vi.mocked(api.myOrganizations).mockResolvedValue([{id:'f',name:'家庭甲',roleCodes:['PARENT_ADMIN'],owner:true,canEnterFamily:true,canEnterInstitution:false}] as any);
 const logout=vi.fn();render(<OrganizationOnboardingPage currentUser={currentUser} onLogout={logout}/>);
 expect(await screen.findByText('家庭管理员')).toBeInTheDocument();
 fireEvent.click(screen.getByRole('button',{name:'退出登录'}));expect(logout).toHaveBeenCalledOnce();
 expect(screen.getByText('灵动伴随')).toBeInTheDocument();
});
beforeEach(()=>{vi.clearAllMocks();vi.mocked(api.myOrganizations).mockResolvedValue([]);vi.mocked(api.applications).mockResolvedValue([]);vi.mocked(api.regions).mockResolvedValue([]);vi.mocked(api.context).mockResolvedValue({mobile:'13800000000',verifiedMobile:false});});
it('无组织账号可以创建家庭，失败保留输入',async()=>{
 vi.mocked(api.regions).mockResolvedValue([{id:'region',name:'两江新区',typeCode:'REGION'}]);
 vi.mocked(api.families).mockRejectedValue(new Error('网络失败'));
 render(<OrganizationOnboardingPage currentUser={currentUser}/>);
 fireEvent.click(screen.getByRole('button',{name:'创建家庭'}));
 fireEvent.mouseDown(screen.getByLabelText('所属区域'));
 fireEvent.click(await screen.findByText('两江新区（区域）'));
 fireEvent.change(screen.getByLabelText('家庭名称'),{target:{value:'我的家庭'}});
 fireEvent.click(screen.getByRole('button',{name:'提 交'}));
 await waitFor(()=>expect(api.families).toHaveBeenCalledWith(expect.objectContaining({name:'我的家庭',regionId:'region'})));
 expect(await screen.findByText('网络失败')).toBeInTheDocument();
 expect(screen.getByLabelText('家庭名称')).toHaveValue('我的家庭');
});
it('学生不会获得入驻入口',()=>{render(<OrganizationOnboardingPage currentUser={{...currentUser,userType:'STUDENT'}}/>);expect(screen.queryByRole('button',{name:'创建家庭'})).not.toBeInTheDocument();});

it('机构申请要求验证当前账号手机号',async()=>{
 render(<OrganizationOnboardingPage currentUser={currentUser}/>);
 fireEvent.click(screen.getByRole('button',{name:'学校 / 培训机构入驻'}));
 expect(await screen.findByText(/138\*\*\*\*0000/)).toBeInTheDocument();
 expect(screen.getByRole('button',{name:'提 交'})).toBeDisabled();
 expect(screen.getByRole('button',{name:'验证手机号'})).toBeDisabled();
});

it('只读或显式拒绝提交的账号不能打开入驻操作',()=>{render(<OrganizationOnboardingPage currentUser={{...currentUser,permissionCodes:['ORGANIZATION_ONBOARDING_READ']}}/>);expect(screen.getByRole('button',{name:'创建家庭'})).toBeDisabled();expect(screen.getByRole('button',{name:'学校 / 培训机构入驻'})).toBeDisabled();});
