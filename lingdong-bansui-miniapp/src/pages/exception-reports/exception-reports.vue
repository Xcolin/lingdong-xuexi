<template>
  <view class="prototype-workspace has-tabbar page-shell"><view class="prototype-section-header"><PrototypeIcon name="school" :size="22" /><text>异常报备</text></view>
    <view v-if="error" class="state">{{ error }}</view>
    <view v-if="loading" class="state">正在加载</view>
    <template v-else>
      <button v-if="identity === 'teacher' && canCreate" class="primary block-action" @tap="creating=!creating">{{creating?'返回报备记录':'＋ 新增异常报备'}}</button><view v-if="creating && identity === 'teacher' && canCreate" class="form-band">
        <text class="section-title">新增异常报备</text>
        <picker :range="classes" range-key="className" @change="changeClass">
          <view class="field">{{ selectedClass?.className || '选择班级' }}</view>
        </picker>
        <picker :range="students" range-key="studentName" @change="changeStudent">
          <view class="field">{{ selectedStudent ? `${selectedStudent.studentName} ${selectedStudent.studentAccountMasked}` : '选择学生' }}</view>
        </picker>
        <picker :range="typeOptions" range-key="label" @change="changeType">
          <view class="field">{{ typeOptions[typeIndex].label }}</view>
        </picker>
        <textarea v-model="content" class="textarea" maxlength="1000" placeholder="填写客观异常事实" />
        <button class="primary" :disabled="submitting" @tap="submit">提交报备</button>
      </view>

      <view v-if="!creating" class="list-band">
        <view class="list-head">
          <text class="section-title">{{ statusFilter==='SUBMITTED'?'待处理异常':'报备记录' }} · 共 {{ total }} 项</text>
          <button class="filter-button" @tap="toggleStatus">{{ statusFilter==='SUBMITTED'?'全部记录':'只看待处理' }}</button>
        </view>
        <view v-if="!error && reports.length === 0" class="state">暂无报备记录</view>
        <view v-for="item in reports" :key="item.id" class="report-row" @tap="showDetails(item.id)">
          <view class="row-head">
            <text class="student">{{ item.studentName }} · {{ item.className }}</text>
            <text :class="['status', item.status === 'HANDLED' ? 'handled' : 'submitted']">{{ statusName(item.status) }}</text>
          </view>
          <text class="meta">{{ typeName(item.exceptionType) }} · {{ formatTime(item.reportedAt) }}</text>
          <text class="content">{{ item.content }}</text>
          <template v-if="identity === 'organization' && canHandle && item.status === 'SUBMITTED'">
            <textarea v-if="handlingId === item.id" v-model="handlingNote" class="handle-note" maxlength="1000" placeholder="填写处理说明" @tap.stop />
            <button v-if="handlingId !== item.id" class="outline" @tap.stop="startHandle(item.id)">处理</button>
            <button v-else class="primary compact" :disabled="submitting" @tap.stop="confirmHandle(item)">确认处理</button>
          </template>
        </view>
        <view v-if="total" class="pager">
          <button class="pager-button" :disabled="page<=1||submitting" @tap="load(page-1)">上一页</button>
          <text class="pager-label">第 {{ page }} 页</text>
          <button class="pager-button" :disabled="page*20>=total||submitting" @tap="load(page+1)">下一页</button>
        </view>
      </view>
    </template>
  <AppTabBar v-if="identity === 'teacher'" :items="TEACHER_TABS" :active="2" />
  </view>
</template>

