<template>
  <view class="prototype-workspace page-shell"><view class="prototype-section-header"><PrototypeIcon name="school" :size="22" /><text>学员关系</text></view>
    <view class="mode-control">
      <button :class="{ active: mode === 'TRANSFER' }" @tap="setMode('TRANSFER')">校内转班</button>
      <button :class="{ active: mode === 'DEACTIVATE' }" @tap="setMode('DEACTIVATE')">离校</button>
    </view>

    <view v-if="loading" class="state-text">正在加载</view>
    <view v-else class="form-band">
      <text class="field-label">成长小主人</text>
      <picker :range="studentLabels" :value="studentIndex" @change="selectStudent">
        <view class="picker-value">{{ selectedStudentLabel || '请选择成长小主人' }}</view>
      </picker>

      <view v-if="selectedStudent" class="student-summary"><image src="/static/pixel/default-child-avatar.png" mode="aspectFill"/><view><text class="summary-name">{{selectedStudent.studentName}}</text><text class="summary-class">当前班级：{{selectedStudent.currentClassOrganizationName||'未分班'}}</text><text class="summary-class">{{selectedStudent.enrollmentOrganizationName}}</text></view></view>
      <template v-if="mode === 'TRANSFER'">
        <text class="field-label">目标班级</text>
        <picker :disabled="submitting||classesLoading||!selectedStudent" :range="classLabels" :value="classIndex" @change="selectClass">
          <view class="picker-value">{{classesLoading?'正在加载本校班级':selectedClassLabel || '请选择目标班级'}}</view>
        </picker>
      </template>

      <text class="field-label">变更原因</text><radio-group v-if="mode==='TRANSFER'" class="reason-options" @change="chooseReason"><label v-for="item in reasonOptions" :key="item"><radio :value="item" :checked="reasonCategory===item" :disabled="submitting" color="#184f40"/><text>{{item}}</text></label></radio-group><text class="field-label">申请说明</text>
      <textarea
        v-model="reason"
        class="reason-input"
        maxlength="200"
        placeholder="请输入变更原因"
      />

      <button
        class="submit-button"
        :class="{ danger: mode === 'DEACTIVATE' }"
        :disabled="submitting"
        @tap="submit"
      >
        {{ submitting ? '正在提交' : mode === 'TRANSFER' ? '确认转班' : '确认转出' }}
      </button>
    </view>
  </view>
</template>

<script setup lang="ts">
import PrototypeIcon from '@/components/PrototypeIcon.vue';
import { computed, ref } from 'vue';
import { onShow,onHide,onUnload } from '@dcloudio/uni-app';
import { getMiniappCapabilities } from '@/api/capability';
import {
  deactivateOrganizationStudent,
  listOrganizationClasses,
  listOrganizationStudents,
  transferOrganizationStudent,
  type OrganizationClassOption,
  type OrganizationStudentRelationship
} from '@/api/organization-students';
import { getOrganizationSession } from '@/session/organization-session';

type OperationMode = 'TRANSFER' | 'DEACTIVATE';

const reasonOptions=['学习进度调整','班级人数调整','成长小主人个人原因','其他'];const reasonCategory=ref('');function chooseReason(event:{detail:{value:string}}){if(submitting.value)return;reasonCategory.value=event.detail.value;}
const mode = ref<OperationMode>('TRANSFER');
const students = ref<OrganizationStudentRelationship[]>([]);
const classes = ref<OrganizationClassOption[]>([]);
const studentIndex = ref(-1);
const classIndex = ref(-1);
const reason = ref('');
const loading = ref(true);
const submitting = ref(false);let classRevision=0;const classesLoading=ref(false);

const studentLabels = computed(() => students.value.map((student) => (
  `${student.studentName}${student.currentClassOrganizationName
    ? ` · 当前 ${student.currentClassOrganizationName}` : ' · 未分班'}`
)));
const classLabels = computed(() => classes.value.map((item) => item.name));
const selectedStudent = computed(() => students.value[studentIndex.value] || null);
const selectedStudentLabel = computed(() => studentLabels.value[studentIndex.value] || '');
const selectedClass = computed(() => classes.value[classIndex.value] || null);
const selectedClassLabel = computed(() => classLabels.value[classIndex.value] || '');

