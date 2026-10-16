import {MenuConfigurationProvider} from '../../app/MenuConfiguration';
import {render,screen,fireEvent,waitFor} from '@testing-library/react';
import {it,expect,vi} from 'vitest';
import {InstitutionEnrollmentReviewDrawer} from './InstitutionEnrollmentReviewDrawer';
import {institutionEnrollmentApi} from '../../api/institutionEnrollment';
vi.mock('../../api/institutionEnrollment',()=>({institutionEnrollmentApi:{list:vi.fn(),approve:vi.fn(),reject:vi.fn()}}));
it('按当前机构班级加载申请并确认后刷新',async()=>{
 vi.mocked(institutionEnrollmentApi.list).mockResolvedValueOnce([{id:'req',studentName:'学生甲',institutionName:'培训',className:'数学班',status:'PENDING'} as any]).mockResolvedValue([]);vi.mocked(institutionEnrollmentApi.approve).mockResolvedValue({id:'req',status:'APPROVED'} as any);const done=vi.fn();render(<MenuConfigurationProvider nodes={[{id:'review',code:'STUDENT_ENROLLMENT_REVIEW',name:'审核学生入学申请',type:'BUTTON',parentId:null,route:null,icon:null,permissionCode:'STUDENT_ENROLLMENT_REVIEW',sortOrder:1,status:'ENABLED',version:'1'}]} refresh={async()=>{}}><InstitutionEnrollmentReviewDrawer open institutionId="training" classId="math" onClose={()=>{}} onApproved={done}/></MenuConfigurationProvider>);await screen.findByText('学生甲');expect(institutionEnrollmentApi.list).toHaveBeenCalledWith({institutionOrganizationId:'training',classOrganizationId:'math',status:'PENDING'});expect(screen.getByRole('button',{name:'驳回'})).toBeInTheDocument();fireEvent.click(screen.getByRole('button',{name:'通过'}));await waitFor(()=>expect(institutionEnrollmentApi.approve).toHaveBeenCalledWith('req'));expect(done).toHaveBeenCalledOnce();
});
