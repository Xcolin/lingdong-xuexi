<template>
  <view class="has-tabbar page-shell">
    <view class="toolbar">
      <view>
        <text class="page-title">{{ reviewMode ? '待审核任务' : identity === 'teacher' ? '班级任务' : '机构任务' }}</text>
        <text class="page-subtitle">{{ contextName }}</text>
      </view>
      <view class="toolbar-actions">
        <button :disabled="loading || reviewing" v-if="canReview && (!reviewMode || permissionCodes.includes('LEARNING_TASK_READ_MANAGED'))" class="review-button" @tap="toggleReviews">{{ reviewMode ? '任务' : '审核' }}</button>
        <button v-if="canCreate && !reviewMode" class="add-button" @tap="startCreate">新增</button>
      </view>
    </view>

    <button :disabled="loading || reviewing" @tap="initialize">刷新</button>
    <view v-if="loading" class="state-text">正在加载</view>
    <view v-else-if="errorMessage" class="state-text error">{{ errorMessage }}</view>
    <view v-else-if="reviewMode && reviews.length === 0" class="state-text">暂无审核待办</view>
    <view v-else-if="!reviewMode && tasks.length === 0" class="state-text">暂无任务</view>
    <view v-else-if="!reviewMode" class="task-list">
      <view v-for="task in tasks" :key="task.id" class="task-row">
        <view class="task-main">
          <view class="task-heading">
            <text class="task-title">{{ task.title }}</text>
            <text class="source-badge">{{ sourceName(task.sourceType) }}</text>
          </view>
          <text class="task-meta">{{ task.scheduledDate }} · {{ task.basePoints }}积分 · {{ statusName(task.status) }}</text>
        </view>
        <view class="task-actions">
          <button v-if="task.status === 'DRAFT'" class="text-button" @tap="editTask(task.id)">编辑</button>
          <button v-if="task.status === 'DRAFT'" class="text-button primary" @tap="publishTask(task)">下发</button>
          <button v-if="task.status === 'PUBLISHED' && canReadProgress" class="text-button" @tap="openProgress(task)">进度</button>
        </view>
      </view>
    </view>

    <view v-else class="task-list">
      <text class="task-meta">共 {{ reviewTotal }} 项待审核任务</text>
      <view v-for="review in reviews" :key="review.assignmentId" class="task-row">
        <view class="task-main">
          <text class="task-title">{{ review.title }}</text>
          <text class="task-meta">{{ review.studentName }} · {{ review.basePoints }}积分</text>
          <text class="checkin-content">{{ review.latestCheckIn.content || '学生已提交图片打卡' }}</text>
          <button v-for="file in review.latestCheckIn.attachments || []" :key="file.id" :disabled="reviewing" @tap="previewAttachment(file.contentUrl)">查看附件：{{ file.originalName }}</button>
        </view>
        <view v-if="rejectingAssignmentId === review.assignmentId" class="reject-area">
          <textarea v-model="rejectComment" class="field-textarea" maxlength="500" placeholder="请填写中性驳回意见" />
          <view class="task-actions">
            <button class="text-button" @tap="cancelReject">取消</button>
            <button class="text-button danger" :disabled="reviewing" @tap="confirmReject(review.assignmentId)">确认驳回</button>
          </view>
        </view>
        <view v-else class="task-actions">
          <button class="text-button danger" :disabled="reviewing" @tap="startReject(review.assignmentId)">驳回</button>
          <button class="text-button primary" :disabled="reviewing" @tap="approveReview(review)">通过</button>
        </view>
      </view>
    </view>

    <view v-if="reviewMode && !loading && !errorMessage && reviewTotal > 20" class="toolbar-actions"><button :disabled="reviewing || reviewPage <= 1" @tap="loadReviews(reviewPage - 1)">上一页</button><text>第 {{ reviewPage }} 页</text><button :disabled="reviewing || reviewPage * 20 >= reviewTotal" @tap="loadReviews(reviewPage + 1)">下一页</button></view>

    <view v-if="editorVisible" class="editor-band">
      <text class="section-title">{{ editingId ? '编辑任务' : '新增任务' }}</text>
      <text class="field-label">任务标题</text>
      <input v-model="form.title" class="field-input" maxlength="50" placeholder="请输入任务标题" />
      <text class="field-label">下发范围</text>
      <picker :range="targetNames" :value="targetIndex" @change="changeTarget">
        <view class="picker-field">{{ selectedTargetName || '请选择班级或组织' }}</view>
      </picker>
      <view class="two-columns">
        <view>
          <text class="field-label">难度</text>
          <picker :range="difficultyNames" :value="form.difficultyLevel - 1" @change="changeDifficulty">
            <view class="picker-field">{{ difficultyNames[form.difficultyLevel - 1] }}</view>
          </picker>
        </view>
        <view>
          <text class="field-label">时长（分钟）</text>
          <input v-model.number="form.durationMinutes" class="field-input" type="number" />
        </view>
      </view>
      <text class="field-label">计划日期</text>
      <picker mode="date" :value="form.scheduledDate" @change="changeDate">
        <view class="picker-field">{{ form.scheduledDate }}</view>
      </picker>
      <text class="field-label">备注</text>
      <textarea v-model="form.remark" class="field-textarea" maxlength="200" placeholder="可选" />
      <view class="editor-actions">
        <button class="cancel-button" @tap="closeEditor">取消</button>
        <button class="save-button" :loading="saving" :disabled="saving" @tap="saveTask">保存草稿</button>
      </view>
    </view>

    <view v-if="progressTask" class="progress-band">
      <view class="progress-heading">
        <text class="section-title">{{ progressTask.title }} · 学生进度</text>
        <button class="close-button" @tap="progressTask = null">关闭</button>
      </view>
      <view v-if="progressLoading" class="state-text compact">正在加载</view>
      <view v-else-if="progressItems.length === 0" class="state-text compact">暂无进度</view>
      <view v-for="item in progressItems" :key="item.assignmentId" class="progress-row">
        <view>
          <text class="student-name">{{ item.studentName }}</text>
          <text class="task-meta">{{ item.studentAccountMasked || '-' }} · {{ item.className || '-' }}</text>
        </view>
        <text class="status-text">{{ assignmentStatusName(item.currentStatus) }}</text>
      </view>
    </view>
  <AppTabBar v-if="identity === 'teacher'" :items="TEACHER_TABS" :active="0" />
  <AppTabBar v-else :items="ORG_TABS" :active="1" />
  </view>