onShow(async () => {
  const session = getOrganizationSession();
  if (!session) return leavePage();
  loading.value = true;
  try {
    const capabilities = await getMiniappCapabilities();
    if (!capabilities.studentOrganizationRelationshipEnabled) return leavePage();
    await loadData(session.accessToken);
  } catch {
    uni.showToast({ title: '学员关系加载失败', icon: 'none' });
  } finally {
    loading.value = false;
  }
});

function setMode(nextMode: OperationMode): void {
  if(submitting.value)return;
  mode.value = nextMode;
  classIndex.value = -1;
}

function selectStudent(event: { detail: { value: string } }): void {
  if(submitting.value)return;
  studentIndex.value = Number(event.detail.value);classIndex.value=-1;void loadStudentClasses();
}

function selectClass(event: { detail: { value: string } }): void {
  if(submitting.value||classesLoading.value)return;
  classIndex.value = Number(event.detail.value);
}

async function submit(): Promise<void> {
  if(submitting.value)return;
  const session = getOrganizationSession();
  const student = selectedStudent.value;const operation=mode.value,targetId=selectedClass.value?.id,n=classRevision;
  const normalizedReason = [mode.value==='TRANSFER'?reasonCategory.value:'',reason.value.trim()].filter(Boolean).join('：');
  if (!session || !student) return showError('请选择成长小主人');
  if (!reason.value.trim()) return showError('请输入申请说明');
  if(normalizedReason.length>200)return showError('原因和说明合计不能超过200字');
  if (mode.value === 'TRANSFER' && !selectedClass.value) return showError('请选择目标班级');

  submitting.value = true;
  try {
    if (operation === 'DEACTIVATE') {
      const confirmed = await confirmDeactivation();
      if (!confirmed) return;
    }
    if(n!==classRevision||getOrganizationSession()?.accessToken!==session.accessToken)return;
    if (operation === 'TRANSFER') {
      await transferOrganizationStudent(
        session.accessToken,
        student.studentId,
        targetId!,
        normalizedReason
      );
      if(n!==classRevision||getOrganizationSession()?.accessToken!==session.accessToken)return;
      uni.showToast({ title: '班级关系已更新', icon: 'success' });
    } else {
      await deactivateOrganizationStudent(
        session.accessToken,
        student.studentId,
        student.enrollmentOrganizationId,
        normalizedReason
      );
      if(n!==classRevision||getOrganizationSession()?.accessToken!==session.accessToken)return;
      uni.showToast({ title: '机构关系已停用', icon: 'success' });
    }
    studentIndex.value = -1;
    classIndex.value = -1;
    reason.value = '';reasonCategory.value='';
    await loadData(session.accessToken);
  } catch (error) {
    if(n===classRevision&&getOrganizationSession()?.accessToken===session.accessToken)showError(error instanceof Error ? error.message : '提交失败');
  } finally {
    submitting.value = false;
  }
}

async function loadStudentClasses():Promise<void>{const n=++classRevision,student=selectedStudent.value,t=getOrganizationSession()?.accessToken;classes.value=[];classesLoading.value=false;if(!student||!t)return;classesLoading.value=true;try{const rows=await listOrganizationClasses(t,student.enrollmentOrganizationId);if(n===classRevision&&getOrganizationSession()?.accessToken===t&&selectedStudent.value?.studentId===student.studentId)classes.value=rows.filter(row=>row.schoolOrganizationId===student.enrollmentOrganizationId&&row.id!==student.currentClassOrganizationId);}catch(e){if(n===classRevision&&getOrganizationSession()?.accessToken===t&&selectedStudent.value?.studentId===student.studentId)showError(e instanceof Error?e.message:'班级加载失败');}finally{if(n===classRevision)classesLoading.value=false;}}
async function loadData(accessToken: string): Promise<void> {
  const n=++classRevision;classes.value=[];classesLoading.value=false;
  const rows=await listOrganizationStudents(accessToken);if(n===classRevision&&getOrganizationSession()?.accessToken===accessToken)students.value=rows;
}

function hidePage(){classRevision++;students.value=[];classes.value=[];studentIndex.value=-1;classIndex.value=-1;classesLoading.value=false;}onHide(hidePage);onUnload(hidePage);
function confirmDeactivation(): Promise<boolean> {
  return new Promise((resolve) => {
    uni.showModal({
      title: '确认转出该成长小主人？',
      content: '转出后机构和班级活动关系立即停用，历史数据继续保留。',
      confirmText: '确认转出',
      confirmColor: '#b54732',
      success: (result) => resolve(result.confirm),
      fail: () => resolve(false)
    });
  });
}

