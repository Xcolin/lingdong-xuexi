const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const assert = require('node:assert/strict');
(async()=>{
 const browser=await chromium.launch({headless:true,channel:'msedge'});
 try {
  const page=await browser.newPage({viewport:{width:390,height:844}});
  let allowed=true, reads=0;
  const studentId='1874244142494691001', reviewId='1874244142494691002';
  const summary={studentId,reviewId,studentName:'测试孩子',periodType:'WEEK',periodStart:'2026-09-01',periodEnd:'2026-09-07',taskTotalCount:10,completedCount:6,inProgressCount:2,pendingOptimizationCount:1,exemptedCount:1,completionRate:0.75,earnedPoints:50,pauseCount:2,contentVersion:1,generatedAt:'2026-09-08T07:00:00'};
  await page.addInitScript(()=>localStorage.setItem('lingdong.parent.session',JSON.stringify({type:'object',data:{accessToken:'synthetic-weekly-parent'}})));
  await page.route('**/api/v1/**',async route=>{
   const path=new URL(route.request().url()).pathname; let body={};
   if(path.endsWith('/auth/me')) body={clientType:'MINIAPP',roleCodes:['PARENT'],permissionCodes:allowed?['MINIAPP_GROWTH_REVIEW_READ_CHILD']:[]};
   else {
    assert.equal(route.request().headers().authorization,'Bearer synthetic-weekly-parent'); reads++;
    if(path.endsWith('/miniapp/students')) body=[{studentId,studentName:'测试孩子'}];
    else if(path.endsWith('/'+studentId)) body={items:[summary],total:21,page:1,pageSize:20};
    else if(path.endsWith('/'+reviewId)) body={...summary,dataCutoffAt:'2026-09-08T00:00:00',categories:[{categoryCode:'GENERAL',taskCount:10,completedCount:6}],dailyTrends:[],supplements:[]};
    else return route.fulfill({status:404,contentType:'application/json',body:'{"message":"周报不可访问"}'});
   }
   await route.fulfill({contentType:'application/json',body:JSON.stringify(body)});
  });
  const base=(process.env.MINI_URL||'http://127.0.0.1:5176')+'/#/pages/parent-weekly-reviews/parent-weekly-reviews';
  await page.goto(base); await page.getByText('共 21 份周报',{exact:true}).waitFor();
  await page.getByText('查看周报',{exact:true}).click(); await page.getByText('完成率：75.0%',{exact:true}).waitFor();
  await page.screenshot({path:'../.local-verification/parent-weekly-mini.png',fullPage:true});
  await page.goto('about:blank'); await page.goto(base+'?studentId='+studentId+'&reviewId='+reviewId); await page.getByText('完成率：75.0%',{exact:true}).waitFor();
  allowed=false; const before=reads; await page.reload(); await page.getByText('无家长周报读取权限',{exact:true}).waitFor();
  assert.equal(reads,before); assert.equal(await page.getByText('完成率：75.0%',{exact:true}).count(),0);
  allowed=true; await page.goto('about:blank'); await page.goto(base+'?studentId='+studentId+'&reviewId=1874244142494691999');
  await page.getByText('周报不可访问',{exact:true}).waitFor();
  assert.equal(await page.getByText('完成率：75.0%',{exact:true}).count(),0);
  const beforeMalformed=reads; await page.goto('about:blank'); await page.goto(base+'?studentId=1&reviewId=2');
  await page.getByText('周报链接不合法',{exact:true}).waitFor(); assert.equal(reads,beforeMalformed);
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
  console.log('家长周报：专用权限、分页总数、详情、消息直达与权限撤回清空通过');
 }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