</template>

<script setup lang="ts">
import AppTabBar from '@/components/AppTabBar.vue';
import { TEACHER_TABS, ORG_TABS } from '@/config/tabbar';
import { computed, reactive, ref } from 'vue';
import { onLoad, onShow, onHide, onUnload, onPullDownRefresh } from '@dcloudio/uni-app';
import { ApiError, apiUrl } from '@/api/http';
import { requireManagedAccess } from '@/api/managed-review-access';
import {
  approveManagedTaskReview, createManagedTask, getManagedTask, listManagedOrganizations,
  listManagedTaskProgress, listManagedTaskReviews, listManagedTasks, publishManagedTask,
  rejectManagedTaskReview, updateManagedTask,
  type ManagedLearningTask, type ManagedOrganizationOption, type ManagedTaskProgress,
  type ManagedTaskReview
} from '@/api/managed-learning-task';
import type { TeacherWorkbenchClass } from '@/api/teacher-workbench';
import { getOrganizationSession } from '@/session/organization-session';

type Identity = 'organization' | 'teacher';
const identity = ref<Identity>('organization');
const contextName = ref('');
const currentUserId = ref('');
const permissionCodes = ref<string[]>([]);
const targets = ref<Array<TeacherWorkbenchClass | ManagedOrganizationOption>>([]);
const tasks = ref<ManagedLearningTask[]>([]);
const loading = ref(true);
const saving = ref(false);
const errorMessage = ref('');
const editorVisible = ref(false);
const editingId = ref<string | null>(null);
const targetIndex = ref(-1);
const progressTask = ref<ManagedLearningTask | null>(null);
const progressItems = ref<ManagedTaskProgress[]>([]);
const progressLoading = ref(false);
const reviews = ref<ManagedTaskReview[]>([]);
const reviewMode = ref(false);
const reviewPage = ref(1), reviewTotal = ref(0), reviewing = ref(false);
let revision = 0;
function clearVisible() { tasks.value=[]; reviews.value=[]; permissionCodes.value=[]; targets.value=[]; contextName.value=''; editorVisible.value=false; progressTask.value=null; progressItems.value=[]; cancelReject(); }
function active(n: number, token: string) { return n===revision && getOrganizationSession()?.accessToken===token; }
function hide() { revision++; clearVisible(); loading.value=false; reviewing.value=false; }
onHide(hide); onUnload(hide);
const rejectingAssignmentId = ref<string | null>(null);
const rejectComment = ref('');
const difficultyNames = ['简单', '中等', '困难'];
const form = reactive({ title: '', difficultyLevel: 2, durationMinutes: 30, scheduledDate: today(), remark: '' });

