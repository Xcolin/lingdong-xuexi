// 合成身份与接口，仅验收 Web 交互，不触发微信或远程数据库。
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const assert = require('node:assert/strict');
const path = require('node:path');
const base = process.env.WEB_URL || 'http://127.0.0.1:5173';
const studentId = '1874244142494661102';
(async () => {
  const browser = await chromium.launch({ headless: true, channel: 'msedge' });
  try {
    for (const width of [1280, 390]) {
      const page = await browser.newPage({ viewport: { width, height: 900 } });
      let enabled = false, version = 0, feature = true, allow = true, reads = 0;
      const writes = [], errors = [];
      page.on('pageerror', error => errors.push(error.message));
      await page.addInitScript(() => sessionStorage.setItem('lingdong-learning.web.session', JSON.stringify({
        sessionId: '2', accessToken: 'synthetic-token', refreshToken: 'synthetic-refresh', accessExpiresAt: '2099-01-01T00:00:00'
      })));
      await page.route('**/api/v1/**', async route => {
        const url = new URL(route.request().url());
        let body = [];
        if (url.pathname.endsWith('/auth/me')) body = { userId: '1874244142494661101', sessionId: '2', roleCodes: ['PARENT'],
          permissionCodes: ['GROWTH_REVIEW_READ_CHILD', ...(allow ? ['GROWTH_REVIEW_SUBSCRIBE_CHILD'] : [])], clientType: 'WEB', displayName: '测试家长' };
        else if (url.pathname.endsWith('/public/capabilities')) body = { client: 'WEB', dailyGrowthReviewEnabled: true,
          periodicGrowthReportEnabled: true, growthReviewSubscriptionEnabled: feature };
        else if (url.pathname.endsWith('/growth-points/students')) body = [{ studentId, studentName: '测试学生' }];
        else if (url.pathname.includes('/growth-review-subscriptions/students/')) {
          if (route.request().method() === 'PUT') {
            const input = route.request().postDataJSON(); writes.push(input);
            assert.equal(input.version, version); enabled = input.enabled; version++;
          } else reads++;
          body = { studentId, enabled, version };
        } else if (url.pathname.includes('/growth-reviews/students/')) body = { items: [], total: 0, page: 1, pageSize: 20 };
        await route.fulfill({ contentType: 'application/json', body: JSON.stringify(body) });
      });
      await page.goto(base + '/growth-reviews');
      const toggle = page.getByRole('switch', { name: '周报订阅' });
      await toggle.waitFor();
      await toggle.click();
      await page.waitForFunction(() => {
        const toggle = document.querySelector('[role="switch"]');
        return toggle?.getAttribute('aria-checked') === 'true' && !toggle.disabled;
      });
      assert.deepEqual(writes, [{ enabled: true, version: 0 }]);
      await page.screenshot({ path: path.resolve(__dirname, `../../.local-verification/subscription-${width}.png`), fullPage: true });
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
      allow = false;
      await page.reload();
      await toggle.waitFor();
      await toggle.click();
      await page.waitForFunction(() => document.querySelector('[role="switch"]')?.getAttribute('aria-checked') === 'false');
      assert.equal(await toggle.isDisabled(), true);
      assert.deepEqual(writes[1], { enabled: false, version: 1 });
      feature = false;
      const before = reads;
      await page.reload();
      await page.getByRole('heading', { name: '成长复盘', exact: true }).waitFor();
      assert.equal(await toggle.count(), 0);
      assert.equal(reads, before);
      assert.deepEqual(errors, []);
      console.log(`订阅 ${width}px：开启、撤权后取消、停用隐藏、无横向溢出通过`);
      await page.close();
    }
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
