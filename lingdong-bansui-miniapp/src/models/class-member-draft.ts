import type {OrganizationStudentRelationship} from '@/api/organization-students';
export function unassignedClassCandidates(rows:OrganizationStudentRelationship[],schoolId:string){
 return rows.filter(row=>row.enrollmentOrganizationId===schoolId&&row.currentClassOrganizationId===null&&Array.isArray(row.currentClassOrganizationIds)&&row.currentClassOrganizationIds.length===0).sort((a,b)=>a.studentName.localeCompare(b.studentName,'zh-Hans-CN',{numeric:true}));
}
export function classMemberName(value:string){const name=value.trim();if(!name||name.length>64)throw new Error('请填写64字以内的姓名');return name;}
