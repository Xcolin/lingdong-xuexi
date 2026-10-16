import {render,screen,waitFor} from '@testing-library/react';
import {it,expect,vi} from 'vitest';
import {OwnerTransferPanel} from './OwnerTransferPanel';
import {organizationOnboardingApi as api} from '../../api/organizationOnboarding';
vi.mock('../../api/organizationOnboarding',()=>({organizationOnboardingApi:{myOrganizations:vi.fn()}}));
vi.mock('../../api/http',()=>({organizationContextStore:{getSelectedId:()=>undefined}}));
it('非负责人不显示整个转交区域',async()=>{
 vi.mocked(api.myOrganizations).mockResolvedValue([{id:'1',name:'家庭甲',owner:false}] as any);
 render(<OwnerTransferPanel/>);
 await waitFor(()=>expect(api.myOrganizations).toHaveBeenCalled());
 expect(screen.queryByText('负责人转交')).not.toBeInTheDocument();
});
it('负责人显示转交区域',async()=>{
 vi.mocked(api.myOrganizations).mockResolvedValue([{id:'1',name:'家庭甲',owner:true}] as any);
 render(<OwnerTransferPanel/>);
 expect(await screen.findByText('负责人转交')).toBeInTheDocument();
 expect(screen.getByRole('button',{name:'家庭甲：转交负责人'})).toBeInTheDocument();
});
