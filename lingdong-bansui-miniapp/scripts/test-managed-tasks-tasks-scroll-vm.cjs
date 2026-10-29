const fs=require('fs'),vm=require('vm'),ts=require('typescript'),assert=require('node:assert/strict');
const script=fs.readFileSync('src/pages/managed-tasks/managed-tasks.vue','utf8').match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1],flush=()=>new Promise(r=>setImmediate(r));
const calls=[];
const task=(id)=>({id,sourceType:'ORGANIZATION',sourceOrganizationId:'o1',title:'中文机构任务'+id,difficultyLevel:2,basePoints:5,durationMinutes:30,scheduledDate:'2026-10-22',status:'DRAFT'});
const range=(prefix,from,to)=>Array.from({length:to-from+1},(_,i)=>task(prefix+(from+i)));
function setup(){
 const hooks={};
 let token='fixture-token',storage={accessToken:'fixture-token',organizationId:'21'};
 const api={listManagedTasks:async(t,p,st)=>{calls.push(['task',p,st]);return p===1?{items:range('t',1,20),page:1,pageSize:20,total:60}:{items:[],page:p,pageSize:20,total:60};},listManagedTaskReviews:async(t,p,ps)=>{calls.push(['review',p]);return {items:[],page:p,pageSize:ps,total:0};},listManagedOrganizations:async()=>[{id:'o1',name:'测试机构'}],getManagedTask:async()=>task('x'),createManagedTask:async()=>task('x'),updateManagedTask:async()=>task('x'),publishManagedTask:async()=>{},approveManagedTaskReview:async()=>{},rejectManagedTaskReview:async()=>{},listManagedTaskProgress:async()=>({items:[]}),listManagedStudents:async()=>[]};
 const imports={vue:{ref:v=>({value:v}),reactive:x=>x,computed:fn=>({get value(){return fn();}})},'@dcloudio/uni-app':{onLoad:fn=>hooks.load=fn,onShow:fn=>hooks.show=fn,onHide:fn=>hooks.hide=fn,onUnload:fn=>hooks.unload=fn,onPullDownRefresh:fn=>hooks.pull=fn,onReachBottom:fn=>hooks.bottom=fn},'@/api/capability':{getMiniappCapabilities:async()=>({taskWeekdayRecurrenceEnabled:false})},'@/models/family-task-draft':{recurrenceFields:()=>[],recurrenceError:()=>null},'@/model/managed-task-targets':{managedTargets:()=>[]},'@/api/http':{ApiError:class ApiError extends Error{constructor(code){super('http');this.statusCode=code;}},apiUrl:u=>u},'@/api/managed-review-access':{requireManagedAccess:async()=>({user:{permissionCodes:['LEARNING_TASK_READ_MANAGED','LEARNING_TASK_CREATE','TASK_ASSIGNMENT_REVIEW']},context:{displayName:'测试学校',userId:'u1'}})},'@/api/managed-learning-task':api,'@/api/teacher-workbench':{},'@/session/organization-session':{getOrganizationSession:()=>({accessToken:token})}};
 const out={};vm.runInNewContext(ts.transpileModule(script+'\nexports.test={initialize,loadMoreTasks,tasks,taskPage,taskTotal,taskHasMore,taskAppendError,taskLoadingMore,reviewMode,editorVisible,progressTask,toggleReviews};',{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2020}}).outputText,{exports:out,require:name=>imports[name]||{},Error,uni:{getStorageSync:()=>storage,showToast:()=>{},showModal:()=>new Promise(()=>{}),downloadFile:()=>{},previewImage:()=>{}}});
 return {page:out.test,hooks,api,setToken:v=>{token=v;},setStorage:v=>storage=v};
}
(async()=>{
const s=setup();s.hooks.load({});s.hooks.show();await flush();await flush();
// 首屏：20/60，可分页
assert.equal(s.page.reviewMode.value,false);
assert.equal(s.page.tasks.value.length,20);
assert.equal(s.page.taskPage.value,1);
assert.equal(s.page.taskTotal.value,60);
assert.equal(s.page.taskHasMore.value,true);
// 触底追加 page2（含重复 t1 与新增 t21..t39 → 去重后 39）
s.api.listManagedTasks=async(t,p,st)=>{calls.push(['task',p,st]);return {items:[task('t1'),...range('t',21,39)],page:p,pageSize:20,total:100};};
await s.page.loadMoreTasks();assert.equal(s.page.tasks.value.length,39);assert.equal(s.page.taskPage.value,2);assert.equal(s.page.taskAppendError.value,'');assert.equal(s.page.taskHasMore.value,true);
// 失败同页：page3 报错，page 保持 2；触底不自动重试
s.api.listManagedTasks=async()=>{calls.push(['fail']);throw Error('网络失败');};
await s.page.loadMoreTasks();assert.equal(s.page.taskPage.value,2);assert.ok(s.page.taskAppendError.value.includes('网络失败'));
s.hooks.bottom();await flush();assert.equal(calls.filter(c=>c[0]==='fail').length,1);
// 编辑器 / 进度面板打开时零追加
s.page.editorVisible.value=true;let blocked=0;s.api.listManagedTasks=async()=>{blocked++;return {items:[],page:3,pageSize:20,total:3};};await s.page.loadMoreTasks();assert.equal(blocked,0);s.page.editorVisible.value=false;
s.page.progressTask.value=task('p1');await s.page.loadMoreTasks();assert.equal(blocked,0);s.page.progressTask.value=null;
// 重试同页成功（t40..t59，20 条新增）
s.api.listManagedTasks=async(t,p,st)=>{calls.push(['task',p,st]);return {items:range('t',40,59),page:p,pageSize:20,total:100};};
await s.page.loadMoreTasks();assert.equal(s.page.taskPage.value,3);assert.equal(s.page.tasks.value.length,59);assert.equal(s.page.taskAppendError.value,'');
// 忙时切换任务/审核再返回：旧 page4 响应不回填
s.api.listManagedTasks=async(t,p,ps)=>{calls.push(['pending',p]);if(p===4)return new Promise(r=>setTimeout(()=>r({items:[task('old')],page:4,pageSize:20,total:100}),20));return {items:range('fresh',1,20),page:1,pageSize:20,total:40};};
const pending=s.page.loadMoreTasks();await s.page.toggleReviews();await flush();await s.page.toggleReviews();await flush();await pending;await flush();
assert.equal(s.page.taskPage.value,1);assert.equal(s.page.tasks.value.length,20);assert.ok(!s.page.tasks.value.some(x=>x.id==='old'));assert.ok(s.page.tasks.value.some(x=>x.id==='fresh1'));assert.equal(s.page.taskHasMore.value,true);
// 空页终止：page2 空 → hasMore=false，再触底零请求
s.api.listManagedTasks=async(t,p)=>{calls.push(['task',p]);return {items:[],page:p,pageSize:20,total:40};};
await s.page.loadMoreTasks();assert.equal(s.page.taskHasMore.value,false);let extra=0;s.api.listManagedTasks=async()=>{extra++;return {items:[],page:3,pageSize:20,total:40};};await s.page.loadMoreTasks();assert.equal(extra,0);
// hide 清理 + 受控 deferred 释放后旧响应不得恢复（先恢复可分页状态确保请求真正挂起）
s.page.taskHasMore.value=true;let releaseHang;const hangGate=new Promise(r=>releaseHang=r);s.api.listManagedTasks=async()=>hangGate;const hang=s.page.loadMoreTasks();s.hooks.hide();assert.equal(s.page.tasks.value.length,0);assert.equal(s.page.taskHasMore.value,false);assert.equal(s.page.taskAppendError.value,'');releaseHang({items:[task('late')],page:2,pageSize:20,total:100});await hang;await flush();assert.ok(!s.page.tasks.value.some(x=>x.id==='late'),'hide 后旧响应不得恢复');
// 组织切换（scope 变化）后触底零请求
s.api.listManagedTasks=async(t,p,st)=>{calls.push(['task',p,st]);return {items:range('t',1,20),page:1,pageSize:20,total:60};};
s.hooks.show();await flush();await flush();assert.equal(s.page.tasks.value.length,20);assert.equal(s.page.taskHasMore.value,true);
let after=0;s.api.listManagedTasks=async()=>{after++;return {items:[],page:2,pageSize:20,total:60};};s.setStorage({accessToken:'token-2',organizationId:'22'});await s.page.loadMoreTasks();assert.equal(after,0);
console.log('通过：managed-tasks 普通任务列表触底追加、去重、失败同页重试、忙时模式切换重置、空页终止、编辑器/进度面板禁触底、组织切换隔离');
console.log('完成哨兵：managed-tasks-tasks-scroll-vm 全部断言已执行');
})().catch(e=>{console.error(e);process.exitCode=1;});
