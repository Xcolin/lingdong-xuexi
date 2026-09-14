// 真实 H5 产物上的小程序界面检查；接口全部使用合成数据。
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const base = process.env.MINI_URL || 'http://127.0.0.1:5174';
const output = path.resolve(__dirname, '../../.local-verification');
const record = { id: '1874244142494647101', studentId: '1874244142494647201', studentName: '张*', classOrganizationId: '1874244142494647301', className: '一年级一班', attendanceDate: '2026-09-01', status: 'LATE', checkinTime: '09:00:00', checkoutTime: null, source: 'MANUAL', recordedBy: '1874244142494647001', recorderName: '测试教师', versionNo: 3, createdAt: '2026-09-01T09:00:00', updatedAt: '2026-09-01T10:00:00' };
(async () => {
  fs.mkdirSync(output, { recursive: true });
  const browser = await chromium.launch({ headless: true, channel: process.env.BROWSER_CHANNEL || 'msedge' });
  try {
    for (const identity of ['teacher', 'organization', 'parent', 'student']) {
      const page = await browser.newPage({ viewport: { width: 390, height: 844 } });
      const role = { teacher: 'TEACHER', organization: 'ORG_ADMIN', parent: 'PARENT', student: 'STUDENT' }[identity];
      const key = `lingdong.${identity === 'teacher' ? 'organization' : identity}.session`;
      await page.addInitScript(({ key }) => {
        localStorage.setItem(key, JSON.stringify({ type: 'object', data: { sessionId: '2', accessToken: 'synthetic-mini-token', studentAccount: '26010001', mobile: '138****0000' } }));
      }, { key });
      let enabled = true;
      let batch;
      const errors = [];
      page.on('pageerror', e => errors.push(e.message));
      await page.route('**/api/v1/**', async route => {
        const url = new URL(route.request().url());
        let body = [];
        if (url.pathname.endsWith('/public/capabilities')) body = { client: 'MINIAPP', attendanceManagementEnabled: enabled, organizationMiniappAuthEnabled: true };
        else if (url.pathname.endsWith('/auth/me')) body = { userId: '1874244142494647001', sessionId: '2', roleCodes: [role], permissionCodes: ['ATTENDANCE_READ', 'ATTENDANCE_RECORD'] };
        else if (url.pathname.endsWith('/class-options')) body = [{ classOrganizationId: record.classOrganizationId, className: record.className }];
        else if (url.pathname.endsWith('/roster')) body = [{ studentId: record.studentId, studentName: '张小明', record }];
        else if (url.pathname.endsWith('/batch')) { batch = route.request().postDataJSON(); body = [record]; }
        else if (url.pathname.endsWith('/attendance-records')) body = { items: [record], total: 1, page: 1, pageSize: 20 };
        else if (url.pathname.endsWith('/' + record.id)) body = { record, actions: [{ id: '1874244142494647401', actionType: 'CREATE', operatorUserId: record.recordedBy, operatorName: '测试教师', beforeStatus: null, afterStatus: 'LATE', beforeCheckinTime: null, afterCheckinTime: '09:00:00', beforeCheckoutTime: null, afterCheckoutTime: null, createdAt: record.createdAt }] };
        else if (url.pathname.includes('parent-auth-context')) body = { enabled: true };
        else if (url.pathname.includes('parent-state')) body = { onboardingRequired: false, agreementAcceptanceRequired: false };
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
      });
      await page.goto(base + `/#/pages/attendance/attendance?identity=${identity}`);
      await page.locator('.record-row').filter({ hasText: '张*' }).waitFor();
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 2), true);
      await page.screenshot({ path: path.join(output, `attendance-mini-${identity}.png`), fullPage: true });
      if (identity === 'parent' || identity === 'student') assert.equal(await page.getByText('班级点名', { exact: true }).count(), 0);
      else {
        await page.getByText('班级点名', { exact: true }).click();
        await page.getByText('选择点名班级', { exact: true }).click();
        // H5 会同时保留日期和班级选择器，只操作当前班级选择器。
        await page.locator('.uni-selector-select .uni-picker-action-confirm').click();
        await page.getByText('载入名单', { exact: true }).click();
        await page.getByText('已登记', { exact: true }).waitFor();
        await page.getByText('张小明', { exact: true }).waitFor();
        assert.equal(await page.locator('uni-button').filter({ hasText: '提交点名' }).getAttribute('disabled') !== null, true);
        await page.locator('uni-checkbox').click();
        await page.locator('uni-button').filter({ hasText: '提交点名' }).click();
        await page.waitForFunction(() => document.body.innerText.includes('点名已提交'));
        assert.equal(batch.items.length, 1);
        assert.equal(batch.items[0].studentId, record.studentId);
        assert.equal(batch.items[0].versionNo, 3);
      }
      assert.deepEqual(errors, []);
      enabled = false;
      await page.reload();
      await page.waitForURL(url => !url.hash.includes('/pages/attendance/attendance'));
      console.log(`小程序 H5 ${identity}：权限裁剪、台账、布局${batch ? '、点名载荷' : ''}及停用直达检查通过`);
      await page.close();
    }
  } finally { await browser.close(); }
})().catch(e => { console.error(e); process.exitCode = 1; });
