<template>
  <view class="has-tabbar page-shell">
    <view v-if="loading" class="state">正在核验考勤权限</view>
    <template v-else-if="enabled">
      <view class="heading-band">
        <text class="title">{{ canRecord ? '人工考勤' : '考勤记录' }}</text>
        <view v-if="canRecord" class="tabs">
          <view :class="['tab', mode === 'list' && 'active']" @tap="changeMode('list')">考勤记录</view>
          <view :class="['tab', mode === 'record' && 'active']" @tap="changeMode('record')">班级点名</view>
        </view>
      </view>

      <view v-if="detail" class="band">
        <button class="outline" @tap="detail = null">返回记录</button>
        <text class="section-title">{{ detail.record.studentName }} · {{ statusName(detail.record.status) }}</text>
        <text class="meta">{{ detail.record.className }} · {{ detail.record.attendanceDate }}</text>
        <text class="meta">签到 {{ detail.record.checkinTime || '未填写' }} · 签退 {{ detail.record.checkoutTime || '未填写' }}</text>
        <text class="meta">来源：人工登记 · 登记人：{{ detail.record.recorderName }}</text>
        <text class="meta">创建：{{ formatTime(detail.record.createdAt) }}</text>
        <text class="meta">更新：{{ formatTime(detail.record.updatedAt) }}</text>
        <text class="section-title">动作历史</text>
        <view v-if="!detail.actions.length" class="state">暂无动作历史</view>
        <view v-for="action in detail.actions" :key="action.id" class="history-row">
          <text class="name">{{ action.actionType === 'CREATE' ? '首次登记' : '更正' }} · {{ action.operatorName }}</text>
          <text class="meta">{{ formatTime(action.createdAt) }}</text>
          <text class="meta">状态：{{ statusName(action.beforeStatus) }} → {{ statusName(action.afterStatus) }}</text>
          <text class="meta">签到：{{ action.beforeCheckinTime || '未填写' }} → {{ action.afterCheckinTime || '未填写' }}</text>
          <text class="meta">签退：{{ action.beforeCheckoutTime || '未填写' }} → {{ action.afterCheckoutTime || '未填写' }}</text>
        </view>
      </view>

      <template v-else-if="mode === 'list'">
        <view class="band">
          <picker :range="queryClasses" range-key="className" :disabled="busy" @change="changeQueryClass">
            <view class="field">班级：{{ queryClasses.find(item => item.classOrganizationId === filters.classOrganizationId)?.className || '全部班级' }}</view>
          </picker>
          <view class="two-columns">
            <picker mode="date" :value="filters.dateFrom" :disabled="busy" @change="filters.dateFrom = $event.detail.value">
              <view class="field">{{ filters.dateFrom || '开始日期' }}</view>
            </picker>
            <picker mode="date" :value="filters.dateTo" :disabled="busy" @change="filters.dateTo = $event.detail.value">
              <view class="field">{{ filters.dateTo || '结束日期' }}</view>
            </picker>
          </view>
          <picker :range="queryStatuses" range-key="label" :disabled="busy" @change="changeQueryStatus">
            <view class="field">状态：{{ filters.status ? statusName(filters.status) : '全部状态' }}</view>
          </picker>
          <input v-model="filters.keyword" class="field" :disabled="busy" maxlength="64" placeholder="学生姓名" @confirm="queryRecords(1)" />
          <view class="two-columns">
            <button class="primary" :disabled="busy" @tap="queryRecords(1)">查询</button>
            <button class="outline" :disabled="busy" @tap="resetFilters">重置</button>
          </view>
        </view>
        <view class="list-band">
          <view v-if="busy" class="state">正在加载</view>
          <view v-else-if="!records.length" class="state">暂无考勤记录</view>
          <view v-for="record in records" :key="record.id" class="record-row" @tap="showDetail(record.id)">
            <view class="row-head"><text class="name">{{ record.studentName }}</text><text :class="['status', record.status]">{{ statusName(record.status) }}</text></view>
            <text class="meta">{{ record.className }} · {{ record.attendanceDate }}</text>
            <text class="meta">签到 {{ record.checkinTime || '未填写' }} · 签退 {{ record.checkoutTime || '未填写' }}</text>
            <text class="meta">{{ record.recorderName }} · {{ formatTime(record.updatedAt) }}</text>
          </view>
          <view class="pagination">
            <button class="outline" :disabled="busy || page <= 1" @tap="queryRecords(page - 1)">上一页</button>
            <text>{{ page }} / {{ Math.max(1, Math.ceil(total / pageSize)) }} · 共 {{ total }} 条</text>
            <button class="outline" :disabled="busy || page * pageSize >= total" @tap="queryRecords(page + 1)">下一页</button>
          </view>
        </view>
      </template>

      <template v-else-if="canRecord">
        <view class="band">
          <picker :range="recordClasses" range-key="className" :disabled="busy" @change="changeRecordClass">
            <view class="field">{{ recordClasses.find(item => item.classOrganizationId === recordClassId)?.className || '选择点名班级' }}</view>
          </picker>
          <picker mode="date" :value="recordDate" :end="today" :disabled="busy" @change="changeRecordDate">
            <view class="field">考勤日期：{{ recordDate }}</view>
          </picker>
          <button class="outline" :disabled="busy || !recordClassId" @tap="loadRoster">{{ rosterLoaded ? '重新载入名单' : '载入名单' }}</button>
          <view v-if="!recordClasses.length" class="state">暂无可点名班级</view>
        </view>
        <view v-if="rosterLoaded" class="list-band">
          <view v-if="!drafts.length" class="state">当日暂无可登记学生</view>
          <view v-for="row in drafts" :key="row.studentId" class="record-row">
            <checkbox-group @change="row.selected = $event.detail.value.length > 0">
              <label class="row-head"><checkbox :value="row.studentId" :checked="row.selected" :disabled="busy" color="#167c5a" /><text class="name">{{ row.studentName }}</text><text class="meta">{{ row.versionNo === null ? '未登记' : '已登记' }}</text></label>
            </checkbox-group>
            <picker :range="attendanceStatuses" range-key="label" :value="statusIndex(row.status)" :disabled="busy" @change="changeRowStatus(row, $event)">
              <view class="field">状态：{{ row.status ? statusName(row.status) : '未选择' }}</view>
            </picker>
            <view v-if="row.status !== 'ABSENT' && row.status !== 'LEAVE'" class="two-columns">
              <view><text class="meta">签到时间</text><picker mode="time" :value="row.checkinTime" :disabled="busy" @change="row.checkinTime = $event.detail.value; row.selected = true"><view class="field">{{ row.checkinTime || '未填写' }}</view></picker><button v-if="row.checkinTime" class="outline" :disabled="busy" @tap="row.checkinTime = ''; row.selected = true">清除签到</button></view>
              <view><text class="meta">签退时间</text><picker mode="time" :value="row.checkoutTime" :disabled="busy" @change="row.checkoutTime = $event.detail.value; row.selected = true"><view class="field">{{ row.checkoutTime || '未填写' }}</view></picker><button v-if="row.checkoutTime" class="outline" :disabled="busy" @tap="row.checkoutTime = ''; row.selected = true">清除签退</button></view>
            </view>
          </view>
          <view class="submit-band"><text class="meta">已选择 {{ selectedCount }} 人</text><button class="primary" :loading="submitting" :disabled="busy || selectedCount < 1 || selectedCount > 100" @tap="submit">提交点名（{{ selectedCount }} 人）</button></view>
        </view>
      </template>
      <view v-if="errorMessage" class="error">{{ errorMessage }}</view>
    </template>
    <view v-else class="state"><text>{{ errorMessage || '考勤暂不可用' }}</text><button class="outline" @tap="refresh">重新加载</button></view>
  <AppTabBar v-if="identity === 'teacher'" :items="TEACHER_TABS" :active="2" />
  <AppTabBar v-else-if="identity === 'organization'" :items="ORG_TABS" :active="2" />
  </view>
