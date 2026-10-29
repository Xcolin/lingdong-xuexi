const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'ld-completion-'));
const harness = path.join(__dirname, '.script-completion.cjs');
function execute(name, source) {
  const file = path.join(dir, name + '.cjs');
  fs.writeFileSync(file, source);
  return spawnSync(process.execPath, [harness, file], { encoding: 'utf8', timeout: 5000 });
}
try {
  const good = execute('正常异步', '(async()=>{await Promise.resolve();require("node:assert/strict").equal(2,2)})().catch(e=>{process.exitCode=1})');
  assert.equal(good.status, 0, good.stderr);
  assert.match(good.stdout, /LD_TEST_COMPLETE/);
  const pending = execute('悬空异步', '(async()=>{await new Promise(()=>{});throw Error("尾部不可跳过")})()');
  const legacy = spawnSync(process.execPath, [path.join(dir, '悬空异步.cjs')], { encoding: 'utf8', timeout: 5000 });
  assert.equal(legacy.status, 0, '反例须先证明普通 node 会提前退出0');
  assert.equal(pending.status, 1);
  assert.match(pending.stderr, /未完成异步入口/);
  assert.doesNotMatch(pending.stdout, /LD_TEST_COMPLETE/);
  const assigned = execute('变量异步', 'const task=(async()=>{await new Promise(()=>{})})()');
  assert.equal(assigned.status, 1);
  const discarded = execute('丢弃返回值', 'void (async()=>{await new Promise(()=>{})})()');
  assert.equal(discarded.status, 1);
  const failed = execute('尾部失败', '(async()=>{await Promise.resolve();throw Error("故意破坏守卫")})().catch(e=>{process.exitCode=1})');
  assert.equal(failed.status, 1);
  assert.doesNotMatch(failed.stdout, /LD_TEST_COMPLETE/);
  const early = execute('提前退出', 'process.exit(0)');
  assert.equal(early.status, 0);
  assert.doesNotMatch(early.stdout, /LD_TEST_COMPLETE/);
  const strict = execute('严格模式', '"use strict"; unintendedGlobal=1;');
  assert.equal(strict.status, 1, '执行器不能改变原测试的严格模式语义');
  assert.match(strict.stderr, /ReferenceError/);
  const main = execute('主入口', 'if(require.main===module) throw Error("主入口已执行");');
  assert.equal(main.status,1);
  assert.match(main.stderr,/主入口已执行/);
  console.log('完成哨兵：正常完成、悬空 Promise、变量入口、尾部失败及提前退出已验证');
} finally {
  fs.rmSync(dir, { recursive: true, force: true });
}
