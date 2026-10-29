const {spawnSync}=require('node:child_process'),path=require('node:path'),assert=require('node:assert/strict');
const result=spawnSync(process.execPath,['--test',path.join(__dirname,'../tests/teacher-home-stats.test.cjs')],{stdio:'inherit',timeout:30000});
if(result.error)throw result.error;
assert.equal(result.status,0,'教师统计受控异步测试必须全部完成');
console.log('完成哨兵：教师统计9项node:test正常终态，纳入统一scripts回归');