function showError(title: string): void {
  uni.showToast({ title, icon: 'none' });
}

function leavePage(): Promise<unknown> {
  return uni.reLaunch({ url: '/pages/index/index' });
}
</script>

<style scoped>
.secondary-page,.prototype-workspace{padding:24rpx 28rpx 60rpx;min-height:calc(100vh - var(--window-top,0px));box-sizing:border-box;background:#f6f9f5;color:#172e25}.has-footer,.has-tabbar{padding-bottom:160rpx}.prototype-section-header{display:flex;justify-content:center;align-items:center;gap:12rpx;margin:-24rpx -28rpx 24rpx;padding:28rpx;background:linear-gradient(120deg,#206b52,#0c4f3e);color:white;font-size:32rpx;font-weight:650}.section,.form-band,.band,.heading-band{background:#fff;border:1rpx solid #e4ece3;border-radius:24rpx;padding:26rpx;margin-bottom:22rpx}.segmented,.mode-control,.tabs,.action-grid,.form-actions,.paging,.search-row,.two-columns{display:flex;align-items:center;gap:14rpx;margin:18rpx 0}.segmented button,.mode-control button,.tab,.action-grid button{flex:1}.active{background:#e2f2e6;color:#0a774d}.field-label{display:block;font-size:26rpx;margin:20rpx 0 12rpx}.picker-field,.picker-value,.field,input,textarea{background:#f7faf6;border:1rpx solid #dfe8dc;border-radius:16rpx;padding:18rpx;font-size:26rpx;box-sizing:border-box}.picker-field{display:flex;justify-content:space-between}.search-row input{flex:1;min-width:0}textarea{width:100%;min-height:180rpx}.row,.request-person,.invitation-class{display:flex;align-items:center;gap:18rpx;padding:24rpx 0;border-bottom:1rpx solid #e8eee6}.person{width:80rpx;height:80rpx;border-radius:50%;background:#e2eee3;display:flex;align-items:center;justify-content:center;flex-shrink:0}.person-copy{flex:1;min-width:0}.row-top,.section-heading,.detail-line,.list-head{display:flex;justify-content:space-between;align-items:center;gap:12rpx}.name,.section-title,.title,.form-title{font-size:30rpx;font-weight:650}.meta,.small,.counter{display:block;font-size:23rpx;color:#778179;line-height:1.7}.badge,.status{border-radius:12rpx;background:#e2f4e7;color:#13734d;padding:4rpx 12rpx;font-size:23rpx}.PENDING,.submitted{background:#fff0cb;color:#b37a20}.detail-line{font-size:25rpx;padding:15rpx 0}.error,.danger{color:#ac4e42}.empty,.state,.state-text{padding:30rpx;text-align:center;font-size:25rpx;color:#7c867d}.empty text{display:block}.bottom-actions{position:fixed;bottom:0;left:0;right:0;background:#fff;padding:24rpx 28rpx calc(24rpx + env(safe-area-inset-bottom));border-top:1rpx solid #e2ebe0;z-index:4}.qr-content{text-align:center;padding:28rpx 0}.invite-code{display:block;font-size:38rpx;font-weight:700;letter-spacing:2rpx;margin:20rpx 0}.block-action{width:100%;margin-top:24rpx}.list-band{background:#fff;border-radius:24rpx;padding:20rpx}.list-row,.record-row,.history-row{border-bottom:1rpx solid #e6ede4;padding:24rpx 0}button{font-size:26rpx;line-height:1.5;margin:0;padding:16rpx 22rpx;background:#eaf2e8;color:#24583d;border-radius:16rpx}button::after{border:0}.primary,.submit-button,.save-button{background:#176f50;color:white}.secondary,.outline{background:#f2f6ef;color:#28563e}.required{color:#ba5b4c}.reason-input,.textarea{width:100%}
</style>

<style scoped>.student-summary{display:flex;align-items:center;gap:22rpx;background:#f2f7f3;padding:22rpx;border-radius:20rpx;margin-top:24rpx}.student-summary image{width:92rpx;height:92rpx;border-radius:50%;flex-shrink:0}.summary-name{display:block;font-size:30rpx;font-weight:650;color:#203c2e}.summary-class{display:block;font-size:23rpx;color:#738278;margin-top:7rpx}.reason-options label{display:flex;align-items:center;gap:12rpx;padding:12rpx 0;font-size:25rpx}.reason-options radio{transform:scale(.8)}</style>
