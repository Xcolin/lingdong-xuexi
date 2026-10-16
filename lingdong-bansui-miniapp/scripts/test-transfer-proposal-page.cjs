const fs=require('fs'),vm=require('vm'),ts=require('typescript'),assert=require('node:assert/strict');
const sfc=fs.readFileSync('src/pages/transfer-proposal/transfer-proposal.vue','utf8'),script=sfc.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1];
const modelExports={};vm.runInNewContext(ts.transpileModule(fs.readFileSync('src/models/transfer-proposal-draft.ts','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2020}}).outputText,{exports:modelExports});
const optionsValue={studentId:'11',studentName:'fixture child',institutions:[{id:'21',name:'fixture school',typeCode:'SCHOOL',classNames:['fixture class']}]};
const flush=()=>new Promise(r=>setImmediate(r));
function setup(institution=false,permissions=['STUDENT_TRANSFER_REQUEST_CREATE','STUDENT_ORGANIZATION_MANAGE']){
 let selection={accessToken:'fixture-token',organizationId:'22',typeCode:'FAMILY'},session={accessToken:'fixture-token'};const hooks={},calls=[];
 const api={options:async()=>optionsValue,submit:async(c,data)=>{calls.push({c,data});throw new Error('fixture failure');},retire:async()=>{throw new Error('fixture retire failure');}};
 const exportsValue={};const imports={vue:{ref:value=>({value}),computed:fn=>({get value(){return fn();}})},'@dcloudio/uni-app':{onLoad:fn=>hooks.load=fn,onShow:fn=>hooks.show=fn,onHide:fn=>hooks.hide=fn,onUnload:fn=>hooks.unload=fn},'@/session/parent-session':{getParentSession:()=>session},'@/session/organization-session':{getOrganizationSession:()=>session},'@/api/parent-children':{parentChildrenApi:{me:async()=>({permissionCodes:permissions})}},'@/api/transfer-application':{transferApplicationApi:api,uploadTransferProof:async()=>{}},'@/models/transfer-proposal-draft':modelExports};
 const code=script+'\nexports.test={submit,load,removeProof,reason,proofs,options,application,submitting,uncertain,error};';
 vm.runInNewContext(ts.transpileModule(code,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2020}}).outputText,{exports:exportsValue,require:name=>imports[name]||{},uni:{getStorageSync:()=>selection,navigateBack(){},reLaunch(){}},console,Error});
 if(institution){selection={accessToken:'fixture-token',organizationId:'21',typeCode:'SCHOOL'};api.institutionOptions=async()=>optionsValue;api.submitInstitution=async(c,data)=>{calls.push({c,data});throw new Error('institution failure');};}
 hooks.load(institution?{studentId:'11',sourceId:'21',mode:'INSTITUTION'}:{studentId:'11',familyId:'22'});
 return {hooks,page:exportsValue.test,api,calls,setSelection:value=>selection=value,setSession:value=>session=value};
}
(async()=>{
 const failed=setup();failed.hooks.show();await flush();assert.equal(failed.page.options.value.studentName,'fixture child');failed.page.reason.value='fixture reason';failed.page.proofs.value=[{id:'41',originalName:'fixture.pdf',sizeBytes:10}];
 await failed.page.submit();assert.equal(failed.calls.length,1);assert.equal(failed.page.reason.value,'fixture reason');assert.equal(failed.page.proofs.value.length,1);assert.match(failed.page.error.value,/fixture failure/);
 await failed.page.removeProof(failed.page.proofs.value[0]);assert.equal(failed.page.proofs.value.length,1);
 const duplicate=setup();duplicate.hooks.show();await flush();duplicate.page.reason.value='fixture reason';let resolve;duplicate.api.submit=(c,data)=>{duplicate.calls.push({c,data});return new Promise(r=>resolve=r);};
 const first=duplicate.page.submit();await duplicate.page.submit();assert.equal(duplicate.calls.length,1);duplicate.hooks.hide();assert.equal(duplicate.page.uncertain.value,true);resolve({id:'1',status:'PENDING'});await first;assert.equal(duplicate.page.application.value,null);
 duplicate.hooks.show();await flush();assert.match(duplicate.page.error.value,/核对申请记录/);await duplicate.page.submit();assert.equal(duplicate.calls.length,1);
 const stale=setup();let resolveOptions;stale.api.options=()=>new Promise(r=>resolveOptions=r);stale.hooks.show();await flush();stale.setSelection({accessToken:'fixture-token',organizationId:'99',typeCode:'FAMILY'});resolveOptions(optionsValue);await flush();assert.equal(stale.page.options.value,null);
 const account=setup();account.hooks.show();await flush();account.page.proofs.value=[{id:'41',originalName:'private fixture.pdf',sizeBytes:10}];account.hooks.hide();account.setSession({accessToken:'other-token'});account.setSelection({accessToken:'other-token',organizationId:'22',typeCode:'FAMILY'});account.hooks.show();await flush();assert.equal(account.page.proofs.value.length,0);
 const institution=setup(true);institution.hooks.show();await flush();assert.equal(institution.page.options.value?.studentName,'fixture child');institution.page.reason.value='fixture reason';await institution.page.submit();assert.equal(institution.calls.length,1);assert.match(institution.page.error.value,/institution failure/);
 const deniedInstitution=setup(true,['STUDENT_TRANSFER_REQUEST_CREATE']);deniedInstitution.hooks.show();await flush();assert.equal(deniedInstitution.page.options.value,null);assert.match(deniedInstitution.page.error.value,/权限/);
 const switchedInstitution=setup(true);let late;switchedInstitution.api.institutionOptions=()=>new Promise(r=>late=r);switchedInstitution.hooks.show();await flush();switchedInstitution.setSelection({accessToken:'fixture-token',organizationId:'99',typeCode:'SCHOOL'});late(optionsValue);await flush();assert.equal(switchedInstitution.page.options.value,null);
 console.log('PASS actual transfer page double-submit, unknown result, stale family, account switch, failure retention');
})().catch(e=>{console.error(e);process.exitCode=1;});
