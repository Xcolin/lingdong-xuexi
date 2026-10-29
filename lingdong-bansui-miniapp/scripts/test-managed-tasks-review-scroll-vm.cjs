const fs=require('fs'),vm=require('vm'),ts=require('typescript'),assert=require('node:assert/strict');
const script=fs.readFileSync('src/pages/managed-tasks/managed-tasks.vue','utf8').match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1],flush=()=>new Promise(r=>setImmediate(r));
const calls=[];
function setup(){
 const hooks={},review={assignmentId:'a1',title:'中文审核任务',studentName:'小灵',basePoints:5,sourceType:'FAMILY',sourceOrganizationName:'测试家庭',latestCheckIn:{id:'c1',content:'已完成打卡',attachments:[]}};
 let token='fixture-token',storage={accessToken:'fixture-token',organizationId:'21'};
 const api={listManagedTaskReviews:async(t,p,ps)=>{calls.push([p]);return {items:[{...review,assignmentId:'a'+p}],page:p,pageSize:ps,total:100};},listManagedOrganizations:async()=>[{id:'o1',name:'测试机构'}],listManagedTasks:async()=>({items:[]}),getManagedTask:async()=>review,createManagedTask:async()=>review,updateManagedTask:async()=>review,publishManagedTask:async()=>{},approveManagedTaskReview:async()=>{},rejectManagedTaskReview:async()=>{},listManagedTaskProgress:async()=>({items:[]}),listManagedStudents:async()=>[]};
 const imports={vue:{ref:v=>({value:v}),reactive:x=>x,computed:fn=>({get value(){return fn();}})},'@dcloudio/uni-app':{onLoad:fn=>hooks.load=fn,onShow:fn=>hooks.show=fn,onHide:fn=>hooks.hide=fn,onUnload:fn=>hooks.unload=fn,onPullDownRefresh:fn=>hooks.pull=fn,onReachBottom:fn=>hooks.bottom=fn},'@/api/capability':{getMiniappCapabilities:async()=>({taskWeekdayRecurrenceEnabled:false})},'@/models/family-task-draft':{recurrenceFields:()=>[],recurrenceError:()=>null},'@/model/managed-task-targets':{managedTargets:()=>[]},'@/api/http':{ApiError:class ApiError extends Error{constructor(code){super('http');this.statusCode=code;}},apiUrl:u=>u},'@/api/managed-review-access':{requireManagedAccess:async()=>({user:{permissionCodes:['TASK_ASSIGNMENT_REVIEW']},context:{displayName:'测试学校',userId:'u1'}})},'@/api/managed-learning-task':api,'@/api/teacher-workbench':{},'@/session/organization-session':{getOrganizationSession:()=>({accessToken:token})}};
 const out={};vm.runInNewContext(ts.transpileModule(script+'\nexports.test={initialize,loadReviews,loadMoreReviews,reviews,reviewPage,reviewTotal,reviewHasMore,reviewAppendError,reviewLoadingMore,reviewMode,editorVisible,toggleReviews};',{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2020}}).outputText,{exports:out,require:name=>imports[name]||{},Error,uni:{getStorageSync:()=>storage,showToast:()=>{},showModal:()=>new Promise(()=>{}),downloadFile:()=>{},previewImage:()=>{}}});
 return {page:out.test,hooks,api,setToken:v=>{token=v;},setStorage:v=>storage=v};
}
(async()=>{
const s=setup();s.hooks.load({review:'true'});s.hooks.show();await flush();await flush();assert.equal(s.page.reviewMode.value,true);assert.equal(s.page.reviews.value.length,1);assert.equal(s.page.reviewHasMore.value,true);assert.equal(s.page.reviewPage.value,1);
// 触底追加 page2（含重复 a1 与新增 a3 → 去重）
s.api.listManagedTaskReviews=async(t,p,ps)=>{calls.push([p]);return {items:[{...s.page.reviews.value[0]},{...s.page.reviews.value[0],assignmentId:'a3'}],page:p,pageSize:ps,total:100};};
await s.page.loadMoreReviews();assert.equal(s.page.reviews.value.length,2);assert.equal(s.page.reviewPage.value,2);assert.equal(s.page.reviewAppendError.value,'');
// 失败同页：page3 报错，page 保持 2；触底不自动重试
s.api.listManagedTaskReviews=async()=>{calls.push(['fail']);throw Error('网络失败');};
await s.page.loadMoreReviews();assert.equal(s.page.reviewPage.value,2);assert.ok(s.page.reviewAppendError.value.includes('网络失败'));
s.hooks.bottom();await flush();assert.equal(calls.filter(c=>c[0]==='fail').length,1);
// 编辑器打开时零追加
s.page.editorVisible.value=true;let editorBlocked=0;s.api.listManagedTaskReviews=async()=>{editorBlocked++;return {items:[],page:3,pageSize:20,total:3};};await s.page.loadMoreReviews();assert.equal(editorBlocked,0);s.page.editorVisible.value=false;
// 重试同页成功
s.api.listManagedTaskReviews=async(t,p)=>{calls.push([p]);return {items:[{...s.page.reviews.value[0],assignmentId:'a4'}],page:p,pageSize:20,total:100};};
await s.page.loadMoreReviews();assert.equal(s.page.reviewPage.value,3);assert.equal(s.page.reviews.value.length,3);assert.equal(s.page.reviewAppendError.value,'');
// 忙时切换审核/任务再返回：旧 page4 响应不回填
s.api.listManagedTaskReviews=async(t,p,ps)=>{calls.push(['pending',p]);if(p===4)return new Promise(r=>setTimeout(()=>r({items:[{...s.page.reviews.value[0],assignmentId:'old'}],page:4,pageSize:ps,total:100}),20));return {items:[{...s.page.reviews.value[0],assignmentId:'fresh'}],page:1,pageSize:ps,total:2};};
const pending=s.page.loadMoreReviews();await s.page.toggleReviews();await flush();await s.page.toggleReviews();await flush();await pending;await flush();assert.equal(s.page.reviewPage.value,1);assert.ok(!s.page.reviews.value.some(r=>r.assignmentId==='old'));assert.ok(s.page.reviews.value.some(r=>r.assignmentId==='fresh'));
// 空页终止
s.api.listManagedTaskReviews=async(t,p)=>{calls.push([p]);return {items:[],page:p,pageSize:20,total:3};};await s.page.loadMoreReviews();assert.equal(s.page.reviewHasMore.value,false);let extra=0;s.api.listManagedTaskReviews=async()=>{extra++;return {items:[],page:5,pageSize:20,total:3};};await s.page.loadMoreReviews();assert.equal(extra,0);
// hide 清理 + 受控 deferred 释放后旧响应不得恢复（先恢复可分页状态确保请求真正挂起）
s.page.reviewHasMore.value=true;let releaseHang;const hangGate=new Promise(r=>releaseHang=r);s.api.listManagedTaskReviews=async()=>hangGate;const hang=s.page.loadMoreReviews();s.hooks.hide();assert.equal(s.page.reviews.value.length,0);assert.equal(s.page.reviewHasMore.value,false);assert.equal(s.page.reviewAppendError.value,'');releaseHang({items:[{assignmentId:'late',title:'中文审核任务',studentName:'小灵',basePoints:5,sourceType:'FAMILY',sourceOrganizationName:'测试家庭',latestCheckIn:{id:'c1',content:'已完成打卡',attachments:[]}}],page:2,pageSize:20,total:100});await hang;await flush();assert.ok(!s.page.reviews.value.some(r=>r.assignmentId==='late'),'hide 后旧响应不得恢复');s.api.listManagedTaskReviews=async(t,p,ps)=>({items:[],page:p,pageSize:ps,total:0});
// 组织切换（scope 变化）后触底零请求
s.hooks.show();await flush();await flush();let after=0;s.api.listManagedTaskReviews=async()=>{after++;return {items:[],page:1,pageSize:20,total:0};};s.setStorage({accessToken:'token-2',organizationId:'22'});await s.page.loadMoreReviews();assert.equal(after,0);
console.log('通过：managed-tasks 审核列表触底追加、去重、失败同页重试、忙时重置、空页终止、编辑器禁触底、组织切换隔离');
console.log('完成哨兵：managed-tasks-review-scroll-vm 全部断言已执行');
})().catch(e=>{console.error(e);process.exitCode=1;});
