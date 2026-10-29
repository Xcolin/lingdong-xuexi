<template><view class="ld-page has-tabbar prototype-workspace"><NativeStatusSpacer/>
<view class="ld-topbar"><view class="ld-topbar-brand"><text class="ld-topbar-name">灵动伴随</text></view><button class="space-switch" @tap="switchOrganization">{{ context?.classes[0]?.schoolName || '切换组织' }} ⌄</button></view>
<view v-if="loading" class="ld-loading">正在加载</view>
<view v-else class="workspace-intro"><view class="ld-topbar-brand"><view class="profile-avatar"><PrototypeIcon name="user" :size="40"/></view><view><view class="intro-name-row"><text class="workspace-intro-title">{{context?.displayName || '教师'}}</text><text class="teacher-badge">成长伙伴</text></view><text class="workspace-intro-caption">成长伙伴 · {{context?.classes.length || 0}} 个任教班级</text></view></view><view class="workspace-stats"><view v-for="item in teacherStats" :key="item.label"><text class="stats-value">{{ item.value }}</text><text>{{ item.label }}</text></view></view></view>
<view class="workspace-shortcuts">
<view class="workspace-shortcut"><view class="shortcut-symbol"><PrototypeIcon name="school" :size="25"/></view>我的教学班级<text>查看班级与成员</text><view v-for="item in context?.classes || []" :key="item.classId" class="class-row"><view class="class-main"><text class="class-name">{{item.className}}</text><text class="school-name">{{item.schoolName}}</text></view></view></view>
<button v-if="taskEnabled" class="workspace-shortcut" @tap="openTasks"><view class="shortcut-symbol"><PrototypeIcon name="book" :size="25"/></view>教学任务<text>备课 · 发布 · 完成情况</text></button>
<button class="workspace-shortcut" @tap="openTraining"><view class="shortcut-symbol"><PrototypeIcon name="book" :size="25"/></view>培训学习<text>我的培训与证书</text></button>
<button class="workspace-shortcut" @tap="openGrowthRecords"><view class="shortcut-symbol"><PrototypeIcon name="growth" :size="25"/></view>成长记录<text>学生成长与课堂观察</text></button>
<button v-if="context?.permissionCodes.includes('LEARNING_TASK_CREATE') && taskEnabled" class="workspace-shortcut" @tap="openCreateTask"><view class="shortcut-symbol"><PrototypeIcon name="plus" :size="25"/></view>发布任务<text>安排今天的小目标</text></button>
<button v-if="exceptionReportEnabled" class="workspace-shortcut" @tap="openReports"><view class="shortcut-symbol"><PrototypeIcon name="shield" :size="25"/></view>异常报备<text>记录班级情况</text></button>
<button v-if="attendanceEnabled" class="workspace-shortcut" @tap="openAttendance()"><view class="shortcut-symbol"><PrototypeIcon name="calendar" :size="25"/></view>考勤记录<text>查看人工考勤</text></button>
<button v-if="accountSecurityEnabled" class="workspace-shortcut" @tap="openSecurity"><view class="shortcut-symbol"><PrototypeIcon name="shield" :size="25"/></view>账号安全<text>登录设备与安全事件</text></button>
</view><AppTabBar :items="TEACHER_TABS" :active="0"/></view></template>
<script setup lang="ts">
import NativeStatusSpacer from "@/components/NativeStatusSpacer.vue";
function switchOrganization(){uni.navigateTo({url:"/pages/my-organizations/my-organizations?identity=organization&choose=1"});}

import PrototypeIcon from '@/components/PrototypeIcon.vue';
import { ref, computed } from 'vue';
import AppTabBar from '@/components/AppTabBar.vue';
import { TEACHER_TABS } from '@/config/tabbar';
import { onShow,onHide,onUnload } from '@dcloudio/uni-app';
import { logoutOrganization } from '@/api/auth';
import { getMiniappCapabilities } from '@/api/capability';
import { useAttendanceEntry } from '@/composables/use-attendance-entry';
const { attendanceEnabled, openAttendance } = useAttendanceEntry('teacher');
import { getTeacherWorkbenchContext, type TeacherWorkbenchContext } from '@/api/teacher-workbench';
import { getAttendanceRoster } from '@/api/attendance';
import { listManagedTasks } from '@/api/managed-learning-task';
import { clearOrganizationSession, getOrganizationSession } from '@/session/organization-session';

