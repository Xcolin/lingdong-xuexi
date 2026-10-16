export function canReadReviewPeriod(identity:{clientType:string;roleCodes:string[];permissionCodes:string[]},featureEnabled:boolean):boolean {
 return featureEnabled&&identity.clientType==='MINIAPP'&&identity.roleCodes.includes('STUDENT')&&!identity.roleCodes.includes('SYS_AUDITOR')&&identity.permissionCodes.includes('GROWTH_REVIEW_READ_SELF');
}
export function matchesReviewPeriod(actual:string,expected:'DAY'|'WEEK'):boolean{return actual===expected;}

export async function applyCurrentReviewRefresh<T>(load:()=>Promise<T>,isCurrent:()=>boolean,apply:(result:T)=>void):Promise<boolean> {
 const result=await load();if(!isCurrent())return false;apply(result);return true;
}