<script setup lang="ts">
import PrototypeIcon from '@/components/PrototypeIcon.vue';
import AppTabBar from '@/components/AppTabBar.vue';
import { TEACHER_TABS, ORG_TABS } from '@/config/tabbar';
import { computed, ref } from 'vue';
import { onLoad, onPullDownRefresh, onShow, onHide, onUnload } from '@dcloudio/uni-app';
import { requireExceptionAccess } from '@/api/exception-access';
import { ApiError } from '@/api/http';
import type { TeacherWorkbenchClass } from '@/api/teacher-workbench';
import { createExceptionReport, getExceptionReport, handleExceptionReport, listExceptionReports,
  listExceptionReportStudents, type ExceptionReport, type ExceptionReportStudentOption,
  type ExceptionReportType, type ExceptionReportStatus } from '@/api/exception-report';
import { getOrganizationSession } from '@/session/organization-session';

const identity = ref<'teacher' | 'organization'>('teacher');
const creating = ref(false); const loading = ref(true); const submitting = ref(false);
const reports = ref<ExceptionReport[]>([]); const classes = ref<TeacherWorkbenchClass[]>([]);
const students = ref<ExceptionReportStudentOption[]>([]); const selectedClass = ref<TeacherWorkbenchClass>();
const selectedStudent = ref<ExceptionReportStudentOption>(); const typeIndex = ref(0); const content = ref('');
const permissionCodes = ref<string[]>([]); const handlingId = ref(''); const handlingNote = ref('');
const enabled = ref(false);
const error=ref(''),page=ref(1),total=ref(0),statusFilter=ref<ExceptionReportStatus>();
let revision=0;
function reset(){creating.value=false;revision++;reports.value=[];total.value=0;permissionCodes.value=[];enabled.value=false;classes.value=[];students.value=[];selectedClass.value=undefined;selectedStudent.value=undefined;handlingId.value='';handlingNote.value='';content.value='';submitting.value=false;loading.value=false;}
function active(n:number,token:string){return n===revision&&getOrganizationSession()?.accessToken===token;}
function failed(cause:unknown,message:string){if(cause instanceof ApiError&&([401,403,404].includes(cause.statusCode)||cause.code==='FEATURE_DISABLED'))reset();error.value=cause instanceof Error?cause.message:message;}
function toggleStatus(){if(loading.value||submitting.value)return;statusFilter.value=statusFilter.value==='SUBMITTED'?undefined:'SUBMITTED';void load(1);}
const typeOptions: Array<{ label: string; value: ExceptionReportType }> = [
  { label: '出勤异常', value: 'ATTENDANCE' }, { label: '学习状态异常', value: 'LEARNING_STATUS' },
  { label: '心态异常', value: 'MENTAL_STATE' }
];
const canCreate = computed(() => enabled.value && permissionCodes.value.includes('EXCEPTION_REPORT_CREATE'));
const canHandle = computed(() => enabled.value && permissionCodes.value.includes('EXCEPTION_REPORT_HANDLE'));

onLoad((query) => { identity.value = query?.identity === 'organization' ? 'organization' : 'teacher';statusFilter.value=query?.status==='SUBMITTED'?'SUBMITTED':undefined; });
onShow(()=>load(1));
onHide(reset);onUnload(reset);
onPullDownRefresh(async () => { await load(); uni.stopPullDownRefresh(); });

