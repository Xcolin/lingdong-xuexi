const fs=require('node:fs'),assert=require('node:assert/strict');
const page=fs.readFileSync('src/pages/rewards/rewards.vue','utf8');
const config=JSON.parse(fs.readFileSync('src/pages.json','utf8'));
assert.equal(config.pages.find(p=>p.path==='pages/rewards/rewards').style.navigationStyle,'custom');
assert.match(page,/<RewardShopHeader[^>]+:points="summary\?\.availablePoints/);
assert.match(page,/<AppTabBar[^>]+:items="STUDENT_TABS"/);
assert.doesNotMatch(page,/<view class="points-compact">/);
console.log('完成哨兵：奖励商城自定义一体导航、真实积分与底栏结构已核对');
