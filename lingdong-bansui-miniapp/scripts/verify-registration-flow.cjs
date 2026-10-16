const assert=require('node:assert/strict');
const fs=require('node:fs');const ts=require('typescript');
const source=fs.readFileSync(require('node:path').join(__dirname,'../src/pages/public-registration/registration-flow.ts'),'utf8');
const compiled=ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.CommonJS}}).outputText;
const adapter={exports:{}};new Function('exports','module',compiled)(adapter.exports,adapter);
const {accountError,contactError,registrationPayload,advanceRegistrationStep}=adapter.exports;
const f={username:' parent_1 ',displayName:' Parent ',password:'Password123',confirmation:'Password123',channel:'EMAIL',destination:' parent@example.com ',code:'123456',accepted:true};
assert.equal(accountError(f),'');assert.equal(contactError(f),'');
assert.ok(accountError({...f,username:'.parent'}));assert.ok(accountError({...f,displayName:' '}));assert.ok(accountError({...f,password:'12345678'}));assert.ok(accountError({...f,confirmation:'Other123'}));
assert.ok(contactError({...f,destination:'bad'}));assert.ok(contactError({...f,code:'1234'}));assert.equal(contactError({...f,channel:'SMS',destination:'13800000000'}),'');
const payload=registrationPayload(f,'v1','device','name');
assert.deepEqual(payload,{username:'parent_1',displayName:'Parent',password:'Password123',passwordConfirmation:'Password123',channel:'EMAIL',destination:'parent@example.com',code:'123456',agreementAccepted:true,agreementVersion:'v1',clientType:'MINIAPP',deviceId:'device',deviceName:'name'});
assert.equal(registrationPayload({...f,channel:'SMS',destination:'13800000000'},'v1','device','name').channel,'SMS');
console.log('registration field validation and unchanged registration payload passed');

assert.equal(advanceRegistrationStep(1,f,false,true).step,2);assert.equal(advanceRegistrationStep(2,f,false,true).step,3);assert.equal(advanceRegistrationStep(1,f,true,true).step,1);assert.equal(advanceRegistrationStep(1,f,false,false).step,1);assert.equal(advanceRegistrationStep(2,{...f,code:'1234'},false,true).step,2);