async function load(target=1): Promise<void> {
  const session = getOrganizationSession();
  if (!session) { await leave(); return; }
  reset();const n=revision;error.value='';loading.value = true;
  try {
    const access=await requireExceptionAccess(session.accessToken,identity.value);
    if(!active(n,session.accessToken))return;
    enabled.value=true;permissionCodes.value=access.user.permissionCodes;
    if('classes' in access.context)classes.value=access.context.classes;
    const result=await listExceptionReports(session.accessToken,target,20,statusFilter.value);
    if(active(n,session.accessToken)){reports.value=result.items;page.value=result.page;total.value=result.total;}
  } catch(cause) { if(active(n,session.accessToken))failed(cause,'报备记录加载失败'); }
  finally { if(n===revision||!enabled.value)loading.value = false; }
}
async function changeClass(event: { detail: { value: string } }): Promise<void> {
  if(submitting.value)return;
  selectedClass.value = classes.value[Number(event.detail.value)]; selectedStudent.value = undefined;
  students.value=[];const n=++revision,selectedId=selectedClass.value?.classId;
  const session = getOrganizationSession();
  if(session&&selectedId)try{await requireExceptionAccess(session.accessToken,identity.value,'EXCEPTION_REPORT_CREATE');if(!active(n,session.accessToken))return;const result=await listExceptionReportStudents(session.accessToken,selectedId);if(active(n,session.accessToken))students.value=result;}catch(cause){if(active(n,session.accessToken))failed(cause,'学生选项加载失败');}
}
function changeStudent(event: { detail: { value: string } }): void { selectedStudent.value = students.value[Number(event.detail.value)]; }
function changeType(event: { detail: { value: string } }): void { typeIndex.value = Number(event.detail.value); }
async function submit(): Promise<void> {
  if(submitting.value)return;
  const session = getOrganizationSession();
  if (!session || !selectedClass.value || !selectedStudent.value || !content.value.trim()) {
    return void uni.showToast({ title: '请完整填写报备内容', icon: 'none' });
  }
  submitting.value = true;
  const n=revision;
  try {
    await requireExceptionAccess(session.accessToken,identity.value,'EXCEPTION_REPORT_CREATE');if(!active(n,session.accessToken))return;
    await createExceptionReport(session.accessToken, { classOrganizationId: selectedClass.value.classId,
      studentId: selectedStudent.value.studentId, exceptionType: typeOptions[typeIndex.value].value,
      content: content.value.trim(), idempotencyKey: `mini-${Date.now()}-${Math.random().toString(36).slice(2, 8)}` });
    if(!active(n,session.accessToken))return;content.value = ''; selectedStudent.value = undefined; await load();
    uni.showToast({ title: '报备已提交', icon: 'success' });
  } catch(cause) { if(active(n,session.accessToken))failed(cause,'报备提交失败'); }
  finally { if(active(n,session.accessToken))submitting.value = false; }
}
function startHandle(id: string): void { handlingId.value = id; handlingNote.value = ''; }
async function confirmHandle(item: ExceptionReport): Promise<void> {
  if(submitting.value)return;
  const session = getOrganizationSession();
  if (!session || !handlingNote.value.trim()) return void uni.showToast({ title: '请填写处理说明', icon: 'none' });
  submitting.value = true;
  const n=revision;
  try { await requireExceptionAccess(session.accessToken,identity.value,'EXCEPTION_REPORT_HANDLE');if(!active(n,session.accessToken))return;await handleExceptionReport(session.accessToken, item.id, item.versionNo, handlingNote.value.trim());if(!active(n,session.accessToken))return;handlingId.value = ''; await load(page.value); }
  catch(cause) { if(active(n,session.accessToken)){if(cause instanceof ApiError&&cause.statusCode===409){reports.value=[];total.value=0;handlingId.value='';}failed(cause,'处理失败，请刷新后重试');} }
  finally { if(active(n,session.accessToken))submitting.value = false; }
}
async function showDetails(id: string): Promise<void> {
  if(submitting.value||loading.value)return;
  const session = getOrganizationSession(); if (!session) return;
  const n=++revision;
  try { await requireExceptionAccess(session.accessToken,identity.value);if(!active(n,session.accessToken))return;const details = await getExceptionReport(session.accessToken, id);if(!active(n,session.accessToken))return;
    const history = details.actions.map((a) => `${a.operatorName}：${a.actionNote || a.actionType}`).join('\n');
    uni.showModal({ title: '报备详情', content: `${details.report.content}\n\n${history}`, showCancel: false });
  } catch(cause) { if(active(n,session.accessToken))failed(cause,'详情加载失败'); }
}
function typeName(type: ExceptionReportType): string { return typeOptions.find((item) => item.value === type)?.label || type; }
function statusName(status: ExceptionReportStatus): string { return status === 'HANDLED' ? '已处理' : '待处理'; }
function formatTime(value: string): string { return value ? value.replace('T', ' ').slice(0, 16) : ''; }
function leave(): Promise<unknown> { return uni.reLaunch({ url: '/pages/index/index' }); }
</script>

