/** Mirror /auth/me identity; never grant actions from a display role name. */
export interface ParentIdentity {clientType:string;roleCodes:string[];parentProfile?:boolean;userType?:string;}
export function isParentIdentity(user:ParentIdentity):boolean{return user.clientType==='MINIAPP'&&user.userType!=='STUDENT'&&user.roleCodes.length>0&&!user.roleCodes.includes('SYS_AUDITOR')&&(user.parentProfile===true||(user.parentProfile===undefined&&user.roleCodes.includes('PARENT')));}
