<template>
  <view class="prototype-workspace page-shell"><view class="prototype-section-header"><PrototypeIcon name="school" :size="22" /><text>{{formVisible ? (editingClass ? "班级详情" : "新增班级") : "班级列表"}}</text></view>
    <view class="mode-control">
      <button :class="{ active: mode === 'CLASSES' }" @tap="setMode('CLASSES')">班级维护</button>
      <button :class="{ active: mode === 'TEACHERS' }" @tap="setMode('TEACHERS')">教师班级</button>
    </view>

    <view v-if="loading" class="state-text">正在加载</view>
    <template v-else-if="mode === 'CLASSES'">
      <view class="action-band"><button v-if="entryPermissions.includes('ORG_MEMBER_READ')" class="secondary-button" @tap="openMembers">组织成员</button><button v-if="entryPermissions.some(p=>['STUDENT_ENROLLMENT_INVITE_CREATE','STUDENT_ENROLLMENT_REVIEW'].includes(p))" class="secondary-button" @tap="openEnrollment">班级入学</button>
        <button class="secondary-button" @tap="openStudents">学员关系</button>
        <button class="primary-button" @tap="openCreate">新增班级</button>
      </view>

      <view v-if="formVisible" class="form-band">
        <text class="form-title">{{ editingClass ? '编辑班级' : '新增班级' }}</text>
        <template v-if="!editingClass">
          <text class="field-label">所属学校</text>
          <picker :range="schoolLabels" :value="schoolIndex" @change="selectSchool">
            <view class="picker-value">{{ selectedSchoolLabel || '请选择所属学校' }}</view>
          </picker>
          <text class="field-label">所属年级</text>
          <picker :range="gradeLabels" :value="gradeIndex" @change="selectGrade">
            <view class="picker-value">{{ gradeLabels[gradeIndex] || '请选择所属年级' }}</view>
          </picker>
        </template>
        <text class="field-label">班级名称</text>
        <input v-model="className" class="text-input" maxlength="50" placeholder="请输入班级名称" />
        <text class="field-label">排序</text>
        <input v-model="sortOrderText" class="text-input" type="number" placeholder="请输入排序值" />
        <view class="form-actions">
          <button class="cancel-button" @tap="closeForm">取消</button>
          <button class="save-button" :disabled="submitting" @tap="saveClass">
            {{ submitting ? '正在保存' : '保存' }}
          </button>
        </view>
      </view>

      <view v-if="!formVisible" class="list-band"><text class="class-meta">共 {{classes.length}} 个班级</text>
        <view v-for="item in classes" :key="item.id" class="class-row">
          <view class="class-badge"><PrototypeIcon name="people" :size="28"/></view><view class="class-main" @tap="openDetail(item)">
            <text class="class-name">{{ item.name }}</text>
            <text class="class-meta">{{ item.schoolName || schoolName(item.schoolOrganizationId || item.parentId) }} · {{ item.gradeName || '待归属年级' }} · 排序 {{ item.sortOrder }}</text>
            <text class="class-code">{{ item.code }}</text>
          </view>
          <view class="row-actions">
            <text :class="['status-text', item.status === 'ENABLED' ? 'enabled' : 'disabled']">
              {{ item.status === 'ENABLED' ? '启用' : '停用' }}
            </text>
            <button class="row-button" @tap="openEdit(item)">编辑</button>
            <button
              :class="['row-button', item.status === 'ENABLED' ? 'danger' : 'enable']"
              @tap="confirmStatus(item)"
            >{{ item.status === 'ENABLED' ? '停用' : '启用' }}</button>
          </view>
        </view>
        <view v-if="classes.length === 0" class="empty-text">暂无班级</view>
      </view>
    </template>

    <view v-else class="form-band teacher-form">
      <text class="field-label first">班级</text>
      <picker :range="teacherClassLabels" :value="teacherClassIndex" @change="selectTeacherClass">
        <view class="picker-value">{{ selectedTeacherClassLabel || '请选择班级' }}</view>
      </picker>
      <text class="field-label">教师</text>
      <picker :range="teacherLabels" :value="teacherIndex" @change="selectTeacher">
        <view class="picker-value">{{ selectedTeacherLabel || '请选择教师' }}</view>
      </picker>
      <view class="teacher-actions">
        <button class="unbind-button" :disabled="submitting" @tap="updateTeacher(false)">解除绑定</button>
        <button class="bind-button" :disabled="submitting" @tap="updateTeacher(true)">确认绑定</button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import {classDetailRoute} from '@/models/class-detail';
