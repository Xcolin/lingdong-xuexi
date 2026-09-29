<template>
  <view class="page-shell">
    <view class="search-band">
      <input v-model="keyword" class="search-input" maxlength="64" placeholder="搜索教师姓名或账号" confirm-type="search" @confirm="search" />
      <button class="search-button" @tap="search">查询</button>
      <button v-if="canCreate" class="create-button" @tap="openCreate">新增</button>
    </view>

    <view v-if="loading" class="state-text">正在加载</view>
    <template v-else>
      <view v-if="mode !== 'LIST'" class="form-band">
        <text class="form-title">{{ formTitle }}</text>

        <template v-if="mode === 'CREATE'">
          <text class="field-label">教师账号</text>
          <input v-model="username" class="text-input" maxlength="64" placeholder="请输入教师账号" />
          <text class="field-label">教师姓名</text>
          <input v-model="displayName" class="text-input" maxlength="20" placeholder="请输入教师姓名" />
          <text class="field-label">手机号</text>
          <input v-model="mobile" class="text-input" maxlength="32" type="number" placeholder="可不填写" />
          <text class="field-label">初始密码</text>
          <input v-model="password" class="text-input" maxlength="20" password placeholder="8 至 20 位字母和数字组合" />
          <text class="field-label">所属学校</text>
          <picker :range="schoolLabels" :value="schoolIndex" @change="selectSchool">
            <view class="picker-value">{{ selectedSchool?.name || '请选择学校' }}</view>
          </picker>
          <text v-if="canManageClass" class="field-label">初始班级</text>
          <checkbox-group v-if="canManageClass" class="check-list" @change="selectCreateClasses">
            <label v-for="item in availableCreateClasses" :key="item.id" class="check-row">
              <checkbox :value="item.id" :checked="createClassIds.includes(item.id)" color="#167c5a" />
              <text>{{ item.name }}</text>
            </label>
          </checkbox-group>
        </template>

        <template v-else-if="mode === 'EDIT'">
          <text class="field-label">教师姓名</text>
          <input v-model="displayName" class="text-input" maxlength="20" placeholder="请输入教师姓名" />
          <text class="field-label">新手机号</text>
          <input v-model="mobile" class="text-input" maxlength="32" type="number" :disabled="clearMobile" placeholder="留空保持现有手机号" />
          <label class="switch-row">
            <text>清空现有手机号</text>
            <switch :checked="clearMobile" color="#167c5a" @change="toggleClearMobile" />
          </label>
        </template>

        <template v-else-if="mode === 'PASSWORD'">
          <text class="field-label">新密码</text>
          <input v-model="password" class="text-input" maxlength="20" password placeholder="8 至 20 位字母和数字组合" />
        </template>

        <template v-else-if="mode === 'CLASSES'">
          <checkbox-group class="check-list" @change="selectTeacherClasses">
            <label v-for="item in availableTeacherClasses" :key="item.id" class="check-row">
              <checkbox :value="item.id" :checked="teacherClassIds.includes(item.id)" color="#167c5a" />
              <text>{{ item.name }}</text>
            </label>
          </checkbox-group>
          <view v-if="availableTeacherClasses.length === 0" class="empty-text">该学校暂无可用班级</view>
        </template>

        <view class="form-actions">
          <button class="cancel-button" @tap="closeForm">取消</button>
          <button class="save-button" :disabled="submitting" @tap="saveForm">
            {{ submitting ? '正在保存' : '保存' }}
          </button>
        </view>
      </view>

      <view class="list-band">
        <view v-for="teacher in teachers" :key="teacher.id" class="teacher-row">
          <view class="teacher-head">
            <view class="teacher-main">
              <text class="teacher-name">{{ teacher.displayName }}</text>
              <text class="teacher-account">{{ teacher.username }}</text>
            </view>
            <text :class="['status-text', teacher.status.toLowerCase()]">{{ statusName(teacher.status) }}</text>
          </view>
          <text class="teacher-meta">{{ teacher.schoolName }} · {{ teacher.mobile || '未留手机号' }}</text>
          <text class="teacher-meta">已绑定 {{ teacher.classOrganizationIds.length }} 个班级</text>
          <view class="row-actions">
            <button v-if="canUpdate" class="row-button" @tap="openEdit(teacher)">编辑</button>
            <button v-if="canResetPassword" class="row-button" @tap="openPassword(teacher)">密码</button>
            <button v-if="canManageClass" class="row-button" @tap="openClasses(teacher)">班级</button>
            <button v-if="canChangeStatus && teacher.status !== 'ENABLED'" class="row-button enable" @tap="confirmStatus(teacher, 'ENABLED')">启用</button>
            <button v-if="canChangeStatus && teacher.status !== 'DISABLED'" class="row-button danger" @tap="confirmStatus(teacher, 'DISABLED')">停用</button>
            <button v-if="canChangeStatus && teacher.status !== 'LOCKED'" class="row-button warning" @tap="confirmStatus(teacher, 'LOCKED')">锁定</button>
          </view>
        </view>
        <view v-if="teachers.length === 0" class="empty-text">暂无匹配教师</view>
      </view>

      <button v-if="teachers.length < total" class="load-more" :disabled="loadingMore" @tap="loadMore">
        {{ loadingMore ? '正在加载' : '加载更多' }}
      </button>
    </template>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { getMiniappCapabilities } from '@/api/capability';
