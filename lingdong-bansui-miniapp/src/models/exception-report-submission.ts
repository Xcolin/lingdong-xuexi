export interface SubmissionInput { classOrganizationId:string; studentId:string; exceptionType:'ATTENDANCE'|'LEARNING_STATUS'|'MENTAL_STATE'; content:string }
export interface SubmissionEntry { owner:string; request:SubmissionInput & {idempotencyKey:string} }
export function stableSubmissionOwner(userId:string,identity:string,organizationId:unknown){
 if(!userId)throw new Error('无法确认报备账号');
 return JSON.stringify([String(userId),identity,String(organizationId??'')]);
}
export function migratePending(entries:SubmissionEntry[],sessionId:string,userId:string):SubmissionEntry[]{
 return entries.map(entry=>{
  let legacy:unknown;try{legacy=JSON.parse(entry.owner);}catch{}
  if(!Array.isArray(legacy))return {owner:'unresolved',request:{...entry.request}};
  const selected=legacy[2];
  const known=selected&&typeof selected==='object'?selected.userId:undefined;
  const owner=known||legacy[0]===sessionId?stableSubmissionOwner(String(known||userId),String(legacy[1]),selected?.organizationId):'unresolved';
  return {owner,request:{...entry.request}};
 });
}
// Pending requests are indexed by session/organization and exact normalized payload.
// Keep uncertain attempts even when another draft is edited or the page is unloaded.
export function prepareSubmission(entries:SubmissionEntry[],owner:string,input:SubmissionInput){
 if(entries.some(e=>e.owner==='unresolved'&&e.request.classOrganizationId===input.classOrganizationId&&e.request.studentId===input.studentId&&e.request.exceptionType===input.exceptionType&&e.request.content===input.content))throw new Error('存在归属待核对的未确认报备，请先核对原报备记录');
 const existing=entries.find(e=>e.owner===owner&&e.request.classOrganizationId===input.classOrganizationId&&e.request.studentId===input.studentId&&e.request.exceptionType===input.exceptionType&&e.request.content===input.content);
 const entry=existing||{owner,request:{...input,idempotencyKey:`mini-${Date.now()}-${Math.random().toString(36).slice(2,14)}`}};
 return {entries:existing?entries:[...entries,entry],request:{...entry.request}};
}
export function confirmSubmission(entries:SubmissionEntry[],key:string){return entries.filter(e=>e.request.idempotencyKey!==key);}
