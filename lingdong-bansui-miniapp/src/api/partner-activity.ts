import { parentReviewApi } from './parent-task-review';
export interface PartnerActivityScope { studentIds: string[]; source: 'FAMILY'|'ORGANIZATION'; organizationId?: string }
export interface PartnerActivity { id:string; assignmentId:string; studentId:string; studentName:string; title:string; occurredAt:string; type:'TASK_SUBMITTED' }
/** Pending submission events only; task definitions never become completion events. Existing API enforces reviewer/student authorization. */
export async function listPartnerActivities(token:string, scope:PartnerActivityScope, permissions:string[], enabled:boolean, limit=5, isCurrent:()=>boolean=()=>true):Promise<PartnerActivity[]> {
  if (!enabled || !permissions.includes('TASK_ASSIGNMENT_REVIEW') || !scope.studentIds.length || (scope.source==='ORGANIZATION'&&!scope.organizationId)) return [];
  const ids=new Set(scope.studentIds), events:PartnerActivity[]=[];
  let page=1, seen=0;
  while(true){
    if(!isCurrent())return [];
    const result=await parentReviewApi.list(token,page);
    if(!isCurrent())return [];
    for(const item of result.items){
      const row=item;
      if(!ids.has(row.studentId)||!row.latestCheckIn||row.sourceType!==scope.source)continue;
      if(scope.source==='ORGANIZATION'&&row.sourceOrganizationId!==scope.organizationId)continue;
      events.push({id:row.latestCheckIn.id,assignmentId:row.assignmentId,studentId:row.studentId,studentName:row.studentName,title:row.title,occurredAt:row.latestCheckIn.submittedAt,type:'TASK_SUBMITTED'});
    }
    seen+=result.items.length;
    if(!result.items.length||seen>=result.total)break;
    page++;
  }
  return events.sort((a,b)=>b.occurredAt.localeCompare(a.occurredAt)||b.id.localeCompare(a.id)).slice(0,Math.max(0,limit));
}