import { ApiError } from '@/api/http';
import {
  listManageableSchools, listManagedClasses, type OrganizationClass
} from '@/api/organization-classes';
import { getOrganizationWorkbenchContext } from '@/api/organization-workbench';
import {
  bindOrganizationTeacherClass,
  changeOrganizationTeacherStatus,
  createOrganizationTeacher,
  listOrganizationTeachers,
  resetOrganizationTeacherPassword,
  unbindOrganizationTeacherClass,
  updateOrganizationTeacher,
  type OrganizationTeacher,
  type TeacherStatus
} from '@/api/teacher-management';
import { getOrganizationSession } from '@/session/organization-session';

type FormMode = 'LIST' | 'CREATE' | 'EDIT' | 'PASSWORD' | 'CLASSES';

const PAGE_SIZE = 20;
const teachers = ref<OrganizationTeacher[]>([]);
const schools = ref<OrganizationClass[]>([]);
const classes = ref<OrganizationClass[]>([]);
const permissions = ref<string[]>([]);
const loading = ref(true);
const loadingMore = ref(false);
const submitting = ref(false);
const total = ref(0);
const page = ref(1);
const keyword = ref('');
const mode = ref<FormMode>('LIST');
const selectedTeacher = ref<OrganizationTeacher | null>(null);
const username = ref('');
const displayName = ref('');
const mobile = ref('');
const password = ref('');
const clearMobile = ref(false);
const schoolIndex = ref(-1);
const createClassIds = ref<string[]>([]);
const teacherClassIds = ref<string[]>([]);

const canCreate = computed(() => permissions.value.includes('TEACHER_CREATE'));
const canUpdate = computed(() => permissions.value.includes('TEACHER_UPDATE'));
const canChangeStatus = computed(() => permissions.value.includes('TEACHER_STATUS_CHANGE'));
const canResetPassword = computed(() => permissions.value.includes('TEACHER_PASSWORD_RESET'));
const canManageClass = computed(() => permissions.value.includes('TEACHER_CLASS_ASSIGN'));
const schoolLabels = computed(() => schools.value.map((item) => item.name));
const selectedSchool = computed(() => schools.value[schoolIndex.value] || null);
const availableCreateClasses = computed(() => classes.value.filter(
  (item) => item.status === 'ENABLED' && item.parentId === selectedSchool.value?.id
));
const availableTeacherClasses = computed(() => classes.value.filter(
  (item) => item.status === 'ENABLED' && item.parentId === selectedTeacher.value?.schoolId
));
const formTitle = computed(() => {
  if (mode.value === 'CREATE') return '新增教师';
  if (mode.value === 'EDIT') return `编辑${selectedTeacher.value?.displayName || '教师'}`;
  if (mode.value === 'PASSWORD') return `重置${selectedTeacher.value?.displayName || '教师'}的密码`;
  if (mode.value === 'CLASSES') return `${selectedTeacher.value?.displayName || '教师'}的班级范围`;
  return '';
});