const context = ref<TeacherWorkbenchContext | null>(null);
const loading = ref(true);
const loggingOut = ref(false);
const taskEnabled = ref(false);
const accountSecurityEnabled = ref(false);
const exceptionReportEnabled = ref(false);
// 原型 03-03 四项统计：仅真实取数；无聚合来源的计数如实显示占位，不造假。
const studentTotal = ref<number | null>(null);
const inProgressTotal = ref<number | null>(null);
function formatCount(value: number | null | undefined): string {
  return value == null ? '—' : value.toLocaleString('en-US');
}
const teacherStats = computed(() => [
  { label: '我的班级', value: formatCount(context.value?.classes.length ?? null) },
  { label: '成长小主人', value: formatCount(studentTotal.value) },
  { label: '已发布任务', value: formatCount(inProgressTotal.value) },
  { label: '待完成', value: '—' }
]);
function selectedScope(): string {
  const selection = uni.getStorageSync('lingdong.selected-organization');
  return JSON.stringify([selection?.accessToken, selection?.organizationId]);
}
function isCurrent(n: number, token: string, scope: string): boolean {
  return n === homeRevision && getOrganizationSession()?.accessToken === token && selectedScope() === scope;
}
async function loadRealStats(token: string, n: number, scope: string) {
  // 成长小主人：教师任教班级花名册人数汇总；无权限或失败时保持占位。
  try {
    const rosters = await Promise.all((context.value?.permissionCodes.includes('ATTENDANCE_READ') ? context.value.classes : []).map(c =>
      getAttendanceRoster(token, c.classId, new Date().toISOString().slice(0, 10)).catch(() => null)));
    if (isCurrent(n, token, scope) && context.value?.permissionCodes.includes('ATTENDANCE_READ') && rosters.every(r => Array.isArray(r))) studentTotal.value = new Set(rosters.flatMap(r => (r || []).map(s => s.studentId))).size;
  } catch { if (isCurrent(n, token, scope)) studentTotal.value = null; }
  // 进行中任务：教师已发布（执行中）任务计数；草稿不计入。
  try {
    if (!isCurrent(n, token, scope) || !taskEnabled.value) return;
    const page = await listManagedTasks(token, 1, 'TEACHER', 'PUBLISHED');
    if (isCurrent(n, token, scope)) inProgressTotal.value = page.total;
  } catch { if (isCurrent(n, token, scope)) inProgressTotal.value = null; }
}

let homeRevision=0;
function hideWorkspace(){homeRevision++;context.value=null;loading.value=false;taskEnabled.value=false;exceptionReportEnabled.value=false;accountSecurityEnabled.value=false;studentTotal.value=null;inProgressTotal.value=null;}
onHide(hideWorkspace);onUnload(hideWorkspace);
onShow(async () => {
  hideWorkspace();const n=homeRevision;const scope=selectedScope();
  const session = getOrganizationSession();
  if (!session) return leave();
  loading.value = true;
  try {
    const [capabilities, workbench] = await Promise.all([
      getMiniappCapabilities(), getTeacherWorkbenchContext(session.accessToken)
    ]);
    if(!isCurrent(n,session.accessToken,scope))return;
    context.value = workbench;
    taskEnabled.value = capabilities.learningTaskManagementEnabled
      && workbench.permissionCodes.includes('LEARNING_TASK_READ_MANAGED');
    exceptionReportEnabled.value = capabilities.studentExceptionReportEnabled === true
      && workbench.permissionCodes.includes('EXCEPTION_REPORT_READ');
    accountSecurityEnabled.value = capabilities.accountSecurityManagementEnabled;
    studentTotal.value = null;inProgressTotal.value = null;
    void loadRealStats(session.accessToken,n,scope);
  } catch {
    if(!isCurrent(n,session.accessToken,scope))return;
    await leave();
  } finally {
    if(isCurrent(n,session.accessToken,scope))loading.value = false;
  }
});

