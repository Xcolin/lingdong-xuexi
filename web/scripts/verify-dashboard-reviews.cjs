// 工作台复用原审核抽屉；合成数据不写真实业务。
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const assert = require('node:assert/strict');
(async () => {
  const browser = await chromium.launch({ headless: true, channel: 'msedge' });
  try {
    const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
    let pending = true, allowed = true, writes = 0;
    await page.addInitScript(() => sessionStorage.setItem('lingdong-learning.web.session', JSON.stringify({
      sessionId: '2', accessToken: 'synthetic-token', refreshToken: 'synthetic-refresh', accessExpiresAt: '2099-01-01T00:00:00'
    })));
    const review = { assignmentId: '1874244142494698001', taskId: '1874244142494698002', title: '测试家庭阅读', basePoints: 10,
      studentId: '1874244142494698003', studentName: '测试学生', sourceType: 'FAMILY', sourceOrganizationId: null, sourceOrganizationName: null,
      currentStatus: 'PENDING_REVIEW', currentReviewerId: '1874244142494698004', reviewerDisplayName: '测试家长',
      latestCheckIn: { id: '1874244142494698005', submissionNo: 1, content: '已阅读', status: 'SUBMITTED', submittedAt: '2026-09-10T09:00:00', reviewComment: null, attachments: [] } };
    await page.route('**/api/v1/**', async route => {
      const url = new URL(route.request().url());
      let body = [];
      if (url.pathname.endsWith('/auth/me')) body = { userId: '1874244142494698004', sessionId: '2', clientType: 'WEB', roleCodes: ['PARENT'],
        permissionCodes: allowed ? ['TASK_ASSIGNMENT_REVIEW'] : [], displayName: '测试家长' };
      else if (url.pathname.endsWith('/public/capabilities')) body = { client: 'WEB', learningTaskManagementEnabled: true };
      else if (url.pathname.endsWith('/approve')) {
        assert.deepEqual(route.request().postDataJSON(), { expectedCheckInId: review.latestCheckIn.id });
        writes++; pending = false; body = { awardedPoints: 10 };
      }
      else if (url.pathname.endsWith('/task-reviews')) body = { items: pending ? [review] : [], page: 1, pageSize: 20, total: pending ? 1 : 0 };
      else if (url.pathname.endsWith('/' + review.assignmentId)) body = review;
      await route.fulfill({ contentType: 'application/json', body: JSON.stringify(body) });
    });
    await page.goto((process.env.WEB_URL || 'http://127.0.0.1:5173') + '/dashboard');
    await page.getByRole('button', { name: '查看审核 测试家庭阅读' }).click();
    await page.getByRole('button', { name: '审核通过并发放 10 积分' }).click();
    await page.getByRole('button', { name: '确认通过', exact: true }).click();
    await page.getByText('暂无审核待办', { exact: true }).waitFor();
    assert.equal(writes, 1);
    allowed = false;
    await page.evaluate(() => window.dispatchEvent(new Event('focus')));
    await page.getByText('当前无任务审核权限或功能未开启。', { exact: true }).waitFor();
    assert.equal(await page.locator('section[aria-label="待审核任务"]').getByRole('table').count(), 0);
    console.log('Web 工作台：真实待审契约、复用审核通过流程、成功后清空、恢复窗口权限撤回通过');
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
