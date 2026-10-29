const fs=require('fs'),vm=require('vm'),ts=require('typescript'),assert=require('node:assert/strict');
const script=fs.readFileSync('src/pages/parent-task-reviews/parent-task-reviews.vue','utf8').match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1],flush=()=>new Promise(r=>setImmediate(r));
const calls=[];
function setup(){
 let token='fixture-token';
 const item={assignmentId:'a1',title:'中文打卡任务',taskType:'STUDY',studentName:'小灵',currentClassName:'三年二班',scheduledDate:'2026-10-21',latestCheckIn:{id:'c1',status:'SUBMITTED',content:'完成阅读打卡',submittedAt:'2026-10-21T19:00:00',reviewComment:'',attachments:[]}};
 const api={me:async()=>({clientType:'MINIAPP',roleCodes:['CUSTOM_PARENT'],permissionCodes:['TASK_ASSIGNMENT_REVIEW']}),list:async(t,page,statusTab)=>{calls.push(['list',page,statusTab]);return {items:[{...item,assignmentId:'a'+page}],page,pageSize:20,total:100,statusCounts:{SUBMITTED:3}};},detail:async(t,id)=>{calls.push(['detail',id]);return {...item,assignmentId:id};},download:async()=>'/tmp/x',approve:async()=>{},reject:async()=>{}};
 const imports={vue:{ref:value=>({value}),computed:fn=>({get value(){return fn();}})},'@/components/pixel/PixelPageHeader.vue':{},'@/components/PrototypeIcon.vue':{},'@dcloudio/uni-app':{onShow:fn=>hooks.show=fn,onHide:fn=>hooks.hide=fn,onUnload:fn=>hooks.unload=fn,onReachBottom:fn=>hooks.bottom=fn},'@/api/parent-task-review':{parentReviewApi:api},'@/api/capability':{getMiniappCapabilities:async()=>({learningTaskManagementEnabled:true})},'@/session/parent-session':{getParentSession:()=>({accessToken:token})},'@/models/parent-task-reviews':{canReview:(user,enabled)=>enabled&&user.permissionCodes.includes('TASK_ASSIGNMENT_REVIEW'),requestEpoch:()=>{let value=0;return {next:()=>++value,current:n=>value===n,invalidate:()=>{value++;}};}},'@/api/http':{ApiError:class ApiError extends Error{constructor(statusCode){super('http');this.statusCode=statusCode;}}}};
 const hooks={};const out={};
 vm.runInNewContext(ts.transpileModule(script+'\nexports.test={load,loadMore,open,backToList,switchTab,items,detail,page,total,hasMore,appendError,loadingMore,loaded,stage,statusTab};',{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2020}}).outputText,{exports:out,require:name=>imports[name]||{},Error,uni:{showToast:()=>{},showModal:choice=>choice&&choice.success&&choice.success({confirm:true}),navigateBack:()=>{},reLaunch:()=>{},previewImage:()=>{}}});
 return {page:out.test,hooks,api,item,setToken:v=>token=v};
}
(async()=>{
const s=setup();s.hooks.show();await flush();
assert.equal(s.page.items.value.length,1);assert.equal(s.page.hasMore.value,true);assert.equal(s.page.page.value,1);assert.equal(s.page.loaded.value,true);assert.equal(s.page.statusTab.value,'PENDING_REVIEW');
// 触底追加 page2，返回含重复 assignmentId=a1 与新增 a2 → 去重后 2 条
s.api.list=async(t,page,statusTab)=>{calls.push(['list',page,statusTab]);return {items:[{...s.page.items.value[0]},{...s.page.items.value[0],assignmentId:'a2',title:'家务打卡'}],page,pageSize:20,total:100,statusCounts:{SUBMITTED:3}};};
await s.page.loadMore();assert.equal(s.page.items.value.length,2);assert.equal(s.page.page.value,2);assert.equal(s.page.appendError.value,'');
// 失败同页：page3 报错，page 保持 2，触底不自动重试
s.api.list=async()=>{calls.push(['fail']);throw Error('网络失败');};
await s.page.loadMore();assert.equal(s.page.page.value,2);assert.ok(s.page.appendError.value.includes('网络失败'));assert.equal(s.page.loadingMore.value,false);
s.hooks.bottom();await flush();assert.equal(calls.filter(c=>c[0]==='fail').length,1);
// 点击重试同页成功
s.api.list=async(t,page,statusTab)=>{calls.push(['list',page,statusTab]);return {items:[{...s.page.items.value[0],assignmentId:'a'+page}],page,pageSize:20,total:100,statusCounts:{SUBMITTED:3}};};
await s.page.loadMore();assert.equal(s.page.page.value,3);assert.equal(s.page.items.value.length,3);assert.equal(s.page.appendError.value,'');
// 详情态不触底：open 后 bottom 零请求；返回列表恢复
await s.page.open('a1');await flush();assert.ok(s.page.detail.value);const detailCalls=calls.length;s.hooks.bottom();await flush();assert.equal(calls.length,detailCalls);
await s.page.backToList();await flush();assert.equal(s.page.detail.value,null);
// 筛选切换重置：switchTab('APPROVED') → load(1) 且 statusTab 透传
s.api.list=async(t,page,statusTab)=>{calls.push(['list',page,statusTab]);return {items:[{...s.page.items.value[0],assignmentId:'b'+page}],page,pageSize:20,total:1,statusCounts:{}};};
await s.page.switchTab('APPROVED');await flush();assert.equal(s.page.statusTab.value,'APPROVED');assert.equal(s.page.page.value,1);assert.equal(s.page.total.value,1);assert.equal(s.page.hasMore.value,false);assert.ok(calls.some(c=>c[0]==='list'&&c[1]===1&&c[2]==='APPROVED'));
// 先切回 ALL 重建 hasMore，再测挂起旧响应隔离
s.api.list=async(t,page,statusTab)=>{calls.push(['list',page,statusTab]);return {items:[{...s.item,assignmentId:'c'+page}],page,pageSize:20,total:100,statusCounts:{}};};
await s.page.switchTab('ALL');await flush();assert.equal(s.page.hasMore.value,true);assert.equal(s.page.page.value,1);
await s.page.loadMore();assert.equal(s.page.page.value,2);
// 忙时旧响应隔离：挂起 loadMore(page3) 旧响应，switchTab 触发 load(1) 新响应；旧响应不回填
s.api.list=async(t,page,statusTab)=>{calls.push(['pending',page,statusTab]);if(page===3)return new Promise(r=>setTimeout(()=>r({items:[{...s.item,assignmentId:'old'}],page,pageSize:20,total:100,statusCounts:{}}),20));return {items:[{...s.item,assignmentId:'fresh'}],page:1,pageSize:20,total:2,statusCounts:{}};};
const pending=s.page.loadMore();await flush();await s.page.switchTab('PENDING_REVIEW');await flush();await pending;await flush();
assert.equal(s.page.page.value,1);assert.ok(!s.page.items.value.some(row=>row.assignmentId==='old'));assert.ok(s.page.items.value.some(row=>row.assignmentId==='fresh'));
// hide 清理 + invalidate 后旧响应不恢复
s.hooks.hide();assert.equal(s.page.items.value.length,0);assert.equal(s.page.hasMore.value,false);assert.equal(s.page.appendError.value,'');
// 旧会话令牌变更后触底零请求
s.hooks.show();await flush();assert.equal(s.page.loaded.value,true);let after=0;s.api.list=async()=>{after++;return {items:[],page:1,pageSize:20,total:0,statusCounts:{}};};s.setToken('token-3');await s.page.loadMore();assert.equal(after,0);
console.log('通过：parent-task-reviews 触底追加、去重、失败同页重试、详情态禁触底、筛选重置、忙时旧响应隔离、hide 清理、令牌隔离');
})().catch(e=>{console.error(e);process.exitCode=1;});
