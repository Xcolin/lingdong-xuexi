const fs=require('fs'),vm=require('vm'),ts=require('typescript'),assert=require('node:assert/strict');
const script=fs.readFileSync('src/pages/family-tasks/family-tasks.vue','utf8').match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1],flush=()=>new Promise(r=>setImmediate(r));
function setup(){
 const hooks={},task={id:'1',title:'中文任务',taskType:'STUDY',scheduledDate:'2026-10-22',endDate:null,visibility:'FAMILY_ONLY',status:'PUBLISHED',recurrenceEnabled:false,recurrenceFrequency:'DAILY',memberNames:'小灵',assignmentTotal:2,assignmentCompleted:1,assignmentPendingReview:0};
 let token='fixture-token';const api={me:async()=>({permissionCodes:['LEARNING_TASK_READ_MANAGED']}),listOverview:async(t,page,statusTab)=>{calls.push([page,statusTab]);return {items:[{...task,id:String(page)}],page,total:100,pageSize:20};},students:async()=>[{id:'s1',studentName:'小灵',relationshipRole:'PRIMARY_GUARDIAN',currentClassName:'三年二班',schoolName:'测试学校'}]};
 const imports={vue:{ref:value=>({value}),reactive:x=>x,computed:fn=>({get value(){return fn();}})},'@dcloudio/uni-app':{onShow:fn=>hooks.show=fn,onHide:fn=>hooks.hide=fn,onUnload:fn=>hooks.unload=fn,onReachBottom:fn=>hooks.bottom=fn},'@/api/family-task':{familyTaskApi:api},'@/models/family-task-draft':{canManageFamily:()=>true,draftInput:()=>({}),recurrenceError:()=>null,step1Error:()=>null},'@/session/parent-session':{getParentSession:()=>({accessToken:token})},'@/api/capability':{getMiniappCapabilities:async()=>({learningTaskManagementEnabled:true,taskWeekdayRecurrenceEnabled:false})},'@/api/http':{ApiError:class ApiError extends Error{constructor(statusCode){super('http');this.statusCode=statusCode;}}}};
 const out={};vm.runInNewContext(ts.transpileModule(script+'\nexports.test={load,loadMore,switchTab,tasks,page,total,hasMore,appendError,loadingMore,editing,loaded};',{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2020}}).outputText,{exports:out,require:name=>imports[name]||{},Error,uni:{showToast:()=>{},showModal:()=>{},navigateTo:()=>{}}});
 return {page:out.test,hooks,api,setToken:v=>token=v};
}
const calls=[];
(async()=>{
const s=setup();s.hooks.show();await flush();assert.equal(s.page.tasks.value.length,1);assert.equal(s.page.hasMore.value,true);assert.equal(s.page.page.value,1);
// 触底追加 page2，返回含重复 id=1 与新增 id=3 → 去重后只加 1 条
s.api.listOverview=async(t,p,tab)=>{calls.push([p,tab]);return {items:[{id:'1',title:'中文任务',taskType:'STUDY',scheduledDate:'2026-10-22',endDate:null,visibility:null,status:'PUBLISHED',recurrenceEnabled:false,recurrenceFrequency:'DAILY',memberNames:null,assignmentTotal:0,assignmentCompleted:0,assignmentPendingReview:0},{id:'3',title:'中文任务',taskType:'STUDY',scheduledDate:'2026-10-22',endDate:null,visibility:null,status:'PUBLISHED',recurrenceEnabled:false,recurrenceFrequency:'DAILY',memberNames:null,assignmentTotal:0,assignmentCompleted:0,assignmentPendingReview:0}],page:p,total:100,pageSize:20};};
await s.page.loadMore();assert.equal(s.page.tasks.value.length,2);assert.equal(s.page.page.value,2);assert.equal(s.page.appendError.value,'');
// 失败同页：page3 报错，page 保持 2，触底不自动重试
s.api.listOverview=async()=>{calls.push(['fail']);throw Error('网络失败');};
await s.page.loadMore();assert.equal(s.page.page.value,2);assert.ok(s.page.appendError.value.includes('网络失败'));assert.equal(s.page.loadingMore.value,false);
s.hooks.bottom();await flush();assert.equal(calls.filter(c=>c[0]==='fail').length,1);
// 点击重试同页成功
s.api.listOverview=async(t,p)=>{calls.push([p]);return {items:[{id:'4',title:'中文任务',taskType:'STUDY',scheduledDate:'2026-10-22',endDate:null,visibility:null,status:'PUBLISHED',recurrenceEnabled:false,recurrenceFrequency:'DAILY',memberNames:null,assignmentTotal:0,assignmentCompleted:0,assignmentPendingReview:0}],page:p,total:100,pageSize:20};};
await s.page.loadMore();assert.equal(s.page.page.value,3);assert.equal(s.page.tasks.value.length,3);assert.equal(s.page.appendError.value,'');
// 忙时筛选重置：挂起 loadMore(page4 旧响应)，再 switchTab 触发 load(1) 新响应；旧 page4 响应不回填
s.api.listOverview=async(t,p,tab)=>{calls.push(['pending',p,tab]);if(p===4)return new Promise(r=>setTimeout(()=>r({items:[{id:'old',title:'旧响应',taskType:'STUDY',scheduledDate:'2026-10-22',endDate:null,visibility:null,status:'PUBLISHED',recurrenceEnabled:false,recurrenceFrequency:'DAILY',memberNames:null,assignmentTotal:0,assignmentCompleted:0,assignmentPendingReview:0}],page:4,total:100,pageSize:20}),20));return {items:[{id:'fresh',title:'新响应',taskType:'STUDY',scheduledDate:'2026-10-22',endDate:null,visibility:null,status:'PUBLISHED',recurrenceEnabled:false,recurrenceFrequency:'DAILY',memberNames:null,assignmentTotal:0,assignmentCompleted:0,assignmentPendingReview:0}],page:1,total:2,pageSize:20};};
const pending=s.page.loadMore();await s.page.switchTab('COMPLETED');await flush();await pending;await flush();assert.equal(s.page.page.value,1);assert.ok(!s.page.tasks.value.some(t=>t.id==='old'));assert.ok(s.page.tasks.value.some(t=>t.id==='fresh'));
// 空页终止
s.api.listOverview=async(t,p)=>{calls.push([p]);return {items:[],page:p,total:3,pageSize:20};};await s.page.loadMore();assert.equal(s.page.hasMore.value,false);let extra=0;s.api.listOverview=async()=>{extra++;return {items:[],page:5,total:3,pageSize:20};};await s.page.loadMore();assert.equal(extra,0);
// 编辑态不触底
s.page.editing.value=true;s.hooks.bottom();await flush();assert.equal(extra,0);
// hide 清理 + 受控 deferred 释放后旧响应不得恢复
s.page.editing.value=false;let releaseHang;const hangGate=new Promise(r=>releaseHang=r);s.api.listOverview=async()=>hangGate;const hang=s.page.loadMore();s.hooks.hide();assert.equal(s.page.tasks.value.length,0);assert.equal(s.page.hasMore.value,false);assert.equal(s.page.appendError.value,'');releaseHang({items:[{id:'late',title:'中文任务',taskType:'STUDY',scheduledDate:'2026-10-22',endDate:null,visibility:null,status:'PUBLISHED',recurrenceEnabled:false,recurrenceFrequency:'DAILY',memberNames:null,assignmentTotal:0,assignmentCompleted:0,assignmentPendingReview:0}],page:2,total:10,pageSize:20});await hang;await flush();assert.ok(!s.page.tasks.value.some(t=>t.id==='late'),'hide 后旧响应不得恢复');
// 旧会话令牌变更后触底零请求：先以新 token 重载建立可分页状态，再改 token 触底必须零请求
s.setToken('token-2');s.hooks.show();s.api.listOverview=async(t,p)=>({items:[{id:'reload-'+p,title:'中文任务',taskType:'STUDY',scheduledDate:'2026-10-22',endDate:null,visibility:'FAMILY_ONLY',status:'PUBLISHED',recurrenceEnabled:false,recurrenceFrequency:'DAILY',memberNames:'小灵',assignmentTotal:2,assignmentCompleted:1,assignmentPendingReview:0}],page:p,total:100,pageSize:20});await flush();await flush();assert.equal(s.page.loaded.value,true);assert.equal(s.page.hasMore.value,true);s.setToken('token-3');let after=0;s.api.listOverview=async()=>{after++;return {items:[],page:2,total:100,pageSize:20};};await s.page.loadMore();assert.equal(after,0,'会话令牌变更后触底必须零请求');
console.log('通过：family-tasks 触底追加、去重、失败同页重试、忙时重置、空页终止、编辑禁触底、旧响应隔离、令牌快照隔离');
console.log('完成哨兵：family-tasks-scroll-vm 全部断言已执行');
})().catch(e=>{console.error(e);process.exitCode=1;});
