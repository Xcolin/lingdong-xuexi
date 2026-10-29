const fs=require('fs'),vm=require('vm'),ts=require('typescript'),assert=require('node:assert/strict');
const script=fs.readFileSync('src/pages/family-tasks/family-tasks.vue','utf8').match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1],flush=()=>new Promise(r=>setImmediate(r));
let studentsData=[{id:'s1',studentName:'小灵',relationshipRole:'PRIMARY_GUARDIAN',currentClassName:'三年二班',schoolName:'测试学校'},{id:'s2',studentName:'小动',relationshipRole:'PRIMARY_GUARDIAN',currentClassName:'四年一班',schoolName:'测试学校'}];
const requests=[];
const taskRow=(id)=>({id,title:'中文任务',taskType:'STUDY',scheduledDate:'2026-10-22',endDate:null,visibility:'FAMILY_ONLY',status:'PUBLISHED',recurrenceEnabled:false,recurrenceFrequency:'DAILY',memberNames:'小灵',assignmentTotal:2,assignmentCompleted:1,assignmentPendingReview:0});
function setup(){
 const hooks={};
 let token='fixture-token';
 const api={me:async()=>({permissionCodes:['LEARNING_TASK_READ_MANAGED']}),listOverview:async(t,page,statusTab,taskType,student)=>{requests.push({page,student});return {items:[taskRow('t'+page+'-'+(student||'all'))],page,total:100,pageSize:20};},students:async()=>studentsData};
 const imports={vue:{ref:value=>({value}),reactive:x=>x,computed:fn=>({get value(){return fn();}})},'@dcloudio/uni-app':{onShow:fn=>hooks.show=fn,onHide:fn=>hooks.hide=fn,onUnload:fn=>hooks.unload=fn,onReachBottom:fn=>hooks.bottom=fn},'@/api/family-task':{familyTaskApi:api},'@/models/family-task-draft':{canManageFamily:()=>true,draftInput:()=>({}),recurrenceError:()=>null,step1Error:()=>null},'@/session/parent-session':{getParentSession:()=>({accessToken:token})},'@/api/capability':{getMiniappCapabilities:async()=>({learningTaskManagementEnabled:true,taskWeekdayRecurrenceEnabled:false})},'@/api/http':{ApiError:class ApiError extends Error{constructor(statusCode){super('http');this.statusCode=statusCode;}}}};
 const out={};vm.runInNewContext(ts.transpileModule(script+'\nexports.test={load,loadMore,switchTab,studentFilterChanged,studentFilterIndex,studentFilterLabels,tasks,page,total,loaded,hasMore};',{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2020}}).outputText,{exports:out,require:name=>imports[name]||{},Error,uni:{showToast:()=>{},showModal:()=>{},navigateTo:()=>{}}});
 return {page:out.test,hooks,api,setToken:v=>token=v};
}
(async()=>{
const s=setup();await s.page.load(1);assert.equal(s.page.studentFilterIndex.value,0);
// 1) 选择孩子1后请求与 UI 一致，且 hide/show 返回页面后仍一致
s.page.studentFilterChanged({detail:{value:'1'}});await flush();await flush();
assert.equal(s.page.studentFilterIndex.value,1);assert.equal(requests.at(-1).student,'s1');
s.hooks.hide();await s.page.load(1);assert.equal(s.page.studentFilterIndex.value,1,'hide/show 后孩子筛选必须保留');assert.equal(requests.at(-1).student,'s1','返回页面后已选孩子筛选必须与请求一致');
// 2) 选项顺序变化后按 ID 恢复下标（index 从1变2，请求仍是 s1）
studentsData=[{id:'s2',studentName:'小动',relationshipRole:'PRIMARY_GUARDIAN',currentClassName:'四年一班',schoolName:'测试学校'},{id:'s1',studentName:'小灵',relationshipRole:'PRIMARY_GUARDIAN',currentClassName:'三年二班',schoolName:'测试学校'}];
await s.page.switchTab('IN_PROGRESS');await flush();await flush();
assert.equal(s.page.studentFilterIndex.value,2,'选项重排后下标应按 ID 恢复到2');assert.equal(requests.at(-1).student,'s1','选项重排后请求必须仍是同一孩子');
// 3) 选中孩子失效：新选项不含 s1，必须重置为全部孩子且请求不带学生ID
studentsData=[{id:'s9',studentName:'新家庭孩子',relationshipRole:'PRIMARY_GUARDIAN',currentClassName:'一年一班',schoolName:'其他学校'}];
await s.page.switchTab('COMPLETED');await flush();await flush();
assert.equal(s.page.studentFilterIndex.value,0,'选中孩子失效必须重置为全部孩子');assert.equal(requests.at(-1).student,null,'失效后请求必须不带学生ID');
// 4) 多家庭切换：全部选项变化同样重置为全部
s.page.studentFilterChanged({detail:{value:'1'}});await flush();await flush();assert.equal(requests.at(-1).student,'s9');
studentsData=[{id:'x1',studentName:'另一家庭小满',relationshipRole:'PRIMARY_GUARDIAN',currentClassName:'二年三班',schoolName:'另一学校'}];
await s.page.switchTab('ALL');await flush();await flush();
assert.equal(s.page.studentFilterIndex.value,0,'多家庭切换必须重置为全部孩子');assert.equal(requests.at(-1).student,null);
// 5) 追加与首屏同一查询快照：选择孩子后触底追加必须带同一 studentId
s.page.studentFilterChanged({detail:{value:'1'}});await flush();await flush();assert.equal(requests.at(-1).student,'x1');
await s.page.loadMore();assert.equal(requests.at(-1).student,'x1','追加页必须与首屏同一孩子快照');assert.equal(requests.at(-1).page,2);
// 6) 换回全部孩子：请求不带 studentId，追加也不带
s.page.studentFilterChanged({detail:{value:'0'}});await flush();await flush();assert.equal(requests.at(-1).student,null);
await s.page.loadMore();assert.equal(requests.at(-1).student,null,'全部孩子追加不得带学生ID');
console.log('通过：family-tasks 孩子筛选 hide/show 一致、选项重排按ID恢复、选中孩子失效重置、多家庭切换重置、追加与首屏同一快照');
console.log('完成哨兵：family-tasks-filter-resume 全部断言已执行');
})().catch(e=>{console.error(e);process.exitCode=1;});
