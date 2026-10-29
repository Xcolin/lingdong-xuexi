const fs = require('node:fs'), vm = require('node:vm'), ts = require('typescript'), assert = require('node:assert/strict');
const exportsHelper = {};
const code = ts.transpileModule(fs.readFileSync('src/pages/task-detail/task-detail-presentation.ts', 'utf8'), { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText;
vm.runInNewContext(code, { exports: exportsHelper });
const { isCompactTaskDetail, studentTodayTaskUrl } = exportsHelper;
assert.equal(isCompactTaskDetail('compact', 'IN_PROGRESS'), true);
for (const state of ['PENDING_CLAIM', 'PAUSED', 'NEEDS_IMPROVEMENT', 'PENDING_REVIEW', 'COMPLETED', 'EXEMPT', undefined]) assert.equal(isCompactTaskDetail('compact', state), false);
for (const layout of [undefined, '', 'full', ['compact']]) assert.equal(isCompactTaskDetail(layout, 'IN_PROGRESS'), false);
assert.equal(studentTodayTaskUrl('任务 &/1'), '/pages/task-detail/task-detail?id=%E4%BB%BB%E5%8A%A1%20%26%2F1&layout=compact');
const today = fs.readFileSync('src/components/StudentTodayTasks.vue', 'utf8');
assert.match(today, /url: studentTodayTaskUrl\(id\)/);
const detail = fs.readFileSync('src/pages/task-detail/task-detail.vue', 'utf8');
assert.match(detail, /isCompactTaskDetail\(requestedLayout.value, task.value\?\.effectiveStatus\)/);
assert.match(detail, /requestedLayout.value = options\?\.layout/);
assert.match(detail, /@tap="showFullDetails = true"/);
assert.match(detail, /v-if="!compactView \|\| showMoreActions"/);
assert.match(detail, /maxlength="1000"/);
assert.match(detail, /pendingImages.length < 3/);
const hooks = {}, api = {};
const source = detail.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1];
const out = {};
vm.runInNewContext(ts.transpileModule(source + '\nexports.test = { requestedLayout, task, compactView, showFullDetails, showMoreActions, checkInContent, pendingImages };', { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText, {
  exports: out,
  require: key => key === 'vue' ? { ref: value => ({ value }), computed: fn => ({ get value() { return fn(); } }) }
    : key === '@dcloudio/uni-app' ? { onLoad: fn => hooks.load = fn, onShow: () => {}, onHide: () => {}, onUnload: () => {} }
    : key === './task-detail-presentation' ? exportsHelper : api
});
const state = out.test;
hooks.load({ id: '真实任务', layout: 'compact' });
state.task.value = { effectiveStatus: 'IN_PROGRESS' };
assert.equal(state.compactView.value, true);
state.checkInContent.value = '已有记录';
const images = [{ localId: '图片', status: 'UPLOADED', fileId: '附件' }];
state.pendingImages.value = images;
state.showMoreActions.value = true;
assert.equal(state.compactView.value, true);
state.showFullDetails.value = true;
assert.equal(state.compactView.value, false);
assert.equal(state.checkInContent.value, '已有记录');
assert.equal(state.pendingImages.value, images);
state.showFullDetails.value = false;
for (const status of ['PAUSED', 'PENDING_REVIEW', 'COMPLETED', 'NEEDS_IMPROVEMENT']) {
  state.task.value = { effectiveStatus: status };
  assert.equal(state.compactView.value, false);
}
state.task.value = { effectiveStatus: 'IN_PROGRESS' };
hooks.load({ id: '旧列表任务' });
assert.equal(state.compactView.value, false);
const todayHooks = {}, todayExports = {}, navigations = [];
let token = '当前会话', permissions = ['TASK_ASSIGNMENT_READ_SELF'], readCalls = 0, failRead = false;
let pendingRead = null, readStarted = null;
function deferred() {
  let resolve;
  const promise = new Promise(done => { resolve = done; });
  return { promise, resolve };
}
const todayModules = {
  vue: { ref: value => ({ value }), computed: fn => ({ get value() { return fn(); } }) },
  '@dcloudio/uni-app': { onShow: fn => todayHooks.show = fn, onHide: fn => todayHooks.hide = fn, onUnload: () => {} },
  '@/session/student-session': { getStudentSession: () => ({ accessToken: token }) },
  '@/api/capability': { getMiniappCapabilities: async () => ({ learningTaskManagementEnabled: true }) },
  '@/api/http': { request: async () => ({ clientType: 'MINIAPP', roleCodes: ['STUDENT'], permissionCodes: permissions }) },
  '@/api/learning-task': { listStudentTaskAssignments: async query => {
    readCalls++;
    if (failRead) throw new Error('真实查询错误');
    assert.equal(query.pageSize, 50);
    if (pendingRead) {
      readStarted.resolve();
      return pendingRead.promise;
    }
    return { total: 2, items: [{ id: String(query.page), effectiveStatus: 'IN_PROGRESS', title: '任务' }] };
  } },
  '@/pages/task-detail/task-detail-presentation': exportsHelper
};
vm.runInNewContext(ts.transpileModule(today.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1] + '\nexports.test = { open, tasks, total, allowed };', { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText, { exports: todayExports, require: key => todayModules[key] || {}, uni: { navigateTo: args => navigations.push(args.url) } });
const timeout = setTimeout(() => { console.error('紧凑任务视图测试超过10秒，异步入口未完成'); process.exit(1); }, 10000);
(async () => {
  const home = todayExports.test;
  await todayHooks.show();
  assert.equal(home.total.value, 2);
  assert.equal(readCalls, 2);
  home.open('1');
  assert.equal(navigations[0], studentTodayTaskUrl('1'));
  todayHooks.hide();
  home.open('1');
  assert.equal(navigations.length, 1);
  assert.equal(home.total.value, 0);
  failRead = true;
  await todayHooks.show();
  assert.equal(home.tasks.value.length, 0);
  assert.equal(home.total.value, 0);
  assert.equal(home.allowed.value, false);
  failRead = false;
  const beforeDenied = readCalls;
  permissions = [];
  await todayHooks.show();
  assert.equal(home.allowed.value, false);
  assert.equal(readCalls, beforeDenied);
  assert.match(today, /tasks.slice\(0, 3\)/);
  permissions = ['TASK_ASSIGNMENT_READ_SELF'];
  pendingRead = deferred();
  readStarted = deferred();
  const inFlight = todayHooks.show();
  await readStarted.promise;
  todayHooks.hide();
  pendingRead.resolve({ total: 1, items: [{ id: '迟到任务', effectiveStatus: 'IN_PROGRESS', title: '旧读取' }] });
  await inFlight;
  assert.equal(home.tasks.value.length, 0);
  assert.equal(home.total.value, 0);
  assert.equal(home.allowed.value, false);
  home.open('迟到任务');
  assert.equal(navigations.length, 1);
  console.log('完成哨兵：紧凑状态和首页URL、就地详情数据保留、分页失败清空权限与读取中隐藏守卫通过');
})().catch(error => { console.error(error); process.exitCode = 1; }).finally(() => clearTimeout(timeout));