function openDetail(item:OrganizationClass){uni.navigateTo({url:classDetailRoute(item.id)});}
import PrototypeIcon from '@/components/PrototypeIcon.vue';
import { computed, ref } from 'vue';
import { onShow,onHide,onUnload,onLoad } from '@dcloudio/uni-app';
import {getOrganizationWorkbenchContext} from '@/api/organization-workbench';
import { getMiniappCapabilities } from '@/api/capability';
import { ApiError } from '@/api/http';
import {
  bindOrganizationTeacher,
  changeManagedClassStatus,
  createManagedClass,
  listManageableSchools,
  listManageableGrades,
  listManagedClasses,
  listOrganizationTeachers,
  unbindOrganizationTeacher,
  updateManagedClass,
  type OrganizationClass,
  type OrganizationTeacherOption
} from '@/api/organization-classes';
import { getOrganizationSession } from '@/session/organization-session';

type PageMode = 'CLASSES' | 'TEACHERS';

let requestedEdit='';onLoad(q=>{requestedEdit=typeof q?.editClassId==='string'?q.editClassId:'';});
const mode = ref<PageMode>('CLASSES');
const schools = ref<OrganizationClass[]>([]);
const grades = ref<OrganizationClass[]>([]);
const classes = ref<OrganizationClass[]>([]);
const teachers = ref<OrganizationTeacherOption[]>([]);
const entryPermissions=ref<string[]>([]);let entryEpoch=0;function clearEntries(){entryEpoch++;entryPermissions.value=[];}onHide(clearEntries);onUnload(clearEntries);onShow(async()=>{const s=getOrganizationSession(),n=++entryEpoch;entryPermissions.value=[];if(!s)return;try{const me=await getOrganizationWorkbenchContext(s.accessToken);if(n===entryEpoch&&getOrganizationSession()?.accessToken===s.accessToken)entryPermissions.value=me.permissionCodes;}catch{/* Existing page displays its own authorization error. */}});function openMembers(){uni.navigateTo({url:"/pages/organization-members/organization-members"});}function openEnrollment(){uni.navigateTo({url:"/pages/institution-enrollment/institution-enrollment"});}
const loading = ref(true);
const submitting = ref(false);
const formVisible = ref(false);
const editingClass = ref<OrganizationClass | null>(null);
const schoolIndex = ref(-1);
const gradeIndex = ref(-1);
const className = ref('');
const sortOrderText = ref('100');
const teacherClassIndex = ref(-1);
const teacherIndex = ref(-1);

const schoolLabels = computed(() => schools.value.map((item) => `${item.name}（${item.code}）`));
const selectedSchool = computed(() => schools.value[schoolIndex.value] || null);
const selectedSchoolLabel = computed(() => schoolLabels.value[schoolIndex.value] || '');
const availableGrades = computed(() => grades.value.filter(item => item.parentId === selectedSchool.value?.id && item.effectiveStatus === 'ENABLED'));
const gradeLabels = computed(() => availableGrades.value.map(item => item.name));
const selectedGrade = computed(() => availableGrades.value[gradeIndex.value] || null);
const activeClasses = computed(() => classes.value.filter((item) => item.status === 'ENABLED'));
const teacherClassLabels = computed(() => activeClasses.value.map((item) => item.name));
const selectedTeacherClass = computed(() => activeClasses.value[teacherClassIndex.value] || null);
const selectedTeacherClassLabel = computed(() => teacherClassLabels.value[teacherClassIndex.value] || '');
const teacherLabels = computed(() => teachers.value.map((item) => item.displayName));
const selectedTeacher = computed(() => teachers.value[teacherIndex.value] || null);
const selectedTeacherLabel = computed(() => teacherLabels.value[teacherIndex.value] || '');

