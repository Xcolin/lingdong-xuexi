const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const assert = require('node:assert/strict');

// 仅使用合成 API，验证独立 Web 的路由、表单与审批交互，不连接真实后端。
(async () => {
  const browser = await chromium.launch({ headless: true, channel: 'msedge' });
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
    let auditor = false, allowed = true, submissions = 0, approvals = 0;
    const toggle = { id: '1874244142494697001', featureCode: 'ANONYMOUS_CLASS_RANK', featureName: '合成匿名排行', status: 'DISABLED', versionNo: '9007199254740993', description: '合成全局开关', enableAllowed: true };
    const location = { ...toggle, id: '1874244142494697002', featureCode: 'GEO_ATTENDANCE', featureName: '地理位置考勤', enableAllowed: false };
    let change = null;
    await page.addInitScript(() => sessionStorage.setItem('lingdong-learning.web.session', JSON.stringify({ sessionId: '2', accessToken: 'synthetic-feature-token', refreshToken: 'synthetic-refresh', accessExpiresAt: '2099-01-01T00:00:00' })));
    await page.route('**/api/v1/**', async route => {
      const req = route.request(), url = new URL(req.url());
      let body = [], status = 200;
      if (url.pathname.endsWith('/auth/me')) body = { userId: auditor ? '1874244142494697004' : '1874244142494697003', sessionId: '2', username: 'synthetic', displayName: auditor ? '合成审核员' : '合成管理员', clientType: 'WEB', roleCodes: auditor ? ['SYS_AUDITOR'] : ['SYS_ADMIN'], permissionCodes: allowed ? ['SYSTEM_TASK_READ', 'FEATURE_TOGGLE_READ', auditor ? 'FEATURE_TOGGLE_REVIEW' : 'FEATURE_TOGGLE_MANAGE'] : [] };
      else if (url.pathname.endsWith('/public/capabilities')) body = { client: 'WEB', anonymousClassRankEnabled: toggle.status === 'ENABLED' };
      else if (url.pathname.endsWith('/feature-management/toggles')) body = [toggle, location];
      else if (url.pathname.endsWith('/feature-management/changes')) body = { items: change ? [change] : [], page: 1, pageSize: 20, total: change ? 1 : 0 };
      else if (url.pathname.endsWith('/feature-management/review-queue')) body = { items: change?.taskStatus === 'PENDING_REVIEW' ? [change] : [], page: 1, pageSize: 20, total: change?.taskStatus === 'PENDING_REVIEW' ? 1 : 0 };
      else if (url.pathname.endsWith('/feature-management/review-submissions')) {
        assert.equal(req.method(), 'POST'); assert.equal(auditor, false);
        const input = req.postDataJSON(); assert.equal(input.confirmed, true); assert.equal(input.expectedVersion, '9007199254740993'); assert.equal(input.targetStatus, 'ENABLED');
        submissions++;
        change = { id: '1874244142494697005', taskId: '1874244142494697006', featureCode: toggle.featureCode, featureName: toggle.featureName, beforeStatus: 'DISABLED', targetStatus: 'ENABLED', currentStatus: toggle.status, baseVersion: toggle.versionNo, currentVersion: toggle.versionNo, taskStatus: 'PENDING_REVIEW', title: input.title, description: input.description, submittedBy: '1874244142494697003', submittedAt: '2026-09-14T09:00:00', reviewedBy: null, reviewedAt: null, reviewComment: null, createdAt: '2026-09-14T09:00:00', enableAllowed: true };
        body = change; status = 201;
      } else if (url.pathname.endsWith('/approve')) {
        assert.equal(req.method(), 'POST'); assert.equal(auditor, true); approvals++;
        toggle.status = 'ENABLED'; toggle.versionNo = '9007199254740994';
        change = { ...change, taskStatus: 'EFFECTIVE', currentStatus: toggle.status, currentVersion: toggle.versionNo, reviewedBy: '1874244142494697004', reviewedAt: '2026-09-14T09:01:00', reviewComment: req.postDataJSON().comment }; body = change;
      } else assert.equal(req.method(), 'GET');
      await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
    });
    const base = process.env.WEB_URL || 'http://127.0.0.1:5177';
    await page.goto(base + '/feature-management');
    const row = page.getByRole('row').filter({ hasText: '合成匿名排行' }).first();
    await row.getByRole('button', { name: '申请启用', exact: true }).click();
    await page.getByLabel('申请说明', { exact: true }).fill('验证申请审批完整闭环');
    await page.getByRole('button', { name: '下一步', exact: true }).click();
    assert.equal(submissions, 0);
    await page.getByRole('button', { name: '确认提交', exact: true }).click();
    await page.getByText('待审核', { exact: true }).first().waitFor();
    assert.equal(submissions, 1); assert.equal(toggle.status, 'DISABLED');
    assert.equal(await page.getByRole('row').filter({ hasText: '地理位置考勤' }).getByRole('button', { name: '申请启用', exact: true }).isDisabled(), true);
    auditor = true; await page.reload();
    await page.getByRole('button', { name: '批准', exact: true }).click();
    await page.getByRole('button', { name: '确认批准', exact: true }).click();
    await page.getByText('已生效', { exact: true }).first().waitFor();
    assert.equal(approvals, 1); assert.equal(toggle.status, 'ENABLED');
    await page.screenshot({ path: '../.local-verification/feature-management-web.png', fullPage: true, animations: 'disabled' });
    allowed = false; await page.evaluate(() => window.dispatchEvent(new Event('focus')));
    await page.waitForURL('**/dashboard');
    assert.equal(await page.getByRole('menuitem', { name: '功能开关', exact: true }).count(), 0);
    assert.equal(await page.getByText('验证申请审批完整闭环', { exact: true }).count(), 0);
    console.log('功能开关 Web：申请确认、字符串版本、审批生效、定位禁启、焦点撤权清空通过');
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
