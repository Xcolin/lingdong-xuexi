const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const assert=require('node:assert/strict');
(async()=>{
 const browser=await chromium.launch({headless:true,channel:'msedge'});
 try{
  const page=await browser.newPage({viewport:{width:390,height:844}});
  await page.addInitScript(()=>localStorage.setItem('lingdong.organization.session',JSON.stringify({type:'object',data:{accessToken:'synthetic-exception-org'}})));
  let enabled=true,fail=false,reads=0,handled=false,writes=0;
  const report={id:'1874244142494695001',studentId:'1874244142494695002',studentName:'测试学生',className:'测试班级',status:'SUBMITTED',exceptionType:'ATTENDANCE',content:'合成异常事实',reportedAt:'2026-09-13T09:00:00',versionNo:3};
  await page.route('**/api/v1/**',async route=>{
   const url=new URL(route.request().url());let body={};
   const permissions=['EXCEPTION_REPORT_READ','EXCEPTION_REPORT_HANDLE'];
   if(url.pathname.endsWith('/auth/me'))body={clientType:'MINIAPP',roleCodes:['ORG_ADMIN'],permissionCodes:permissions};
   else if(url.pathname.includes('capabilities'))body={organizationMiniappAuthEnabled:true,studentExceptionReportEnabled:enabled};
   else if(url.pathname.endsWith('/context'))body={displayName:'测试机构',permissionCodes:permissions,organizations:[]};
   else if(url.pathname.endsWith('/exception-reports')){
    reads++;assert.equal(url.searchParams.get('status'),'SUBMITTED');
    if(fail)return route.fulfill({status:500,contentType:'application/json',body:'{"message":"合成失败"}'});
    body={items:url.searchParams.get('pageSize')==='1'||handled?[]:[{...report,content:url.searchParams.get('page')==='2'?'第二页异常事实':report.content}],page:Number(url.searchParams.get('page')),pageSize:Number(url.searchParams.get('pageSize')),total:handled?0:23};
   }else if(url.pathname.endsWith('/handle')){
    writes++;assert.equal(route.request().postDataJSON().versionNo,3);assert.equal(route.request().postDataJSON().handlingNote,'已核实并联系家长');handled=true;body={...report,status:'HANDLED'};
   }else if(url.pathname.endsWith('/'+report.id)){
    body={report,actions:[{operatorName:'测试教师',actionNote:'已报备'}]};
   }
   await route.fulfill({contentType:'application/json',body:JSON.stringify(body)});
  });
  await page.goto((process.env.MINI_URL||'http://127.0.0.1:5176')+'/#/pages/organization-home/organization-home');
  await page.getByText('待处理异常：23 项',{exact:true}).waitFor({timeout:10000});
  fail=true;await page.reload();await page.getByText('待处理异常加载失败，请重试',{exact:true}).waitFor();assert.equal(await page.getByText('暂无待处理异常',{exact:true}).count(),0);
  fail=false;await page.getByText('重试异常查询',{exact:true}).click();await page.getByText('待处理异常：23 项',{exact:true}).waitFor();
  await page.getByText('查看待处理异常',{exact:true}).click();await page.getByText('待处理异常 · 共 23 项',{exact:true}).waitFor();
  await page.getByText('下一页',{exact:true}).click();await page.getByText('第二页异常事实',{exact:true}).waitFor();
  await page.getByText('第二页异常事实',{exact:true}).click();await page.getByText('报备详情',{exact:true}).waitFor();await page.getByText('确定',{exact:true}).click();
  await page.getByText('处理',{exact:true}).click();await page.locator('textarea').fill('已核实并联系家长');await page.getByText('确认处理',{exact:true}).click();await page.getByText('待处理异常 · 共 0 项',{exact:true}).waitFor();assert.equal(writes,1);
  await page.goto('about:blank');await page.goto((process.env.MINI_URL||'http://127.0.0.1:5176')+'/#/pages/organization-home/organization-home');await page.getByText('暂无待处理异常',{exact:true}).waitFor();
  enabled=false;const previous=reads;await page.reload();await page.getByText('测试机构',{exact:true}).waitFor();assert.equal(reads,previous);assert.equal(await page.getByText('待处理异常：23 项',{exact:true}).count(),0);
  console.log('机构异常摘要：服务端待处理总数、失败重试与关闭隐藏通过');
 }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
