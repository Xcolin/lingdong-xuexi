// 使用合成接口数据检查真实浏览器交互与布局，不连接任何外部业务服务。
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const output = path.resolve(__dirname, '../../.local-verification');
const base = process.env.WEB_URL || 'http://127.0.0.1:5173';
const user = { userId: '1874244142494647001', sessionId: '2', username: 'browser-test', displayName: '测试教师', clientType: 'WEB', roleCodes: ['TEACHER'], permissionCodes: ['ATTENDANCE_READ', 'ATTENDANCE_RECORD'] };
const record = { id: '1874244142494647101', studentId: '1874244142494647201', studentName: '张*', classOrganizationId: '1874244142494647301', className: '一年级一班', attendanceDate: '2026-09-01', status: 'LATE', checkinTime: '09:00:00', checkoutTime: null, source: 'MANUAL', recordedBy: user.userId, recorderName: '测试教师', versionNo: 3, createdAt: '2026-09-01T09:00:00', updatedAt: '2026-09-01T10:00:00' };
(async () => {
  fs.mkdirSync(output, { recursive: true });
  const browser = await chromium.launch({ headless: true, channel: process.env.BROWSER_CHANNEL || 'msedge' });
  try {
    for (const width of [1280, 390]) {
      const page = await browser.newPage({ viewport: { width, height: 900 } });
      let enabled = true;
      let submitted;
      const errors = [];
      page.on('pageerror', error => errors.push(error.message));
      await page.addInitScript(() => sessionStorage.setItem('lingdong-learning.web.session', JSON.stringify({ sessionId: '2', accessToken: 'synthetic-test-token', refreshToken: 'synthetic-refresh', accessExpiresAt: '2099-01-01T00:00:00' })));
      await page.route('**/api/v1/**', async route => {
        const url = new URL(route.request().url());
        const prefix = '/api/v1/attendance-records';
        let body;
        if (url.pathname === '/api/v1/auth/me') body = user;
        else if (url.pathname === '/api/v1/public/capabilities') body = { client: 'WEB', attendanceManagementEnabled: enabled };
        else if (url.pathname === prefix + '/class-options') body = [{ classOrganizationId: record.classOrganizationId, className: record.className }];
        else if (url.pathname === prefix + '/roster') body = [{ studentId: record.studentId, studentName: '张小明', record }, { studentId: '1874244142494647202', studentName: '李小红', record: null }];
        else if (url.pathname === prefix + '/batch') { submitted = route.request().postDataJSON(); body = [record]; }
        else if (url.pathname === prefix + '/' + record.id) body = { record, actions: [{ id: '1874244142494647401', actionType: 'CORRECT', operatorUserId: user.userId, operatorName: '测试教师', beforeStatus: 'NORMAL', afterStatus: 'LATE', beforeCheckinTime: '08:00:00', afterCheckinTime: '09:00:00', beforeCheckoutTime: null, afterCheckoutTime: null, createdAt: record.updatedAt }] };
        else if (url.pathname === prefix) body = { items: [record], total: 1, page: 1, pageSize: 20 };
        else body = [];
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
      });
      await page.goto(base + '/attendance-records');
      await page.getByRole('heading', { name: '考勤台账' }).waitFor();
      await page.getByRole('cell', { name: '张*', exact: true }).waitFor();
      await page.screenshot({ path: path.join(output, `attendance-web-${width}.png`), fullPage: true });
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 2), true, '页面不得横向溢出');
      await page.getByRole('button', { name: '班级点名', exact: true }).click();
      await page.getByRole('combobox', { name: '点名班级' }).click();
      await page.locator('.ant-select-item-option-content').getByText('一年级一班', { exact: true }).click();
      await page.getByLabel('考勤日期').fill('2026-09-01');
      await page.getByRole('cell', { name: '李小红', exact: true }).waitFor();
      assert.equal(await page.getByRole('button', { name: '提交考勤' }).isEnabled(), false);
      await page.getByRole('combobox', { name: '李小红的考勤状态' }).click();
      await page.locator('.ant-select-item-option-content').getByText('请假', { exact: true }).click();
      await page.screenshot({ path: path.join(output, `attendance-roster-${width}.png`), fullPage: true });
      await page.getByRole('button', { name: '提交考勤' }).click();
      await page.getByRole('dialog').waitFor({ state: 'hidden' });
      assert.equal(submitted.items.length, 1);
      assert.equal(submitted.items[0].studentId, '1874244142494647202');
      assert.equal(submitted.items[0].status, 'LEAVE');
      enabled = false;
      await page.evaluate(() => window.dispatchEvent(new Event('focus')));
      await page.waitForURL('**/dashboard');
      assert.equal(await page.getByRole('menuitem', { name: '考勤台账' }).count(), 0);
      assert.deepEqual(errors, []);
      console.log(`Web ${width}px：台账、点名、原子载荷、停用入口及布局检查通过`);
      await page.close();
    }
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