function openTasks(){return uni.navigateTo({url:'/pages/managed-tasks/managed-tasks?identity=teacher'});}
function openCreateTask(){return uni.navigateTo({url:'/pages/managed-tasks/managed-tasks?identity=teacher&create=true'});}
// 培训学习/成长记录域暂无后端实现：入口按原型 03-03 展示，点击如实提示未开放，不造虚假页面。
function openTraining(){uni.showToast({title:'培训学习模块暂未开放',icon:'none'});}
function openGrowthRecords(){uni.showToast({title:'成长记录模块暂未开放',icon:'none'});}
function openReports(){return uni.navigateTo({url:'/pages/exception-reports/exception-reports?identity=teacher'});}
function openSecurity(): Promise<unknown> {
  return uni.navigateTo({ url: '/pages/account-security/account-security?identity=organization&workbench=teacher' });
}

async function logout(): Promise<void> {
  const session = getOrganizationSession();
  if (!session || loggingOut.value) return;
  loggingOut.value = true;
  try { await logoutOrganization(session.accessToken); } catch { /* 本地会话仍需清理。 */ }
  finally { loggingOut.value = false; if(getOrganizationSession()?.accessToken===session.accessToken)await leave(); }
}

async function leave(): Promise<void> {
  clearOrganizationSession();
  await uni.reLaunch({ url: '/pages/index/index' });
}
</script>