</template>

<script setup lang="ts">
import AppTabBar from '@/components/AppTabBar.vue';
import { TEACHER_TABS, ORG_TABS } from '@/config/tabbar';
import { computed, ref } from 'vue';
import { onLoad, onShow, onHide, onUnload, onPullDownRefresh } from '@dcloudio/uni-app';
import { getMiniappCapabilities } from '@/api/capability';
import { ApiError } from '@/api/http';
import { getAttendanceUser, listAttendanceClasses, listAttendanceRecords, getAttendanceRoster,
  getAttendanceDetail, submitAttendanceBatch, type AttendanceIdentity, type AttendanceClass,
  type AttendanceRecord, type AttendanceDetail, type AttendanceFilters, type AttendanceUser } from '@/api/attendance';
import { getOrganizationSession } from '@/session/organization-session';
import { getParentSession } from '@/session/parent-session';
import { getStudentSession } from '@/session/student-session';
import { attendancePermissions, attendanceToday, attendanceStatuses, attendanceStatusName as statusName,
  createAttendanceDrafts, buildAttendanceBatch, type AttendanceDraft } from './model';

const identity = ref<AttendanceIdentity>('student');
const identityValid = ref(false);
const enabled = ref(false);
const user = ref<AttendanceUser | null>(null);
const loading = ref(true);
const busy = ref(false);
const submitting = ref(false);
const errorMessage = ref('');
const mode = ref<'list' | 'record'>('list');
const records = ref<AttendanceRecord[]>([]);
const detail = ref<AttendanceDetail | null>(null);
const classes = ref<AttendanceClass[]>([]);
const recordClasses = ref<AttendanceClass[]>([]);
const drafts = ref<AttendanceDraft[]>([]);
const rosterLoaded = ref(false);
const recordClassId = ref('');
const today = ref(attendanceToday());
const recordDate = ref(today.value);
const filters = ref({ classOrganizationId: '', status: '' as AttendanceFilters['status'] | '', dateFrom: '', dateTo: '', keyword: '' });
const page = ref(1);
const pageSize = 20;
const total = ref(0);
const canRecord = computed(() => attendancePermissions(enabled.value, user.value, identity.value).record);
const selectedCount = computed(() => drafts.value.filter((row) => row.selected).length);
const queryClasses = computed(() => [{ classOrganizationId: '', className: '全部班级' }, ...classes.value]);
const queryStatuses = [{ value: '', label: '全部状态' }, ...attendanceStatuses.slice(1)];
// 生命周期序号阻止旧请求在恢复、退出或身份失效后回写。
let epoch = 0;

