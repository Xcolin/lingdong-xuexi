const fs=require('node:fs'),vm=require('node:vm'),ts=require('typescript'),assert=require('node:assert/strict');
const text=fs.readFileSync('src/pages/account-settings/account-settings.vue','utf8'),source=text.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1],out={};
const hooks={onShow(){},onLoad(){},onHide(){},onUnload(){}};
vm.runInNewContext(ts.transpileModule(source+'\nexports.state={newPassword,strengthScore,strengthLabel,strengthSegment};',{compilerOptions:{module:ts.ModuleKind.CommonJS}}).outputText,{exports:out,require:key=>key==='vue'?require('vue'):key==='@dcloudio/uni-app'?hooks:{},uni:{getStorageSync:()=>undefined}});
const s=out.state;
for(const [password,score,label]of [['',0,'请输入新密码'],['abc',1,'弱'],['abc12345',2,'中'],['abc123456789',3,'较强'],['abcdef1234567890',4,'强'],['abcdef123!',0,'不符合规则']]){s.newPassword.value=password;assert.equal(s.strengthScore.value,score);assert.equal(s.strengthLabel.value,label);const segments=Array.from({length:4},(_,i)=>s.strengthSegment(i+1));assert.equal(segments.filter(x=>x!=='s0').length,score,'强度段数必须对应分数，不得全部同时染色');}
assert.match(text,/:class="strengthSegment\(n\)"/);
console.log('完成哨兵：密码强度分段递增，非法字符不标强，原8至20位字母数字规则保持');