const canCreate = computed(() => permissionCodes.value.includes('LEARNING_TASK_CREATE'));
const canReadProgress = computed(() => permissionCodes.value.includes('LEARNING_TASK_PROGRESS_READ'));
const canReview = computed(() => permissionCodes.value.includes('TASK_ASSIGNMENT_REVIEW'));
const targetNames = computed(() => targets.value.map((item) =>
  'className' in item ? `${item.schoolName} · ${item.className}` : item.name));
const selectedTargetName = computed(() => targetNames.value[targetIndex.value] || '');

onLoad((options) => {
  identity.value = options?.identity === 'teacher' ? 'teacher' : 'organization';
  reviewMode.value = options?.review === 'true';
});
onShow(initialize);
onPullDownRefresh(async () => { await initialize(); uni.stopPullDownRefresh(); });

async function initialize(): Promise<void> {
  if (reviewMode.value) { await loadReviews(1); return; }
  const token=getOrganizationSession()?.accessToken, n=++revision;
  clearVisible(); errorMessage.value=''; loading.value=true;
  if (!token) { loading.value=false; await leave(); return; }
  try {
    const {user,context}=await requireManagedAccess(token,identity.value,'LEARNING_TASK_READ_MANAGED');
    if (!active(n,token)) return;
    contextName.value=context.displayName; currentUserId.value=context.userId; permissionCodes.value=user.permissionCodes;
    const options='classes' in context ? context.classes : await listManagedOrganizations(token);
    if (!active(n,token)) return;
    targets.value=options;
    const result=await listManagedTasks(token);
    if (active(n,token)) tasks.value=result.items;
  } catch(error) { if(active(n,token)) { clearVisible(); errorMessage.value=error instanceof Error ? error.message : '任务加载失败，请刷新重试'; } }
  finally { if(n===revision) loading.value=false; }
}

async function authorize(permission: string): Promise<string> {
  const token=getOrganizationSession()?.accessToken, n=revision;
  if (!token) throw new ApiError(401, {message:'登录状态已失效'});
  try {
    const {user}=await requireManagedAccess(token,identity.value,permission);
    if (!active(n,token)) throw new ApiError(401, {message:'页面或登录状态已变化'});
    permissionCodes.value=user.permissionCodes;
    return token;
  } catch(error) { if(n===revision) { clearVisible(); errorMessage.value='任务功能或权限已变化，请刷新'; } throw error; }
}

async function loadTasks(): Promise<void> {
  const session = getOrganizationSession();
  if (!session) { await leave(); return; }
  const result = await listManagedTasks(session.accessToken);
  tasks.value = result.items;
}

function startCreate(): void {
  editingId.value = null;
  resetForm();
  editorVisible.value = true;
}

async function editTask(taskId: string): Promise<void> {
  const session = getOrganizationSession();
  if (!session) { await leave(); return; }
  const task = await getManagedTask(session.accessToken, taskId);
  editingId.value = task.id;
  form.title = task.title;
  form.difficultyLevel = task.difficultyLevel;
  form.durationMinutes = task.durationMinutes;
  form.scheduledDate = task.scheduledDate;
  form.remark = task.remark || '';
  const targetId = task.targets?.[0]?.targetId || task.sourceOrganizationId;
  targetIndex.value = targets.value.findIndex((item) => targetIdentifier(item) === targetId);
  editorVisible.value = true;
}

