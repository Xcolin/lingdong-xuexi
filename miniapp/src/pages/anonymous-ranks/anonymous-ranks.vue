<template>
  <view class="rank-page">
    <text class="title">{{ withdrawalMode ? '排行查看授权' : '班级匿名排行' }}</text>
    <text class="note">{{ withdrawalMode ? '可撤回本人已开启的查看授权，不影响孩子参榜。' : '仅显示名次和当前班级来源净积分，不含家庭积分；同分并列，默认关闭。' }}</text>
    <text v-if="error" class="error">{{ error }}</text>
    <button :disabled="busy" @tap="load">刷新</button>
    <text v-if="busy" class="note">正在加载</text>
    <template v-if="allowed && withdrawalMode">
      <view v-for="(item, index) in withdrawals" :key="item.studentId + item.classId" class="withdrawal">
        <text>查看授权 {{ index + 1 }}</text>
        <button :disabled="busy || !!error" @tap="withdraw(item)">撤回授权</button>
      </view>
      <text v-if="!busy && !withdrawals.length" class="note">没有已开启的查看授权</text>
    </template>
    <template v-if="allowed && !withdrawalMode">
      <picker :range="students" range-key="studentName" :disabled="busy" @change="selectStudent(Number($event.detail.value))">
        <view class="field">孩子：{{ students.find(item => item.studentId === student)?.studentName || '暂无可查看的孩子' }}</view>
      </picker>
      <picker :range="classes" range-key="className" :disabled="busy" @change="selectClass(Number($event.detail.value))">
        <view class="field">班级：{{ classes.find(item => item.classId === classroom)?.className || '暂无有效班级' }}</view>
      </picker>
      <view v-if="preference" class="field toggle"><text>主动开启查看</text>
        <switch :key="`${preference.version}-${busy}-${writeError}`" :checked="preference.enabled" :disabled="busy || writeError" color="#167c5a" @change="toggleChanged" />
      </view>
      <view v-if="rows" class="table">
        <view class="row heading"><text>名次</text><text>积分</text></view>
        <view v-for="(row, index) in rows" :key="index" class="row"><text>{{ row.rank }}</text><text>{{ row.points }}</text></view>
        <text v-if="!rows.length" class="note">暂无排行数据</text>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { onLoad, onShow, onHide, onUnload } from '@dcloudio/uni-app';
