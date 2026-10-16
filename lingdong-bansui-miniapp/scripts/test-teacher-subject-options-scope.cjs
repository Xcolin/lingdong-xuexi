const fs=require('fs'),vm=require('vm'),ts=require('typescript'),assert=require('node:assert/strict'),out={},pending=[];let selection={accessToken:'t',organizationId:'21'};
const source=fs.readFileSync('src/pages/organization-teachers/organization-teachers.vue','utf8').match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1];
const imports={vue:{ref:v=>({value:v}),computed:f=>({get value(){return f()}})},'@dcloudio/uni-app':{onShow:()=>{},onHide:()=>{},onUnload:()=>{}},'@/api/teacher-management':{listTeacherSubjectOptions:()=>new Promise((resolve,reject)=>pending.push({resolve,reject}))},'@/session/organization-session':{getOrganizationSession:()=>({accessToken:'t'})},'@/api/http':{ApiError:class extends Error{}}};
vm.runInNewContext(ts.transpileModule(source+'\nexports.page={loadSubjectOptions,hideTeacherPage,subjectOptions,subjectOptionsError};',{compilerOptions:{module:ts.ModuleKind.CommonJS}}).outputText,{exports:out,require:n=>imports[n]||{},uni:{getStorageSync:()=>selection},Error});
(async()=>{
 const p=out.page;const stale=p.loadSubjectOptions('t');selection={accessToken:'t',organizationId:'22'};pending[0].resolve([{code:'STALE',name:'旧组织'}]);await stale;assert.equal(p.subjectOptions.value.length,0);
 const hidden=p.loadSubjectOptions('t');p.hideTeacherPage();pending[1].reject(new Error('late'));await hidden;assert.equal(p.subjectOptionsError.value,'');
 console.log('PASS subject options ignore switched organization and hidden-page failures');
})().catch(e=>{console.error(e);process.exitCode=1});
