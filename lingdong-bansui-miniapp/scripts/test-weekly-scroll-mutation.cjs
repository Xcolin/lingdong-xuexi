// Mutation is in memory only: prove the race test rejects removal of the stale-response guard.
const assert=require('node:assert/strict');
const {spawnSync}=require('node:child_process');
const fixture='const result=await weeklyApi.list(token,studentId,page.value+1);if(!valid(n,token))return;';
const child=`
const fs=require('node:fs');
const original=fs.readFileSync;
fs.readFileSync=function(file,...args){
  const value=original.call(this,file,...args);
  if(String(file).replaceAll('\\\\','/').endsWith('parent-weekly-reviews/parent-weekly-reviews.vue')){
    if(typeof value!=='string'||!value.includes(${JSON.stringify(fixture)}))throw Error('mutation target missing');
    return value.replace(${JSON.stringify(fixture)},'const result=await weeklyApi.list(token,studentId,page.value+1);');
  }
  return value;
};
require('./scripts/test-parent-weekly-reviews-scroll-vm.cjs');`;
const result=spawnSync(process.execPath,['-e',child],{cwd:require('node:path').join(__dirname,'..'),encoding:'utf8',timeout:15000});
assert.equal(result.status,1,result.stderr);
assert.match(result.stderr,/AssertionError/);
assert.doesNotMatch(result.stderr,/mutation target missing/);
assert.doesNotMatch(result.stdout,/完成哨兵/);
console.log('完成哨兵：移除周复盘旧响应守卫后，受控竞态测试按预期失败；未改产品文件');
