<template>
  <view class="page-shell">
    <view class="mode-control">
      <button :class="{ active: mode === 'CLASSES' }" @tap="setMode('CLASSES')">班级维护</button>
      <button :class="{ active: mode === 'TEACHERS' }" @tap="setMode('TEACHERS')">教师班级</button>
    </view>

    <view v-if="loading" class="state-text">正在加载</view>
    <template v-else-if="mode === 'CLASSES'">
      <view class="action-band">
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

      <view class="list-band">
        <view v-for="item in classes" :key="item.id" class="class-row">
          <view class="class-main">
            <text class="class-name">{{ item.name }}</text>
            <text class="class-meta">{{ schoolName(item.parentId) }} · 排序 {{ item.sortOrder }}</text>
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
import { computed, ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { getMiniappCapabilities } from '@/api/capability';
import { ApiError } from '@/api/http';
import {
  bindOrganizationTeacher,
  changeManagedClassStatus,
  createManagedClass,
  listManageableSchools,
  listManagedClasses,
  listOrganizationTeachers,
  unbindOrganizationTeacher,
  updateManagedClass,
  type OrganizationClass,
  type OrganizationTeacherOption
} from '@/api/organization-classes';
import { getOrganizationSession } from '@/session/organization-session';

type PageMode = 'CLASSES' | 'TEACHERS';

const mode = ref<PageMode>('CLASSES');
const schools = ref<OrganizationClass[]>([]);
const classes = ref<OrganizationClass[]>([]);
const teachers = ref<OrganizationTeacherOption[]>([]);
const loading = ref(true);
const submitting = ref(false);
const formVisible = ref(false);
const editingClass = ref<OrganizationClass | null>(null);
const schoolIndex = ref(-1);
const className = ref('');
const sortOrderText = ref('100');
const teacherClassIndex = ref(-1);
const teacherIndex = ref(-1);

const schoolLabels = computed(() => schools.value.map((item) => `${item.name}（${item.code}）`));
const selectedSchool = computed(() => schools.value[schoolIndex.value] || null);
const selectedSchoolLabel = computed(() => schoolLabels.value[schoolIndex.value] || '');
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
        session.accessToken, selectedSchool.value!.id, normalizedName, sortOrder
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
  [schools.value, classes.value] = await Promise.all([
    listManageableSchools(accessToken), listManagedClasses(accessToken)
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

<style lang="scss" scoped>
.page-shell { min-height: 100vh; padding: 28rpx 0 48rpx; box-sizing: border-box; background: #f4f7f5; }
.mode-control { display: grid; grid-template-columns: 1fr 1fr; margin: 0 40rpx 28rpx; border: 2rpx solid #b8c7c0; border-radius: 10rpx; overflow: hidden; }
.mode-control button { height: 72rpx; margin: 0; border-radius: 0; background: #ffffff; color: #52625b; font-size: 26rpx; line-height: 72rpx; }
.mode-control button::after, button::after { border: 0; }
.mode-control button.active { background: #167c5a; color: #ffffff; }
.state-text { min-height: 360rpx; display: flex; align-items: center; justify-content: center; color: #708078; font-size: 28rpx; }
.action-band { display: grid; grid-template-columns: 1fr 1fr; gap: 20rpx; margin: 0 40rpx 28rpx; }
.action-band button { height: 76rpx; margin: 0; border-radius: 8rpx; font-size: 27rpx; }
.secondary-button { border: 2rpx solid #167c5a; background: #ffffff; color: #167c5a; }
.primary-button, .save-button, .bind-button { background: #167c5a; color: #ffffff; }
.form-band { padding: 32rpx 40rpx 40rpx; border-top: 2rpx solid #dbe3df; border-bottom: 2rpx solid #dbe3df; background: #ffffff; }
.form-title, .field-label, .class-name, .class-meta, .class-code { display: block; }
.form-title { color: #1c2b28; font-size: 30rpx; font-weight: 700; }
.field-label { margin: 28rpx 0 12rpx; color: #31413b; font-size: 26rpx; font-weight: 600; }
.field-label.first { margin-top: 0; }
.picker-value, .text-input { min-height: 84rpx; padding: 0 24rpx; box-sizing: border-box; border: 2rpx solid #cfd9d4; border-radius: 8rpx; color: #1c2b28; font-size: 27rpx; }
.picker-value { display: flex; align-items: center; }
.form-actions, .teacher-actions { display: grid; grid-template-columns: 1fr 1fr; gap: 20rpx; margin-top: 36rpx; }
.form-actions button, .teacher-actions button { height: 80rpx; margin: 0; border-radius: 8rpx; font-size: 27rpx; }
.cancel-button { background: #eef2f0; color: #52625b; }
.list-band { margin-top: 28rpx; border-top: 2rpx solid #dbe3df; border-bottom: 2rpx solid #dbe3df; background: #ffffff; }
.class-row { padding: 28rpx 40rpx; border-top: 2rpx solid #edf1ef; }
.class-row:first-child { border-top: 0; }
.class-main { min-width: 0; }
.class-name { color: #1c2b28; font-size: 30rpx; font-weight: 650; word-break: break-word; }
.class-meta { margin-top: 10rpx; color: #52625b; font-size: 24rpx; }
.class-code { margin-top: 8rpx; color: #87938d; font-size: 21rpx; word-break: break-all; }
.row-actions { display: flex; align-items: center; gap: 14rpx; margin-top: 22rpx; }
.status-text { margin-right: auto; font-size: 24rpx; font-weight: 600; }
.status-text.enabled { color: #167c5a; }
.status-text.disabled { color: #7b8781; }
.row-button { width: 112rpx; height: 60rpx; margin: 0; padding: 0; border-radius: 8rpx; background: #eef2f0; color: #31413b; font-size: 24rpx; line-height: 60rpx; }
.row-button.danger, .unbind-button { background: #fff1ef; color: #b42318; }
.row-button.enable { background: #e9f5ef; color: #167c5a; }
.empty-text { padding: 80rpx 40rpx; color: #87938d; font-size: 27rpx; text-align: center; }
.teacher-form { margin-top: 0; }
</style>
