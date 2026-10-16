const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),ts=require('typescript');
const code=ts.transpileModule(fs.readFileSync(__dirname+'/transfer-application.ts','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2020}}).outputText;
const calls=[],uploads=[];class ApiError extends Error{constructor(statusCode,body){super(body.message);this.statusCode=statusCode;}}
const moduleValue={exports:{}};vm.runInNewContext(code,{exports:moduleValue.exports,require:()=>({apiUrl:p=>'/api/v1'+p,ApiError,request:(path,options)=>{calls.push({path,options});return Promise.resolve({});}}),uni:{uploadFile:options=>{uploads.push(options);}}});
const {transferApplicationApi:api,uploadTransferProof}=moduleValue.exports;
(async()=>{
 const context={accessToken:'fixture-token',studentId:'2110374231123427328',familyOrganizationId:'22',sourceInstitutionId:'21'};
 await api.options(context.accessToken,context.studentId,'22');assert.match(calls[0].path,/family-options/);assert.equal(calls[0].options.header['X-Organization-Id'],'22');
 await api.submit(context,{targetDescription:'destination',reason:'reason',effectiveDate:'2026-10-14',attachmentIds:['2110374231123427329']});assert.equal(calls[1].options.data.studentId,context.studentId);assert.equal(calls[1].options.data.attachmentIds[0],'2110374231123427329');
 await api.action('fixture-token','21','1','reject-blocked',3,'reason');assert.equal(calls[2].options.data.versionNo,3);assert.match(calls[2].path,/reject-blocked$/);
 const pending=uploadTransferProof(context,'fixture.pdf');const upload=uploads[0];assert.equal(upload.header.Authorization,'Bearer fixture-token');assert.equal(upload.header['X-Organization-Id'],'22');assert.equal(upload.formData.studentId,context.studentId);assert.equal(upload.timeout,15000);
 upload.success({statusCode:201,data:JSON.stringify({id:'2110374231123427329',originalName:'fixture.pdf',sizeBytes:10,contentType:'application/pdf'})});assert.equal((await pending).id,'2110374231123427329');
 const bad=uploadTransferProof(context,'fixture.pdf');uploads[1].success({statusCode:201,data:JSON.stringify({id:2110374231123427329,originalName:'fixture.pdf',sizeBytes:10})});await assert.rejects(bad,/不完整/);
 const denied=uploadTransferProof(context,'fixture.pdf');uploads[2].success({statusCode:403,data:JSON.stringify({message:'fixture denied'})});await assert.rejects(denied,e=>e.statusCode===403);
 const institution={accessToken:'fixture-token',studentId:context.studentId,sourceInstitutionId:'21'};
 await api.institutionOptions(institution.accessToken,institution.studentId,'21');assert.match(calls.at(-1).path,/institution-options/);assert.equal(calls.at(-1).options.header['X-Organization-Id'],'21');
 await api.submitInstitution(institution,{targetDescription:'destination',reason:'reason',effectiveDate:'2026-10-15',attachmentIds:[]});assert.match(calls.at(-1).path,/institution-submissions$/);assert.equal('familyOrganizationId' in calls.at(-1).options.data,false);
 const proofRequest=moduleValue.exports.uploadInstitutionTransferProof(institution,'fixture.pdf');const institutionUpload=uploads.at(-1);assert.match(institutionUpload.url,/institution-materials$/);assert.equal(institutionUpload.header['X-Organization-Id'],'21');assert.equal('familyOrganizationId' in institutionUpload.formData,false);institutionUpload.success({statusCode:201,data:JSON.stringify({id:'71',originalName:'fixture.pdf',sizeBytes:10})});assert.equal((await proofRequest).id,'71');
 console.log('PASS transfer API contract, selected family, exact IDs, upload errors');
})().catch(e=>{console.error(e);process.exitCode=1;});
