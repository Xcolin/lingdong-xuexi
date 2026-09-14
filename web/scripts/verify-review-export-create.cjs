// 合成复盘接口验证创建页面，不连接远程业务服务。
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const output = path.resolve(__dirname, '../../.local-verification');
const base = process.env.WEB_URL || 'http://127.0.0.1:5173';
(async () => {
  fs.mkdirSync(output, { recursive: true });
  const browser = await chromium.launch({ headless: true, channel: 'msedge' });
  try {
    for (const width of [1280, 390]) {
      const page = await browser.newPage({ viewport: { width, height: 900 } });
      let enabled = true;
      const errors = [], submissions = [];
      page.on('pageerror', error => errors.push(error.message));
      const studentId = '1874244142494650102', reviewId = '1874244142494650151', templateId = '1874244142494650105';
      const job = { id: '1874244142494650180', jobCode: 'EXP-测试创建', status: 'SUCCEEDED', templateName: '复盘模板',
        templateVersion: 'V1', requestReason: '复盘留存', totalRows: 1, processedRows: 1, requestedAt: '2026-09-08T12:00:00' };
      const review = { reviewId, studentId, studentName: '测试学生', periodType: 'DAY', periodStart: '2026-09-08', periodEnd: '2026-09-08',
        contentVersion: 1, taskTotalCount: 1, completedCount: 1, completionRate: 1, earnedPoints: 10, inProgressCount: 0, pauseCount: 0,
        categories: [], dailyTrends: [], supplements: [] };
      await page.addInitScript(() => sessionStorage.setItem('lingdong-learning.web.session', JSON.stringify({ accessToken: 'synthetic', refreshToken: 'synthetic' })));
      await page.route('**/api/v1/**', async route => {
        const url = new URL(route.request().url());
        let body = [];
        if (url.pathname.endsWith('/auth/me')) body = { roleCodes: ['PARENT'], permissionCodes: ['EXPORT_JOB_CREATE', 'EXPORT_JOB_READ', 'GROWTH_REVIEW_READ_CHILD'], displayName: '测试家长', clientType: 'WEB' };
        else if (url.pathname.endsWith('/public/capabilities')) body = { dailyGrowthReviewEnabled: true, attachmentServiceEnabled: true, growthReviewPdfExportEnabled: enabled };
        else if (url.pathname.endsWith('/growth-points/students')) body = [{ studentId, studentName: '测试学生' }];
        else if (url.pathname.includes('/growth-reviews/students/')) body = url.searchParams.has('periodType') ? { items: [review], total: 1 } : review;
        else if (url.pathname.endsWith('/growth-review-export-jobs/options')) {
          assert.equal(url.searchParams.get('studentId'), studentId);
          body = [{ id: templateId, templateName: '复盘模板', version: 'V1', modes: ['SIMPLE', 'DETAILED'] }];
        } else if (url.pathname.endsWith('/growth-review-export-jobs')) {
          if (route.request().method() === 'POST') { submissions.push(route.request().postDataJSON()); body = job; }
          else body = { items: [job], page: 1, pageSize: 20, total: 1 };
        }
        await route.fulfill({ contentType: 'application/json', body: JSON.stringify(body) });
      });
      for (const range of [false, true]) {
        await page.goto(base + '/growth-reviews');
        await page.getByRole('heading', { name: '测试学生 · 2026-09-08' }).waitFor();
        await page.getByRole('button', { name: '导出 PDF', exact: true }).click();
        await page.getByText('复盘模板 · V1', { exact: true }).waitFor();
        if (range) {
          await page.getByText('日期区间', { exact: true }).click();
          await page.getByLabel('开始日期').fill('2026-09-01');
          await page.getByLabel('结束日期').fill('2026-09-08');
        }
        await page.getByLabel('导出原因').fill('复盘留存');
        await page.screenshot({ path: path.join(output, `review-create-${width}-${range ? 'range' : 'single'}.png`), fullPage: true, animations: 'disabled' });
        assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 2), true);
        await page.getByRole('button', { name: '提交导出' }).click();
        await page.getByText(job.jobCode, { exact: true }).waitFor();
        assert.deepEqual(submissions.at(-1), { studentId, templateId, mode: 'SIMPLE', reason: '复盘留存',
          ...(range ? { periodType: 'DAY', dateFrom: '2026-09-01', dateTo: '2026-09-08' } : { reviewId }) });
      }
      enabled = false;
      await page.reload();
      await page.getByRole('heading', { name: '成长复盘', exact: true }).waitFor();
      assert.equal(await page.getByRole('button', { name: '导出 PDF', exact: true }).count(), 0);
      assert.equal(await page.getByRole('button', { name: '导出历史', exact: true }).count(), 1);
      assert.deepEqual(errors, []);
      console.log(`复盘创建 ${width}px：单份/区间载荷、中文表单、提交后历史、新导出关闭隐藏通过`);
      await page.close();
    }
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
