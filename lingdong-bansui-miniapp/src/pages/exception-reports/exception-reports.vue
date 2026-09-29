<template>
  <view class="page-shell">
    <button :disabled="loading||submitting" @tap="load()">刷新报备记录</button>
    <view v-if="error" class="state">{{ error }}</view>
    <view v-if="loading" class="state">正在加载</view>
    <template v-else>
      <view v-if="identity === 'teacher' && canCreate" class="form-band">
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

      <view class="list-band">
        <text class="section-title">{{ statusFilter==='SUBMITTED'?'待处理异常':'报备记录' }} · 共 {{ total }} 项</text>
        <button @tap="toggleStatus">{{ statusFilter==='SUBMITTED'?'查看全部记录':'只看待处理' }}</button>
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
        <view v-if="total"><button :disabled="page<=1||submitting" @tap="load(page-1)">上一页</button><text>第 {{ page }} 页</text><button :disabled="page*20>=total||submitting" @tap="load(page+1)">下一页</button></view>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
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
const loading = ref(true); const submitting = ref(false);
const reports = ref<ExceptionReport[]>([]); const classes = ref<TeacherWorkbenchClass[]>([]);
const students = ref<ExceptionReportStudentOption[]>([]); const selectedClass = ref<TeacherWorkbenchClass>();
const selectedStudent = ref<ExceptionReportStudentOption>(); const typeIndex = ref(0); const content = ref('');
const permissionCodes = ref<string[]>([]); const handlingId = ref(''); const handlingNote = ref('');
const enabled = ref(false);
const error=ref(''),page=ref(1),total=ref(0),statusFilter=ref<ExceptionReportStatus>();
let revision=0;
function reset(){revision++;reports.value=[];total.value=0;permissionCodes.value=[];enabled.value=false;classes.value=[];students.value=[];selectedClass.value=undefined;selectedStudent.value=undefined;handlingId.value='';handlingNote.value='';content.value='';submitting.value=false;loading.value=false;}
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

<style lang="scss" scoped>
.page-shell { min-height: 100vh; background: #f4f7f5; padding-bottom: 40rpx; }
.form-band, .list-band { border-block: 2rpx solid #dbe3df; background: #fff; }
.form-band { padding: 28rpx 40rpx; } .list-band { margin-top: 28rpx; }
.section-title { display: block; color: #1c2b28; font-size: 30rpx; font-weight: 700; }
.field, .textarea, .handle-note { box-sizing: border-box; width: 100%; margin-top: 22rpx; border: 2rpx solid #cbd7d1; border-radius: 8rpx; background: #fff; color: #1c2b28; font-size: 28rpx; }
.field { min-height: 82rpx; padding: 22rpx 24rpx; } .textarea { height: 180rpx; padding: 20rpx 24rpx; }
.primary, .outline { height: 82rpx; margin: 24rpx 0 0; border-radius: 8rpx; background: #167c5a; color: #fff; font-size: 28rpx; }
.outline { border: 2rpx solid #167c5a; background: #fff; color: #167c5a; } .compact { height: 72rpx; }
.primary::after, .outline::after { border: 0; } .state { padding: 80rpx 40rpx; color: #708078; text-align: center; }
.list-band > .section-title { padding: 28rpx 40rpx; } .report-row { padding: 28rpx 40rpx; border-top: 2rpx solid #edf1ef; }
.row-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 20rpx; }
.student { min-width: 0; color: #1c2b28; font-size: 29rpx; font-weight: 600; word-break: break-word; }
.status { flex: none; font-size: 24rpx; } .submitted { color: #a15c00; } .handled { color: #167c5a; }
.meta, .content { display: block; margin-top: 12rpx; } .meta { color: #708078; font-size: 24rpx; }
.content { color: #31433e; font-size: 27rpx; line-height: 1.6; word-break: break-word; }
.handle-note { height: 140rpx; padding: 18rpx 22rpx; }
</style>
