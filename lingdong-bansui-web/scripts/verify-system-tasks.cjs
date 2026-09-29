const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const assert=require('node:assert/strict');
(async()=>{
 const browser=await chromium.launch({headless:true,channel:'msedge'});
 try{
  const page=await browser.newPage({viewport:{width:1280,height:900}});
  let allowed=true,reads=0,readGranted=false;
  const task={id:'1874244142494696001',code:'synthetic-task-1',type:'CACHE_CLEAR',title:'合成缓存任务',description:'核对后清除全部缓存',impactScope:'GLOBAL',status:'PENDING_REVIEW',submittedBy:'1874244142494696002',submittedAt:'2026-09-13T09:00:00',reviewedBy:null,reviewedAt:null,reviewComment:null,createdAt:'2026-09-13T08:00:00',updatedAt:'2026-09-13T09:00:00'};
  await page.addInitScript(()=>sessionStorage.setItem('lingdong-learning.web.session',JSON.stringify({sessionId:'2',accessToken:'synthetic-system-task-token',refreshToken:'synthetic-refresh',accessExpiresAt:'2099-01-01T00:00:00'})));
  await page.route('**/api/v1/**',async route=>{
   const url=new URL(route.request().url());let body=[];
   assert.equal(route.request().method(),'GET');
   if(url.pathname.endsWith('/auth/me'))body={userId:'1874244142494696003',sessionId:'2',clientType:'WEB',roleCodes:['SYS_AUDITOR'],permissionCodes:allowed?['SYSTEM_TASK_READ','CACHE_REVIEW',...(readGranted?['CACHE_READ']:[])]:[],displayName:'合成审核员'};
   else if(url.pathname.endsWith('/public/capabilities'))body={client:'WEB',cacheManagementEnabled:true};
   else if(url.pathname.endsWith('/system-tasks')){reads++;body={items:[{...task,title:url.searchParams.get('page')==='2'?'第二页系统任务':task.title}],page:Number(url.searchParams.get('page')),pageSize:20,total:41};}
   else if(url.pathname.endsWith('/'+task.id)){reads++;body=task;}
   await route.fulfill({contentType:'application/json',body:JSON.stringify(body)});
  });
  const base=process.env.WEB_URL||'http://127.0.0.1:5177';
  await page.goto(base+'/system-tasks');await page.getByText('共 41 条',{exact:true}).waitFor();
  readGranted=true;await page.evaluate(()=>window.dispatchEvent(new Event('focus')));await page.getByText('合成缓存任务',{exact:true}).waitFor();
  await page.locator('.ant-pagination-item-2').click();await page.getByText('第二页系统任务',{exact:true}).waitFor();
  await page.getByRole('button',{name:'查看详情',exact:true}).click();await page.getByText('核对后清除全部缓存',{exact:true}).waitFor();
  await page.screenshot({path:'../.local-verification/system-tasks-web.png',fullPage:true,animations:'disabled'});
  await page.getByRole('button',{name:'前往领域处理页',exact:true}).click();await page.waitForURL('**/cache-management',{timeout:10000});
  await page.getByRole('heading',{name:'缓存管理',exact:true}).waitFor({timeout:10000});
  await page.goto(base+'/system-tasks');await page.getByRole('button',{name:'查看详情',exact:true}).click();await page.getByText('核对后清除全部缓存',{exact:true}).waitFor();
  allowed=false;const before=reads;await page.evaluate(()=>window.dispatchEvent(new Event('focus')));await page.getByText('当前会话无系统任务读取权限',{exact:true}).waitFor();
  assert.equal(reads,before);assert.equal(await page.getByText('核对后清除全部缓存',{exact:true}).count(),0);assert.equal(await page.getByText('合成缓存任务',{exact:true}).count(),0);
  console.log('系统任务：总数分页、详情、领域跳转、焦点撤权立即清空且无写请求通过');
 }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
