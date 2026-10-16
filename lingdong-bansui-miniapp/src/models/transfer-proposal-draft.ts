export const transferTargets=['转至其他学校','转至培训机构'];
export interface TransferDraft {targetIndex:number;reason:string;effectiveDate:string;attachmentIds:string[]}
function dateValid(value:string){if(!/^\d{4}-\d{2}-\d{2}$/.test(value))return false;const date=new Date(value+'T00:00:00Z');return Number.isFinite(date.getTime())&&date.toISOString().slice(0,10)===value;}
export function buildTransferSubmission(draft:TransferDraft,today:string){
 if(!Number.isInteger(draft.targetIndex)||!transferTargets[draft.targetIndex])throw new Error('请选择转学类型');
 const reason=draft.reason.trim();if(!reason||Array.from(reason).length>200)throw new Error('请填写200字以内的转学原因');
 if(!dateValid(draft.effectiveDate)||!dateValid(today)||draft.effectiveDate<today)throw new Error('预计日期不能早于今天');
 const ids=draft.attachmentIds;
 if(ids.length>3||new Set(ids).size!==ids.length||ids.some(id=>typeof id!=='string'||! /^[1-9]\d*$/.test(id)||id.length>19||(id.length===19&&id>'9223372036854775807')))throw new Error('材料信息不完整，请重新选择');
 return {targetDescription:transferTargets[draft.targetIndex],reason,effectiveDate:draft.effectiveDate,attachmentIds:[...ids]};
}
