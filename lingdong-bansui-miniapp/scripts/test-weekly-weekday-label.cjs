const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const ts = require('typescript');
const source = fs.readFileSync('src/pages/parent-weekly-reviews/parent-weekly-reviews.vue', 'utf8');
const declaration = source.match(/function weekday\(value:string\)\{[^\n]+\}/);
assert.ok(declaration, '必须测试页面实际日期标签函数');
const output = {};
vm.runInNewContext(ts.transpileModule(declaration[0] + '\nexports.weekday=weekday;', {
  compilerOptions: { module: ts.ModuleKind.CommonJS }
}).outputText, { exports: output });
for (const [date, label] of [
  ['2026-10-25', '周日'], ['2026-10-26', '周一'], ['2026-10-27', '周二'],
  ['2026-10-28', '周三'], ['2026-10-29', '周四'], ['2026-10-30', '周五'],
  ['2026-10-31', '周六'], ['2024-02-29', '周四'], ['2027-01-01', '周五']
]) assert.equal(output.weekday(date), label, date);
console.log('周趋势星期标签九个日期验证通过');
