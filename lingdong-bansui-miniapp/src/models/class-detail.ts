export function classDetailRoute(id:string){if(!/^[1-9][0-9]{0,18}$/.test(id))throw Error('班级编号不合法');return '/pages/organization-class-detail/organization-class-detail?classId='+encodeURIComponent(id);}
export function membersOfClass<T extends {currentClassOrganizationId:string|null;currentClassOrganizationIds?:string[]}>(rows:T[],id:string):T[]{return rows.filter(row=>Array.isArray(row.currentClassOrganizationIds)?row.currentClassOrganizationIds.includes(id):row.currentClassOrganizationId===id);}

export function memberStatusLabel(status:unknown):string{return status==='ENABLED'?'正常':status==='DISABLED'?'停用':'';}

export function matchesClassMember(row:{studentName:string;studentNumber?:string|null},keyword:string):boolean{const value=keyword.trim().toLocaleLowerCase();return !value||row.studentName.toLocaleLowerCase().includes(value)||(row.studentNumber||'').toLocaleLowerCase().includes(value);}
