// 仅使用合成响应验证历史入口、中文下载及窄屏，不连接业务服务。
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
      const page = await browser.newPage({ viewport: { width, height: 900 }, acceptDownloads: true });
      let enabled = true;
      const errors = [];
      page.on('pageerror', error => errors.push(error.message));
      const job = { id: '1874244142494650180', jobCode: 'EXP-1874244142494650180', status: 'SUCCEEDED',
        templateName: '中文复盘模板', templateVersion: 'V1', requestReason: '复盘留存', totalRows: 1, processedRows: 1,
        requestedAt: '2026-09-08T12:00:00' };
      await page.addInitScript(() => sessionStorage.setItem('lingdong-learning.web.session', JSON.stringify({ accessToken: 'synthetic', refreshToken: 'synthetic' })));
      await page.route('**/api/v1/**', async route => {
        const url = new URL(route.request().url());
        let body = [];
        if (url.pathname.endsWith('/auth/me')) body = { roleCodes: ['PARENT'], permissionCodes: ['EXPORT_JOB_READ', 'GROWTH_REVIEW_READ_CHILD'], displayName: '测试家长', clientType: 'WEB' };
        else if (url.pathname.endsWith('/public/capabilities')) body = { dailyGrowthReviewEnabled: true, attachmentServiceEnabled: enabled, dataExportEnabled: false };
        else if (url.pathname.endsWith('/growth-points/students')) body = [{ studentId: '1874244142494650102', studentName: '测试学生' }];
        else if (url.pathname.includes('/growth-reviews/students/')) body = { items: [], total: 0 };
        else if (url.pathname.endsWith('/download')) return route.fulfill({ contentType: 'application/pdf', body: '%PDF-synthetic',
          headers: { 'Content-Disposition': "attachment; filename*=UTF-8''" + encodeURIComponent('灵动学习-测试学生-日报.pdf') } });
        else if (url.pathname.endsWith('/growth-review-export-jobs')) {
          assert.equal(url.searchParams.get('studentId'), '1874244142494650102');
          body = { items: [job], page: 1, pageSize: 20, total: 1 };
        } else if (url.pathname.includes('/growth-review-export-jobs/')) body = job;
        await route.fulfill({ contentType: 'application/json', body: JSON.stringify(body) });
      });
      await page.goto(base + '/growth-reviews');
      await page.getByRole('button', { name: '导出历史', exact: true }).click();
      await page.getByRole('button', { name: `详情-${job.jobCode}` }).click();
      await page.getByText('中文复盘模板', { exact: true }).waitFor();
      const downloadEvent = page.waitForEvent('download');
      await page.getByRole('button', { name: `下载-${job.jobCode}` }).click();
      assert.equal((await downloadEvent).suggestedFilename(), '灵动学习-测试学生-日报.pdf');
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 2), true);
      await page.screenshot({ path: path.join(output, `review-history-${width}.png`), fullPage: true, animations: 'disabled' });
      enabled = false;
      await page.reload();
      await page.getByRole('heading', { name: '成长复盘', exact: true }).waitFor();
      assert.equal(await page.getByRole('button', { name: '导出历史', exact: true }).count(), 0);
      assert.deepEqual(errors, []);
      console.log(`历史导出 ${width}px：详情、中文下载、新导出关闭仍可读取、附件停用隐藏通过`);
      await page.close();
    }
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
