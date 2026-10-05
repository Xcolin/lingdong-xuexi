const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const assert = require('node:assert/strict');
(async () => {
 const browser = await chromium.launch({ headless: true, channel: 'msedge' });
 try {
  const page = await browser.newPage({ viewport: { width: 390, height: 844 } });
  let allowed = true, fail = false, calls = 0;
  await page.addInitScript(() => localStorage.setItem('lingdong.student.session', JSON.stringify({ type: 'object', data: { accessToken: 'synthetic-student', studentAccount: '测试学号' } })));
  await page.route('**/api/v1/**', async route => {
   const url = new URL(route.request().url()); let body = {};
   if (url.pathname.endsWith('/public/capabilities')) body = { learningTaskManagementEnabled: true };
   else if (url.pathname.endsWith('/auth/me')) body = { clientType: 'MINIAPP', roleCodes: ['STUDENT'], permissionCodes: allowed ? ['TASK_ASSIGNMENT_READ_SELF'] : [] };
   else if (url.pathname.endsWith('/task-assignments')) {
    calls++; assert.equal(route.request().headers().authorization, 'Bearer synthetic-student');
    assert.match(url.searchParams.get('scheduledDate'), /^\d{4}-\d{2}-\d{2}$/);
    assert.equal(url.searchParams.get('pageSize'), '3');
    if (fail) return route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ message: '今日任务查询失败' }) });
    body = { items: [{ id: '1874244142494699991', title: '家庭阅读', sourceType: 'FAMILY', effectiveStatus: 'IN_PROGRESS' }], total: 8, page: 1, pageSize: 3 };
   }
   await route.fulfill({ contentType: 'application/json', body: JSON.stringify(body) });
  });
  await page.goto((process.env.MINI_URL || 'http://127.0.0.1:5176') + '/#/pages/student-home/student-home');
  await page.getByText('今日共 8 项任务', { exact: true }).waitFor();
  await page.getByText('家庭阅读', { exact: true }).waitFor();
  fail = true; await page.reload();
  await page.getByText('今日任务查询失败', { exact: true }).waitFor();
  assert.equal(await page.getByText('家庭阅读', { exact: true }).count(), 0);
  assert.equal(await page.getByText('今日暂无任务', { exact: true }).count(), 0);
  const before = calls; allowed = false; await page.reload();
  await page.getByText('今日任务暂不可用', { exact: true }).waitFor(); assert.equal(calls, before);
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
  console.log('学生首页：日期查询、真实总数、家庭任务、失败清空及权限撤回通过');
 } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
