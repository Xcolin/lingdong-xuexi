const fs=require('fs'),path=require('path');
const dir=path.join(process.cwd(),'scripts');
const files=fs.readdirSync(dir).filter(f=>/^test-.*\.cjs$/.test(f));
let withLog=0,withSentinel=0,noLog=[],noAsync=0,hangRisk=[];
for(const f of files){
  const c=fs.readFileSync(path.join(dir,f),'utf8');
  if(c.includes("console.log('"))withLog++;
  if(c.includes('完成哨兵'))withSentinel++;
  if(!c.includes("console.log('"))noLog.push(f);
  if(!c.includes('(async()=>'))noAsync++;
  // 永不resolve的Promise（排除showModal桩）
  const lines=c.split('\n');
  lines.forEach((l,i)=>{ if(/new Promise\(\(\)=>\{\}\)/.test(l)&&!l.includes('showModal'))hangRisk.push(f+':'+(i+1)); });
}
console.log(JSON.stringify({total:files.length,withLog,withSentinel,noAsync,noLog,hangRisk},null,1));
