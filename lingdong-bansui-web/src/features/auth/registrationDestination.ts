export function registrationDestination(pages:string[], organizations:Array<{canEnterFamily:boolean;canEnterInstitution:boolean}>, pending=false):string {
 if(pending&&!organizations.some(o=>o.canEnterFamily||o.canEnterInstitution))return '/registration-progress';
 const business=pages.filter(p=>p!=='/my-organizations'&&p!=='/registration-progress');
 if(!organizations.some(o=>o.canEnterFamily||o.canEnterInstitution)&&business.length===0)return '/registration-progress';
 return business.includes('/dashboard')?'/dashboard':business[0]??'/registration-progress';
}
