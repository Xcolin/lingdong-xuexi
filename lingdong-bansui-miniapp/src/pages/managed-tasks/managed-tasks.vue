<template>
  <view class="has-tabbar page-shell prototype-workspace">
    <view class="prototype-header"><PrototypeIcon name="task" tone="white" :size="26" /><text class="prototype-header-title">任务管理</text></view>
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
          <button v-if="task.status === 'DRAFT' && canCreate" :disabled="saving||publishing||editingLoading" class="text-button" @tap="editTask(task.id)">编辑</button>
          <button v-if="task.status === 'DRAFT' && permissionCodes.includes('LEARNING_TASK_PUBLISH')" :disabled="publishing||saving" class="text-button primary" @tap="publishTask(task)">下发</button>
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
      <view class="publish-steps"><text>{{confirming?'2 确认发布':'1 填写任务'}}</text></view>
      <view v-if="!confirming">
      <text class="field-label">任务标题</text>
      <input :disabled="saving||editingLoading" v-model="form.title" class="field-input" maxlength="50" placeholder="请输入任务标题" />
      <text class="field-label">下发范围</text>
      <picker :disabled="saving||editingLoading" :range="targetNames" :value="targetIndex" @change="changeTarget">
        <view class="picker-field">{{ selectedTargetName || '请选择班级或组织' }}</view>
      </picker>
      <view class="target-mode"><button :class="{selected:!studentMode}" :disabled="saving||editingLoading" @tap="setStudentMode(false)">该范围全部孩子</button><button :class="{selected:studentMode}" :disabled="saving||editingLoading" @tap="setStudentMode(true)">指定孩子</button></view>
      <view v-if="studentMode" class="target-children"><text v-if="studentsLoading">正在加载授权孩子</text><text v-else-if="!studentOptions.length">该范围暂无可选择孩子</text><checkbox-group @change="selectStudents"><label v-for="child in studentOptions" :key="child.id" class="target-child"><checkbox :value="child.id" :checked="selectedStudents.includes(child.id)" :disabled="saving||studentsLoading" color="#184f40" /><view><text class="target-child-name">{{child.studentName}}</text><text v-if="child.currentClassName" class="target-child-class">{{child.currentClassName}}</text></view></label></checkbox-group></view>
      <view class="two-columns">
        <view>
          <text class="field-label">难度</text>
          <picker :disabled="saving||editingLoading" :range="difficultyNames" :value="form.difficultyLevel - 1" @change="changeDifficulty">
            <view class="picker-field">{{ difficultyNames[form.difficultyLevel - 1] }}</view>
          </picker>
        </view>
        <view>
          <text class="field-label">时长（分钟）</text>
          <input :disabled="saving||editingLoading" v-model.number="form.durationMinutes" class="field-input" type="number" />
        </view>
      </view>
      <text class="field-label">任务日期</text>
      <picker :disabled="saving||editingLoading" mode="date" :value="form.scheduledDate" @change="changeDate">
        <view class="picker-field">{{ form.scheduledDate }}</view>
      </picker>
      <text class="field-label">备注</text>
      <view class="recurrence-controls"><label>重复任务 <switch :checked="!!form.recurrenceEnabled" :disabled="saving||editingLoading" @change="recurrenceChanged" /></label>
      <template v-if="form.recurrenceEnabled"><picker :range="weekdayCapability?['每天','每周','自定义星期']:['每天']" :value="['DAILY','WEEKLY','CUSTOM'].indexOf(form.recurrenceFrequency||'DAILY')" :disabled="saving||editingLoading" @change="frequencyChanged"><view class="field">重复频率：{{frequencyLabels[form.recurrenceFrequency||'DAILY']}}</view></picker>
      <view v-if="form.recurrenceFrequency!=='DAILY'" class="weekday-options"><button v-for="(label,index) in weekdayLabels" :key="label" :class="{selected:form.recurrenceWeekdays?.includes(index+1)}" :disabled="saving||editingLoading" @tap="toggleWeekday(index+1)">{{label}}</button></view>
      <picker mode="date" :value="form.recurrenceEndDate||form.scheduledDate" :start="form.scheduledDate" :disabled="saving||editingLoading" @change="form.recurrenceEndDate=value($event)"><view class="field">重复截止：{{form.recurrenceEndDate||'不设截止日期'}}</view></picker><button :disabled="saving||editingLoading" @tap="form.recurrenceEndDate=null">不设截止日期</button></template></view>
      <textarea :disabled="saving||editingLoading" v-model="form.remark" class="field-textarea" maxlength="200" placeholder="可选" />
      </view><view v-else class="confirm-card"><text class="task-title">{{form.title}}</text><text class="field-label">下发范围</text><text>{{originalDraft&&!targetChanged?'保留原有全部目标':studentMode?studentOptions.filter(x=>selectedStudents.includes(x.id)).map(x=>x.studentName).join('、'):selectedTargetName+' · 全部孩子'}}</text><text class="field-label">任务日期</text><text>{{form.scheduledDate}}</text><text class="field-label">难度 / 时长</text><text>{{difficultyNames[form.difficultyLevel-1]}} · {{form.durationMinutes}}分钟</text><text class="field-label">任务要求</text><text>{{form.remark||'未填写备注'}}</text></view>
      <view class="editor-actions">
        <button class="cancel-button" :disabled="saving" @tap="confirming?confirming=false:closeEditor()">{{confirming?'返回修改':'取消'}}</button>
        <button class="save-button" :loading="saving" :disabled="saving" @tap="saveTask(false)">保存草稿</button><button v-if="permissionCodes.includes('LEARNING_TASK_PUBLISH')" class="save-button" :disabled="saving" :loading="saving" @tap="confirming?saveTask(true):confirming=true">{{confirming?'发布任务':'下一步'}}</button>
      </view>
    </view>

    <view v-if="progressTask" class="progress-band">
      <view class="progress-heading">
        <text class="section-title">{{ progressTask.title }} · 完成进度</text>
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
  <AppTabBar v-if="!editorVisible && identity === 'teacher'" :items="TEACHER_TABS" :active="1" />
  <AppTabBar v-if="!editorVisible && identity !== 'teacher'" :items="ORG_TABS" :active="1" />
  </view>
