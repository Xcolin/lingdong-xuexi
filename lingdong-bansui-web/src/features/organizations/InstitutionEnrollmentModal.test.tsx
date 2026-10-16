import {render,screen,fireEvent,waitFor} from '@testing-library/react';
import {beforeEach,it,expect,vi} from 'vitest';
import {InstitutionEnrollmentModal} from './InstitutionEnrollmentModal';
import {institutionEnrollmentApi} from '../../api/institutionEnrollment';
vi.mock('../../api/institutionEnrollment',()=>({institutionEnrollmentApi:{resolve:vi.fn(),apply:vi.fn(),list:vi.fn().mockResolvedValue([])}}));
beforeEach(()=>{vi.clearAllMocks();vi.mocked(institutionEnrollmentApi.list).mockResolvedValue([]);});
it('仅用邀请码定位班级后为已有家庭学生提交请求',async()=>{
 vi.mocked(institutionEnrollmentApi.resolve).mockResolvedValue({institutionOrganizationId:'training',institutionName:'培训中心',classOrganizationId:'class',className:'数学班',expiresAt:'2026-10-16'});
 vi.mocked(institutionEnrollmentApi.apply).mockResolvedValue({id:'request',status:'PENDING'} as any);const close=vi.fn();render(<InstitutionEnrollmentModal user={{id:'student-user',displayName:'学生'} as any} familyOrganizationId="family" onClose={close}/>);
 expect(screen.queryByLabelText('学生学校')).not.toBeInTheDocument();fireEvent.change(screen.getByLabelText('机构班级邀请码'),{target:{value:'invite-code'}});fireEvent.click(screen.getByRole('button',{name:'确认班级'}));await screen.findByText('培训中心');fireEvent.click(screen.getByRole('button',{name:/提交入学申请/}));await waitFor(()=>expect(institutionEnrollmentApi.apply).toHaveBeenCalledWith('student-user','family','invite-code'));expect(close).toHaveBeenCalledOnce();
});
it('修改邀请码后清除旧班级，错误不提交关联',async()=>{
 vi.mocked(institutionEnrollmentApi.resolve).mockRejectedValue(new Error('邀请码已失效'));render(<InstitutionEnrollmentModal user={{id:'u',displayName:'学生'} as any} familyOrganizationId="f" onClose={()=>{}}/>);fireEvent.change(screen.getByLabelText('机构班级邀请码'),{target:{value:'bad'}});fireEvent.click(screen.getByRole('button',{name:'确认班级'}));await screen.findByText('邀请码已失效');expect(screen.getByRole('button',{name:/提交入学申请/})).toBeDisabled();expect(institutionEnrollmentApi.apply).not.toHaveBeenCalled();
});