onLoad((query) => {
  const value = query?.identity ?? hashQueryIdentity();
  if (value === 'teacher' || value === 'organization' || value === 'parent' || value === 'student') {
    identity.value = value;
    identityValid.value = true;
  }
});
onShow(refresh);
onHide(invalidate);
onUnload(invalidate);
onPullDownRefresh(async () => { await refresh(); uni.stopPullDownRefresh(); });

/** H5 直接刷新或粘贴链接进入时，onLoad 的 query 可能为空，从地址栏 hash 兜底解析。 */
function hashQueryIdentity(): string | undefined {
  // #ifdef H5
  const match = window.location.hash.match(/[?&]identity=(teacher|organization|parent|student)/);
  if (match) return match[1];
  // #endif
  return undefined;
}

function token(): string {
  if (!identityValid.value) return '';
  if (identity.value === 'parent') return getParentSession()?.accessToken || '';
  if (identity.value === 'student') return getStudentSession()?.accessToken || '';
  return getOrganizationSession()?.accessToken || '';
}
function clearRoster(): void { drafts.value = []; rosterLoaded.value = false; }
function invalidate(): void {
  epoch++;
  enabled.value = false; user.value = null; records.value = []; detail.value = null;
  classes.value = []; recordClasses.value = []; clearRoster(); total.value = 0;
  busy.value = false; submitting.value = false;
}
async function leave(): Promise<void> {
  const home = identityValid.value && token() ? `/pages/${identity.value}-home/${identity.value}-home` : '/pages/index/index';
  invalidate();
  // #ifdef H5
  // 页面栈被破坏时 reLaunch 可能静默不跳转，超时后改写 hash 强制离开，避免滞留死状态。
  await new Promise<void>((resolve) => {
    let settled = false;
    const fallback = () => { if (!settled) { settled = true; window.location.hash = `#${home}`; resolve(); } };
    const timer = setTimeout(fallback, 800);
    uni.reLaunch({ url: home, success: () => { clearTimeout(timer); if (!settled) { settled = true; resolve(); } }, fail: fallback });
  });
  // #endif
  // #ifndef H5
  await uni.reLaunch({ url: home });
  // #endif
}
async function verifyAccess(current: number, accessToken: string): Promise<boolean> {
  const capabilities = await getMiniappCapabilities();
  if (current !== epoch) return false;
  if (capabilities.attendanceManagementEnabled !== true) { await leave(); return false; }
  const currentUser = await getAttendanceUser(accessToken);
  if (current !== epoch) return false;
  const permissions = attendancePermissions(true, currentUser, identity.value);
  if (!permissions.read) { await leave(); return false; }
  user.value = currentUser; enabled.value = true;
  if (!permissions.record) { clearRoster(); recordClasses.value = []; mode.value = 'list'; }
  return true;
}
async function handleError(error: unknown, current: number): Promise<void> {
  if (current !== epoch) return;
  if (error instanceof ApiError && (error.statusCode === 401 || error.statusCode === 403 || error.code === 'FEATURE_DISABLED')) {
    await leave(); return;
  }
  errorMessage.value = error instanceof Error ? error.message : '考勤请求失败，请重试';
  uni.showToast({ title: errorMessage.value, icon: 'none' });
}
async function refresh(): Promise<void> {
  invalidate();
  const current = epoch;
  loading.value = true; errorMessage.value = ''; today.value = attendanceToday();
  try {
    const accessToken = token();
    if (!accessToken) { await leave(); return; }
    if (!await verifyAccess(current, accessToken)) return;
    const [queryOptions, operationalOptions] = await Promise.all([
      listAttendanceClasses(accessToken), canRecord.value ? listAttendanceClasses(accessToken, true) : Promise.resolve([])
    ]);
    if (current !== epoch) return;
    classes.value = queryOptions; recordClasses.value = operationalOptions;
    if (!classes.value.some((item) => item.classOrganizationId === filters.value.classOrganizationId)) filters.value.classOrganizationId = '';
    if (!recordClasses.value.some((item) => item.classOrganizationId === recordClassId.value)) recordClassId.value = '';
    await queryRecords(1);
  } catch (error) {
    if (current === epoch) { enabled.value = false; user.value = null; records.value = []; }
    await handleError(error, current);
  } finally { if (current === epoch || !enabled.value) loading.value = false; }
}
async function queryRecords(nextPage = 1): Promise<void> {
  if (!enabled.value || busy.value) return;
  const { dateFrom, dateTo } = filters.value;
  if (!!dateFrom !== !!dateTo || (dateFrom && dateFrom > dateTo)) {
    errorMessage.value = '请同时选择开始和结束日期，且开始日期不得晚于结束日期'; return;
  }
  const current = epoch;
  busy.value = true; errorMessage.value = ''; records.value = []; detail.value = null;
  try {
    const response = await listAttendanceRecords(token(), {
      ...filters.value, keyword: filters.value.keyword.trim(), status: filters.value.status || undefined,
      dateFrom: dateFrom || undefined, dateTo: dateTo || undefined, page: nextPage, pageSize
    });
    if (current !== epoch) return;
    records.value = response.items; page.value = response.page; total.value = response.total;
  } catch (error) { await handleError(error, current); }
  finally { if (current === epoch) busy.value = false; }
}
async function loadRoster(): Promise<void> {
  if (!canRecord.value || busy.value || !recordClassId.value) return;
  if (!recordDate.value || recordDate.value > attendanceToday()) { errorMessage.value = '不能登记未来日期的考勤'; return; }
  const current = epoch;
  busy.value = true; errorMessage.value = ''; clearRoster();
  try {
    const roster = await getAttendanceRoster(token(), recordClassId.value, recordDate.value);
    if (current !== epoch) return;
    drafts.value = createAttendanceDrafts(roster); rosterLoaded.value = true;
  } catch (error) { await handleError(error, current); }
  finally { if (current === epoch) busy.value = false; }
}
async function submit(): Promise<void> {
  if (!canRecord.value || busy.value || !rosterLoaded.value) return;
  const current = epoch;
  busy.value = true; submitting.value = true; errorMessage.value = '';
  try {
    const payload = buildAttendanceBatch(recordClassId.value, recordDate.value, drafts.value);
    const accessToken = token();
    if (!await verifyAccess(current, accessToken) || !canRecord.value) return;
    // 整批只发送一次，由服务端保证原子性；不拆分、不自动覆盖冲突版本。
    await submitAttendanceBatch(accessToken, payload);
    if (current !== epoch) return;
    clearRoster(); records.value = []; detail.value = null;
    uni.showToast({ title: '点名已提交', icon: 'success' });
    busy.value = false;
    await loadRoster();
  } catch (error) {
    if (current === epoch && error instanceof ApiError && error.statusCode === 409 && error.code !== 'FEATURE_DISABLED') {
      clearRoster();
      errorMessage.value = '考勤记录已被更新，请重新载入名单后重试';
      uni.showToast({ title: errorMessage.value, icon: 'none' });
    } else { await handleError(error, current); }
  } finally { if (current === epoch) { busy.value = false; submitting.value = false; } }
}
async function showDetail(id: string): Promise<void> {
  if (!enabled.value || busy.value) return;
  const current = epoch;
  busy.value = true; errorMessage.value = ''; detail.value = null;
  try {
    const response = await getAttendanceDetail(token(), id);
    if (current === epoch) detail.value = response;
  } catch (error) { await handleError(error, current); }
  finally { if (current === epoch) busy.value = false; }
}
function changeMode(value: 'list' | 'record'): void {
  if (busy.value || (value === 'record' && !canRecord.value)) return;
  mode.value = value; detail.value = null; errorMessage.value = '';
  if (value === 'list') void queryRecords(1);
}
function resetFilters(): void {
  filters.value = { classOrganizationId: '', status: '', dateFrom: '', dateTo: '', keyword: '' };
  void queryRecords(1);
}
function changeQueryClass(event: { detail: { value: string } }): void {
  filters.value.classOrganizationId = queryClasses.value[Number(event.detail.value)]?.classOrganizationId || '';
}
function changeQueryStatus(event: { detail: { value: string } }): void {
  filters.value.status = attendanceStatuses[Number(event.detail.value)]?.value || '';
}
function changeRecordClass(event: { detail: { value: string } }): void {
  clearRoster(); recordClassId.value = recordClasses.value[Number(event.detail.value)]?.classOrganizationId || '';
}
function changeRecordDate(event: { detail: { value: string } }): void { clearRoster(); recordDate.value = event.detail.value; }
function statusIndex(status: AttendanceDraft['status']): number { return attendanceStatuses.findIndex((item) => item.value === status); }
function changeRowStatus(row: AttendanceDraft, event: { detail: { value: string } }): void {
  row.status = attendanceStatuses[Number(event.detail.value)]?.value || ''; row.selected = !!row.status;
  if (row.status === 'ABSENT' || row.status === 'LEAVE') { row.checkinTime = ''; row.checkoutTime = ''; }
}
function formatTime(value: string): string { return value ? value.replace('T', ' ').slice(0, 19) : ''; }
</script>

