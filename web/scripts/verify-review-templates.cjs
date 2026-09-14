// 合成接口数据验证真实 Web 模板，不连接远程业务服务。
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const output = path.resolve(__dirname, '../../.local-verification');
const base = process.env.WEB_URL || 'http://127.0.0.1:5175';
const studentId = '1874244142494647101';
(async () => {
  fs.mkdirSync(output, { recursive: true });
  const browser = await chromium.launch({ headless: true, channel: 'msedge' });
  try {
    for (const width of [1280, 390]) {
      const page = await browser.newPage({ viewport: { width, height: 900 } });
      const errors = [];
      page.on('pageerror', error => errors.push(error.message));
      let period = 'DAY';
      let reviewRequests = 0;
      const review = () => ({ reviewId: '1874244142494648101', studentId, studentName: '测试学生',
        periodType: period, periodStart: '2026-08-01', periodEnd: '2026-08-07',
        snapshotId: '1874244142494648102', contentVersion: 1, taskTotalCount: 10,
        completedCount: 6, inProgressCount: 2, pendingOptimizationCount: 2, exemptedCount: 0,
        completionRate: 0.75, earnedPoints: 36, pauseCount: 1, generatedAt: '2026-08-08T00:00:00',
        categories: [{ categoryCode: 'READING', taskCount: 10, completedCount: 6 }],
        dailyTrends: [{ trendDate: '2026-08-01', completedCount: 6, taskTotalCount: 10, completionRate: 0.75, earnedPoints: 36 }],
        supplements: [] });
      await page.addInitScript(() => sessionStorage.setItem('lingdong-learning.web.session', JSON.stringify({
        sessionId: '2', accessToken: 'synthetic-token', refreshToken: 'synthetic-refresh', accessExpiresAt: '2099-01-01T00:00:00'
      })));
      await page.route('**/api/v1/**', async route => {
        const url = new URL(route.request().url());
        let body = [];
        if (url.pathname.endsWith('/auth/me')) body = { userId: '1874244142494647001', sessionId: '2', roleCodes: ['PARENT'], permissionCodes: ['GROWTH_REVIEW_READ_CHILD'], clientType: 'WEB', displayName: '测试家长' };
        else if (url.pathname.endsWith('/public/capabilities')) body = { client: 'WEB', dailyGrowthReviewEnabled: true, periodicGrowthReportEnabled: true };
        else if (url.pathname.endsWith('/growth-points/students')) body = [{ studentId, studentName: '测试学生' }];
        else if (url.pathname.includes('/growth-reviews/students/')) {
          reviewRequests++;
          if (url.searchParams.has('periodType')) { period = url.searchParams.get('periodType'); body = { items: [review()], total: 1, page: 1, pageSize: 20 }; }
          else body = review();
        }
        await route.fulfill({ contentType: 'application/json', body: JSON.stringify(body) });
      });
      await page.goto(base + '/growth-reviews');
      await page.getByRole('heading', { name: '任务分类' }).waitFor();
      assert.equal(await page.getByText('简洁版', { exact: true }).count(), 0);
      for (const label of ['周报', '月报']) {
        await page.getByText(label, { exact: true }).click();
        await page.getByRole('heading', { name: '成长分析' }).waitFor();
        const before = reviewRequests;
        await page.getByText('简洁版', { exact: true }).click();
        assert.equal(await page.getByRole('heading', { name: '任务分类' }).count(), 0);
        await page.getByText('75.00%', { exact: true }).waitFor();
        assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 2), true);
        await page.screenshot({ path: path.join(output, `review-simple-${width}-${period}.png`), fullPage: true, animations: 'disabled' });
        await page.getByText('详细版', { exact: true }).click();
        await page.getByRole('heading', { name: '成长分析' }).waitFor();
        assert.equal(reviewRequests, before);
        assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 2), true);
        await page.screenshot({ path: path.join(output, `review-detailed-${width}-${period}.png`), fullPage: true, animations: 'disabled' });
      }
      assert.deepEqual(errors, []);
      console.log(`复盘模板 ${width}px：周月双向切换、日报隔离、无额外请求和布局检查通过`);
      await page.close();
    }
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