onShow(async () => {
  const session = getOrganizationSession();
  if (!session) {
    await leavePage(true);
    return;
  }
  loading.value = true;
  try {
    const capabilities = await getMiniappCapabilities();
    if (!capabilities.organizationMiniappAuthEnabled || !capabilities.classManagementEnabled) {
      return leavePage(false);
    }
    await loadClasses(session.accessToken);
    if(requestedEdit){const target=classes.value.find(item=>item.id===requestedEdit);if(target&&getOrganizationSession()?.accessToken===session.accessToken)openEdit(target);requestedEdit='';}
  } catch (error) {
    if (error instanceof ApiError
        && (error.statusCode === 401 || error.statusCode === 403 || error.code === 'FEATURE_DISABLED')) {
      return leavePage(error.statusCode === 401);
    }
    showError('班级信息加载失败');
  } finally {
    loading.value = false;
  }
});

function setMode(nextMode: PageMode): void {
  mode.value = nextMode;
  closeForm();
}

function openCreate(): void {
  editingClass.value = null;
  schoolIndex.value = -1;
  gradeIndex.value = -1;
  className.value = '';
  sortOrderText.value = '100';
  formVisible.value = true;
}

function openEdit(item: OrganizationClass): void {
  editingClass.value = item;
  className.value = item.name;
  sortOrderText.value = String(item.sortOrder);
  formVisible.value = true;
}

function closeForm(): void {
  formVisible.value = false;
  editingClass.value = null;
}

function selectSchool(event: { detail: { value: string } }): void {
  schoolIndex.value = Number(event.detail.value);
  gradeIndex.value = -1;
}

function selectGrade(event: { detail: { value: string } }): void {
  gradeIndex.value = Number(event.detail.value);
}

async function saveClass(): Promise<void> {
  const session = getOrganizationSession();
  const normalizedName = className.value.trim();
  const sortOrder = Number(sortOrderText.value);
  if (!session) {
    await leavePage(true);
    return;
  }
  if (!editingClass.value && !selectedSchool.value) return showError('请选择所属学校');
  if (!editingClass.value && !selectedGrade.value) return showError('请选择所属年级');
  if (!normalizedName) return showError('请输入班级名称');
  if (!Number.isInteger(sortOrder) || sortOrder < 0) return showError('排序必须是非负整数');

  submitting.value = true;
  try {
    if (editingClass.value) {
      await updateManagedClass(
        session.accessToken, editingClass.value.id, normalizedName,
        sortOrder, editingClass.value.versionNo
      );
      uni.showToast({ title: '班级已更新', icon: 'success' });
    } else {
      await createManagedClass(
        session.accessToken, selectedSchool.value!.id, normalizedName, sortOrder, selectedGrade.value!.id
      );
      uni.showToast({ title: '班级已创建', icon: 'success' });
    }
    closeForm();
    await loadClasses(session.accessToken);
  } catch (error) {
    showError(error instanceof Error ? error.message : '保存失败');
  } finally {
    submitting.value = false;
  }
}

async function confirmStatus(item: OrganizationClass): Promise<void> {
  const session = getOrganizationSession();
  if (!session) {
    await leavePage(true);
    return;
  }
  const disabling = item.status === 'ENABLED';
  const confirmed = await showConfirm(
    disabling ? '确认停用班级？' : '确认启用班级？',
    disabling
      ? '班内未完成的机构和教师任务将失效，历史记录继续保留。'
      : '已失效的历史任务不会恢复。',
    disabling ? '确认停用' : '确认启用',
    disabling ? '#b42318' : '#167c5a'
  );
  if (!confirmed) return;
  try {
    await changeManagedClassStatus(session.accessToken, item);
    uni.showToast({ title: disabling ? '班级已停用' : '班级已启用', icon: 'success' });
    await loadClasses(session.accessToken);
  } catch (error) {
    showError(error instanceof Error ? error.message : '状态更新失败');
  }
}