<style lang="scss" scoped>
.page-shell { min-height: 100vh; padding-bottom: 40rpx; background: #f4f7f5; color: #1c2b28; font-size: 28rpx; letter-spacing: 0; }
/* #ifdef H5 */
.page-shell { min-height: calc(100vh - 44px); }
/* #endif */
.heading-band, .band, .list-band { background: #fff; border-bottom: 2rpx solid #dbe3df; }
.heading-band, .band { padding: 24rpx 32rpx; }
.band, .list-band { margin-top: 20rpx; }
.title, .section-title, .meta, .name { display: block; overflow-wrap: anywhere; }
.title { font-size: 34rpx; font-weight: 700; }
.section-title { margin-top: 28rpx; font-size: 30rpx; font-weight: 600; }
.tabs { display: flex; margin-top: 22rpx; border-bottom: 2rpx solid #dbe3df; }
.tab { flex: 1; padding: 20rpx 12rpx; text-align: center; border-bottom: 4rpx solid transparent; }
.tab.active { border-color: #167c5a; color: #167c5a; font-weight: 600; }
.two-columns { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16rpx; }
/* 微信 WXSS 不支持 * 通配符选择器，用显式元素选择器替代。 */
.two-columns > view, .two-columns > picker, .two-columns > button { min-width: 0; }
.field { width: 100%; min-height: 80rpx; height: auto; margin-top: 18rpx; padding: 20rpx 16rpx; border: 2rpx solid #cbd7d1; border-radius: 8rpx; box-sizing: border-box; font-size: 27rpx; line-height: 1.5; overflow-wrap: anywhere; }
input.field { height: 84rpx; }
.primary, .outline { width: 100%; min-height: 80rpx; margin: 18rpx 0 0; padding: 18rpx 14rpx; border-radius: 8rpx; font-size: 27rpx; line-height: 1.5; box-sizing: border-box; }
.primary { background: #167c5a; color: #fff; }
.outline { background: #fff; color: #167c5a; border: 2rpx solid #cbd7d1; }
.primary::after, .outline::after { border: 0; }
button[disabled] { opacity: .5; }
.record-row, .history-row { padding: 26rpx 32rpx; border-bottom: 2rpx solid #edf1ef; }
.history-row { padding-left: 0; padding-right: 0; }
.row-head { display: flex; gap: 16rpx; align-items: center; }
.name { flex: 1; min-width: 0; font-size: 29rpx; font-weight: 600; }
.meta { margin-top: 10rpx; font-size: 24rpx; color: #64756d; line-height: 1.6; }
.row-head .meta { margin-top: 0; flex-shrink: 0; }
.status { flex-shrink: 0; color: #167c5a; }
.LATE, .EARLY_LEAVE { color: #946000; }
.ABSENT { color: #b42318; }
.LEAVE { color: #5267a4; }
.pagination { display: grid; grid-template-columns: 140rpx minmax(0, 1fr) 140rpx; align-items: center; gap: 12rpx; padding: 24rpx 20rpx; text-align: center; font-size: 23rpx; }
.pagination .outline { margin-top: 0; font-size: 24rpx; }
.submit-band { padding: 20rpx 32rpx 32rpx; }
.state { padding: 64rpx 32rpx; color: #64756d; text-align: center; }
.error { padding: 24rpx 32rpx; color: #b42318; overflow-wrap: anywhere; }
</style>
