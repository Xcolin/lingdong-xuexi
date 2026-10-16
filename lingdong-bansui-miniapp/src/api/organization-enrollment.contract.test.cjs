// Exercises the real API adapters against a captured request boundary.
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const ts=require('typescript');
const calls=[];
function load(name){const source=fs.readFileSync(path.join(__dirname,name+'.ts'),'utf8');const output=ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2020}}).outputText;const module={exports:{}};new Function('require','module','exports',output)(id=>{assert.equal(id,'./http');return {request:async(url,options)=>{calls.push({url,...options});return {};}};},module,module.exports);return module.exports;}
(async()=>{
 const {organizationMembersApi:m}=load('organization-members');
 const {institutionEnrollmentApi:e,enrollmentDate}=load('institution-enrollment');
 await m.list('mini-token','org/1','CANDIDATES','张 三',2);
 assert.equal(calls.at(-1).url,'/organizations/org%2F1/member-picker?side=CANDIDATES&group=SCHOOL&keyword=%E5%BC%A0%20%E4%B8%89&page=2&pageSize=20');
 await m.list('mini-token','org1','MEMBERS','',1);
 assert.equal(calls.at(-1).url,'/organizations/org1/members?side=MEMBERS&group=SCHOOL&keyword=&page=1&pageSize=20');
 for(const action of ['members:associate','members:remove','administrators:assign','administrators:revoke']){await m.action('mini-token','org1',action,['user1']);assert.equal(calls.at(-1).url,'/organizations/org1/'+action);assert.deepEqual(calls.at(-1).data,{userIds:['user1']});}
 await e.issue('mini-token','class1');assert.deepEqual(calls.at(-1).data,{classOrganizationId:'class1',validDays:7});
 await e.resolve('mini-token','real-code');assert.deepEqual(calls.at(-1).data,{code:'real-code'});
 await e.apply('mini-token','student-user1','family1','real-code');assert.deepEqual(calls.at(-1).data,{studentUserId:'student-user1',familyOrganizationId:'family1',code:'real-code'});
 await e.list('mini-token',{familyOrganizationId:'family1',status:'PENDING',institutionOrganizationId:''});assert.equal(calls.at(-1).url,'/institution-enrollment/requests?familyOrganizationId=family1&status=PENDING');
 await e.approve('mini-token','request1');assert.equal(calls.at(-1).url,'/institution-enrollment/requests/request1/approve');
 await e.reject('mini-token','request1','资料缺失');assert.deepEqual(calls.at(-1).data,{reason:'资料缺失'});
 for(const call of calls){assert.equal(call.header.Authorization,'Bearer mini-token');if(call.data)assert.equal(call.method,'POST');}
 assert.equal(enrollmentDate('2026-10-13T09:30:01.123'),'2026-10-13 09:30:01');
 const server=path.resolve(__dirname,'../../..','lingdong-bansui-server/src/main/java/com/lingdong/learning');
 const controller=fs.readFileSync(path.join(server,'student/web/InstitutionEnrollmentController.java'),'utf8');
 for(const route of ['/invitations','/invitations/resolve','/requests','/requests/{id}/approve','/requests/{id}/reject'])assert.ok(controller.includes('("'+route+'")'),route);
 assert.ok(controller.includes('List<InstitutionEnrollmentMapper.Request> list'));
 assert.ok(controller.includes('record EnrollmentRequest(Long studentUserId,String code,Long familyOrganizationId)'));
 assert.ok(controller.includes('record ReviewRequest(String reason)'));
 const service=fs.readFileSync(path.join(server,'student/application/InstitutionEnrollmentService.java'),'utf8');
 assert.ok(/record CreatedInvitation[\s\S]*?String inviteCode/.test(service));
 for(const permission of ['STUDENT_ENROLLMENT_INVITE_CREATE','STUDENT_ENROLLMENT_REQUEST_CREATE','STUDENT_ENROLLMENT_REVIEW'])assert.ok(service.includes('requirePermission(actor,"'+permission+'")'));
 const memberController=fs.readFileSync(path.join(server,'iam/web/OrganizationMembershipController.java'),'utf8');
 for(const action of ['members:associate','members:remove','administrators:assign','administrators:revoke'])assert.ok(memberController.includes('/'+action+'")'));
 assert.ok(memberController.includes('record MemberPageResponse(List<MemberResponse> items,int page,int pageSize,long total)'));
 assert.ok(memberController.includes('record PickerPageResponse(List<PickerResponse> items,int page,int pageSize,long total,boolean allowedUnbound)'));
 for(const page of ['institution-enrollment','organization-home','organization-classes']){const source=fs.readFileSync(path.join(__dirname,'../pages',page,page+'.vue'),'utf8');assert.ok(!/'ENROLLMENT_(REQUEST_CREATE|INVITE_CREATE|REQUEST_REVIEW)'/.test(source));}
 const memberPage=fs.readFileSync(path.join(__dirname,'../pages/organization-members/organization-members.vue'),'utf8');
 assert.ok(memberPage.includes("q?.identity==='parent'"));
 assert.ok(memberPage.includes('parent.value?getParentSession():getOrganizationSession()'));
 assert.ok(memberPage.includes("request<{permissionCodes:string[]}>('/auth/me'"));
 assert.ok(memberPage.includes("o.typeCode==='FAMILY'&&o.canEnterFamily:o.canEnterInstitution"));
 assert.ok(memberPage.includes("if(!can('ORG_MEMBER_READ'))throw"));
 assert.ok(!memberPage.includes('getOrganizationWorkbenchContext'));
 // Parent mode changes only identity selection; service scope and action permissions still apply.
 console.log('Passed: 12 real adapter request contracts, bearer headers, studentUserId, date format and enrollment permission prefixes.');
})().catch(error=>{console.error(error);process.exitCode=1;});
