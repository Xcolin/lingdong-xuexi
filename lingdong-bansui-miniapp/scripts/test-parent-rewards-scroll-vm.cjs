const fs=require('fs'),vm=require('vm'),ts=require('typescript'),assert=require('node:assert/strict');
const script=fs.readFileSync('src/pages/parent-rewards/parent-rewards.vue','utf8').match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1],flush=()=>new Promise(r=>setImmediate(r));
const calls=[];
function setup(){
 let token='fixture-token';
 const reward={id:'1',studentId:'s1',rewardName:'中文奖励',requiredPoints:10,category:'STUDY_SUPPLIES',stockQuantity:1,imageUrl:null,description:'按时完成作业',expiresAt:null,status:'ONLINE'};
 const exchange={id:'e1',studentId:'s1',rewardName:'中文奖励',requiredPoints:10,status:'PENDING_APPROVAL',description:'兑换说明',approvalDeadline:'2026-10-25T12:00:00',rejectReason:null};
 const api={me:async()=>({clientType:'MINIAPP',roleCodes:['CUSTOM_PARENT'],permissionCodes:['MINIAPP_REWARD_MANAGE_CHILD','MINIAPP_REWARD_EXCHANGE_REVIEW_CHILD']}),children:async()=>[{studentId:'s1',studentName:'小灵',relationshipRole:'PRIMARY_GUARDIAN'}],rewards:async(t,sid,page,status)=>{calls.push(['rewards',page,status]);return {items:[{...reward,id:String(page)}],page,total:100,pageSize:20};},exchanges:async(t,sid,page,status)=>{calls.push(['exchanges',page,status]);return {items:[{...exchange,id:String(page)}],page,total:100,pageSize:20};}};
 const imports={vue:{ref:value=>({value}),reactive:x=>x,computed:fn=>({get value(){return fn();}})},'@dcloudio/uni-app':{onShow:fn=>hooks.show=fn,onHide:fn=>hooks.hide=fn,onUnload:fn=>hooks.unload=fn,onReachBottom:fn=>hooks.bottom=fn},'@/components/PrototypeIcon.vue':{},'@/api/parent-reward':{parentRewardApi:api,canUseParentRewards:(user,permission)=>permission?user.permissionCodes.includes(permission):true,MANAGE_REWARDS:'MINIAPP_REWARD_MANAGE_CHILD',REVIEW_EXCHANGES:'MINIAPP_REWARD_EXCHANGE_REVIEW_CHILD',REWARD_CATEGORY_LABELS:{STUDY_SUPPLIES:'学习用品',LIFE_GOODS:'生活好物',EXPERIENCE:'体验活动'}},'@/api/attachment':{uploadRewardImage:async()=>({contentUrl:'https://example.test/a.png'}),downloadTaskAttachmentAs:async()=>''},'@/api/capability':{getMiniappCapabilities:async()=>({rewardExchangeEnabled:true})},'@/session/parent-session':{getParentSession:()=>({accessToken:token})},'@/api/http':{ApiError:class ApiError extends Error{constructor(statusCode){super('http');this.statusCode=statusCode;}}}};
 const hooks={};const out={};
 vm.runInNewContext(ts.transpileModule(script+'\nexports.test={reload,loadPage,loadMore,setRewardFilter,setExchangeFilter,switchTab,rewards,exchanges,page,total,hasMore,appendError,loadingMore,editing,tab,ready,rewardFilter,exchangeFilter};',{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2020}}).outputText,{exports:out,require:name=>imports[name]||{},Error,uni:{showToast:()=>{},showModal:()=>({success:r=>r&&r.confirm}),navigateTo:()=>{},chooseImage:()=>{}}},);
 return {page:out.test,hooks,api,setToken:v=>token=v};
}
(async()=>{
const s=setup();s.hooks.show();await flush();
assert.equal(s.page.tab.value,'rewards');assert.equal(s.page.rewards.value.length,1);assert.equal(s.page.hasMore.value,true);assert.equal(s.page.page.value,1);
// 触底追加 page2，返回含重复 id=1 与新增 id=2 → 去重后只加 1 条
s.api.rewards=async(t,sid,page,status)=>{calls.push(['rewards',page,status]);return {items:[{...s.page.rewards.value[0]},{id:'2',studentId:'s1',rewardName:'中文奖励',requiredPoints:5,category:'LIFE_GOODS',stockQuantity:2,imageUrl:null,description:null,expiresAt:null,status:'OFFLINE'}],page,total:100,pageSize:20};};
await s.page.loadMore();assert.equal(s.page.rewards.value.length,2);assert.equal(s.page.page.value,2);assert.equal(s.page.appendError.value,'');
// 筛选服务端化：切 ONLINE 触发 loadPage(1) 且携带 status；重复点击同值不重复请求
s.api.rewards=async(t,sid,page,status)=>{calls.push(['rewards',page,status]);return {items:[{id:'9',studentId:'s1',rewardName:'上架奖励',requiredPoints:8,category:'EXPERIENCE',stockQuantity:1,imageUrl:null,description:null,expiresAt:null,status:'ONLINE'}],page,total:1,pageSize:20};};
await s.page.setRewardFilter('ONLINE');await flush();assert.equal(s.page.rewardFilter.value,'ONLINE');assert.equal(s.page.page.value,1);assert.equal(s.page.total.value,1);assert.equal(s.page.hasMore.value,false);assert.ok(calls.some(c=>c[0]==='rewards'&&c[1]===1&&c[2]==='ONLINE'));
const before=calls.length;await s.page.setRewardFilter('ONLINE');await flush();assert.equal(calls.length,before);
// 失败同页：先造 hasMore 再失败，page 保持，触底不自动重试
s.api.rewards=async(t,sid,page,status)=>{calls.push(['rewards',page,status]);return {items:[{id:String(page*10+1),studentId:'s1',rewardName:'更多奖励',requiredPoints:6,category:'LIFE_GOODS',stockQuantity:1,imageUrl:null,description:null,expiresAt:null,status:'ONLINE'}],page,total:100,pageSize:20};};
await s.page.setRewardFilter('ALL');await flush();assert.equal(s.page.hasMore.value,true);
await s.page.loadMore();assert.equal(s.page.hasMore.value,true);assert.equal(s.page.page.value,2);
s.api.rewards=async(t,sid,page,status)=>{calls.push(['fail',page]);throw Error('网络失败');};
await s.page.loadMore();assert.equal(s.page.page.value,2);assert.ok(s.page.appendError.value.includes('网络失败'));assert.equal(s.page.loadingMore.value,false);
s.hooks.bottom();await flush();assert.equal(calls.filter(c=>c[0]==='fail').length,1);
// 点击重试同页成功
s.api.rewards=async(t,sid,page,status)=>{calls.push(['rewards',page,status]);return {items:[{id:'12',studentId:'s1',rewardName:'重试奖励',requiredPoints:3,category:'STUDY_SUPPLIES',stockQuantity:1,imageUrl:null,description:null,expiresAt:null,status:'ONLINE'}],page,total:100,pageSize:20};};
await s.page.loadMore();assert.equal(s.page.page.value,3);assert.equal(s.page.rewards.value.length,3);assert.equal(s.page.appendError.value,'');
// 忙时筛选重置：挂起 loadMore(page4 旧响应)，setRewardFilter('OFFLINE') 触发 load(1)；旧 page4 响应不回填
s.api.rewards=async(t,sid,page,status)=>{calls.push(['pending',page,status]);if(page===4)return new Promise(r=>setTimeout(()=>r({items:[{id:'old',studentId:'s1',rewardName:'旧响应',requiredPoints:1,category:'STUDY_SUPPLIES',stockQuantity:1,imageUrl:null,description:null,expiresAt:null,status:'OFFLINE'}],page:4,total:100,pageSize:20}),20));return {items:[{id:'fresh',studentId:'s1',rewardName:'新响应',requiredPoints:2,category:'STUDY_SUPPLIES',stockQuantity:1,imageUrl:null,description:null,expiresAt:null,status:'OFFLINE'}],page:1,total:2,pageSize:20};};
const pending=s.page.loadMore();await s.page.setRewardFilter('OFFLINE');await flush();await pending;await flush();
assert.equal(s.page.page.value,1);assert.ok(!s.page.rewards.value.some(r=>r.id==='old'));assert.ok(s.page.rewards.value.some(r=>r.id==='fresh'));
// 兑换页签：切 tab + 服务端筛选
s.api.exchanges=async(t,sid,page,status)=>{calls.push(['exchanges',page,status]);return {items:[{id:'x1',studentId:'s1',rewardName:'中文兑换',requiredPoints:5,status:'PENDING_APPROVAL',description:null,approvalDeadline:'2026-10-25T12:00:00',rejectReason:null}],page,total:50,pageSize:20};};
await s.page.switchTab('exchanges');await flush();assert.equal(s.page.tab.value,'exchanges');assert.equal(s.page.exchanges.value.length,1);assert.equal(s.page.hasMore.value,true);
await s.page.setExchangeFilter('PENDING_APPROVAL');await flush();assert.ok(calls.some(c=>c[0]==='exchanges'&&c[1]===1&&c[2]==='PENDING_APPROVAL'));
// 编辑态不触底
s.page.editing.value=true;const editCalls=calls.length;s.hooks.bottom();await flush();assert.equal(calls.length,editCalls);
// hide 清理
s.page.editing.value=false;s.hooks.hide();assert.equal(s.page.rewards.value.length,0);assert.equal(s.page.hasMore.value,false);assert.equal(s.page.appendError.value,'');assert.equal(s.page.loadingMore.value,false);
// 旧会话令牌变更后触底零请求
s.hooks.show();await flush();assert.equal(s.page.ready.value,true);let after=0;s.api.rewards=async()=>{after++;return {items:[],page:1,total:0,pageSize:20};};s.setToken('token-3');await s.page.loadMore();assert.equal(after,0);
console.log('通过：parent-rewards 触底追加、去重、筛选服务端化、失败同页重试、忙时重置、兑换页签筛选、编辑禁触底、hide 清理、旧响应隔离');
})().catch(e=>{console.error(e);process.exitCode=1;});
