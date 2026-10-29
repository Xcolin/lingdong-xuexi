const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');

// 复用项目编译器运行真实业务代码，不额外引入测试依赖（同 attendance.test.cjs 模式）。
function load(mocks = {}) {
  const file = path.join(__dirname, '../src/pages/family-tasks/family-tasks.vue');
  assert.ok(fs.existsSync(file), '尚未实现 family-tasks.vue');
  let source = fs.readFileSync(file, 'utf8');
  source = require('@vue/compiler-sfc').parse(source).descriptor.scriptSetup.content;
  source += '\nexport { load, students, total, tasks, studentFilterIndex, studentFilterChanged, typeFilterChanged, statusTab };';
  const output = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText;
  const exports = {};
  vm.runInNewContext(output, { exports, require: (name) => {
    assert.ok(name in mocks, `未配置依赖 ${name}`);
    return mocks[name];
  }, Date, Set, Error, Number, Math, JSON, Promise, Array, Object, String, Boolean, console, uni: {
    navigateTo: () => {}, showToast: () => {}, showModal: () => {}, reLaunch: () => {}, getStorageSync: () => ({}),
  } });
  return exports;
}

const children = [
  { id: '2113084663298785280', studentName: '孩子甲', relationshipRole: 'PRIMARY_GUARDIAN' },
  { id: '2112737972162920448', studentName: '孩子乙', relationshipRole: 'PRIMARY_GUARDIAN' },
  { id: '2113084539801698304', studentName: '孩子丙', relationshipRole: 'PRIMARY_GUARDIAN' },
];
const overviewCalls = [];
function makePage() {
  const familyTaskApi = {
    me: async () => ({}),
    listOverview: async (token, page, statusTab, taskType, studentId) => {
      overviewCalls.push({ page, statusTab, taskType, studentId });
      const total = studentId ? 25 : 39; // 孩子筛选后 25、全部孩子 39
      return { items: Array.from({ length: Math.min(20, total) }, (_, i) => ({ id: 't' + page + '-' + i, status: 'PUBLISHED' })), page, pageSize: 20, total };
    },
    students: async () => children,
  };
  const page = load({
    'vue': require('vue'),
    '@/components/PixelPageHeader.vue': {}, '@/components/PrototypeIcon.vue': {}, '@/components/AppTabBar.vue': {},
    '@/config/tabbar': { PARENT_TABS: [] },
    '@dcloudio/uni-app': { onShow: () => {}, onHide: () => {}, onUnload: () => {}, onReachBottom: () => {} },
    '@/api/family-task': { familyTaskApi },
    '@/models/family-task-draft': { canManageFamily: () => true, draftInput: () => ({}), recurrenceError: () => '', step1Error: () => '' },
    '@/session/parent-session': { getParentSession: () => ({ accessToken: 'token-1' }) },
    '@/api/capability': { getMiniappCapabilities: async () => ({ learningTaskManagementEnabled: true, taskWeekdayRecurrenceEnabled: false }) },
    '@/api/http': { ApiError: class ApiError extends Error {} },
  });
  return page;
}

const waitFor = async (predicate, timeoutMs = 3000) => {
  for (let i = 0; i < timeoutMs / 20; i++) { if (predicate()) return true; await new Promise(r => setTimeout(r, 20)); }
  return predicate();
};

test('切换孩子筛选后 load 以该学生 ID 重新请求并收窄 total（不退化为全部孩子）', async () => {
  overviewCalls.length = 0;
  const page = makePage();
  await page.load(1);
  assert.equal(page.total.value, 39, '初始全部孩子 total=39');
  assert.equal(page.students.value.length, 3, '学生选项已加载');
  // 模拟 picker change：选择第 1 个孩子（页面内 load 为 fire-and-forget，轮询等待完成）
  await page.studentFilterChanged({ detail: { value: '1' } });
  assert.ok(await waitFor(() => page.total.value === 25), 'load 应完成并收窄 total=25');
  const last = overviewCalls[overviewCalls.length - 1];
  assert.equal(last.studentId, children[0].id, '切换孩子后请求应携带该学生 ID（当前 bug：clear() 先清空 students 导致恒为 null）');
  assert.equal(page.total.value, 25, 'total 应收窄为孩子口径 25');
});

test('切换孩子后重置回第 1 页并保留筛选，再切回全部孩子恢复全量', async () => {
  overviewCalls.length = 0;
  const page = makePage();
  await page.load(1);
  await page.studentFilterChanged({ detail: { value: '2' } });
  assert.ok(await waitFor(() => page.total.value === 25), '第 2 个孩子筛选 load 完成');
  let last = overviewCalls[overviewCalls.length - 1];
  assert.equal(last.studentId, children[1].id, '第 2 个孩子的 ID 应传给 family-overview');
  assert.equal(last.page, 1, '切换筛选后回到第 1 页');
  await page.studentFilterChanged({ detail: { value: '0' } });
  assert.ok(await waitFor(() => page.total.value === 39), '切回全部孩子 load 完成');
  last = overviewCalls[overviewCalls.length - 1];
  assert.equal(last.studentId, null, '切回全部孩子后 studentId 应为空');
  assert.equal(page.total.value, 39, 'total 恢复全部孩子口径 39');
});
