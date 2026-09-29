// 合成接口验收，不连接真实后端或读取学生资料。
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const assert = require('node:assert/strict');
const path = require('node:path');
const base = process.env.WEB_URL || 'http://127.0.0.1:5173';
(async () => {
  const browser = await chromium.launch({ headless: true, channel: 'msedge' });
  try {
    for (const width of [1280, 390]) {
      const page = await browser.newPage({ viewport: { width, height: 900 } });
      let enabled = false, version = 0, feature = true, reads = 0;
      const errors = [];
      page.on('pageerror', error => errors.push(error.message));
      await page.addInitScript(() => sessionStorage.setItem('lingdong-learning.web.session', JSON.stringify({
        sessionId: '2', accessToken: 'synthetic-token', refreshToken: 'synthetic-refresh', accessExpiresAt: '2099-01-01T00:00:00'
      })));
      await page.route('**/api/v1/**', async route => {
        const url = new URL(route.request().url());
        let body = [];
        if (url.pathname.endsWith('/auth/me')) body = { userId: '1874244142494690001', sessionId: '2', roleCodes: ['PARENT'],
          permissionCodes: ['ANONYMOUS_CLASS_RANK_READ'], clientType: 'WEB', displayName: '测试家长' };
        else if (url.pathname.endsWith('/public/capabilities')) body = { client: 'WEB', anonymousClassRankEnabled: feature };
        else if (url.pathname.endsWith('/anonymous-ranks/preferences')) body = enabled ? [{ studentId: '1874244142494690002', classId: '1874244142494690003', version }] : [];
        else if (url.pathname.endsWith('/anonymous-ranks/students')) body = [{ studentId: '1874244142494690002', studentName: '我的孩子' }];
        else if (url.pathname.endsWith('/classes')) body = [{ classId: '1874244142494690003', className: '当前班级' }];
        else if (url.pathname.endsWith('/preference')) {
          if (route.request().method() === 'PUT') {
            const input = route.request().postDataJSON();
            assert.equal(input.version, version); enabled = input.enabled; version++;
          }
          body = { enabled, version };
        } else if (url.pathname.includes('/anonymous-ranks/students/')) {
          reads++; assert.equal(enabled, true);
          body = [{ rank: 1, points: 20 }, { rank: 1, points: 20 }, { rank: 3, points: 0 }];
        }
        await route.fulfill({ contentType: 'application/json', body: JSON.stringify(body) });
      });
      await page.goto(base + '/anonymous-ranks');
      const toggle = page.getByRole('switch', { name: '主动开启查看' });
      await toggle.waitFor();
      assert.equal(reads, 0);
      await toggle.click();
      await page.getByRole('table').waitFor();
      assert.equal(await page.getByRole('columnheader').allTextContents().then(items => items.join(',')), '名次,积分');
      assert.equal(await page.getByRole('row').count(), 4);
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
      await page.screenshot({ path: path.resolve(__dirname, `../../.local-verification/anonymous-rank-${width}.png`), fullPage: true });
      await toggle.click();
      await page.getByRole('table').waitFor({ state: 'hidden' });
      feature = false;
      enabled = true; version++;
      await page.reload();
      await page.waitForURL('**/dashboard');
      assert.equal(await page.getByRole('menuitem', { name: '班级匿名排行' }).count(), 0);
      await page.goto(base + '/rank-preferences');
      await page.getByRole('button', { name: '撤回授权', exact: true }).click();
      await page.getByText('没有已开启的查看授权', { exact: true }).waitFor();
      assert.equal(enabled, false);
      assert.deepEqual(errors, []);
      console.log(`排行 ${width}px：默认关闭、主动开启、1/1/3、取消清空、关闭直达拒绝、无横向溢出通过`);
      await page.close();
    }
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