<style scoped>
.secondary-page,.prototype-workspace{padding:24rpx 28rpx 60rpx;min-height:calc(100vh - var(--window-top,0px));box-sizing:border-box;background:#f6f9f5;color:#172e25}.has-footer,.has-tabbar{padding-bottom:160rpx}.prototype-section-header{display:flex;justify-content:center;align-items:center;gap:12rpx;margin:-24rpx -28rpx 24rpx;padding:28rpx;background:linear-gradient(120deg,#206b52,#0c4f3e);color:white;font-size:32rpx;font-weight:650}.section,.form-band,.band,.heading-band{background:#fff;border:1rpx solid #e4ece3;border-radius:24rpx;padding:26rpx;margin-bottom:22rpx}.segmented,.mode-control,.tabs,.action-grid,.form-actions,.paging,.search-row,.two-columns{display:flex;align-items:center;gap:14rpx;margin:18rpx 0}.segmented button,.mode-control button,.tab,.action-grid button{flex:1}.active{background:#e2f2e6;color:#0a774d}.field-label{display:block;font-size:26rpx;margin:20rpx 0 12rpx}.picker-field,.picker-value,.field,input,textarea{background:#f7faf6;border:1rpx solid #dfe8dc;border-radius:16rpx;padding:18rpx;font-size:26rpx;box-sizing:border-box}.picker-field{display:flex;justify-content:space-between}.search-row input{flex:1;min-width:0}textarea{width:100%;min-height:180rpx}.row,.request-person,.invitation-class{display:flex;align-items:center;gap:18rpx;padding:24rpx 0;border-bottom:1rpx solid #e8eee6}.person{width:80rpx;height:80rpx;border-radius:50%;background:#e2eee3;display:flex;align-items:center;justify-content:center;flex-shrink:0}.person-copy{flex:1;min-width:0}.row-top,.section-heading,.detail-line,.list-head{display:flex;justify-content:space-between;align-items:center;gap:12rpx}.name,.section-title,.title,.form-title{font-size:30rpx;font-weight:650}.meta,.small,.counter{display:block;font-size:23rpx;color:#778179;line-height:1.7}.badge,.status{border-radius:12rpx;background:#e2f4e7;color:#13734d;padding:4rpx 12rpx;font-size:23rpx}.PENDING,.submitted{background:#fff0cb;color:#b37a20}.detail-line{font-size:25rpx;padding:15rpx 0}.error,.danger{color:#ac4e42}.empty,.state,.state-text{padding:30rpx;text-align:center;font-size:25rpx;color:#7c867d}.empty text{display:block}.bottom-actions{position:fixed;bottom:0;left:0;right:0;background:#fff;padding:24rpx 28rpx calc(24rpx + env(safe-area-inset-bottom));border-top:1rpx solid #e2ebe0;z-index:4}.qr-content{text-align:center;padding:28rpx 0}.invite-code{display:block;font-size:38rpx;font-weight:700;letter-spacing:2rpx;margin:20rpx 0}.block-action{width:100%;margin-top:24rpx}.list-band{background:#fff;border-radius:24rpx;padding:20rpx}.list-row,.record-row,.history-row{border-bottom:1rpx solid #e6ede4;padding:24rpx 0}button{font-size:26rpx;line-height:1.5;margin:0;padding:16rpx 22rpx;background:#eaf2e8;color:#24583d;border-radius:16rpx}button::after{border:0}.primary,.submit-button,.save-button{background:#176f50;color:white}.secondary,.outline{background:#f2f6ef;color:#28563e}.required{color:#ba5b4c}.reason-input,.textarea{width:100%}
</style>