async function saveTask(): Promise<void> {
  const session = getOrganizationSession();
  const target = targets.value[targetIndex.value];
  if (!session || !target) return show('请选择下发范围');
  if (!form.title.trim()) return show('请输入任务标题');
  if (form.durationMinutes < 1 || form.durationMinutes > 1440) return show('任务时长应为1至1440分钟');
  const targetId = targetIdentifier(target);
  const input = {
    sourceType: identity.value === 'teacher' ? 'TEACHER' as const : 'ORGANIZATION' as const,
    sourceOrganizationId: targetId,
    title: form.title.trim(), difficultyLevel: form.difficultyLevel,
    durationMinutes: Number(form.durationMinutes), scheduledDate: form.scheduledDate,
    remark: form.remark.trim() || undefined,
    reviewerUserId: identity.value === 'teacher' ? currentUserId.value : undefined,
    targets: [{ targetType: 'ORGANIZATION' as const, targetId }], recurrenceEnabled: false
  };
  saving.value = true;
  try {
    if (editingId.value) await updateManagedTask(session.accessToken, editingId.value, input);
    else await createManagedTask(session.accessToken, input);
    closeEditor();
    await loadTasks();
    show('任务草稿已保存', 'success');
  } catch { show('任务保存失败'); }
  finally { saving.value = false; }
}

async function publishTask(task: ManagedLearningTask): Promise<void> {
  const confirmed = await new Promise<boolean>((resolve) => uni.showModal({
    title: '确认下发', content: `将任务“${task.title}”下发给所选学生。`,
    success: (result) => resolve(result.confirm), fail: () => resolve(false)
  }));
  if (!confirmed) return;
  const session = getOrganizationSession();
  if (!session) { await leave(); return; }
  try { await publishManagedTask(session.accessToken, task.id); await loadTasks(); show('任务已下发', 'success'); }
  catch { show('任务下发失败'); }
}

async function openProgress(task: ManagedLearningTask): Promise<void> {
  const session = getOrganizationSession();
  if (!session) { await leave(); return; }
  progressTask.value = task;
  progressItems.value = [];
  progressLoading.value = true;
  try { progressItems.value = (await listManagedTaskProgress(session.accessToken, task.id)).items; }
  catch { show('学生进度加载失败'); }
  finally { progressLoading.value = false; }
}

async function toggleReviews(): Promise<void> {
  if (reviewing.value || loading.value) return;
  reviewMode.value = !reviewMode.value;
  await initialize();
}

async function loadReviews(target=1): Promise<void> {
  const token=getOrganizationSession()?.accessToken,n=++revision;
  clearVisible(); errorMessage.value=''; loading.value=true;
  if(!token) { loading.value=false; await leave(); return; }
  try {
    const {user,context}=await requireManagedAccess(token,identity.value,'TASK_ASSIGNMENT_REVIEW');
    if(!active(n,token)) return;
    contextName.value=context.displayName; permissionCodes.value=user.permissionCodes;
    const result=await listManagedTaskReviews(token,target,20);
    if(active(n,token)) { reviews.value=result.items; reviewPage.value=result.page; reviewTotal.value=result.total; }
  } catch(error) { if(active(n,token)) { clearVisible(); errorMessage.value=error instanceof Error ? error.message : '审核待办加载失败，请刷新重试'; } }
  finally { if(n===revision) loading.value=false; }
}

async function approveReview(review: ManagedTaskReview): Promise<void> {
  if(reviewing.value || loading.value) return;
  reviewing.value=true;
  const actionRevision=revision;
  const confirmed = await new Promise<boolean>((resolve) => uni.showModal({
    title: '确认审核通过', content: `通过“${review.title}”并发放${review.basePoints}积分。`,
    success: (result) => resolve(result.confirm), fail: () => resolve(false)
  }));
  if(actionRevision!==revision) return;
  if (!confirmed) { reviewing.value=false; return; }
  try { const token=await authorize('TASK_ASSIGNMENT_REVIEW'); await approveManagedTaskReview(token, review.assignmentId, review.latestCheckIn.id); if(!active(actionRevision,token)) return; reviewing.value=false; await loadReviews(); show('审核已通过', 'success'); }
  catch (error) { if(actionRevision===revision) reviewFailure(error); }
  finally { if(actionRevision===revision) reviewing.value=false; }
}

