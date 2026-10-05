const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const assert=require('node:assert/strict');
(async()=>{
 const browser=await chromium.launch({headless:true,channel:'msedge'});
 try { for(const identity of ['teacher','organization']) {
  const page=await browser.newPage({viewport:{width:390,height:844}});
  let permission=true, feature=true, mixed=false, count=21, writes=0, reads=0, listFail=true;
  const item={assignmentId:'1874244142494690101',title:'本班阅读',studentName:'测试学生',basePoints:10,sourceType:'TEACHER',latestCheckIn:{id:'1874244142494690102',content:'已完成阅读',submittedAt:'2026-09-12T09:00:00',attachments:[]}};
  await page.addInitScript(()=>localStorage.setItem('lingdong.organization.session',JSON.stringify({type:'object',data:{accessToken:'synthetic-org-token',sessionId:'4'}})));
  await page.route('**/api/v1/**',async route=>{
   const url=new URL(route.request().url());let body={},status=200;
   if(!url.pathname.includes('/public/'))assert.equal(route.request().headers().authorization,'Bearer synthetic-org-token');
   const permissions=permission?['TASK_ASSIGNMENT_REVIEW']:[];
   if(url.pathname.endsWith('/auth/me'))body={clientType:'MINIAPP',roleCodes:[identity==='teacher'?'TEACHER':'ORG_ADMIN',...(mixed?['SYS_AUDITOR']:[])],permissionCodes:permissions};
   else if(url.pathname.endsWith('/public/capabilities'))body={client:'MINIAPP',organizationMiniappAuthEnabled:true,learningTaskManagementEnabled:feature};
   else if(url.pathname.endsWith('/context'))body={userId:'1874244142494690103',displayName:'测试工作台',username:'tester',permissionCodes:permissions,classes:[],organizations:[]};
   else if(url.pathname.endsWith('/task-reviews')){reads++;if(listFail){status=500;body={message:'合成失败'};}else body={items:count?[{...item,title:url.searchParams.get('page')==='2'?'第二页阅读':item.title}]:[],page:Number(url.searchParams.get('page')),pageSize:20,total:count};}
   else if(url.pathname.endsWith('/reject')||url.pathname.endsWith('/approve')){writes++;assert.equal(route.request().postDataJSON().expectedCheckInId,item.latestCheckIn.id);count=0;}
   else if(url.pathname.includes('/learning-tasks'))throw new Error('仅审核身份不应查询任务管理列表');
   await route.fulfill({status,contentType:'application/json',body:JSON.stringify(body)});
  });
  const base=process.env.MINI_URL||'http://127.0.0.1:5175';
  await page.goto(base+`/#/pages/${identity}-home/${identity}-home`);
  await page.getByText('待审核任务加载失败，请重试',{exact:true}).waitFor();
  listFail=false; await page.getByText('重试待审核查询',{exact:true}).click();
  await page.getByText('待审核任务：21 项',{exact:true}).waitFor();
  await page.screenshot({path:require('node:path').resolve(__dirname,`../../.local-verification/${identity}-review-summary.png`),fullPage:true});
  await page.getByText('查看待审核任务',{exact:true}).click();
  await page.getByText('共 21 项待审核任务',{exact:true}).waitFor();
  await page.screenshot({path:require('node:path').resolve(__dirname,`../../.local-verification/${identity}-review-queue.png`),fullPage:true});
  await page.getByText('下一页',{exact:true}).click();await page.getByText('第二页阅读',{exact:true}).waitFor();
  await page.getByText('驳回',{exact:true}).click();await page.locator('textarea').fill('请补充阅读内容');await page.getByText('确认驳回',{exact:true}).click();
  await page.getByText('暂无审核待办',{exact:true}).waitFor();assert.equal(writes,1);
  count=1;await page.reload();await page.getByText('本班阅读',{exact:true}).waitFor();
  permission=false;await page.getByText('通过',{exact:true}).click();await page.getByText('确定',{exact:true}).click();
  await page.getByText('审核内容或权限已变化，请刷新',{exact:true}).first().waitFor();assert.equal(writes,1);assert.equal(await page.getByText('本班阅读',{exact:true}).count(),0);
  permission=true;mixed=true;const before=reads;await page.reload();await page.getByText('任务功能未开启或无操作权限',{exact:true}).waitFor();assert.equal(reads,before);
  mixed=false;feature=false;await page.reload();await page.getByText('任务功能未开启或无操作权限',{exact:true}).waitFor();assert.equal(reads,before);
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
  await page.close();
 }
 console.log('教师/机构 H5：失败重试、真实total、仅审核权限入口、分页、审核打卡ID、退回、空态、提交前撤权、混合审核员及关闭直达拦截通过');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
