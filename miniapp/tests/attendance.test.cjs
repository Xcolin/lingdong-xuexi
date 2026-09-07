const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');

// 复用项目编译器运行真实业务代码，不额外引入测试依赖。
function load(relative, mocks = {}, globals = {}) {
  const file = path.join(__dirname, '../src', relative);
  assert.ok(fs.existsSync(file), `尚未实现 ${relative}`);
  let source = fs.readFileSync(file, 'utf8');
  if (relative.endsWith('.vue')) {
    source = require('@vue/compiler-sfc').parse(source).descriptor.scriptSetup.content;
    source += '\nexport { records, drafts, detail, enabled, canRecord, classes, recordClasses, recordClassId, recordDate, rosterLoaded, mode, filters, page, total, refresh, loadRoster, submit, showDetail, queryRecords };';
  }
  const output = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText;
  const exports = {};
  vm.runInNewContext(output, { exports, require: (name) => {
    assert.ok(name in mocks, `未配置依赖 ${name}`);
    return mocks[name];
  }, Date, Set, Error, ...globals });
  return exports;
}
const plain = (value) => JSON.parse(JSON.stringify(value));
const model = () => load('pages/attendance/model.ts');
const studentId = '9223372036854775801';
const existing = { studentId, studentName: '小明', record: { versionNo: 7, status: 'LATE', checkinTime: '09:01:27', checkoutTime: null } };

test('新名单不默认正常、不默认选中，已有记录保留秒精度和版本', () => {
  const rows = model().createAttendanceDrafts([{ studentId, studentName: '小明', record: null }, existing]);
  assert.equal(rows[0].status, '');
  assert.equal(rows[0].selected, false);
  assert.equal(rows[0].versionNo, null);
  assert.equal(rows[1].checkinTime, '09:01:27');
  assert.equal(rows[1].versionNo, 7);
  assert.equal(rows[1].selected, false);
});

test('原子载荷仅包含明确选择的学生，ID不转数值且更正带版本', () => {
  const m = model();
  const rows = m.createAttendanceDrafts([existing, { studentId: '2', studentName: '小红', record: null }]);
  rows[0].selected = true;
  const result = m.buildAttendanceBatch('9223372036854775802', '2026-09-06', rows, '2026-09-07');
  assert.deepEqual(plain(result), { classOrganizationId: '9223372036854775802', attendanceDate: '2026-09-06', items: [
    { studentId, status: 'LATE', checkinTime: '09:01:27', checkoutTime: null, versionNo: 7 }
  ] });
});

test('批量限制1至100，拒绝重复学生、未选择状态、未来日期和非法时间', () => {
  const m = model();
  const valid = { ...m.createAttendanceDrafts([existing])[0], selected: true };
  const build = (rows, date = '2026-09-07') => m.buildAttendanceBatch('1', date, rows, '2026-09-07');
  assert.throws(() => build([]), /1.*100/);
  assert.throws(() => build(Array.from({ length: 101 }, (_, i) => ({ ...valid, studentId: String(i) }))), /1.*100/);
  assert.equal(build(Array.from({ length: 100 }, (_, i) => ({ ...valid, studentId: String(i) }))).items.length, 100);
  assert.throws(() => build([valid, valid]), /重复/);
  assert.throws(() => build([{ ...valid, status: '' }]), /状态/);
  assert.throws(() => build([valid], '2026-09-08'), /未来/);
  assert.throws(() => build([{ ...valid, status: 'ABSENT' }]), /时间/);
  assert.throws(() => build([{ ...valid, checkinTime: '25:00:00' }]), /时间/);
  assert.throws(() => build([{ ...valid, checkoutTime: '08:00:00' }]), /签到/);
  assert.equal(build([{ ...valid, checkinTime: '09:01' }]).items[0].checkinTime, '09:01:00');
});

test('动态权限支持自定义角色；家长学生只读；审核员和停用状态不可读', () => {
  const m = model();
  const user = { roleCodes: ['CUSTOM_ATTENDANCE'], permissionCodes: ['ATTENDANCE_READ', 'ATTENDANCE_RECORD'] };
  assert.deepEqual(plain(m.attendancePermissions(true, user, 'organization')), { read: true, record: true });
  for (const identity of ['parent', 'student']) {
    assert.equal(m.attendancePermissions(true, user, identity).record, false);
    assert.equal(m.attendancePermissions(true, { ...user, roleCodes: [identity.toUpperCase()] }, 'organization').record, false);
  }
  assert.equal(m.attendancePermissions(true, { ...user, roleCodes: ['SYSTEM_AUDITOR'] }, 'organization').read, false);
  assert.equal(m.attendancePermissions(false, user, 'teacher').read, false);
  assert.equal(m.attendancePermissions(true, { ...user, permissionCodes: [] }, 'teacher').read, false);
});

test('上海业务日期不依赖设备时区', () => {
  assert.equal(model().attendanceToday(new Date('2026-09-06T16:00:00Z')), '2026-09-07');
});