function startReject(assignmentId: string): void { rejectingAssignmentId.value = assignmentId; rejectComment.value = ''; }
function cancelReject(): void { rejectingAssignmentId.value = null; rejectComment.value = ''; }
async function confirmReject(assignmentId: string): Promise<void> {
  if(reviewing.value || loading.value) return;
  const comment = rejectComment.value.trim();
  if (!comment) return show('请填写驳回意见');
  const review = reviews.value.find(item => item.assignmentId === assignmentId);
  if (!review) return show('审核内容已变化，请刷新');
  reviewing.value=true; const actionRevision=revision;
  try { const token=await authorize('TASK_ASSIGNMENT_REVIEW'); await rejectManagedTaskReview(token, assignmentId, comment, review.latestCheckIn.id); if(!active(actionRevision,token)) return; cancelReject(); reviewing.value=false; await loadReviews(); show('已退回学生完善', 'success'); }
  catch (error) { if(actionRevision===revision) reviewFailure(error); }
  finally { if(actionRevision===revision) reviewing.value=false; }
}

async function previewAttachment(url: string): Promise<void> {
  if(reviewing.value || loading.value) return;
  const actionRevision=revision; reviewing.value=true;
  try {
    const token=await authorize('TASK_ASSIGNMENT_REVIEW');
    const file=await new Promise<string>((resolve,reject)=>uni.downloadFile({
      url:apiUrl(url),header:{Authorization:`Bearer ${token}`},
      success:response=>response.statusCode===200 ? resolve(response.tempFilePath) : reject(new Error('附件读取失败')),
      fail:()=>reject(new Error('附件读取失败'))
    }));
    if(active(actionRevision,token)) await uni.previewImage({urls:[file]});
  } catch(error) { if(actionRevision===revision) reviewFailure(error); }
  finally { if(actionRevision===revision) reviewing.value=false; }
}

function reviewFailure(error: unknown): void {
  if (error instanceof ApiError && [401, 403, 404, 409].includes(error.statusCode)) {
    reviews.value = [];
    cancelReject();
    errorMessage.value='审核内容或权限已变化，请刷新';
    show(errorMessage.value);
    return;
  }
  show('审核处理失败，请重试');
}