onShow(async () => {
  const session = getOrganizationSession();
  if (!session) return leavePage();
  loading.value = true;
  try {
    const [capabilities, context, loadedSchools, loadedClasses] = await Promise.all([
      getMiniappCapabilities(),
      getOrganizationWorkbenchContext(session.accessToken),
      listManageableSchools(session.accessToken),
      listManagedClasses(session.accessToken)
    ]);
    if (!capabilities.organizationMiniappAuthEnabled
        || !capabilities.teacherManagementEnabled
        || !context.permissionCodes.includes('TEACHER_READ')) {
      return leavePage();
    }
    permissions.value = context.permissionCodes;
    schools.value = loadedSchools.filter((item) => item.effectiveStatus === 'ENABLED');
    classes.value = loadedClasses;
    await loadTeachers(session.accessToken, 1, false);
  } catch (error) {
    if (isAccessFailure(error)) return leavePage();
    showError('教师信息加载失败');
  } finally {
    loading.value = false;
  }
});

async function loadTeachers(accessToken: string, targetPage: number, append: boolean): Promise<void> {
  const result = await listOrganizationTeachers(accessToken, keyword.value, targetPage, PAGE_SIZE);
  teachers.value = append ? [...teachers.value, ...result.items] : result.items;
  total.value = result.total;
  page.value = result.page;
}

async function search(): Promise<void> {
  const session = getOrganizationSession();
  if (!session) return leavePage();
  loading.value = true;
  closeForm();
  try {
    await loadTeachers(session.accessToken, 1, false);
  } catch (error) {
    showError(error instanceof Error ? error.message : '查询失败');
  } finally {
    loading.value = false;
  }
}

async function loadMore(): Promise<void> {
  const session = getOrganizationSession();
  if (!session || loadingMore.value) return;
  loadingMore.value = true;
  try {
    await loadTeachers(session.accessToken, page.value + 1, true);
  } catch (error) {
    showError(error instanceof Error ? error.message : '加载失败');
  } finally {
    loadingMore.value = false;
  }
}

function openCreate(): void {
  resetForm();
  mode.value = 'CREATE';
}

function openEdit(teacher: OrganizationTeacher): void {
  resetForm();
  selectedTeacher.value = teacher;
  displayName.value = teacher.displayName;
  mode.value = 'EDIT';
}

function openPassword(teacher: OrganizationTeacher): void {
  resetForm();
  selectedTeacher.value = teacher;
  mode.value = 'PASSWORD';
}

function openClasses(teacher: OrganizationTeacher): void {
  resetForm();
  selectedTeacher.value = teacher;
  teacherClassIds.value = [...teacher.classOrganizationIds];
  mode.value = 'CLASSES';
}

function closeForm(): void {
  resetForm();
  mode.value = 'LIST';
}

function resetForm(): void {
  selectedTeacher.value = null;
  username.value = '';
  displayName.value = '';
  mobile.value = '';
  password.value = '';
  clearMobile.value = false;
  schoolIndex.value = -1;
  createClassIds.value = [];
  teacherClassIds.value = [];
}

function selectSchool(event: { detail: { value: string } }): void {
  schoolIndex.value = Number(event.detail.value);
  createClassIds.value = [];
}

function selectCreateClasses(event: { detail: { value: string[] } }): void {
  createClassIds.value = event.detail.value;
}

function selectTeacherClasses(event: { detail: { value: string[] } }): void {
  teacherClassIds.value = event.detail.value;
}

function toggleClearMobile(event: Event): void {
  clearMobile.value = (event as CustomEvent<{ value: boolean }>).detail.value;
  if (clearMobile.value) mobile.value = '';
}