async function selectTeacherClass(event: { detail: { value: string } }): Promise<void> {
  teacherClassIndex.value = Number(event.detail.value);
  teacherIndex.value = -1;
  const session = getOrganizationSession();
  const selectedClass = selectedTeacherClass.value;
  if (!session || !selectedClass) return;
  try {
    teachers.value = await listOrganizationTeachers(session.accessToken, selectedClass.id);
  } catch (error) {
    showError(error instanceof Error ? error.message : '教师候选加载失败');
  }
}

function selectTeacher(event: { detail: { value: string } }): void {
  teacherIndex.value = Number(event.detail.value);
}

async function updateTeacher(active: boolean): Promise<void> {
  const session = getOrganizationSession();
  const selectedClass = selectedTeacherClass.value;
  const teacher = selectedTeacher.value;
  if (!session) {
    await leavePage(true);
    return;
  }
  if (!selectedClass) return showError('请选择班级');
  if (!teacher) return showError('请选择教师');
  if (!active) {
    const confirmed = await showConfirm(
      '确认解除教师班级？', `${teacher.displayName} 将不再关联 ${selectedClass.name}。`,
      '确认解除', '#b42318'
    );
    if (!confirmed) return;
  }
  submitting.value = true;
  try {
    if (active) {
      await bindOrganizationTeacher(session.accessToken, teacher.userId, selectedClass.id);
    } else {
      await unbindOrganizationTeacher(session.accessToken, teacher.userId, selectedClass.id);
    }
    uni.showToast({ title: active ? '教师班级已绑定' : '教师班级已解除', icon: 'success' });
  } catch (error) {
    showError(error instanceof Error ? error.message : '教师班级更新失败');
  } finally {
    submitting.value = false;
  }
}

async function loadClasses(accessToken: string): Promise<void> {
  [schools.value, grades.value, classes.value] = await Promise.all([
    listManageableSchools(accessToken), listManageableGrades(accessToken), listManagedClasses(accessToken)
  ]);
}

function schoolName(parentId: string | null): string {
  if (!parentId) return '未知学校';
  return schools.value.find((item) => item.id === parentId)?.name || `学校标识 ${parentId}`;
}

function openStudents(): Promise<unknown> {
  return uni.navigateTo({ url: '/pages/organization-students/organization-students' });
}

function leavePage(noSession: boolean): Promise<unknown> {
  return noSession
    ? uni.reLaunch({ url: '/pages/index/index' })
    : uni.redirectTo({ url: '/pages/organization-home/organization-home' });
}

function showError(title: string): void {
  uni.showToast({ title, icon: 'none' });
}

function showConfirm(
  title: string,
  content: string,
  confirmText: string,
  confirmColor: string
): Promise<boolean> {
  return new Promise((resolve) => {
    uni.showModal({
      title, content, confirmText, confirmColor,
      success: (result) => resolve(result.confirm), fail: () => resolve(false)
    });
  });
}
</script>

