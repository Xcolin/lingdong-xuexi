const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const ts=require('typescript');
function model(){const exports={};vm.runInNewContext(ts.transpileModule(fs.readFileSync('src/models/exception-report-submission.ts','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS}}).outputText,{exports,Date,Math});return exports;}
test('accepted request with lost response reuses snapshot and key after reload; success and scope isolate',async()=>{
 const {prepareSubmission:p,confirmSubmission:c}=model();
 const input={classOrganizationId:'1',studentId:'2',exceptionType:'ATTENDANCE',content:'fact'};
 const first=p([],'sessionA:scopeA',input);let reject;
 const response=new Promise((_,r)=>{reject=r;});
 const accepted=new Set();let logicalCreates=0;
 async function server(request,response){if(!accepted.has(request.idempotencyKey)){accepted.add(request.idempotencyKey);logicalCreates++;}await response;}
 const pending=server(first.request,response);reject(new Error('accepted but response lost'));await assert.rejects(pending);
 const entries=JSON.parse(JSON.stringify(first.entries));
 const retry=p(entries,'sessionA:scopeA',input);
 assert.equal(retry.request.idempotencyKey,first.request.idempotencyKey);
 await server(retry.request,Promise.resolve());assert.equal(logicalCreates,1);
 input.content='edited';assert.equal(first.request.content,'fact');
 assert.notEqual(p(entries,'sessionA:scopeA',input).request.idempotencyKey,first.request.idempotencyKey);
 input.content='fact';
 assert.notEqual(p(entries,'sessionB:scopeA',input).request.idempotencyKey,first.request.idempotencyKey);
 assert.notEqual(p(entries,'sessionA:scopeB',input).request.idempotencyKey,first.request.idempotencyKey);
 assert.notEqual(p(c(entries,first.request.idempotencyKey),'sessionA:scopeA',input).request.idempotencyKey,first.request.idempotencyKey);
});
