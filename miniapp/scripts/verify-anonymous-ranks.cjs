// 独立 uni-app H5 产物与合成家长会话，不调用真实消息或数据库。
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const assert = require('node:assert/strict');
const path = require('node:path');
const base = process.env.MINI_URL || 'http://127.0.0.1:5174';
(async () => {
  const browser = await chromium.launch({ headless: true, channel: 'msedge' });
  try {
    const page = await browser.newPage({ viewport: { width: 390, height: 844 } });
    let enabled = false, version = 0, feature = true, reads = 0;
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.addInitScript(() => localStorage.setItem('lingdong.parent.session', JSON.stringify({ type: 'object', data: {
      sessionId: '2', accessToken: 'synthetic-parent-token', mobile: '测试家长'
    } })));
    await page.route('**/api/v1/**', async route => {
      const url = new URL(route.request().url());
      let body = [];
      if (!url.pathname.includes('/public/')) assert.equal(route.request().headers().authorization, 'Bearer synthetic-parent-token');
      if (url.pathname.endsWith('/auth/me')) body = { roleCodes: ['PARENT'], permissionCodes: ['MINIAPP_ANONYMOUS_CLASS_RANK_READ'], clientType: 'MINIAPP' };
      else if (url.pathname.endsWith('/public/capabilities')) body = { client: 'MINIAPP', anonymousClassRankEnabled: feature };
      else if (url.pathname.endsWith('/anonymous-ranks/preferences')) body = enabled ? [{ studentId: '1874244142494690002', classId: '1874244142494690003', version }] : [];
      else if (url.pathname.endsWith('/anonymous-ranks/students')) body = [{ studentId: '1874244142494690002', studentName: '我的孩子' }];
      else if (url.pathname.endsWith('/classes')) body = [{ classId: '1874244142494690003', className: '当前班级' }];
      else if (url.pathname.endsWith('/preference')) {
        if (route.request().method() === 'PUT') {
          const input = route.request().postDataJSON(); assert.equal(input.version, version);
          enabled = input.enabled; version++;
        }
        body = { enabled, version };
      } else if (url.pathname.includes('/anonymous-ranks/students/')) {
        reads++; assert.equal(enabled, true);
        body = [{ rank: 1, points: 20 }, { rank: 1, points: 20 }, { rank: 3, points: 0 }];
      }
      await route.fulfill({ contentType: 'application/json', body: JSON.stringify(body) });
    });
    await page.goto(base + '/#/pages/anonymous-ranks/anonymous-ranks');
    await page.getByText('孩子：我的孩子', { exact: true }).waitFor();
    assert.equal(reads, 0);
    await page.locator('uni-switch').click();
    await page.getByText('积分', { exact: true }).waitFor();
    assert.equal(await page.locator('.table .row').count(), 4);
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
    await page.screenshot({ path: path.resolve(__dirname, '../../.local-verification/anonymous-rank-mini.png'), fullPage: true });
    feature = false;
    await page.reload();
    await page.getByText('班级匿名排行未开启或无查看权限', { exact: true }).waitFor();
    assert.equal(await page.locator('uni-switch').count(), 0);
    await page.goto(base + '/#/pages/anonymous-ranks/anonymous-ranks?withdraw=true');
    await page.reload();
    await page.getByText('撤回授权', { exact: true }).click();
    await page.getByText('没有已开启的查看授权', { exact: true }).waitFor();
    assert.equal(enabled, false);
    assert.deepEqual(errors, []);
    console.log('小程序 H5：独立家长令牌、默认关闭、1/1/3、关闭直达拒绝、停用后撤回及390px布局通过');
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