import { getParentSession } from '@/session/parent-session';
import { getMiniappCapabilities } from '@/api/capability';
import { rankApi as api, type RankStudent, type RankClass, type RankRow, type RankPreference, type RankWithdrawal } from '@/api/anonymous-rank';
const withdrawalMode = ref(false), allowed = ref(false), busy = ref(false), writeError = ref(false);
const error = ref(''), student = ref(''), classroom = ref('');
const students = ref<RankStudent[]>([]), classes = ref<RankClass[]>([]), rows = ref<RankRow[]>();
const preference = ref<RankPreference>(), withdrawals = ref<RankWithdrawal[]>([]);
let sequence = 0, token = '';
const message = (cause: unknown) => cause instanceof Error ? cause.message : '请求失败，请刷新重试';
function toggleChanged(event: Event) { void change((event as unknown as { detail: { value: boolean } }).detail.value); }
onLoad(query => { withdrawalMode.value = query?.withdraw === 'true'; });
onShow(() => { void load(); });
function invalidate() { sequence++; busy.value = false; rows.value = undefined; }
onHide(invalidate); onUnload(invalidate);
async function load() {
  if (busy.value) return;
  const version = ++sequence;
  busy.value = true; error.value = ''; allowed.value = false; rows.value = undefined; preference.value = undefined;
  students.value = []; classes.value = []; withdrawals.value = []; writeError.value = false;
  try {
    token = getParentSession()?.accessToken || '';
    if (!token) throw new Error('请先登录家长账号');
    const user = await api.me(token);
    if (version !== sequence) return;
    if (user.clientType !== 'MINIAPP' || !user.roleCodes.includes('PARENT') || user.roleCodes.includes('SYS_AUDITOR')) throw new Error('当前身份无权使用此功能');
    if (withdrawalMode.value) {
      const result = await api.withdrawals(token);
      if (version === sequence) { withdrawals.value = result; allowed.value = true; }
      return;
    }
    const capability = await getMiniappCapabilities();
    if (version !== sequence) return;
    if (!capability.anonymousClassRankEnabled || !user.permissionCodes.includes('MINIAPP_ANONYMOUS_CLASS_RANK_READ')) throw new Error('班级匿名排行未开启或无查看权限');
    allowed.value = true;
    const result = await api.students(token);
    if (version !== sequence) return;
    students.value = result; student.value = result[0]?.studentId || '';
    if (student.value) await loadClasses(version);
  } catch (cause) { if (version === sequence) error.value = message(cause); }
  finally { if (version === sequence) busy.value = false; }
}
async function loadClasses(version: number) {
  const result = await api.classes(token, student.value);
  if (version !== sequence) return;
  classes.value = result; classroom.value = result[0]?.classId || '';
  if (classroom.value) await loadRanking(version);
}
async function loadRanking(version: number) {
  const value = await api.preference(token, student.value, classroom.value);
  if (version !== sequence) return;
  preference.value = value;
  if (value.enabled) {
    const result = await api.ranking(token, student.value, classroom.value);
    if (version === sequence) rows.value = result;
  }
}
async function selectStudent(index: number) {
  if (busy.value || !students.value[index]) return;
  student.value = students.value[index].studentId; classes.value = []; classroom.value = '';
  await selectRange(true);
}
async function selectClass(index: number) {
  if (busy.value || !classes.value[index]) return;
  classroom.value = classes.value[index].classId;
  await selectRange(false);
}
async function selectRange(child: boolean) {
  const version = ++sequence;
  busy.value = true; error.value = ''; preference.value = undefined; rows.value = undefined; writeError.value = false;
  try { if (child) await loadClasses(version); else await loadRanking(version); }
  catch (cause) { if (version === sequence) error.value = message(cause); }
  finally { if (version === sequence) busy.value = false; }
}
async function change(enabled: boolean) {
  if (busy.value || !preference.value || writeError.value) return;
  const version = ++sequence;
  busy.value = true; error.value = ''; rows.value = undefined;
  let saved = false;
  try {
    const value = await api.set(token, student.value, classroom.value, { enabled, version: preference.value.version });
    saved = true;
    if (version !== sequence) return;
    preference.value = value;
    if (value.enabled) {
      const result = await api.ranking(token, student.value, classroom.value);
      if (version === sequence) rows.value = result;
    }
  } catch (cause) { if (version === sequence) { error.value = message(cause); writeError.value = !saved; } }
  finally { if (version === sequence) busy.value = false; }
}
async function withdraw(item: RankWithdrawal) {
  if (busy.value || error.value) return;
  const version = ++sequence; busy.value = true;
  try {
    await api.set(token, item.studentId, item.classId, { enabled: false, version: item.version });
    if (version === sequence) withdrawals.value = withdrawals.value.filter(value => value !== item);
  } catch (cause) { if (version === sequence) error.value = message(cause); }
  finally { if (version === sequence) busy.value = false; }
}
</script>

<style scoped>
.rank-page { padding: 32rpx; color: #1c2b28; }
.title { display: block; font-size: 40rpx; font-weight: 700; }
.note, .error { display: block; margin: 24rpx 0; line-height: 1.7; }
.error { color: #b42318; }
.field, .table, .withdrawal { margin-top: 24rpx; padding: 24rpx; background: white; border-radius: 12rpx; }
.toggle, .row, .withdrawal { display: flex; justify-content: space-between; align-items: center; }
.row { padding: 22rpx 0; border-bottom: 1px solid #edf0ee; }
.row text { width: 50%; }
.heading { font-weight: 700; }
.withdrawal button { margin: 0; }
</style>
