const {spawnSync}=require('node:child_process'),path=require('node:path'),assert=require('node:assert/strict');
const result=spawnSync(process.execPath,['--test',path.join(__dirname,'../tests/exception-report-submission.test.cjs')],{stdio:'inherit',timeout:30000});
if(result.error)throw result.error;
assert.equal(result.status,0,'报备幂等模型测试必须正常完成');
console.log('完成哨兵：报备幂等模型node:test正常终态，纳入统一scripts回归');
