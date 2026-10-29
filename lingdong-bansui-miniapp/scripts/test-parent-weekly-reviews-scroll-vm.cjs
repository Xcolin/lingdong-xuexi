const fs=require('fs'),vm=require('vm'),ts=require('typescript'),assert=require('node:assert/strict');
const script=fs.readFileSync('src/pages/parent-weekly-reviews/parent-weekly-reviews.vue','utf8').match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1],flush=()=>new Promise(r=>setImmediate(r));
const calls=[];
function setup(){
 let token='fixture-token';
 const summary={reviewId:'r1',studentId:'s1',studentName:'小灵',periodType:'WEEK',periodStart:'2026-10-13',periodEnd:'2026-10-19',taskTotalCount:10,completedCount:8,inProgressCount:1,pendingOptimizationCount:1};
 const detail={...summary,dataCutoffAt:'2026-10-19T23:59:59',generatedAt:'2026-10-20T08:00:00',earnedPoints:80,completionRate:0.8,exemptedCount:0,pauseCount:0,categories:[],dailyTrends:[],supplements:[]};
 const api={me:async()=>({clientType:'MINIAPP',roleCodes:['CUSTOM_PARENT'],permissionCodes:['MINIAPP_GROWTH_REVIEW_READ_CHILD']}),students:async()=>[{studentId:'s1',studentName:'小灵'}],list:async(t,sid,page)=>{calls.push(['list',page]);return {items:[{...summary,reviewId:'r'+page}],total:100,page,pageSize:20};},detail:async(t,sid,reviewId)=>{calls.push(['detail',reviewId]);return {...detail,reviewId};}};
 const imports={vue:{ref:value=>({value})},'@/components/pixel/PixelPageHeader.vue':{},'@/components/PrototypeIcon.vue':{},'@/components/AppTabBar.vue':{},'@/config/tabbar':{PARENT_TABS:[]},'./overview-model':{latestWeekly:items=>items[0]||null,weeklyOverview:report=>report,trendHeight:(ov,count)=>Math.min(100,count*10)},'@dcloudio/uni-app':{onLoad:fn=>hooks.load=fn,onShow:fn=>hooks.show=fn,onHide:fn=>hooks.hide=fn,onUnload:fn=>hooks.unload=fn,onReachBottom:fn=>hooks.bottom=fn},'@/api/parent-weekly-review':{weeklyApi:api,canReadWeekly:user=>user.permissionCodes.includes('MINIAPP_GROWTH_REVIEW_READ_CHILD')},'@/api/growth-point':{getChildGrowthPointAccount:async()=>({totalPoints:120})},'@/session/parent-session':{getParentSession:()=>({accessToken:token})}};
 const hooks={};const out={};
 vm.runInNewContext(ts.transpileModule(script+'\nexports.test={initialize,loadPage,loadMore,open,rows,page,total,hasMore,appendError,loadingMore,detail,loaded,busy};',{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2020}}).outputText,{exports:out,require:name=>imports[name]||{},Error,uni:{showToast:()=>{},showModal:()=>{},navigateTo:()=>{}}});
 return {page:out.test,hooks,api,setToken:v=>token=v};
}
const testTimeout=setTimeout(()=>{console.error('周复盘测试超时：尾部未完成');process.exit(1);},10000);
(async()=>{
const s=setup();s.hooks.load({});s.hooks.show();await flush();
assert.equal(s.page.rows.value.length,1);assert.equal(s.page.hasMore.value,true);assert.equal(s.page.page.value,1);assert.equal(s.page.loaded.value,true);
// 触底追加 page2，返回含重复 reviewId=r1 与新增 r2 → 去重后 2 条
s.api.list=async(t,sid,page)=>{calls.push(['list',page]);return {items:[{...s.page.rows.value[0]},{...s.page.rows.value[0],reviewId:'r2',periodEnd:'2026-10-12'}],total:100,page,pageSize:20};};
await s.page.loadMore();assert.equal(s.page.rows.value.length,2);assert.equal(s.page.page.value,2);assert.equal(s.page.appendError.value,'');
// 失败同页：page3 报错，page 保持 2，触底不自动重试
s.api.list=async()=>{calls.push(['fail']);throw Error('网络失败');};
await s.page.loadMore();assert.equal(s.page.page.value,2);assert.ok(s.page.appendError.value.includes('网络失败'));assert.equal(s.page.loadingMore.value,false);
s.hooks.bottom();await flush();assert.equal(calls.filter(c=>c[0]==='fail').length,1);
// 点击重试同页成功
s.api.list=async(t,sid,page)=>{calls.push(['list',page]);return {items:[{...s.page.rows.value[0],reviewId:'r'+page}],total:100,page,pageSize:20};};
await s.page.loadMore();assert.equal(s.page.page.value,3);assert.equal(s.page.rows.value.length,3);assert.equal(s.page.appendError.value,'');
// 详情态不触底：open 后 detail 非空，bottom 零请求；返回列表后恢复
await s.page.open('r3');await flush();assert.ok(s.page.detail.value);const detailCalls=calls.length;s.hooks.bottom();await flush();assert.equal(calls.length,detailCalls);
await s.page.loadPage(1);await flush();assert.equal(s.page.detail.value,null);
// 首屏已重置为 page1，此处下一页实际是 page2，不能错等 page4。
let releaseOld;
const oldGate=new Promise(resolve=>{releaseOld=resolve;});
const oldRow={...s.page.rows.value[0],reviewId:'old'};
s.api.list=async(t,sid,page)=>{calls.push(['pending',page]);if(page>1){await oldGate;return {items:[oldRow],total:100,page,pageSize:20};}return {items:[{...oldRow,reviewId:'fresh'}],total:1,page:1,pageSize:20};};
const pending=s.page.loadMore();await flush();assert.equal(s.page.loadingMore.value,true);
await s.page.loadPage(1);await flush();assert.equal(s.page.rows.value[0].reviewId,'fresh');
releaseOld();await pending;await flush();
assert.equal(s.page.page.value,1);assert.ok(!s.page.rows.value.some(r=>r.reviewId==='old'));assert.ok(s.page.rows.value.some(r=>r.reviewId==='fresh'));assert.equal(s.page.hasMore.value,false);
// 空页终止：page3 返回空 → hasMore 终止，再次触底零请求
s.api.list=async(t,sid,page)=>{calls.push(['list',page]);return {items:[{...s.page.rows.value[0],reviewId:'r'+page}],total:100,page,pageSize:20};};
await s.page.loadPage(1);await flush();await s.page.loadMore();assert.equal(s.page.rows.value.length,2);assert.equal(s.page.hasMore.value,true);let extra=0;s.api.list=async(t,sid,page)=>{extra++;return {items:[],total:100,page,pageSize:20};};await s.page.loadMore();assert.equal(extra,1);assert.equal(s.page.rows.value.length,2);assert.equal(s.page.hasMore.value,false);await s.page.loadMore();assert.equal(extra,1);
// hide 清理 + 旧响应不恢复
s.hooks.hide();assert.equal(s.page.rows.value.length,0);assert.equal(s.page.hasMore.value,false);assert.equal(s.page.appendError.value,'');
// 旧会话令牌变更后触底零请求
s.hooks.show();await flush();assert.equal(s.page.loaded.value,true);let after=0;s.api.list=async()=>{after++;return {items:[],total:0,page:1,pageSize:20};};s.setToken('token-3');await s.page.loadMore();assert.equal(after,0);
console.log('通过：parent-weekly-reviews 触底追加、去重、失败同页重试、详情态禁触底、忙时重置、空页终止、hide 清理、旧响应隔离');
console.log('完成哨兵：周复盘受控旧响应与全部尾部断言完成');
})().catch(e=>{console.error(e);process.exitCode=1;}).finally(()=>clearTimeout(testTimeout));
