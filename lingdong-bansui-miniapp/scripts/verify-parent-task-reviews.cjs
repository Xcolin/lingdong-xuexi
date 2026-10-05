const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const assert = require('node:assert/strict');
(async () => {
 const browser = await chromium.launch({ headless:true, channel:'msedge' });
 try {
  const page = await browser.newPage({ viewport:{width:390,height:844} });
  let feature=true, auditor=false, count=21, mode='', listCalls=0, writes=0;
  const item={assignmentId:'1874244142494690001',title:'家庭阅读',studentName:'测试孩子',basePoints:10,sourceType:'FAMILY',latestCheckIn:{id:'1874244142494690002',content:'阅读完成',submittedAt:'2026-09-11T08:00:00',attachments:[]}};
  await page.addInitScript(() => localStorage.setItem('lingdong.parent.session',JSON.stringify({type:'object',data:{sessionId:'2',accessToken:'synthetic-parent-token',mobile:'测试家长'}})));
  await page.route('**/api/v1/**',async route=>{
   const url=new URL(route.request().url()); let body={},status=200;
   if(!url.pathname.includes('/public/')) assert.equal(route.request().headers().authorization,'Bearer synthetic-parent-token');
   if(url.pathname.endsWith('/auth/me')) body={clientType:'MINIAPP',roleCodes:auditor?['PARENT','SYS_AUDITOR']:['PARENT'],permissionCodes:['TASK_ASSIGNMENT_REVIEW']};
   else if(url.pathname.endsWith('/public/parent-auth-context')) body={enabled:true};
   else if(url.pathname.endsWith('/auth/parent-state')) body={onboardingRequired:false,agreementAcceptanceRequired:false};
   else if(url.pathname.endsWith('/public/capabilities')) body={client:'MINIAPP',learningTaskManagementEnabled:feature};
   else if(url.pathname.endsWith('/task-reviews')) { listCalls++; const p=Number(url.searchParams.get('page')); body={items:count?[{...item,title:p===2?'第二页任务':item.title}]:[],page:p,pageSize:20,total:count}; }
   else if(url.pathname.endsWith('/approve')||url.pathname.endsWith('/reject')) { writes++; assert.equal(route.request().postDataJSON().expectedCheckInId,item.latestCheckIn.id); if(mode==='closed'){status=409;body={code:'FEATURE_DISABLED',message:'功能已关闭'};} else if(mode==='conflict'){status=409;body={message:'任务状态已变更'};} else {if(url.pathname.endsWith('/reject'))assert.equal(route.request().postDataJSON().reviewComment,'请补充说明');count=0;} }
   else if(url.pathname.includes('/task-reviews/')) body=item;
   await route.fulfill({status,contentType:'application/json',body:JSON.stringify(body)});
  });
  const base=process.env.MINI_URL||'http://127.0.0.1:5175';
  await page.goto(base+'/#/pages/parent-home/parent-home');
  await page.getByText('待审核任务：21 项',{exact:true}).waitFor();
  await page.getByText('查看待审核任务',{exact:true}).click();
  await page.getByText('共 21 项待审核任务',{exact:true}).waitFor();
  await page.getByText('下一页',{exact:true}).click(); await page.getByText('第二页任务',{exact:true}).waitFor();
  await page.getByText('查看并审核',{exact:true}).click(); await page.getByText('阅读完成',{exact:true}).waitFor();
  await page.screenshot({path:require('node:path').resolve(__dirname,'../../.local-verification/parent-task-review-mini.png'),fullPage:true});
  mode='conflict'; await page.getByText('审核通过',{exact:true}).click(); await page.getByText('任务状态已变更；请刷新后重试',{exact:true}).waitFor();
  assert.equal(await page.locator('uni-button[disabled]').count()>0,true);
  await page.reload(); await page.getByText('查看并审核',{exact:true}).click();
  mode=''; await page.locator('textarea').fill('请补充说明'); await page.getByText('退回修改',{exact:true}).click();
  await page.getByText('暂无待审核任务',{exact:true}).waitFor(); assert.equal(writes,2);
  count=1; await page.reload(); await page.getByText('查看并审核',{exact:true}).click(); await page.getByText('审核通过',{exact:true}).click(); await page.getByText('暂无待审核任务',{exact:true}).waitFor(); assert.equal(writes,3);
  count=1; await page.reload(); await page.getByText('查看并审核',{exact:true}).click(); mode='closed'; await page.getByText('审核通过',{exact:true}).click(); await page.getByText('功能已关闭；请刷新后重试',{exact:true}).waitFor(); assert.equal(await page.getByText('阅读完成',{exact:true}).count(),0);
  const before=listCalls; feature=false; await page.reload(); await page.getByText('任务审核未开启或无审核权限',{exact:true}).waitFor(); assert.equal(listCalls,before);
  feature=true; auditor=true; await page.reload(); await page.getByText('任务审核未开启或无审核权限',{exact:true}).waitFor(); assert.equal(listCalls,before);
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
  console.log('家长待审 H5：真实 total、分页、详情、冲突锁定、退回、空态、功能关闭与混合审核员直达拦截通过');
 } finally {await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