test('API精确路径、筛选参数、显式身份令牌和原子POST', async () => {
  const calls = [];
  const api = load('api/attendance.ts', { './http': { request: async (...args) => { calls.push(args); return []; } } });
  await api.listAttendanceRecords('parent-token', { classOrganizationId: studentId, studentId, keyword: '小 明', status: 'LEAVE', dateFrom: '2026-09-01', dateTo: '2026-09-07', page: 2, pageSize: 20 });
  const url = new URL(calls[0][0], 'https://local');
  assert.equal(url.pathname, '/attendance-records');
  assert.deepEqual(Object.fromEntries(url.searchParams), { classOrganizationId: studentId, studentId, keyword: '小 明', status: 'LEAVE', dateFrom: '2026-09-01', dateTo: '2026-09-07', page: '2', pageSize: '20' });
  assert.equal(calls[0][1].header.Authorization, 'Bearer parent-token');
  await api.listAttendanceClasses('t');
  assert.equal(calls[1][0], '/attendance-records/class-options');
  await api.listAttendanceClasses('t', true);
  assert.equal(calls[2][0], '/attendance-records/class-options?operational=true');
  await api.getAttendanceRoster('t', studentId, '2026-09-07');
  assert.equal(calls[3][0], `/attendance-records/roster?classOrganizationId=${studentId}&attendanceDate=2026-09-07`);
  const batch = { classOrganizationId: studentId, attendanceDate: '2026-09-07', items: [] };
  await api.submitAttendanceBatch('t', batch);
  assert.equal(calls[4][0], '/attendance-records/batch');
  assert.equal(calls[4][1].method, 'POST');
  assert.equal(calls[4][1].data, batch);
  await api.getAttendanceDetail('t', studentId);
  assert.equal(calls[5][0], `/attendance-records/${studentId}`);
  await api.getAttendanceUser('t');
  assert.equal(calls[6][0], '/auth/me');
});

function pageHarness(identity = 'teacher') {
  const hooks = {};
  const state = { enabled: true, roles: ['TEACHER'], permissions: ['ATTENDANCE_READ', 'ATTENDANCE_RECORD'], calls: [], redirects: [], batches: [], roster: [existing] };
  class ApiError extends Error { constructor(statusCode, code) { super(code); this.statusCode = statusCode; this.code = code; } }
  const api = {
    getAttendanceUser: async () => ({ roleCodes: state.roles, permissionCodes: state.permissions }),
    listAttendanceClasses: async (token, operational) => { state.calls.push(['classes', token, operational]); return [{ classOrganizationId: 'c1', className: '一班' }]; },
    listAttendanceRecords: async (token, filters) => {
      state.calls.push(['list', token, plain(filters)]);
      return state.listPromise || { items: [{ id: studentId, studentName: '小明' }], page: filters.page, pageSize: 20, total: 41 };
    },
    getAttendanceRoster: async (...args) => { state.calls.push(['roster', ...args]); return state.roster; },
    getAttendanceDetail: async () => ({ record: { id: studentId }, actions: [{ id: 'a1', actionType: 'CREATE' }] }),
    submitAttendanceBatch: async (token, batch) => { state.batches.push(plain(batch)); if (state.submitError) throw state.submitError; return []; }
  };
  const mocks = {
    vue: require('vue'), '@dcloudio/uni-app': Object.fromEntries(['onLoad', 'onShow', 'onHide', 'onUnload', 'onPullDownRefresh'].map((name) => [name, (fn) => { hooks[name] = fn; }])),
    '@/api/capability': { getMiniappCapabilities: async () => ({ attendanceManagementEnabled: state.enabled }) },
    '@/api/attendance': api, '@/api/http': { ApiError }, './model': model(),
    '@/session/organization-session': { getOrganizationSession: () => ({ accessToken: 'organization-token' }) },
    '@/session/parent-session': { getParentSession: () => ({ accessToken: 'parent-token' }) },
    '@/session/student-session': { getStudentSession: () => ({ accessToken: 'student-token' }) }
  };
  const page = load('pages/attendance/attendance.vue', mocks, { uni: {
    showToast: () => {}, reLaunch: async (value) => state.redirects.push(value.url),
    redirectTo: async (value) => state.redirects.push(value.url), stopPullDownRefresh: () => {}
  } });
  hooks.onLoad({ identity });
  return { page, state, hooks, ApiError };
}

test('进入和恢复重查开关，关闭清空列表名单详情且退出角色首页', async () => {
  const { page, state, hooks } = pageHarness();
  await hooks.onShow();
  assert.equal(page.records.value.length, 1);
  await page.showDetail(studentId);
  assert.equal(page.detail.value.actions[0].id, 'a1');
  state.enabled = false;
  await hooks.onShow();
  assert.equal(page.records.value.length, 0);
  assert.equal(page.drafts.value.length, 0);
  assert.equal(page.detail.value, null);
  assert.equal(page.enabled.value, false);
  assert.equal(state.redirects.at(-1), '/pages/teacher-home/teacher-home');
});

