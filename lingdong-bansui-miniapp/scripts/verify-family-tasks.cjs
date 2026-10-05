const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const assert = require('node:assert/strict');
(async () => {
 const browser = await chromium.launch({headless:true,channel:'msedge'});
 try {
  const page = await browser.newPage({viewport:{width:390,height:844}});
  const id='1874244142494692001', child='1874244142494692002';
  let task=null, allowed=true, writes=0, failSave=false;
  await page.addInitScript(()=>localStorage.setItem('lingdong.parent.session',JSON.stringify({type:'object',data:{accessToken:'synthetic-family-parent'}})));
  await page.route('**/api/v1/**', async route => {
   const request=route.request(), path=new URL(request.url()).pathname; let body={};
   if(path.endsWith('/auth/me')) body={clientType:'MINIAPP',roleCodes:['PARENT'],permissionCodes:allowed?['LEARNING_TASK_READ_MANAGED','LEARNING_TASK_CREATE','LEARNING_TASK_PUBLISH']:[]};
   else if(path.includes('capabilit')) body={learningTaskManagementEnabled:true};
   else {
    assert.equal(request.headers().authorization,'Bearer synthetic-family-parent');
    if(request.method()==='POST'||request.method()==='PUT') {
     writes++;
     if(failSave) return route.fulfill({status:409,contentType:'application/json',body:JSON.stringify({message:'任务状态已变化'})});
     if(path.endsWith('/publish')) {task.status='PUBLISHED';body={taskId:id,assignmentCount:1,status:'PUBLISHED'};}
     else {
      const input=request.postDataJSON();assert.equal(input.sourceType,'FAMILY');assert.equal(input.targets[0].targetId,child);
      assert.equal(input.organizationId,undefined);assert.equal(input.reviewerUserId,undefined);
      if(request.method()==='PUT') {assert.equal(input.categoryCode,'READING');assert.deepEqual(input.tagCodes,['DAILY']);assert.equal(input.recurrenceEnabled,true);assert.equal(input.recurrenceEndDate,'2026-12-31');}
      task={...input,id,status:'DRAFT'};body=task;
     }
    } else if(path.includes('/learning-task-options/students')) body=[{id:child,studentName:'测试主监护孩子',relationshipRole:'PRIMARY_GUARDIAN'},{id:'1874244142494692003',studentName:'测试副监护孩子',relationshipRole:'SECONDARY_GUARDIAN'}];
    else if(path.endsWith('/'+id)) body=task;
    else if(path.endsWith('/learning-tasks')) {assert.equal(new URL(request.url()).searchParams.get('sourceType'),'FAMILY');body={items:task?[task]:[],total:task?1:0,page:1,pageSize:20};}
    else throw new Error('未预期接口 '+path);
   }
   await route.fulfill({contentType:'application/json',body:JSON.stringify(body)});
  });
  await page.goto((process.env.MINI_URL||'http://127.0.0.1:5176')+'/#/pages/family-tasks/family-tasks');
  await page.getByText('暂无家庭任务',{exact:true}).waitFor();
  await page.getByText('新建家庭任务',{exact:true}).click();
  await page.locator('uni-input input').first().fill('亲子阅读');
  await page.getByText('测试主监护孩子',{exact:true}).click();
  await page.getByText('保存草稿',{exact:true}).click();
  await page.getByText('共 1 项家庭任务',{exact:true}).waitFor();
  task.categoryCode='READING';task.tagCodes=['DAILY'];task.recurrenceEnabled=true;task.recurrenceEndDate='2026-12-31';
  await page.getByText('编辑草稿',{exact:true}).click();
  await page.locator('uni-input input').first().fill('保留原配置的阅读');
  failSave=true;await page.getByText('保存草稿',{exact:true}).click();await page.getByText('任务状态已变化',{exact:true}).waitFor();
  assert.equal(await page.locator('uni-input input').first().inputValue(),'保留原配置的阅读');
  failSave=false;await page.getByText('保存草稿',{exact:true}).click();await page.getByText('共 1 项家庭任务',{exact:true}).waitFor();
  await page.getByText('发布任务',{exact:true}).click();await page.getByText('确定',{exact:true}).click();
  await page.getByText(/已发布/).waitFor();assert.equal(writes,4);
  await page.screenshot({path:'../.local-verification/family-tasks-mini.png',fullPage:true});
  allowed=false;await page.reload();await page.getByText('家庭任务未开启或无操作权限',{exact:true}).waitFor();
  assert.equal(await page.getByText('保留原配置的阅读',{exact:true}).count(),0);assert.equal(writes,4);
  console.log('家庭任务：创建、编辑保留配置、失败保留输入、发布和权限撤回通过');
 } finally {await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
