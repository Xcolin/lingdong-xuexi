// 全量回归 runner：逐个执行 test-*.cjs，记录退出码与完成哨兵，超时按失败处理
const fs=require('fs'),path=require('path'),{spawnSync}=require('child_process');
const dir=__dirname;
const files=fs.readdirSync(dir).filter(f=>/^test-.*\.cjs$/.test(f)).sort();
const results=[];
for(const f of files){
  const r=spawnSync(process.execPath,[path.join(dir,'.script-completion.cjs'),path.join(dir,f)],{cwd:path.join(dir,'..'),encoding:'utf8',timeout:30000});
  const out=(r.stdout||'')+'\n'+(r.stderr||'');
  const exit=r.status===null?'TIMEOUT/ERR':r.status;
  const sentinelOk=out.split(/\r?\n/).includes(`LD_TEST_COMPLETE ${f}`);
  results.push({file:f,exit,sentinelOk});
  console.log(`${exit===0&&sentinelOk?'PASS':'FAIL'} exit=${exit} sentinel=${sentinelOk?'y':'n'} ${f}`);
  if(exit!==0||!sentinelOk)console.log(out.split('\n').filter(l=>l.trim()).slice(-8).join('\n'));
}
const bad=results.filter(r=>r.exit!==0||!r.sentinelOk);
console.log(JSON.stringify({total:results.length,failed:bad.length,bad:bad.map(b=>b.file)},null,1));
process.exitCode=bad.length?1:0;
