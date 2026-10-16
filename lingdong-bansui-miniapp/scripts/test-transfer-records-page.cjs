const fs=require('fs'),vm=require('vm'),ts=require('typescript'),assert=require('node:assert/strict');
const script=fs.readFileSync('src/pages/transfer-records/transfer-records.vue','utf8').match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1],flush=()=>new Promise(r=>setImmediate(r));
function setup(){
 let selection={accessToken:'fixture-token',organizationId:'21',typeCode:'SCHOOL'};const hooks={},calls=[],modals=[];const row={id:'1',studentId:'11',applicantUserId:'31',status:'PENDING',versionNo:2,attachmentIds:[],display:{studentName:'fixture child',institutionName:'fixture school',proofs:[]}};
 const me={userId:'32',permissionCodes:['STUDENT_TRANSFER_VIEW','STUDENT_TRANSFER_REVIEW']};
 const api={list:async()=>({items:[row],total:1,page:1,pageSize:20}),read:async()=>row,action:async(...args)=>{calls.push(args);throw new Error('fixture conflict');}};
 const imports={vue:{ref:value=>({value}),computed:fn=>({get value(){return fn();}})},'@dcloudio/uni-app':{onShow:fn=>hooks.show=fn,onHide:fn=>hooks.hide=fn,onUnload:fn=>hooks.unload=fn},'@/session/parent-session':{getParentSession:()=>({accessToken:'fixture-token'})},'@/session/organization-session':{getOrganizationSession:()=>({accessToken:'fixture-token'})},'@/api/parent-children':{parentChildrenApi:{me:async()=>me}},'@/api/transfer-application':{transferApplicationApi:api,downloadTransferProof:async()=>''}};
 const out={};vm.runInNewContext(ts.transpileModule(script+'\nexports.test={load,open,act,rows,detail,reviewReason,error,identity};',{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2020}}).outputText,{exports:out,require:name=>imports[name]||{},Error,uni:{getStorageSync:()=>selection,showModal:()=>new Promise(r=>modals.push(r))}});
 return {page:out.test,hooks,api,calls,modals,row,me,setSelection:s=>selection=s};
}
(async()=>{
 const review=setup();review.hooks.show();await flush();await review.page.open(review.row);review.page.reviewReason.value='fixture reason';const first=review.page.act('reject');await review.page.act('reject');assert.equal(review.modals.length,1);review.modals[0]({confirm:false});await first;assert.equal(review.calls.length,0);
 const rejected=review.page.act('reject');review.modals[1]({confirm:true});await rejected;assert.equal(review.calls.length,1);assert.equal(review.calls[0][4],2);assert.equal(review.page.reviewReason.value,'fixture reason');assert.equal(review.page.detail.value.id,'1');assert.match(review.page.error.value,/fixture conflict/);
 const stale=setup();stale.hooks.show();await flush();await stale.page.open(stale.row);const pending=stale.page.act('approve');stale.setSelection({accessToken:'fixture-token',organizationId:'99',typeCode:'SCHOOL'});stale.modals[0]({confirm:true});await pending;assert.equal(stale.calls.length,0);
 const self=setup();self.me.userId='31';self.hooks.show();await flush();await self.page.open(self.row);await self.page.act('approve');assert.equal(self.modals.length,0);
 const oldError=setup();let rejectList;oldError.api.list=()=>new Promise((resolve,reject)=>rejectList=reject);oldError.hooks.show();await flush();oldError.setSelection({accessToken:'fixture-token',organizationId:'99',typeCode:'SCHOOL'});rejectList(new Error('private fixture error'));await flush();assert.equal(oldError.page.error.value,'');
 console.log('PASS actual transfer records modal lock, cancel, scope change, self-review, conflict retention');
})().catch(e=>{console.error(e);process.exitCode=1;});