</template>

<script setup lang="ts">
import {getMiniappCapabilities} from '@/api/capability';
import {recurrenceFields,recurrenceError,type FamilyForm} from '@/models/family-task-draft';
import PrototypeIcon from '@/components/PrototypeIcon.vue';
import AppTabBar from '@/components/AppTabBar.vue';
import { TEACHER_TABS, ORG_TABS } from '@/config/tabbar';
import { computed, reactive, ref } from 'vue';
import { managedTargets } from '@/model/managed-task-targets';
import { onLoad, onShow, onHide, onUnload, onPullDownRefresh } from '@dcloudio/uni-app';
import { ApiError, apiUrl } from '@/api/http';
import { requireManagedAccess } from '@/api/managed-review-access';
import {
  approveManagedTaskReview, createManagedTask, getManagedTask, listManagedOrganizations,
  listManagedTaskProgress, listManagedTaskReviews, listManagedTasks, publishManagedTask,
  rejectManagedTaskReview, updateManagedTask, listManagedStudents, type ManagedStudentOption,
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
const saving = ref(false), publishing=ref(false),editingLoading=ref(false);
let editRevision=0;
const errorMessage = ref('');
const editorVisible = ref(false);
const editingId = ref<string | null>(null);
const originalDraft=ref<ManagedLearningTask|null>(null),targetChanged=ref(false),confirming=ref(false);
const targetIndex = ref(-1);
const studentOptions=ref<ManagedStudentOption[]>([]), selectedStudents=ref<string[]>([]), studentMode=ref(false), studentsLoading=ref(false);
let studentRevision=0;
const progressTask = ref<ManagedLearningTask | null>(null);
const progressItems = ref<ManagedTaskProgress[]>([]);
const progressLoading = ref(false);
const reviews = ref<ManagedTaskReview[]>([]);
const reviewMode = ref(false);
const createRequested=ref(false);
const reviewPage = ref(1), reviewTotal = ref(0), reviewing = ref(false);
let revision = 0;
let selectedScope = '';
function scopeKey(){const selected=uni.getStorageSync('lingdong.selected-organization') as {organizationId?:string;accessToken?:string}|undefined;return `${selected?.accessToken||''}:${selected?.organizationId||''}`;}
function clearVisible() { studentRevision++;studentOptions.value=[];selectedStudents.value=[];studentMode.value=false;studentsLoading.value=false; tasks.value=[]; reviews.value=[]; permissionCodes.value=[]; targets.value=[]; contextName.value=''; editorVisible.value=false; progressTask.value=null; progressItems.value=[]; cancelReject(); }
function active(n: number, token: string) { return n===revision && getOrganizationSession()?.accessToken===token && selectedScope===scopeKey(); }
function hide() { revision++;editRevision++;editingLoading.value=false; clearVisible(); loading.value=false; reviewing.value=false;saving.value=false;publishing.value=false; }
onHide(hide); onUnload(hide);
const rejectingAssignmentId = ref<string | null>(null);
const rejectComment = ref('');
const difficultyNames = ['简单', '中等', '困难'];
const form = reactive<FamilyForm>({ title: '', difficultyLevel: 2, durationMinutes: 30, scheduledDate: today(), remark: '', studentIds:[], recurrenceEnabled:false, recurrenceFrequency:'DAILY', recurrenceWeekdays:[], recurrenceEndDate:null });

const canCreate = computed(() => permissionCodes.value.includes('LEARNING_TASK_CREATE'));
const canReadProgress = computed(() => permissionCodes.value.includes('LEARNING_TASK_PROGRESS_READ'));
const canReview = computed(() => permissionCodes.value.includes('TASK_ASSIGNMENT_REVIEW'));
const targetNames = computed(() => targets.value.map((item) =>
  'className' in item ? `${item.schoolName} · ${item.className}` : item.name));
const selectedTargetName = computed(() => targetNames.value[targetIndex.value] || '');

onLoad((options) => {
  identity.value = options?.identity === 'teacher' ? 'teacher' : 'organization';
  createRequested.value=options?.create==='true';
  reviewMode.value = options?.review === 'true';
});
onShow(initialize);
onPullDownRefresh(async () => { await initialize(); uni.stopPullDownRefresh(); });

async function initialize(): Promise<void> {
  selectedScope=scopeKey();
  if (reviewMode.value) { await loadReviews(1); return; }
  const token=getOrganizationSession()?.accessToken, n=++revision;
  clearVisible(); errorMessage.value=''; loading.value=true;
  if (!token) { loading.value=false; await leave(); return; }
  try {
    weekdayCapability.value=false;
    const capability=await getMiniappCapabilities();
    if (!active(n,token)) return;
    weekdayCapability.value=capability.taskWeekdayRecurrenceEnabled===true;
    const {user,context}=await requireManagedAccess(token,identity.value,'LEARNING_TASK_READ_MANAGED');
    if (!active(n,token)) return;
    contextName.value=context.displayName; currentUserId.value=context.userId; permissionCodes.value=user.permissionCodes;
    const scopedOptions=await listManagedOrganizations(token,identity.value==='teacher'?'TEACHER':'ORGANIZATION');
    const ids=new Set(scopedOptions.map(item=>item.id));
    const options='classes' in context ? context.classes.filter(item=>ids.has(item.classId)) : scopedOptions;
    if (!active(n,token)) return;
    targets.value=options;
    const result=await listManagedTasks(token,1,identity.value==='teacher'?'TEACHER':'ORGANIZATION');
    if (active(n,token)){tasks.value=result.items;if(createRequested.value&&canCreate.value){startCreate();createRequested.value=false;}}
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
  const n=revision;const result = await listManagedTasks(session.accessToken,1,identity.value==='teacher'?'TEACHER':'ORGANIZATION');
  if(active(n,session.accessToken))tasks.value = result.items;
}

function startCreate(): void {
  if(saving.value||publishing.value||editingLoading.value)return;
  editingId.value = null;
  resetForm();
  editorVisible.value = true;
}

async function editTask(taskId: string): Promise<void> {
  if(saving.value||publishing.value)return;const serial=++editRevision;editingLoading.value=true;
  const session = getOrganizationSession();
  if (!session) { await leave(); return; }
  const n=revision;let task:ManagedLearningTask;try{task=await getManagedTask(session.accessToken,taskId);}catch(e){if(active(n,session.accessToken)&&serial===editRevision)show(e instanceof Error?e.message:'草稿加载失败');return;}finally{if(active(n,session.accessToken)&&serial===editRevision)editingLoading.value=false;}
  if(!active(n,session.accessToken)||serial!==editRevision||saving.value)return;
  studentRevision++;studentOptions.value=[];selectedStudents.value=[];studentMode.value=false;studentsLoading.value=false;originalDraft.value=task;targetChanged.value=false;confirming.value=false;
  editingId.value = task.id;
  form.title = task.title;
  form.difficultyLevel = task.difficultyLevel;
  form.durationMinutes = task.durationMinutes;
  form.scheduledDate = task.scheduledDate;
  form.remark = task.remark || '';
  Object.assign(form,{recurrenceEnabled:task.recurrenceEnabled,recurrenceFrequency:task.recurrenceFrequency||'DAILY',recurrenceWeekdays:[...(task.recurrenceWeekdays??[])],recurrenceEndDate:task.recurrenceEndDate??null});
  const targetId = task.targets?.[0]?.targetId || task.sourceOrganizationId;
  targetIndex.value = targets.value.findIndex((item) => targetIdentifier(item) === targetId);
  editorVisible.value = true;
}

async function saveTask(publish=false): Promise<void> {
  if(saving.value||editingLoading.value)return;
  const draftId=editingId.value;
  if(publish&&!permissionCodes.value.includes('LEARNING_TASK_PUBLISH'))return show('无发布权限');
  const session = getOrganizationSession();
  const target = targets.value[targetIndex.value];
  const n=revision;const preserving=!!originalDraft.value&&!targetChanged.value;
  if (!session || (!target&&!preserving)) return show('请选择下发范围');
  if (recurrenceError(form,weekdayCapability.value)) return show(recurrenceError(form,weekdayCapability.value));
  if (!form.title.trim()) return show('请输入任务标题');
  if (form.durationMinutes < 1 || form.durationMinutes > 1440) return show('任务时长应为1至1440分钟');
  const targetId = target?targetIdentifier(target):originalDraft.value!.sourceOrganizationId;
  if(!preserving&&studentMode.value&&!selectedStudents.value.length)return show('请选择指定孩子');
  const input = {
    sourceType:originalDraft.value?.sourceType??(identity.value === 'teacher' ? 'TEACHER' as const : 'ORGANIZATION' as const),
    sourceOrganizationId: originalDraft.value?.sourceOrganizationId||targetId,
    title: form.title.trim(), difficultyLevel: form.difficultyLevel,
    durationMinutes: Number(form.durationMinutes), scheduledDate: form.scheduledDate,
    remark: form.remark.trim() || undefined,
    reviewerUserId: originalDraft.value?.reviewerUserId ?? (identity.value === 'teacher' ? currentUserId.value : undefined),
    categoryCode:originalDraft.value?.categoryCode,tagCodes:originalDraft.value?.tagCodes,...recurrenceFields(form),
    targets: managedTargets(originalDraft.value?.targets,preserving,studentMode.value,targetId,selectedStudents.value)
  };
  saving.value = true;
  try {
    await authorize('LEARNING_TASK_CREATE');if(!active(n,session.accessToken))return;
    if(!preserving){
      const options=await listManagedOrganizations(session.accessToken,input.sourceType);
      if(!active(n,session.accessToken))return;
      if(!options.some(x=>x.id===targetId))throw new Error('下发范围权限已变化，请重新选择');
      if(studentMode.value){
        const children=await listManagedStudents(session.accessToken,input.sourceType,targetId);
        if(!active(n,session.accessToken))return;
        if(!input.targets.length||input.targets.some(x=>!children.some(child=>child.id===x.targetId)))throw new Error('请选择当前范围内有效孩子');
      }
    }
    const saved=draftId?await updateManagedTask(session.accessToken,draftId,input):await createManagedTask(session.accessToken,input);
    if(!active(n,session.accessToken))return;
    editingId.value=saved.id;originalDraft.value=saved;targetChanged.value=false;
    if(publish){await authorize('LEARNING_TASK_PUBLISH');if(!active(n,session.accessToken))return;await publishManagedTask(session.accessToken,saved.id);}
    if(!active(n,session.accessToken))return;
    closeEditor();
    await loadTasks();
    if(active(n,session.accessToken))show(publish?'任务已发布':'任务草稿已保存', 'success');
  } catch(e) { if(active(n,session.accessToken))show(e instanceof Error?e.message:'任务保存或发布失败，已保存的草稿可重试'); }
  finally { if(n===revision)saving.value = false; }
}

async function publishTask(task: ManagedLearningTask): Promise<void> {
 if(publishing.value||saving.value||loading.value||editingLoading.value)return;const session=getOrganizationSession(),n=revision;if(!session){await leave();return;}publishing.value=true;
 try{const confirmed=await new Promise<boolean>(resolve=>uni.showModal({title:'确认发布',content:`发布“${task.title}”？`,success:r=>resolve(r.confirm),fail:()=>resolve(false)}));if(!confirmed||!active(n,session.accessToken))return;await authorize('LEARNING_TASK_PUBLISH');if(!active(n,session.accessToken))return;await publishManagedTask(session.accessToken,task.id);if(!active(n,session.accessToken))return;await loadTasks();if(active(n,session.accessToken))show('任务已发布','success');}catch(e){if(active(n,session.accessToken))show(e instanceof Error?e.message:'发布失败');}finally{if(n===revision)publishing.value=false;}
}

async function openProgress(task: ManagedLearningTask): Promise<void> {
  const session = getOrganizationSession();
  if (!session) { await leave(); return; }
  progressTask.value = task;
  progressItems.value = [];
  progressLoading.value = true;
  try { progressItems.value = (await listManagedTaskProgress(session.accessToken, task.id)).items; }
  catch { show('完成进度加载失败'); }
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
    weekdayCapability.value=false;
    const capability=await getMiniappCapabilities();
    if (!active(n,token)) return;
    weekdayCapability.value=capability.taskWeekdayRecurrenceEnabled===true;
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

function changeTarget(event: { detail: { value: string } }): void { targetChanged.value=true;targetIndex.value = Number(event.detail.value); selectedStudents.value=[];studentOptions.value=[];if(studentMode.value)void loadStudentOptions(); }
const weekdayCapability=ref(false);
const weekdayLabels=['周一','周二','周三','周四','周五','周六','周日'];
const frequencyLabels={DAILY:'每天',WEEKLY:'每周',CUSTOM:'自定义星期'};
function recurrenceChanged(event:Event){form.recurrenceEnabled=(event as unknown as {detail:{value:boolean}}).detail.value;}
function frequencyChanged(event:Event){form.recurrenceFrequency=(['DAILY','WEEKLY','CUSTOM'] as const)[Number(value(event))];form.recurrenceWeekdays=form.recurrenceFrequency==='DAILY'?[]:[new Date(form.scheduledDate+'T00:00:00Z').getUTCDay()||7];}
function toggleWeekday(day:number){if(form.recurrenceFrequency==='WEEKLY'){form.recurrenceWeekdays=[day];return;}const days=form.recurrenceWeekdays??[];form.recurrenceWeekdays=days.includes(day)?days.filter(x=>x!==day):[...days,day].sort((a,b)=>a-b);}
function value(event:Event){return (event as unknown as {detail:{value:string}}).detail.value;}
async function loadStudentOptions(){
  const target=targets.value[targetIndex.value],token=getOrganizationSession()?.accessToken,n=revision,serial=++studentRevision;
  studentOptions.value=[];if(!target||!token)return;studentsLoading.value=true;
  try{const children=await listManagedStudents(token,identity.value==='teacher'?'TEACHER':'ORGANIZATION',targetIdentifier(target));if(active(n,token)&&serial===studentRevision)studentOptions.value=children;}
  catch(e){if(active(n,token)&&serial===studentRevision)show(e instanceof Error?e.message:'孩子候选加载失败');}
  finally{if(active(n,token)&&serial===studentRevision)studentsLoading.value=false;}
}
function setStudentMode(mode:boolean){studentMode.value=mode;targetChanged.value=true;selectedStudents.value=[];studentRevision++;studentsLoading.value=false;if(mode)void loadStudentOptions();}
function selectStudents(event:{detail:{value:string[]}}){if(saving.value||studentsLoading.value)return;targetChanged.value=true;selectedStudents.value=event.detail.value;}
function changeDifficulty(event: { detail: { value: string } }): void { form.difficultyLevel = Number(event.detail.value) + 1; }
function changeDate(event: { detail: { value: string } }): void { form.scheduledDate = event.detail.value; }
function targetIdentifier(item: TeacherWorkbenchClass | ManagedOrganizationOption): string { return 'classId' in item ? item.classId : item.id; }
function sourceName(source: string): string { return source === 'TEACHER' ? '教师' : '机构'; }
function statusName(status: string): string { return status === 'DRAFT' ? '草稿' : '已下发'; }
function assignmentStatusName(status: string): string { return ({ PENDING_CLAIM: '待认领', IN_PROGRESS: '进行中', PENDING_REVIEW: '待审核', NEEDS_IMPROVEMENT: '待优化', EXEMPT: '免执行', COMPLETED: '已完成' } as Record<string, string>)[status] || status; }
function closeEditor(): void { editRevision++;editingLoading.value=false;editorVisible.value = false; editingId.value = null; }
function resetForm(): void { studentRevision++;studentMode.value=false;selectedStudents.value=[];studentOptions.value=[]; originalDraft.value=null;confirming.value=false;targetChanged.value=false; form.title = ''; form.difficultyLevel = 2; form.durationMinutes = 30; form.scheduledDate = today(); form.remark = ''; Object.assign(form,{recurrenceEnabled:false,recurrenceFrequency:'DAILY',recurrenceWeekdays:[],recurrenceEndDate:null}); targetIndex.value = targets.value.length === 1 ? 0 : -1; }
function today(): string { const now = new Date(); return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`; }
function show(title: string, icon: 'none' | 'success' = 'none'): void { uni.showToast({ title, icon }); }
function leave(): Promise<unknown> { return uni.reLaunch({ url: '/pages/index/index' }); }
</script>

<style lang="scss" scoped>
.page-shell { min-height: calc(100vh - var(--window-top,0px)); padding-bottom: 56rpx; background: #f4f7f5; }
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
.weekday-options{display:flex;flex-wrap:wrap;gap:10rpx}.weekday-options button{padding:0 16rpx;font-size:23rpx!important}.weekday-options button.selected{background:#184F40!important;color:#fff!important}
</style>

<style lang="scss" scoped>
@import '@/styles/workspace-prototype.scss';
.prototype-workspace .toolbar {padding:24rpx;background:#fffefa;border:1rpx solid #e0e8e1;border-radius:24rpx;margin-bottom:24rpx;}
.prototype-workspace .task-row {padding:26rpx;margin-bottom:18rpx;border:1rpx solid #e0e8e1;border-radius:22rpx;background:#fffefa;}
.prototype-workspace .editor-band {position:fixed;inset:0;z-index:110;background:#F6F8F5;margin:0;padding:32rpx 28rpx calc(180rpx + env(safe-area-inset-bottom));overflow-y:auto;border-radius:0;box-sizing:border-box;}
.prototype-workspace .editor-actions {position:fixed;bottom:0;left:0;right:0;padding:20rpx 28rpx calc(20rpx + env(safe-area-inset-bottom));background:#fffefa;border-top:1rpx solid #e0e8e1;display:flex;gap:20rpx;z-index:111;}
.prototype-workspace .editor-actions button {flex:1;border-radius:18rpx;margin:0;}
.prototype-workspace .save-button,.prototype-workspace .add-button {background:#184F40;color:white;}
.prototype-workspace .field-input,.prototype-workspace .picker-field,.prototype-workspace .field-textarea {background:#fffefa;border:1rpx solid #dce7de;border-radius:16rpx;}
.weekday-options{display:flex;flex-wrap:wrap;gap:10rpx}.weekday-options button{padding:0 16rpx;font-size:23rpx!important}.weekday-options button.selected{background:#184F40!important;color:#fff!important}
</style>

<style scoped>.publish-steps{padding:24rpx 0;color:#184F40;font-size:26rpx}.confirm-card{padding:28rpx;border:1rpx solid #dce7de;border-radius:24rpx;background:#fffefa}.confirm-card text{display:block}.editor-actions button{font-size:24rpx!important;padding:0 12rpx!important;min-width:0;}.weekday-options{display:flex;flex-wrap:wrap;gap:10rpx}.weekday-options button{padding:0 16rpx;font-size:23rpx!important}.weekday-options button.selected{background:#184F40!important;color:#fff!important}
</style>

<style scoped>.prototype-workspace .editor-band{top:var(--window-top,0px);}.weekday-options{display:flex;flex-wrap:wrap;gap:10rpx}.weekday-options button{padding:0 16rpx;font-size:23rpx!important}.weekday-options button.selected{background:#184F40!important;color:#fff!important}
</style>

<style scoped>
.prototype-workspace{background:#f4faf7!important;padding:0 28rpx 48rpx!important;min-height:calc(100vh - var(--window-top,0px));box-sizing:border-box;color:#172d27;}
.prototype-workspace.has-tabbar{padding-bottom:160rpx!important;}
.prototype-header{margin:0 -28rpx 28rpx;padding:32rpx 32rpx 48rpx;display:flex;align-items:center;gap:18rpx;background:linear-gradient(115deg,#226d5c,#07573f);color:#fff;border-radius:0 0 30rpx 30rpx;}
.prototype-header-title{font-size:34rpx;font-weight:700;letter-spacing:1rpx;}
.card,.section-band,.summary-band,.action-band,.heading-band{background:#fff!important;border:1rpx solid #e2eee7!important;border-radius:24rpx!important;box-shadow:none!important;margin-bottom:22rpx;padding:24rpx!important;}
input,textarea,.field{background:#fbfcfb!important;border:1rpx solid #dce5df!important;border-radius:14rpx!important;padding:18rpx!important;box-sizing:border-box;font-size:26rpx!important;}
input{min-height:76rpx;}textarea{width:100%;}
button{border-radius:18rpx!important;font-size:26rpx!important;}button::after{border:0;}
.heading,.section-title,.row-title,.task-title{color:#162b24;font-weight:650;font-size:28rpx;}
.row-meta,.task-meta,.hint{color:#78847e;font-size:24rpx;line-height:1.65;}
.list-row,.relationship-row,.task-row{border-bottom:1rpx solid #edf1ee;padding:24rpx 0;}
.current-tag,.read-tag,.source-badge{background:#e7f7e9;color:#168450;border-radius:10rpx;padding:6rpx 12rpx;font-size:22rpx;}
.weekday-options{display:flex;flex-wrap:wrap;gap:10rpx}.weekday-options button{padding:0 16rpx;font-size:23rpx!important}.weekday-options button.selected{background:#184F40!important;color:#fff!important}
</style>
<style scoped>
.target-mode{display:flex;gap:16rpx;margin:20rpx 0}.target-mode button{flex:1;margin:0;background:#f0f5f1;color:#547062;border:1rpx solid #dce7df;border-radius:16rpx;font-size:25rpx}.target-mode button.selected{background:#184f40;color:#fff;border-color:#184f40}.target-children{background:#fff;border:1rpx solid #e1e9e2;border-radius:18rpx;padding:8rpx 20rpx;margin-bottom:24rpx}.target-child{display:flex;align-items:center;gap:14rpx;padding:20rpx 0;border-bottom:1rpx solid #edf1ec}.target-child:last-child{border-bottom:0}.target-child-name,.target-child-class{display:block}.target-child-name{font-size:28rpx;color:#243c30}.target-child-class{font-size:23rpx;color:#7a8b80;margin-top:5rpx}
</style>