async function saveForm(): Promise<void> {
  const session = getOrganizationSession();
  if (!session) return leavePage();
  submitting.value = true;
  try {
    if (mode.value === 'CREATE') await saveCreate(session.accessToken);
    else if (mode.value === 'EDIT') await saveEdit(session.accessToken);
    else if (mode.value === 'PASSWORD') await savePassword(session.accessToken);
    else if (mode.value === 'CLASSES') await saveClasses(session.accessToken);
    else return;
    uni.showToast({ title: '保存成功', icon: 'success' });
    closeForm();
    await loadTeachers(session.accessToken, 1, false);
  } catch (error) {
    showError(error instanceof Error ? error.message : '保存失败');
  } finally {
    submitting.value = false;
  }
}

async function saveCreate(accessToken: string): Promise<void> {
  const account = username.value.trim();
  const name = displayName.value.trim();
  if (!account) throw new Error('请输入教师账号');
  if (!name) throw new Error('请输入教师姓名');
  if (!validPassword(password.value)) throw new Error('密码必须为 8 至 20 位字母和数字组合');
  if (!selectedSchool.value) throw new Error('请选择所属学校');
  await createOrganizationTeacher(accessToken, {
    username: account,
    displayName: name,
    mobile: mobile.value.trim() || undefined,
    initialPassword: password.value,
    schoolId: selectedSchool.value.id,
    classOrganizationIds: createClassIds.value
  });
}

async function saveEdit(accessToken: string): Promise<void> {
  if (!selectedTeacher.value) throw new Error('教师信息已失效');
  const name = displayName.value.trim();
  if (!name) throw new Error('请输入教师姓名');
  await updateOrganizationTeacher(accessToken, selectedTeacher.value.id, {
    displayName: name,
    mobile: mobile.value.trim() || undefined,
    clearMobile: clearMobile.value
  });
}

async function savePassword(accessToken: string): Promise<void> {
  if (!selectedTeacher.value) throw new Error('教师信息已失效');
  if (!validPassword(password.value)) throw new Error('密码必须为 8 至 20 位字母和数字组合');
  await resetOrganizationTeacherPassword(accessToken, selectedTeacher.value.id, password.value);
}

async function saveClasses(accessToken: string): Promise<void> {
  const teacher = selectedTeacher.value;
  if (!teacher) throw new Error('教师信息已失效');
  const before = new Set(teacher.classOrganizationIds);
  const after = new Set(teacherClassIds.value);
  for (const classId of teacherClassIds.value.filter((id) => !before.has(id))) {
    await bindOrganizationTeacherClass(accessToken, teacher.id, classId);
  }
  for (const classId of teacher.classOrganizationIds.filter((id) => !after.has(id))) {
    await unbindOrganizationTeacherClass(accessToken, teacher.id, classId);
  }
}

async function confirmStatus(teacher: OrganizationTeacher, status: TeacherStatus): Promise<void> {
  const session = getOrganizationSession();
  if (!session) return leavePage();
  const result = await uni.showModal({
    title: `确认${statusName(status)}教师？`,
    content: status === 'ENABLED' ? '恢复后教师可以重新登录。' : '操作后教师现有登录会话将立即失效。',
    confirmText: `确认${statusName(status)}`,
    confirmColor: status === 'ENABLED' ? '#167c5a' : '#b42318'
  });
  if (!result.confirm) return;
  try {
    await changeOrganizationTeacherStatus(session.accessToken, teacher.id, status);
    uni.showToast({ title: `已${statusName(status)}`, icon: 'success' });
    await loadTeachers(session.accessToken, 1, false);
  } catch (error) {
    showError(error instanceof Error ? error.message : '状态更新失败');
  }
}

function validPassword(value: string): boolean {
  return /^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d]{8,20}$/.test(value);
}

function statusName(status: TeacherStatus): string {
  return status === 'ENABLED' ? '启用' : status === 'DISABLED' ? '停用' : '锁定';
}

function isAccessFailure(error: unknown): boolean {
  return error instanceof ApiError
    && (error.statusCode === 401 || error.statusCode === 403 || error.code === 'FEATURE_DISABLED');
}

function showError(title: string): void {
  uni.showToast({ title, icon: 'none' });
}

