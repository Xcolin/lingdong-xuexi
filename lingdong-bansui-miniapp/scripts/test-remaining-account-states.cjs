const fs=require('fs'),vm=require('vm'),ts=require('typescript'),assert=require('node:assert/strict'),path=require('path');
process.chdir(path.resolve(__dirname,'..'));
const timeout=setTimeout(()=>{console.error('状态测试未完成');process.exit(1)},10000);
function setup(name,names,api={},caps={}){
 const hooks={},calls=[];let session={accessToken:'fixture-only'};
 const imports={vue:{ref:value=>({value}),computed:fn=>({get value(){return fn()}})},'@dcloudio/uni-app':Object.fromEntries(['onLoad','onShow','onHide','onShareAppMessage'].map(k=>[k,fn=>hooks[k]=fn])), '@/api/capability':{getMiniappCapabilities:async()=>caps},'@/session/parent-session':{getParentSession:()=>session,getOrCreateParentDeviceId:()=> 'device',getParentDeviceName:()=> 'fixture',saveParentSession:()=>calls.push('save')},'@/models/parent-invitation-qr':{validInvitationId:x=>/^[1-9]\d*$/.test(x),invitationRoute:id=>'/pages/parent-relationship-invitation/parent-relationship-invitation?invitationId='+id},'@/api/auth':{getParentAuthContext:async()=>({agreementVersion:'v1'})}};
 imports['@/api/http']={request:async()=>({permissionCodes:[]})};
 if(name==='parent-relationships'){
  const helper={};
  vm.runInNewContext(ts.transpileModule(fs.readFileSync('src/pages/parent-relationships/paged-section.ts','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2020}}).outputText,{exports:helper,require:key=>{assert.equal(key,'vue');return imports.vue;},Error});
  imports['./paged-section']=helper;
 }
 for(const key of ['parent-relationship','student-wechat-binding','parent-account-lifecycle'])imports['@/api/'+key]=api;
 const source=fs.readFileSync(`src/pages/${name}/${name}.vue`,'utf8');
 let script=source.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1];
 if(process.argv.includes('--mutate-eligibility') && name==='parent-cancel-form')script=script.replace("state.value?.cancellationStatus === 'NONE' && state.value.activeStudentRelationshipCount === 0",'true');
 const out={};const uni={reLaunch:o=>calls.push(o.url),redirectTo:o=>calls.push(o.url),navigateTo:o=>calls.push(o.url),showToast:o=>calls.push(o.title),showModal:o=>o.success({confirm:false})};
 vm.runInNewContext(ts.transpileModule(script+'\nexports.page={'+names+'}',{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2020}}).outputText,{exports:out,require:key=>{if(key.endsWith('.vue'))return {};assert.ok(key in imports,'未声明依赖 '+key);return imports[key]},uni,Error,Date,Promise});
 return {p:out.page,hooks,calls,session,uni,source,setSession:value=>session=value};
}
(async()=>{
 let writes=0;
 const lifecycle={getParentAccountLifecycle:async()=>({cancellationStatus:'NONE',activeStudentRelationshipCount:0}),issueParentCancellationCode:async()=>writes++,requestParentCancellation:async()=>writes++};
 const c=setup('parent-cancel-form','state,enabled,loading,errorMessage,reason,agreed,code,confirmation,canRequestCancellation,sendCode,submitCancellation,validForm',lifecycle,{parentAccountLifecycleEnabled:true});
 await c.hooks.onShow();assert.equal(c.p.canRequestCancellation.value,true);
 await c.p.submitCancellation();assert.equal(writes,0,'未填写表单不得提交');
 c.p.reason.value='OTHER';c.p.agreed.value=true;c.p.code.value='123456';c.p.confirmation.value='确认注销';
 for(const state of [{cancellationStatus:'NONE',activeStudentRelationshipCount:1},{cancellationStatus:'COOLING_OFF',activeStudentRelationshipCount:0},{cancellationStatus:'READY_FOR_FINALIZATION',activeStudentRelationshipCount:0},{cancellationStatus:'UNKNOWN',activeStudentRelationshipCount:0},{cancellationStatus:'NONE',activeStudentRelationshipCount:-1}]){
  c.p.state.value=state;assert.equal(c.p.canRequestCancellation.value,false);await c.p.sendCode();await c.p.submitCancellation();assert.equal(writes,0,'受阻状态不得调用写API');
 }
 c.p.state.value={cancellationStatus:'NONE',activeStudentRelationshipCount:0};assert.equal(c.p.validForm(),true);
 await c.p.submitCancellation();assert.equal(writes,1,'可提交状态必须调用真实契约');
 c.setSession({accessToken:'changed'});await c.p.submitCancellation();assert.equal(writes,1,'过期会话禁止写操作');
 const bound={studentId:'1',studentName:'小明',bound:true,boundAt:'2026-10-27',studentAccountMasked:'12****34'};
 let unbinds=0,reads=0;
 const wa={listStudentWechatBindings:async()=>{reads++;return [{...bound}]},unbindStudentWechat:async()=>unbinds++};
 const w=setup('parent-student-wechat','items,initialize,confirmUnbind,errorMessage',wa,{studentWechatAuthEnabled:true});
 await w.p.initialize();assert.equal(reads,1);assert.equal(w.p.items.value[0].bound,true);
 await w.p.confirmUnbind(w.p.items.value[0]);assert.equal(unbinds,0,'取消不写API');
 w.uni.showModal=o=>o.success({confirm:true});await w.p.confirmUnbind(w.p.items.value[0]);assert.equal(unbinds,1);assert.equal(w.p.items.value[0].bound,false);
 await w.p.confirmUnbind(w.p.items.value[0]);assert.equal(unbinds,1,'未绑定不可解绑');
 await w.p.initialize();wa.unbindStudentWechat=async()=>{throw Error('权限已收回')};
 await w.p.confirmUnbind(w.p.items.value[0]);assert.equal(w.p.items.value[0].bound,true,'服务端失败保留绑定状态');
 let modal;w.uni.showModal=o=>modal=o;const unbinding=w.p.confirmUnbind(w.p.items.value[0]);
 await w.p.confirmUnbind(w.p.items.value[0]);assert.ok(modal,'二次确认必须实际发生');
 w.setSession({accessToken:'replacement'});modal.success({confirm:true});await unbinding;assert.equal(unbinds,1,'确认期间会话切换不得解绑');
 w.setSession({accessToken:'fixture-only'});
 wa.listStudentWechatBindings=async()=>{throw Error('无权限')};await w.p.initialize();assert.equal(w.p.errorMessage.value,'无权限');assert.equal(w.p.items.value.length,0);
 const disabled=setup('parent-student-wechat','initialize,items',wa,{studentWechatAuthEnabled:false});await disabled.p.initialize();assert.equal(reads,2);assert.ok(disabled.calls.includes('/pages/parent-home/parent-home'));
 let release;wa.listStudentWechatBindings=()=>new Promise(r=>release=r);const pending=w.p.initialize();await new Promise(r=>setImmediate(r));assert.equal(typeof release,'function');w.hooks.onHide();release([bound]);await pending;assert.equal(w.p.items.value.length,0,'hide后旧列表不得回填');
 let accepts=0,rejects=0;
 const i=setup('parent-relationship-invitation','enabled,mobile,smsCode,agreementAccepted,acceptInvitation,rejectInvitation', {acceptParentRelationshipInvitation:async()=>{accepts++;return {session:{}}},rejectParentRelationshipInvitation:async()=>rejects++},{parentRelationshipManagementEnabled:true});
 await i.hooks.onLoad({invitationId:'123'});i.p.mobile.value='13800000000';i.p.smsCode.value='123456';await i.p.acceptInvitation();assert.equal(accepts,0,'未同意协议不得接受');i.p.agreementAccepted.value=true;await i.p.acceptInvitation();assert.equal(accepts,1);
 i.p.enabled.value=false;await i.p.rejectInvitation();await i.p.acceptInvitation();assert.equal(rejects,0);assert.equal(accepts,1,'停用函数级阻断');
 const r=setup('parent-relationships','enabled,loading,students,selectedIndex,openInvitation,invitationMode,issuedInvitation,openReceivedInvitation',{},{});r.p.enabled.value=true;r.p.loading.value=false;r.p.students.value=[{studentId:'1',relationshipRole:'SECONDARY_GUARDIAN'}];r.p.openInvitation('SECONDARY');assert.equal(r.p.invitationMode.value,null);r.p.students.value[0].relationshipRole='PRIMARY_GUARDIAN';r.p.openInvitation('SECONDARY');assert.equal(r.p.invitationMode.value,'SECONDARY');r.p.openReceivedInvitation('123');assert.ok(r.calls.some(x=>x.includes('invitationId=123')));
 r.p.issuedInvitation.value={invitationId:'456'};assert.match(r.hooks.onShareAppMessage().path,/invitationId=456$/);assert.match(r.source,/@tap="openReceivedInvitation\(inv.invitationId\)"/);assert.match(r.source,/发起陪伴邀请/);
 const invalid=setup('parent-relationship-invitation','errorMessage,enabled',{},{});await invalid.hooks.onLoad({invitationId:'bad'});assert.equal(invalid.p.enabled.value,false);assert.match(invalid.p.errorMessage.value,/无效/);
 const off=setup('parent-cancel-form','state,sendCode,submitCancellation',lifecycle,{parentAccountLifecycleEnabled:false});await off.hooks.onShow();assert.equal(off.p.state.value,null);await off.p.sendCode();await off.p.submitCancellation();assert.equal(writes,1,'关闭开关不读取资格或提交');
 // API契约独立执行，禁止用页面假数据代替真实路由和固定确认语句。
 const requests=[];for(const file of ['student-wechat-binding','parent-account-lifecycle','parent-relationship']){
  const e={};vm.runInNewContext(ts.transpileModule(fs.readFileSync('src/api/'+file+'.ts','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS}}).outputText,{exports:e,require:key=>{assert.equal(key,'./http');return {request:async(...args)=>requests.push(args)}}});
  if(file==='student-wechat-binding'){await e.listStudentWechatBindings('token');await e.unbindStudentWechat('token','101');}
  if(file==='parent-account-lifecycle')await e.requestParentCancellation('token','123456','确认注销','OTHER','说明');
  if(file==='parent-relationship'){await e.createSecondaryParentInvitation('101','13800000000','token');await e.acceptParentRelationshipInvitation('202',{mobile:'13800000000',smsCode:'123456'});}
 }
 assert.equal(requests[0][0],'/student-wechat-bindings');assert.equal(requests[1][0],'/students/101/wechat-unbindings');assert.equal(requests[1][1].data.confirmation,'确认解绑学生微信');
 assert.equal(requests[2][0],'/auth/parent-account-cancellations');assert.equal(requests[2][1].data.reason,'OTHER');assert.equal(requests[2][1].data.note,'说明');
 assert.equal(requests[3][0],'/students/101/secondary-parent-invitations');assert.equal(requests[4][0],'/parent-relationship-invitations/202/acceptance');
 for(const request of requests.slice(0,4))assert.equal(request[1].header.Authorization,'Bearer token');
 let invites=0;const outgoing=setup('parent-relationships','enabled,loading,students,openInvitation,targetMobile,submitInvitation,issuedInvitation', {createSecondaryParentInvitation:async(id,mobile,token)=>{invites++;assert.equal(id,'9');assert.equal(mobile,'13800000000');assert.equal(token,'fixture-only');return {invitationId:'999',maskedMobile:'138****0000',expiresAt:'2026-10-27T10:00:00'}}},{});
 outgoing.p.enabled.value=true;outgoing.p.loading.value=false;outgoing.p.students.value=[{studentId:'9',relationshipRole:'PRIMARY_GUARDIAN'}];outgoing.p.openInvitation('SECONDARY');outgoing.p.targetMobile.value='invalid';await outgoing.p.submitInvitation();assert.equal(invites,0);outgoing.p.targetMobile.value='13800000000';await outgoing.p.submitInvitation();assert.equal(invites,1);assert.equal(outgoing.p.issuedInvitation.value.invitationId,'999');outgoing.hooks.onHide();assert.equal(outgoing.p.issuedInvitation.value,null,'隐藏必须清除邀请凭据');
 assert.match(c.source,/<template v-else-if="canRequestCancellation">/,'提交表单必须严格按资格呈现');
 const mutant=require('node:child_process').spawnSync(process.execPath,[__filename,'--mutate-eligibility'],{encoding:'utf8',timeout:12000});assert.notEqual(mutant.status,0,'删除资格守卫后测试必须红');assert.match(mutant.stderr,/AssertionError/,'红测必须源于有效断言');
 clearTimeout(timeout);console.log('完成哨兵：remaining-account-states 严格VM与负例全部执行');
})().catch(e=>{clearTimeout(timeout);console.error(e);process.exitCode=1});

