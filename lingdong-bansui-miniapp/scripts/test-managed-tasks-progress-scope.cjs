const fs=require('fs'),vm=require('vm'),ts=require('typescript'),assert=require('node:assert/strict');
const script=fs.readFileSync('src/pages/managed-tasks/managed-tasks.vue','utf8').match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1],flush=()=>new Promise(r=>setImmediate(r));
const task=(id)=>({id,sourceType:'ORGANIZATION',sourceOrganizationId:'o1',title:'中文机构任务'+id,difficultyLevel:2,basePoints:5,durationMinutes:30,scheduledDate:'2026-10-22',status:'PUBLISHED'});
const range=(prefix,from,to)=>Array.from({length:to-from+1},(_,i)=>task(prefix+(from+i)));
const toasts=[];
function setup(){
 const hooks={};
 let token='fixture-token',storage={accessToken:'fixture-token',organizationId:'21'};
 const api={listManagedTasks:async(t,p,st)=>p===1?{items:range('t',1,20),page:1,pageSize:20,total:60}:{items:[],page:p,pageSize:20,total:60},listManagedTaskReviews:async()=>({items:[],page:1,pageSize:20,total:0}),listManagedOrganizations:async()=>[{id:'o1',name:'测试机构'}],getManagedTask:async()=>task('x'),createManagedTask:async()=>task('x'),updateManagedTask:async()=>task('x'),publishManagedTask:async()=>{},approveManagedTaskReview:async()=>{},rejectManagedTaskReview:async()=>{},listManagedTaskProgress:async()=>({items:[]}),listManagedStudents:async()=>[]};
 const imports={vue:{ref:v=>({value:v}),reactive:x=>x,computed:fn=>({get value(){return fn();}})},'@dcloudio/uni-app':{onLoad:fn=>hooks.load=fn,onShow:fn=>hooks.show=fn,onHide:fn=>hooks.hide=fn,onUnload:fn=>hooks.unload=fn,onPullDownRefresh:fn=>hooks.pull=fn,onReachBottom:fn=>hooks.bottom=fn},'@/api/capability':{getMiniappCapabilities:async()=>({taskWeekdayRecurrenceEnabled:false})},'@/models/family-task-draft':{recurrenceFields:()=>[],recurrenceError:()=>null},'@/model/managed-task-targets':{managedTargets:()=>[]},'@/api/http':{ApiError:class ApiError extends Error{constructor(code){super('http');this.statusCode=code;}},apiUrl:u=>u},'@/api/managed-review-access':{requireManagedAccess:async()=>({user:{permissionCodes:['LEARNING_TASK_READ_MANAGED','LEARNING_TASK_PROGRESS_READ']},context:{displayName:'测试学校',userId:'u1'}})},'@/api/managed-learning-task':api,'@/api/teacher-workbench':{},'@/session/organization-session':{getOrganizationSession:()=>({accessToken:token})}};
 const out={};vm.runInNewContext(ts.transpileModule(script+'\nexports.test={initialize,openProgress,closeProgress,progressItems,progressLoading,progressTask,tasks};',{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2020}}).outputText,{exports:out,require:name=>imports[name]||{},Error,uni:{getStorageSync:()=>storage,showToast:(t)=>toasts.push(t),showModal:()=>new Promise(()=>{}),downloadFile:()=>{},previewImage:()=>{},reLaunch:()=>{}}});
 return {page:out.test,hooks,api,setToken:v=>token=v,setStorage:v=>storage=v};
}
(async()=>{
const s=setup();s.hooks.load({});s.hooks.show();await flush();await flush();
// 1) 正常成功路径：打开进度，响应回填且 loading 结束
s.api.listManagedTaskProgress=async()=>({items:[{studentName:'正常孩子',assignmentId:'a1',currentStatus:'IN_PROGRESS'}]});
await s.page.openProgress(task('ok'));assert.equal(s.page.progressItems.value.length,1);assert.equal(s.page.progressLoading.value,false);
s.page.closeProgress();
// 2) A 慢 B 快：打开A挂起，再打开B立即返回，A晚到不得覆盖B
let releaseA;const gateA=new Promise(r=>releaseA=r);
s.api.listManagedTaskProgress=()=>gateA;
const pendingA=s.page.openProgress(task('慢任务'));
s.api.listManagedTaskProgress=async()=>({items:[{studentName:'新任务孩子',assignmentId:'b1',currentStatus:'PENDING_CLAIM'}]});
await s.page.openProgress(task('快任务'));
assert.equal(s.page.progressItems.value.length,1);assert.equal(s.page.progressItems.value[0].studentName,'新任务孩子');
releaseA({items:[{studentName:'旧任务孩子',assignmentId:'a2',currentStatus:'IN_PROGRESS'}]});
await pendingA;await flush();
assert.equal(s.page.progressItems.value.length,1,'A 慢 B 快时旧响应不得覆盖新面板');
assert.equal(s.page.progressItems.value[0].studentName,'新任务孩子');
// 3) 关闭面板后旧响应不得回填
s.page.closeProgress();
let releaseC;const gateC=new Promise(r=>releaseC=r);
s.api.listManagedTaskProgress=()=>gateC;
const pendingC=s.page.openProgress(task('关闭中的任务'));
s.page.closeProgress();
releaseC({items:[{studentName:'关闭后回填'}]});
await pendingC;await flush();
assert.equal(s.page.progressItems.value.length,0,'关闭面板后旧响应不得回填');assert.equal(s.page.progressTask.value,null);
// 4) hide 后旧响应不得回填
let releaseH;const gateH=new Promise(r=>releaseH=r);
s.api.listManagedTaskProgress=()=>gateH;
const pendingH=s.page.openProgress(task('隐藏中的任务'));
s.hooks.hide();
releaseH({items:[{studentName:'旧组织孩子'}]});
await pendingH;await flush();
assert.equal(s.page.progressItems.value.length,0,'页面隐藏后旧任务进度不得回填');
// 5) 换 token/组织后旧响应不得回填
s.setToken('token-2');s.setStorage({accessToken:'token-2',organizationId:'22'});s.hooks.show();await flush();await flush();
let releaseT;const gateT=new Promise(r=>releaseT=r);
s.api.listManagedTaskProgress=()=>gateT;
const pendingT=s.page.openProgress(task('换组织的任务'));
s.setStorage({accessToken:'token-3',organizationId:'23'});
releaseT({items:[{studentName:'旧组织回填'}]});
await pendingT;await flush();
assert.equal(s.page.progressItems.value.length,0,'换组织后旧进度不得回填');
// 6) 旧失败不弹错误提示、不清新面板状态：A 挂起→关闭→打开B→A 拒绝，B 正常提交且无 toast
s.hooks.hide();s.setToken('token-4');s.setStorage({accessToken:'token-4',organizationId:'24'});s.hooks.show();await flush();await flush();
let rejectFail;const gateFail=new Promise((r,j)=>{rejectFail=j});
s.api.listManagedTaskProgress=()=>gateFail;
const pendingFail=s.page.openProgress(task('会失败的任务'));
s.page.closeProgress();
s.api.listManagedTaskProgress=async()=>({items:[{studentName:'新面板孩子',assignmentId:'n1',currentStatus:'IN_PROGRESS'}]});
await s.page.openProgress(task('新面板任务'));
assert.equal(s.page.progressItems.value.length,1);assert.equal(s.page.progressItems.value[0].studentName,'新面板孩子');
const beforeToasts=toasts.length;
rejectFail(new Error('网络失败'));
await pendingFail;await flush();
assert.equal(s.page.progressItems.value.length,1,'旧失败不得清空新面板');
assert.equal(s.page.progressItems.value[0].studentName,'新面板孩子');
assert.equal(toasts.length,beforeToasts,'旧失败不得弹出错误提示');
assert.equal(s.page.progressLoading.value,false,'新面板 loading 必须正常结束');
console.log('通过：managed-tasks 进度面板正常回填、A慢B快隔离、关闭/hide/换组织失效、旧失败不覆盖新面板');
console.log('完成哨兵：managed-tasks-progress-scope 全部断言已执行');
})().catch(e=>{console.error(e);process.exitCode=1;});
