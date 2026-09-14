const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const assert=require('node:assert/strict');
(async()=>{
 const browser=await chromium.launch({headless:true,channel:'msedge'});
 try{
  const page=await browser.newPage({viewport:{width:390,height:844}});
  const studentId='1874244142494693001',rewardId='1874244142494693002';
  let primary=true,enabled=true,auditor=false,reads=0,writes=0,reward=null;
  const exchanges=[{id:'1874244142494693003',rewardId,studentId,rewardName:'测试兑换一',requiredPoints:30,description:'测试快照',status:'PENDING_APPROVAL',approvalDeadline:'2026-12-31T23:59:59'},
   {id:'1874244142494693004',rewardId,studentId,rewardName:'测试兑换二',requiredPoints:20,description:'测试快照二',status:'PENDING_APPROVAL',approvalDeadline:'2026-12-31T23:59:59'}];
  await page.addInitScript(()=>localStorage.setItem('lingdong.parent.session',JSON.stringify({type:'object',data:{accessToken:'synthetic-reward-parent'}})));
  await page.route('**/api/v1/**',async route=>{
   const request=route.request(),path=new URL(request.url()).pathname;let body={};
   if(path.endsWith('/auth/me'))body={clientType:'MINIAPP',roleCodes:auditor?['PARENT','SYS_AUDITOR']:['PARENT'],permissionCodes:['MINIAPP_REWARD_MANAGE_CHILD','MINIAPP_REWARD_EXCHANGE_REVIEW_CHILD']};
   else if(path.includes('capabilit'))body={rewardExchangeEnabled:enabled};
   else{
    assert.equal(request.headers().authorization,'Bearer synthetic-reward-parent');
    if(request.method()==='GET'){
     reads++;
     if(path==='/api/v1/parent-rewards/students')body=[{studentId,studentName:'测试孩子',relationshipRole:primary?'PRIMARY_GUARDIAN':'SECONDARY_GUARDIAN'}];
     else if(path.includes('parent-reward-exchanges'))body={items:exchanges,total:2,page:1,pageSize:20};
     else body={items:reward?[reward]:[],total:reward?1:0,page:1,pageSize:20};
    }else{
     writes++;assert.equal(primary,true);
     if(path.includes('parent-reward-exchanges')){
      const exchange=exchanges.find(item=>path.includes(item.id));assert.ok(exchange);
      if(path.endsWith('/approve'))exchange.status='PENDING_VERIFICATION';
      else if(path.endsWith('/verify'))exchange.status='VERIFIED';
      else {assert.equal(request.postDataJSON().rejectReason,'补充计划后再申请');exchange.status='REJECTED';exchange.rejectReason=request.postDataJSON().rejectReason;}
      body=exchange;
     }else if(request.method()==='DELETE'){reward=null;body={};}
     else{const input=request.postDataJSON();assert.equal(input.requiredPoints,30);if(request.method()==='PUT')assert.equal(input.expiresAt,'2026-12-31T23:59:59');reward={...input,id:rewardId,studentId};body=reward;}
    }
   }
   await route.fulfill({contentType:'application/json',body:JSON.stringify(body)});
  });
  const base=(process.env.MINI_URL||'http://127.0.0.1:5176')+'/#/pages/parent-rewards/parent-rewards';
  await page.goto(base);await page.getByText('暂无奖励',{exact:true}).waitFor();
  await page.getByText('新增奖励',{exact:true}).click();
  await page.locator('uni-input input').nth(0).fill('周末阅读奖励');await page.locator('uni-input input').nth(1).fill('30');
  await page.locator('uni-input input').nth(2).fill('2026-12-31T23:59:59');
  await page.getByText('保存奖励',{exact:true}).click();await page.getByText('确定',{exact:true}).click();await page.getByText('共 1 项奖励',{exact:true}).waitFor();
  await page.getByText('编辑奖励',{exact:true}).click();await page.locator('uni-input input').first().fill('编辑后的奖励');
  await page.getByText('保存奖励',{exact:true}).click();await page.getByText('确定',{exact:true}).click();await page.getByText('编辑后的奖励',{exact:true}).waitFor();
  await page.getByText('兑换处理',{exact:true}).click();await page.getByText('共 2 项兑换记录',{exact:true}).waitFor();
  await page.getByText('同意兑换',{exact:true}).first().click();await page.getByText('确定',{exact:true}).click();await page.getByText('确认已兑现',{exact:true}).waitFor();
  await page.getByText('确认已兑现',{exact:true}).click();await page.getByText('确定',{exact:true}).click();await page.getByText('30 积分 · 已核销',{exact:true}).waitFor();
  await page.locator('textarea').fill('补充计划后再申请');await page.getByText('驳回兑换',{exact:true}).click();await page.getByText('确定',{exact:true}).click();await page.getByText('20 积分 · 已驳回',{exact:true}).waitFor();
  assert.equal(writes,5);await page.screenshot({path:'../.local-verification/parent-rewards-mini.png',fullPage:true});
  await page.getByText('奖励库',{exact:true}).click();await page.getByText('删除奖励',{exact:true}).click();await page.getByText('确定',{exact:true}).click();await page.getByText('暂无奖励',{exact:true}).waitFor();assert.equal(writes,6);
  primary=false;await page.getByText('刷新',{exact:true}).click();await page.getByText('暂无奖励',{exact:true}).waitFor();assert.equal(await page.getByText('新增奖励',{exact:true}).count(),0);assert.equal(await page.getByText('编辑奖励',{exact:true}).count(),0);
  enabled=false;const before=reads;await page.getByText('刷新',{exact:true}).click();await page.getByText('奖励功能未开启或无访问权限',{exact:true}).waitFor();assert.equal(reads,before);
  enabled=true;auditor=true;await page.reload();await page.getByText('奖励功能未开启或无访问权限',{exact:true}).waitFor();assert.equal(reads,before);assert.equal(writes,6);
  console.log('家长奖励：创建编辑保留有效期、同意扣分确认、核销、驳回、副家长只读、关闭与混合审核员拦截通过');
 }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