<style scoped>
.prototype-workspace{min-height:calc(100vh - var(--window-top,0px));box-sizing:border-box;background:#f5f8f4;color:#182d26;padding:24rpx 24rpx 150rpx}.page-shell{padding-bottom:48rpx}.ld-topbar{display:flex;align-items:center;gap:18rpx;background:linear-gradient(120deg,#21664f,#0b4b3d);margin:-24rpx -24rpx 28rpx;padding:28rpx 26rpx 50rpx;color:white;border-radius:24rpx 24rpx 0 0}.ld-topbar-brand{display:flex;align-items:center;gap:18rpx;flex:1}.ld-topbar-name{display:block;font-size:32rpx;font-weight:650}.ld-topbar-meta{display:block;font-size:23rpx;opacity:.8;margin-top:8rpx}.ld-topbar-logo,.profile-avatar{width:80rpx;height:80rpx;border-radius:50%;background:#ffffff25;display:flex;align-items:center;justify-content:center}.profile-copy{flex:1}.ld-card,.ld-group,.form-band,.band,.search-band{background:#fff;border:1rpx solid #e5ece5;border-radius:24rpx;padding:24rpx;margin-bottom:20rpx}.workspace-intro{background:#fff;border-radius:24rpx;padding:30rpx 24rpx;margin-bottom:20rpx}.workspace-intro-title{display:block;font-size:34rpx;font-weight:700}.workspace-intro-caption{display:block;font-size:24rpx;color:#778078;margin-top:10rpx}.workspace-shortcuts{display:flex;flex-direction:column;gap:16rpx;margin-bottom:16rpx}.workspace-shortcut{display:flex;align-items:center;flex-wrap:wrap;text-align:left;background:#fff;border-radius:24rpx;padding:22rpx 26rpx;font-size:30rpx;font-weight:600;color:#182d26;min-height:120rpx}.workspace-shortcut>text{width:100%;padding-left:80rpx;font-size:23rpx;font-weight:400;color:#7b837d}.shortcut-symbol,.ld-cell-icon,.class-badge{width:64rpx;height:64rpx;border-radius:14rpx;background:#138655;display:flex;align-items:center;justify-content:center;margin-right:18rpx}.ld-heading{margin:28rpx 0 18rpx}.ld-heading-title,.form-title,.section-title{display:block;font-size:30rpx;font-weight:650}.ld-heading-sub{display:block;font-size:23rpx;color:#7b837d;margin-top:8rpx}.ld-cell,.class-row,.teacher-row,.organization-row{display:flex;align-items:center;padding:24rpx 0;border-bottom:1rpx solid #edf1eb;text-align:left;background:#fff;color:#182d26}.ld-cell:last-child,.class-row:last-child,.teacher-row:last-child{border-bottom:0}.ld-cell-body,.class-main,.organization-main{flex:1}.ld-cell-title,.class-name,.organization-name{display:block;font-size:29rpx;font-weight:600}.ld-cell-sub,.school-name,.organization-type{display:block;font-size:23rpx;color:#7d847e;margin-top:8rpx}.ld-cell-arrow{font-size:34rpx;color:#65726a}.prototype-section-header{display:flex;align-items:center;justify-content:center;font-size:32rpx;font-weight:700;margin-bottom:28rpx;gap:12rpx}.mode-control,.search-band,.action-band,.form-actions,.paging{display:flex;gap:14rpx;align-items:center;flex-wrap:wrap;margin-bottom:24rpx}.mode-control button{flex:1}.active{background:#e1f3e4;color:#0c7748}.field-label{display:block;margin:24rpx 0 12rpx;font-size:26rpx}.text-input,.search-input,.picker-value{background:#f8faf7;border:1rpx solid #e0e7df;border-radius:16rpx;padding:18rpx;min-height:72rpx;box-sizing:border-box;font-size:26rpx}.search-input{flex:1;min-width:180rpx}.check-row{display:flex;align-items:center;gap:12rpx;padding:16rpx 0}.state-text{font-size:26rpx;color:#7a837c;padding:24rpx}.review-card{display:flex;align-items:center;gap:20rpx}.review-info{flex:1}.ld-card-title{display:block;font-size:30rpx;font-weight:650}.review-count{display:block;margin-top:12rpx;font-size:24rpx;color:#718077}button{margin:0;font-size:25rpx;line-height:1.5;background:#eaf2e9;color:#21563e;padding:14rpx 20rpx;border-radius:16rpx}button::after{border:0}.primary-button,.save-button,.submit-button,.create-button{background:#137c53;color:white}.logout-button{margin-top:30rpx;color:#b65c50}.ld-logout,.space-switch{font-size:23rpx;flex-shrink:0}.org-badge{margin-right:18rpx;background:#e4f2e4;border-radius:14rpx;padding:16rpx}.error{color:#a43b31}.check-list{margin:18rpx 0}
/* 对齐原型 03-03：标题与切换组织控件上下堆叠，控件不再与胶囊区域重叠遮挡标题 */
.prototype-workspace .ld-topbar{flex-direction:column;gap:8rpx;align-items:stretch;padding-bottom:106rpx}
.prototype-workspace .ld-topbar .space-switch{background:transparent;color:#ffffffdd;padding:0;font-size:26rpx;text-align:left;justify-content:flex-start}
/* 原型 03-03：姓名+成长伙伴徽章同行；四项统计行样式与学校工作台一致 */
.intro-name-row{display:flex;align-items:center;gap:14rpx;min-width:0}
.teacher-badge{flex-shrink:0;background:#f6e9c8;color:#8a6d2f;border-radius:10rpx;padding:4rpx 14rpx;font-size:21rpx;font-weight:600}
.workspace-intro{position:relative;margin-top:8rpx;border:1rpx solid #e4ece3}
.workspace-stats{display:flex;border-top:1rpx solid #eef1eb;margin-top:20rpx;padding-top:16rpx;text-align:center}
.workspace-stats>view{flex:1;color:#748078;font-size:21rpx}
.workspace-stats>view+view{border-left:1rpx solid #edf1eb}
.workspace-stats text{display:block}
.workspace-stats .stats-value{color:#26362d;font-size:30rpx;font-weight:650;margin-bottom:5rpx}
/* 长班级名/学校名截断，不撑出卡片 */
.class-row{min-width:0}
.class-name,.school-name{overflow:hidden;white-space:nowrap;text-overflow:ellipsis}
</style>
