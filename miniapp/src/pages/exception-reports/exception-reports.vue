<template>
  <view class="page-shell">
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
        <text class="section-title">报备记录</text>
        <view v-if="reports.length === 0" class="state">暂无报备记录</view>
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
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { onLoad, onPullDownRefresh, onShow } from '@dcloudio/uni-app';
import { getMiniappCapabilities } from '@/api/capability';
import { getTeacherWorkbenchContext, type TeacherWorkbenchClass } from '@/api/teacher-workbench';
import { getOrganizationWorkbenchContext } from '@/api/organization-workbench';
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
const typeOptions: Array<{ label: string; value: ExceptionReportType }> = [
  { label: '出勤异常', value: 'ATTENDANCE' }, { label: '学习状态异常', value: 'LEARNING_STATUS' },
  { label: '心态异常', value: 'MENTAL_STATE' }
];
const canCreate = computed(() => enabled.value && permissionCodes.value.includes('EXCEPTION_REPORT_CREATE'));
const canHandle = computed(() => enabled.value && permissionCodes.value.includes('EXCEPTION_REPORT_HANDLE'));

onLoad((query) => { identity.value = query?.identity === 'organization' ? 'organization' : 'teacher'; });
onShow(load);
onPullDownRefresh(async () => { await load(); uni.stopPullDownRefresh(); });

async function load(): Promise<void> {
  const session = getOrganizationSession();
  if (!session) { await leave(); return; }
  loading.value = true;
  try {
    const capabilities = await getMiniappCapabilities();
    enabled.value = capabilities.studentExceptionReportEnabled === true;
    if (!enabled.value) { await leave(); return; }
    if (identity.value === 'teacher') {
      const context = await getTeacherWorkbenchContext(session.accessToken);
      permissionCodes.value = context.permissionCodes; classes.value = context.classes;
    } else {
      const context = await getOrganizationWorkbenchContext(session.accessToken);
      permissionCodes.value = context.permissionCodes;
    }
    if (!permissionCodes.value.includes('EXCEPTION_REPORT_READ')) { await leave(); return; }
    reports.value = (await listExceptionReports(session.accessToken)).items;
  } catch { uni.showToast({ title: '报备记录加载失败', icon: 'none' }); }
  finally { loading.value = false; }
}
async function changeClass(event: { detail: { value: string } }): Promise<void> {
  selectedClass.value = classes.value[Number(event.detail.value)]; selectedStudent.value = undefined;
  const session = getOrganizationSession();
  if (session && selectedClass.value) students.value = await listExceptionReportStudents(session.accessToken, selectedClass.value.classId);
}
function changeStudent(event: { detail: { value: string } }): void { selectedStudent.value = students.value[Number(event.detail.value)]; }
function changeType(event: { detail: { value: string } }): void { typeIndex.value = Number(event.detail.value); }
async function submit(): Promise<void> {
  const session = getOrganizationSession();
  if (!session || !selectedClass.value || !selectedStudent.value || !content.value.trim()) {
    return void uni.showToast({ title: '请完整填写报备内容', icon: 'none' });
  }
  submitting.value = true;
  try {
    await createExceptionReport(session.accessToken, { classOrganizationId: selectedClass.value.classId,
      studentId: selectedStudent.value.studentId, exceptionType: typeOptions[typeIndex.value].value,
      content: content.value.trim(), idempotencyKey: `mini-${Date.now()}-${Math.random().toString(36).slice(2, 8)}` });
    content.value = ''; selectedStudent.value = undefined; await load();
    uni.showToast({ title: '报备已提交', icon: 'success' });
  } catch { uni.showToast({ title: '报备提交失败', icon: 'none' }); }
  finally { submitting.value = false; }
}
function startHandle(id: string): void { handlingId.value = id; handlingNote.value = ''; }
async function confirmHandle(item: ExceptionReport): Promise<void> {
  const session = getOrganizationSession();
  if (!session || !handlingNote.value.trim()) return void uni.showToast({ title: '请填写处理说明', icon: 'none' });
  submitting.value = true;
  try { await handleExceptionReport(session.accessToken, item.id, item.versionNo, handlingNote.value.trim()); handlingId.value = ''; await load(); }
  catch { uni.showToast({ title: '处理失败，请刷新后重试', icon: 'none' }); }
  finally { submitting.value = false; }
}
async function showDetails(id: string): Promise<void> {
  const session = getOrganizationSession(); if (!session) return;
  try { const details = await getExceptionReport(session.accessToken, id);
    const history = details.actions.map((a) => `${a.operatorName}：${a.actionNote || a.actionType}`).join('\n');
    uni.showModal({ title: '报备详情', content: `${details.report.content}\n\n${history}`, showCancel: false });
  } catch { uni.showToast({ title: '详情加载失败', icon: 'none' }); }
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