async function leavePage(): Promise<void> {
  const pages = getCurrentPages();
  if (pages.length > 1) await uni.navigateBack();
  else await uni.reLaunch({ url: '/pages/organization-home/organization-home' });
}
</script>

<style lang="scss" scoped>
.page-shell { min-height: 100vh; box-sizing: border-box; padding-bottom: 48rpx; background: #f4f7f5; }
.search-band { display: grid; grid-template-columns: minmax(0, 1fr) 120rpx 120rpx; gap: 16rpx; padding: 24rpx; border-bottom: 2rpx solid #dbe3df; background: #ffffff; }
.search-input, .text-input, .picker-value { min-height: 76rpx; box-sizing: border-box; border: 2rpx solid #cbd7d1; border-radius: 8rpx; background: #ffffff; color: #1c2b28; font-size: 27rpx; }
.search-input, .text-input { padding: 0 22rpx; }
.search-button, .create-button { height: 76rpx; margin: 0; padding: 0; border-radius: 8rpx; font-size: 27rpx; }
.search-button { background: #ffffff; color: #167c5a; border: 2rpx solid #167c5a; }
.create-button, .save-button { background: #167c5a; color: #ffffff; }
.search-button::after, .create-button::after, .save-button::after, .cancel-button::after, .row-button::after, .load-more::after { border: 0; }
.state-text, .empty-text { padding: 80rpx 32rpx; text-align: center; color: #708078; font-size: 27rpx; }
.form-band, .list-band { margin-top: 24rpx; border-top: 2rpx solid #dbe3df; border-bottom: 2rpx solid #dbe3df; background: #ffffff; }
.form-band { padding: 30rpx 32rpx; }
.form-title { display: block; margin-bottom: 22rpx; color: #1c2b28; font-size: 31rpx; font-weight: 700; }
.field-label { display: block; margin: 24rpx 0 10rpx; color: #43534e; font-size: 25rpx; }
.picker-value { display: flex; align-items: center; padding: 0 22rpx; }
.check-list { display: grid; gap: 4rpx; }
.check-row, .switch-row { min-height: 82rpx; display: flex; align-items: center; gap: 14rpx; border-bottom: 2rpx solid #edf1ef; color: #263a35; font-size: 27rpx; }
.switch-row { justify-content: space-between; }
.form-actions { display: grid; grid-template-columns: 1fr 1fr; gap: 20rpx; margin-top: 32rpx; }
.form-actions button { height: 78rpx; margin: 0; border-radius: 8rpx; font-size: 27rpx; }
.cancel-button { background: #eef3f0; color: #43534e; }
.teacher-row { padding: 28rpx 32rpx; border-bottom: 2rpx solid #edf1ef; }
.teacher-row:last-child { border-bottom: 0; }
.teacher-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 20rpx; }
.teacher-main { min-width: 0; flex: 1; }
.teacher-name, .teacher-account, .teacher-meta { display: block; word-break: break-word; }
.teacher-name { color: #1c2b28; font-size: 30rpx; font-weight: 700; }
.teacher-account, .teacher-meta { margin-top: 8rpx; color: #708078; font-size: 23rpx; }
.status-text { flex: 0 0 auto; padding: 6rpx 12rpx; border-radius: 6rpx; background: #eef3f0; color: #56655f; font-size: 22rpx; }
.status-text.enabled { background: #e5f5ed; color: #167c5a; }
.status-text.locked { background: #fff1db; color: #8a5814; }
.row-actions { display: flex; flex-wrap: wrap; gap: 12rpx; margin-top: 22rpx; }
.row-button { min-width: 94rpx; height: 58rpx; margin: 0; padding: 0 16rpx; border-radius: 6rpx; background: #eef3f0; color: #334943; font-size: 23rpx; }
.row-button.enable { color: #167c5a; }
.row-button.warning { color: #8a5814; }
.row-button.danger { color: #b42318; }
.load-more { width: calc(100% - 64rpx); height: 76rpx; margin: 28rpx 32rpx 0; border-radius: 8rpx; background: #ffffff; color: #167c5a; font-size: 26rpx; }
</style>