<style scoped>
.prototype-workspace{min-height:calc(100vh - var(--window-top,0px));box-sizing:border-box;background:#f5f8f4;color:#182d26;padding:20rpx 28rpx 150rpx}.page-shell{padding-bottom:48rpx}.ld-topbar{display:flex;align-items:center;gap:18rpx;background:linear-gradient(120deg,#21664f,#0b4b3d);margin:-24rpx -24rpx 28rpx;padding:28rpx 26rpx 50rpx;color:white;border-radius:24rpx 24rpx 0 0}.ld-topbar-brand{display:flex;align-items:center;gap:18rpx;flex:1}.ld-topbar-name{display:block;font-size:32rpx;font-weight:650}.ld-topbar-meta{display:block;font-size:23rpx;opacity:.8;margin-top:8rpx}.ld-topbar-logo,.profile-avatar{width:80rpx;height:80rpx;border-radius:50%;background:#ffffff25;display:flex;align-items:center;justify-content:center}.profile-copy{flex:1}.ld-card,.ld-group,.form-band,.band,.search-band{background:#fff;border:1rpx solid #e5ece5;border-radius:24rpx;padding:24rpx;margin-bottom:20rpx}.workspace-intro{background:#fff;border-radius:24rpx;padding:30rpx 24rpx;margin-bottom:20rpx}.workspace-intro-title{display:block;font-size:34rpx;font-weight:700}.workspace-intro-caption{display:block;font-size:24rpx;color:#778078;margin-top:10rpx}.workspace-shortcuts{display:flex;flex-direction:column;gap:16rpx;margin-bottom:16rpx}.workspace-shortcut{display:flex;align-items:center;flex-wrap:wrap;text-align:left;background:#fff;border-radius:24rpx;padding:22rpx 26rpx;font-size:30rpx;font-weight:600;color:#182d26;min-height:120rpx}.workspace-shortcut>text{width:100%;padding-left:80rpx;font-size:23rpx;font-weight:400;color:#7b837d}.shortcut-symbol,.ld-cell-icon,.class-badge{width:64rpx;height:64rpx;border-radius:14rpx;background:#138655;display:flex;align-items:center;justify-content:center;margin-right:18rpx}.ld-heading{margin:28rpx 0 18rpx}.ld-heading-title,.form-title,.section-title{display:block;font-size:30rpx;font-weight:650}.ld-heading-sub{display:block;font-size:23rpx;color:#7b837d;margin-top:8rpx}.ld-cell,.class-row,.teacher-row,.organization-row{display:flex;align-items:center;padding:20rpx 0;border-bottom:1rpx solid #edf1eb;text-align:left;background:#fff;color:#182d26}.ld-cell:last-child,.class-row:last-child,.teacher-row:last-child{border-bottom:0}.ld-cell-body,.class-main,.organization-main{flex:1}.ld-cell-title,.class-name,.organization-name{display:block;font-size:29rpx;font-weight:600}.ld-cell-sub,.school-name,.organization-type{display:block;font-size:23rpx;color:#7d847e;margin-top:8rpx}.ld-cell-arrow{font-size:34rpx;color:#65726a}.prototype-section-header{display:flex;align-items:center;justify-content:center;font-size:32rpx;font-weight:700;margin-bottom:20rpx;gap:12rpx}.prototype-section-header>.prototype-icon{display:none}.mode-control,.search-band,.action-band,.form-actions,.paging{display:flex;gap:14rpx;align-items:center;flex-wrap:wrap;margin-bottom:24rpx}.mode-control button{flex:1}.active{background:#e1f3e4;color:#0c7748}.field-label{display:block;margin:24rpx 0 12rpx;font-size:26rpx}.text-input,.search-input,.picker-value{background:#f8faf7;border:1rpx solid #e0e7df;border-radius:16rpx;padding:18rpx;min-height:72rpx;box-sizing:border-box;font-size:26rpx}.search-input{flex:1;min-width:180rpx}.check-row{display:flex;align-items:center;gap:12rpx;padding:16rpx 0}.state-text{font-size:26rpx;color:#7a837c;padding:24rpx}.review-card{display:flex;align-items:center;gap:20rpx}.review-info{flex:1}.ld-card-title{display:block;font-size:30rpx;font-weight:650}.review-count{display:block;margin-top:12rpx;font-size:24rpx;color:#718077}button{margin:0;font-size:25rpx;line-height:1.5;background:#eaf2e9;color:#21563e;padding:14rpx 20rpx;border-radius:16rpx}button::after{border:0}.primary-button,.save-button,.submit-button,.create-button{background:#137c53;color:white}.logout-button{margin-top:30rpx;color:#b65c50}.ld-logout,.space-switch{font-size:23rpx;flex-shrink:0}.org-badge{margin-right:18rpx;background:#e4f2e4;border-radius:14rpx;padding:16rpx}.error{color:#a43b31}.check-list{margin:18rpx 0}
</style>