test('家长学生只读且使用独立会话，不请求写名单和有效班级', async () => {
  for (const identity of ['parent', 'student']) {
    const { page, state, hooks } = pageHarness(identity);
    await hooks.onShow();
    assert.equal(page.canRecord.value, false);
    await page.loadRoster();
    await page.submit();
    assert.equal(state.batches.length, 0);
    assert.equal(state.calls.some((call) => call[0] === 'roster' || (call[0] === 'classes' && call[2] === true)), false);
    assert.equal(state.calls.find((call) => call[0] === 'list')[1], `${identity}-token`);
  }
});

test('分页透传日期班级状态，点名一次原子提交并携带原版本', async () => {
  const { page, state, hooks } = pageHarness();
  await hooks.onShow();
  page.filters.value = { classOrganizationId: 'c1', dateFrom: '2026-09-01', dateTo: '2026-09-07', status: 'LATE', keyword: '' };
  await page.queryRecords(2);
  assert.equal(state.calls.at(-1)[2].page, 2);
  assert.equal(state.calls.at(-1)[2].status, 'LATE');
  page.recordClassId.value = 'c1';
  page.recordDate.value = '2026-09-06';
  await page.loadRoster();
  page.drafts.value[0].selected = true;
  await page.submit();
  assert.equal(state.batches.length, 1);
  assert.equal(state.batches[0].items[0].versionNo, 7);
  assert.equal(state.batches[0].items[0].studentId, studentId);
});

test('隐藏页面使未完成请求失效，旧响应不能恢复已清除数据', async () => {
  const { page, state, hooks } = pageHarness();
  await hooks.onShow();
  let resolve;
  state.listPromise = new Promise((done) => { resolve = done; });
  const pending = page.queryRecords(2);
  hooks.onHide();
  resolve({ items: [{ id: 'stale' }], page: 2, total: 1, pageSize: 20 });
  await pending;
  assert.equal(page.records.value.length, 0);
  assert.equal(page.enabled.value, false);
});

test('提交前重查授权，撤回登记权限后不发送POST', async () => {
  const { page, state, hooks } = pageHarness();
  await hooks.onShow();
  page.recordClassId.value = 'c1';
  await page.loadRoster();
  page.drafts.value[0].selected = true;
  state.permissions = ['ATTENDANCE_READ'];
  await page.submit();
  assert.equal(state.batches.length, 0);
  assert.equal(page.drafts.value.length, 0);
  assert.equal(page.canRecord.value, false);
});

test('四类首页入口进入、恢复和点击时重查能力与动态权限，失败隐藏', async () => {
  for (const identity of ['teacher', 'organization', 'parent', 'student']) {
    const hooks = {};
    const state = { enabled: true, fail: false, permissions: ['ATTENDANCE_READ'], navigated: [] };
    const { useAttendanceEntry } = load('pages/attendance/use-attendance-entry.ts', {
      vue: require('vue'),
      '@dcloudio/uni-app': { onShow: (fn) => { hooks.show = fn; }, onHide: (fn) => { hooks.hide = fn; }, onUnload: (fn) => { hooks.unload = fn; } },
      '@/api/capability': { getMiniappCapabilities: async () => { if (state.fail) throw new Error('断网'); return { attendanceManagementEnabled: state.enabled }; } },
      '@/api/attendance': { getAttendanceUser: async () => ({ roleCodes: [], permissionCodes: state.permissions }) },
      './model': model(),
      '@/session/parent-session': { getParentSession: () => ({ accessToken: 'p' }) },
      '@/session/student-session': { getStudentSession: () => ({ accessToken: 's' }) },
      '@/session/organization-session': { getOrganizationSession: () => ({ accessToken: 'o' }) }
    }, { uni: { navigateTo: async ({ url }) => state.navigated.push(url), showToast: () => {} } });
    const entry = useAttendanceEntry(identity);
    await hooks.show();
    assert.equal(entry.attendanceEnabled.value, true);
    await entry.openAttendance();
    assert.equal(state.navigated[0], `/pages/attendance/attendance?identity=${identity}`);
    hooks.hide();
    assert.equal(entry.attendanceEnabled.value, false);
    state.enabled = false;
    await hooks.show();
    assert.equal(entry.attendanceEnabled.value, false);
    await entry.openAttendance();
    assert.equal(state.navigated.length, 1);
    state.enabled = true; state.permissions = [];
    await hooks.show();
    assert.equal(entry.attendanceEnabled.value, false);
    state.fail = true;
    await hooks.show();
    assert.equal(entry.attendanceEnabled.value, false);
  }
});

test('独立路由与四类首页注册考勤入口', () => {
  const pages = JSON.parse(fs.readFileSync(path.join(__dirname, '../src/pages.json'), 'utf8'));
  assert.ok(pages.pages.some((page) => page.path === 'pages/attendance/attendance'));
  for (const identity of ['teacher', 'organization', 'parent', 'student']) {
    const source = fs.readFileSync(path.join(__dirname, `../src/pages/${identity}-home/${identity}-home.vue`), 'utf8');
    assert.ok(source.includes(`useAttendanceEntry('${identity}')`));
    assert.ok(source.includes('v-if="attendanceEnabled"'));
    assert.ok(source.includes('@tap="openAttendance"'));
  }
});
