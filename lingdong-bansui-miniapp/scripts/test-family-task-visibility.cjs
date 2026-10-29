const fs=require('node:fs'),vm=require('node:vm'),ts=require('typescript'),assert=require('node:assert/strict');
const out={};vm.runInNewContext(ts.transpileModule(fs.readFileSync('src/models/family-task-visibility.ts','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS}}).outputText,{exports:out});
const {familyTaskVisibilityAvailable,selectFamilyTaskVisibility,familyTaskVisibilitySummary}=out;
assert.equal(familyTaskVisibilitySummary('FAMILY_ONLY'),'仅家庭（按家庭关联与已授予权限访问）');
for(const value of ['FAMILY_CLASS_PARTNER','FAMILY_SCHOOL_PARTNER','CUSTOM'])assert.equal(familyTaskVisibilitySummary(value),'家庭关联与已授予权限（保留原范围说明）');
assert.equal(familyTaskVisibilitySummary(null),'按家庭关联与已授予权限访问');
const draft={visibility:'FAMILY_ONLY',studentIds:['当前家庭孩子']};
for(const value of ['FAMILY_CLASS_PARTNER','FAMILY_SCHOOL_PARTNER','CUSTOM','其他学校',null,undefined]){assert.equal(familyTaskVisibilityAvailable(value),false);assert.equal(selectFamilyTaskVisibility(draft,value),false);assert.equal(draft.visibility,'FAMILY_ONLY','未接通共享范围不能伪装成已选择');assert.deepEqual(draft.studentIds,['当前家庭孩子']);}
const existing={visibility:'CUSTOM'};assert.equal(selectFamilyTaskVisibility(existing,'FAMILY_SCHOOL_PARTNER'),false);assert.equal(existing.visibility,'CUSTOM','拒绝非法选择不能静默重写原任务范围说明');assert.equal(selectFamilyTaskVisibility(existing,'FAMILY_ONLY'),true);assert.equal(existing.visibility,'FAMILY_ONLY');
console.log('完成哨兵：家庭任务只可选择已实现范围，未接通共享不能修改草稿，旧范围不被静默覆盖');
