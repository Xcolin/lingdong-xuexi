const fs=require('fs'),vm=require('vm'),ts=require('typescript'),assert=require('node:assert/strict');
const source=fs.readFileSync('src/models/transfer-proposal-draft.ts','utf8');const exportsValue={};
vm.runInNewContext(ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2020}}).outputText,{exports:exportsValue});
const {buildTransferSubmission:build}=exportsValue;
const input={targetIndex:0,reason:'  fixture reason  ',effectiveDate:'2026-10-14',attachmentIds:['2110374231123427328']};
assert.equal(build(input,'2026-10-14').reason,'fixture reason');assert.equal(build(input,'2026-10-14').attachmentIds[0],'2110374231123427328');
for(const patch of [{reason:' '},{reason:'字'.repeat(201)},{effectiveDate:'2026-02-30'},{effectiveDate:'2026-10-13'},{targetIndex:-1},{targetIndex:2},{attachmentIds:['1','1']},{attachmentIds:['1','2','3','4']},{attachmentIds:[2110374231123427328]},{attachmentIds:['0']}])assert.throws(()=>build({...input,...patch},'2026-10-14'));
assert.equal(build({...input,reason:'🌱'.repeat(200),effectiveDate:'2028-02-29'},'2026-10-14').reason.length,400);
console.log('PASS transfer draft exact IDs, real calendar date, codepoint limit, attachment bounds');