function changeTarget(event: { detail: { value: string } }): void { targetIndex.value = Number(event.detail.value); }
function changeDifficulty(event: { detail: { value: string } }): void { form.difficultyLevel = Number(event.detail.value) + 1; }
function changeDate(event: { detail: { value: string } }): void { form.scheduledDate = event.detail.value; }
function targetIdentifier(item: TeacherWorkbenchClass | ManagedOrganizationOption): string { return 'classId' in item ? item.classId : item.id; }
function sourceName(source: string): string { return source === 'TEACHER' ? '教师' : '机构'; }
function statusName(status: string): string { return status === 'DRAFT' ? '草稿' : '已下发'; }
function assignmentStatusName(status: string): string { return ({ PENDING_CLAIM: '待认领', IN_PROGRESS: '进行中', PENDING_REVIEW: '待审核', NEEDS_IMPROVEMENT: '待优化', EXEMPT: '免执行', COMPLETED: '已完成' } as Record<string, string>)[status] || status; }
function closeEditor(): void { editorVisible.value = false; editingId.value = null; }
function resetForm(): void { form.title = ''; form.difficultyLevel = 2; form.durationMinutes = 30; form.scheduledDate = today(); form.remark = ''; targetIndex.value = targets.value.length === 1 ? 0 : -1; }
function today(): string { const now = new Date(); return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`; }
function show(title: string, icon: 'none' | 'success' = 'none'): void { uni.showToast({ title, icon }); }
function leave(): Promise<unknown> { return uni.reLaunch({ url: '/pages/index/index' }); }
</script>

<style lang="scss" scoped>
.page-shell { min-height: 100vh; padding-bottom: 56rpx; background: #f4f7f5; }
.toolbar { min-height: 136rpx; display: flex; align-items: center; justify-content: space-between; gap: 24rpx; padding: 28rpx 36rpx; box-sizing: border-box; border-bottom: 2rpx solid #dbe3df; background: #fff; }
.page-title, .page-subtitle, .task-title, .task-meta, .section-title, .field-label, .student-name { display: block; }
.page-title { color: #1c2b28; font-size: 38rpx; font-weight: 700; }
.page-subtitle { margin-top: 8rpx; color: #708078; font-size: 23rpx; }
.toolbar-actions { display: flex; gap: 12rpx; }.add-button, .review-button { width: 112rpx; height: 68rpx; margin: 0; border-radius: 8rpx; background: #167c5a; color: #fff; font-size: 27rpx; }.review-button { border: 2rpx solid #167c5a; background: #fff; color: #167c5a; }
.add-button::after, .review-button::after, .text-button::after, .cancel-button::after, .save-button::after, .close-button::after { border: 0; }
.state-text { min-height: 320rpx; display: flex; align-items: center; justify-content: center; color: #708078; font-size: 28rpx; }
.state-text.compact { min-height: 160rpx; }.state-text.error { color: #a54433; }
.task-list, .editor-band, .progress-band { margin-top: 24rpx; border-block: 2rpx solid #dbe3df; background: #fff; }
.task-row { padding: 26rpx 36rpx; border-top: 2rpx solid #edf1ef; }.task-row:first-child { border-top: 0; }
.task-main { min-width: 0; }.task-heading { display: flex; align-items: center; gap: 12rpx; }
.task-title { min-width: 0; flex: 1; color: #1c2b28; font-size: 29rpx; font-weight: 600; word-break: break-word; }
.source-badge { flex: none; padding: 5rpx 12rpx; border-radius: 6rpx; background: #eaf4ef; color: #167c5a; font-size: 21rpx; }
.task-meta { margin-top: 10rpx; color: #708078; font-size: 23rpx; }
.task-actions { display: flex; justify-content: flex-end; gap: 12rpx; margin-top: 20rpx; }
.text-button { width: 112rpx; height: 60rpx; margin: 0; padding: 0; border: 2rpx solid #b9c8c2; border-radius: 7rpx; background: #fff; color: #40514c; font-size: 24rpx; }
.text-button.primary { border-color: #167c5a; color: #167c5a; }
.text-button.danger { border-color: #b54732; color: #b54732; }.checkin-content { display: block; margin-top: 16rpx; color: #40514c; font-size: 25rpx; line-height: 1.6; word-break: break-word; }.reject-area { margin-top: 20rpx; }
.editor-band, .progress-band { padding: 30rpx 36rpx; }.section-title { color: #1c2b28; font-size: 30rpx; font-weight: 700; }
.field-label { margin: 26rpx 0 10rpx; color: #40514c; font-size: 24rpx; font-weight: 600; }
.field-input, .picker-field { width: 100%; height: 82rpx; padding: 0 22rpx; box-sizing: border-box; border: 2rpx solid #c8d3cf; border-radius: 8rpx; background: #fff; color: #1c2b28; font-size: 27rpx; }
.picker-field { display: flex; align-items: center; }.field-textarea { width: 100%; min-height: 150rpx; padding: 18rpx 22rpx; box-sizing: border-box; border: 2rpx solid #c8d3cf; border-radius: 8rpx; font-size: 27rpx; }
.two-columns { display: grid; grid-template-columns: 1fr 1fr; gap: 20rpx; }.editor-actions { display: grid; grid-template-columns: 1fr 1fr; gap: 20rpx; margin-top: 32rpx; }
.cancel-button, .save-button { height: 80rpx; margin: 0; border-radius: 8rpx; font-size: 26rpx; }.cancel-button { background: #edf1ef; color: #40514c; }.save-button { background: #167c5a; color: #fff; }
.progress-heading, .progress-row { display: flex; align-items: center; justify-content: space-between; gap: 18rpx; }.close-button { width: 100rpx; height: 58rpx; margin: 0; background: transparent; color: #9a4b38; font-size: 24rpx; }
.progress-row { padding: 22rpx 0; border-top: 2rpx solid #edf1ef; }.student-name { color: #1c2b28; font-size: 27rpx; }.status-text { flex: none; color: #167c5a; font-size: 24rpx; }
</style>
