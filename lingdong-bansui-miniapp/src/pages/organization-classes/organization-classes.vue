<template>
  <view class="class-list-page"><view class="class-navigation"><NativeStatusSpacer/><view class="class-nav-line" :style="{paddingRight:(navReservedRight+76)+'px'}"><button class="class-back" aria-label="返回" @tap="backClassPage">‹</button><text>{{formVisible?(editingClass?'编辑班级':'新增班级'):mode==='CLASSES'?'班级列表':'教师班级'}}</text><button v-if="!formVisible&&mode==='CLASSES'&&entryPermissions.includes('CLASS_CREATE')" class="class-add" :style="{right:navReservedRight+'px'}" @tap="openCreate">＋ 新增班级</button></view></view>
    <view v-if="loading" class="state-text">正在加载</view>
    <template v-else-if="mode === 'CLASSES'">


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

      <view v-if="!formVisible" class="class-list-filters"><picker :disabled="loading||!!campusFilterError" :range="campusFilterLabels" :value="campusFilterIndex" @change="filterCampus"><view class="class-filter-value">{{campusFilterLabels[campusFilterIndex]}} ⌄</view></picker><picker :disabled="loading" :range="listGradeLabels" :value="listGradeIndex" @change="filterGrade"><view class="class-filter-value">{{listGradeLabels[listGradeIndex]}} ⌄</view></picker><picker :range="listStatusLabels" :value="listStatusIndex" @change="filterStatus"><view class="class-filter-value">{{listStatusLabels[listStatusIndex]}} ⌄</view></picker></view><text v-if="campusFilterError" class="filter-error">{{campusFilterError}}</text>
      <view v-if="!formVisible" class="class-list-content"><text class="class-meta">共 {{filteredClasses.length}} 个班级</text>
        <view v-for="item in filteredClasses" :key="item.id" class="class-card">
          <view class="class-card-top">
            <view class="class-badge"><PrototypeIcon name="people" :size="28"/></view>
            <view class="class-main" @tap="openDetail(item)">
              <text class="class-name">{{ item.name }}</text>
              <text class="class-meta">{{ memberSummary(item) }}</text>

            </view>
            <text :class="['status-badge', item.status === 'ENABLED' ? 'normal' : 'off']">
              {{ item.status === 'ENABLED' ? '正常' : '停用' }}
            </text><button class="class-detail-arrow" :aria-label="'进入'+item.name" @tap="openDetail(item)">›</button>
          </view>

        </view>
        <view v-if="filteredClasses.length === 0" class="empty-text">暂无班级</view>
      </view>
      <view v-if="!formVisible" class="action-band secondary-tools"><button v-if="entryPermissions.includes('ORG_MEMBER_READ')" class="secondary-button" @tap="openMembers">组织成员</button><button v-if="entryPermissions.some(p=>['STUDENT_ENROLLMENT_INVITE_CREATE','STUDENT_ENROLLMENT_REVIEW'].includes(p))" class="secondary-button" @tap="openEnrollment">班级入学</button>
        <button class="secondary-button" @tap="openStudents">学员关系</button>
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
    <view v-if="!formVisible" class="secondary-mode"><view class="mode-control">
      <button :class="{ active: mode === 'CLASSES' }" @tap="setMode('CLASSES')">班级维护</button>
      <button :class="{ active: mode === 'TEACHERS' }" @tap="setMode('TEACHERS')">教师班级</button>
    </view></view>
  </view>
</template>

<script setup lang="ts">
import NativeStatusSpacer from '@/components/NativeStatusSpacer.vue';
import {teacherNavReservedRight} from '@/models/teacher-nav-layout';
import {filterClassList} from '@/models/class-list-filter';
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
  createManagedClass,
  listClassHeadTeachers,
  listClassStudentCounts,
  listManageableSchools,
  listManageableCampuses,
  type OrganizationCampus,
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
const navReservedRight=ref(112);
// #ifdef MP-WEIXIN
try{navReservedRight.value=teacherNavReservedRight(uni.getSystemInfoSync().windowWidth,uni.getMenuButtonBoundingClientRect());}catch{}
// #endif
const listCampuses=ref<OrganizationCampus[]>([]),campusFilterId=ref(''),listGradeId=ref(''),listStatus=ref(''),campusFilterError=ref('');
const campusFilterLabels=computed(()=>['全部校区',...listCampuses.value.map(c=>c.name)]);
const campusFilterIndex=computed(()=>Math.max(0,listCampuses.value.findIndex(c=>c.id===campusFilterId.value)+1));
const filterGrades=computed(()=>grades.value.filter(g=>!campusFilterId.value||g.parentId===campusFilterId.value));
const listGradeLabels=computed(()=>['全部年级',...filterGrades.value.map(g=>g.name)]);
const listGradeIndex=computed(()=>Math.max(0,filterGrades.value.findIndex(g=>g.id===listGradeId.value)+1));
const listStatusLabels=['全部状态','正常','停用'],listStatusValues=['','ENABLED','DISABLED'];
const listStatusIndex=computed(()=>Math.max(0,listStatusValues.indexOf(listStatus.value)));
const filteredClasses=computed(()=>filterClassList(classes.value,grades.value,campusFilterId.value,listGradeId.value,listStatus.value));
function filterCampus(e:{detail:{value:string|number}}){const i=Number(e.detail.value);if(!Number.isInteger(i)||i<0||i>listCampuses.value.length)return;campusFilterId.value=listCampuses.value[i-1]?.id||'';listGradeId.value='';}
function filterGrade(e:{detail:{value:string|number}}){const i=Number(e.detail.value);if(!Number.isInteger(i)||i<0||i>filterGrades.value.length)return;listGradeId.value=filterGrades.value[i-1]?.id||'';}
function filterStatus(e:{detail:{value:string|number}}){const i=Number(e.detail.value);if(Number.isInteger(i)&&i>=0&&i<listStatusValues.length)listStatus.value=listStatusValues[i];}
function backClassPage(){if(formVisible.value){closeForm();return;}uni.navigateBack();}

const schools = ref<OrganizationClass[]>([]);
const grades = ref<OrganizationClass[]>([]);
const classes = ref<OrganizationClass[]>([]);
const teachers = ref<OrganizationTeacherOption[]>([]);
let classListViewEpoch=0,classListRequest=0,classTeacherRequest=0;
function classListScope(){const selected=uni.getStorageSync('lingdong.selected-organization') as {accessToken?:string;organizationId?:string}|undefined;return `${selected?.accessToken||''}:${selected?.organizationId||''}`;}
const entryPermissions=ref<string[]>([]);let entryEpoch=0;function clearEntries(){entryEpoch++;classListViewEpoch++;classListRequest++;classTeacherRequest++;entryPermissions.value=[];schools.value=[];grades.value=[];classes.value=[];teachers.value=[];classCounts.value={};headTeacherNames.value={};listCampuses.value=[];campusFilterId.value='';listGradeId.value='';listStatus.value='';campusFilterError.value='';submitting.value=false;closeForm();}onHide(clearEntries);onUnload(clearEntries);onShow(async()=>{const s=getOrganizationSession(),n=++entryEpoch;entryPermissions.value=[];if(!s)return;try{const me=await getOrganizationWorkbenchContext(s.accessToken);if(n===entryEpoch&&getOrganizationSession()?.accessToken===s.accessToken)entryPermissions.value=me.permissionCodes;}catch{/* Existing page displays its own authorization error. */}});function openMembers(){uni.navigateTo({url:"/pages/organization-members/organization-members"});}function openEnrollment(){uni.navigateTo({url:"/pages/institution-enrollment/institution-enrollment"});}
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
// 班级卡使用真实统计；缺失或失败保持未知，不伪造零人数或未设置。
const classCounts = ref<Record<string, number>>({});
const headTeacherNames = ref<Record<string, string | null>>({});
function memberSummary(item: OrganizationClass): string {
  const actualCount=classCounts.value[item.id];
  const count=Number.isSafeInteger(actualCount)&&actualCount>=0?actualCount:'—';
  const teacher = headTeacherNames.value[item.id];
  return `${count} 名成长小主人 ｜ 班主任：${teacher === undefined ? '—' : teacher || '未设置'}`;
}

const schoolLabels = computed(() => schools.value.map((item) => `${item.name}（${item.code}）`));
const selectedSchool = computed(() => schools.value[schoolIndex.value] || null);
const selectedSchoolLabel = computed(() => schoolLabels.value[schoolIndex.value] || '');
const availableGrades = computed(() => {
  const schoolId=selectedSchool.value?.id;
  if(!schoolId)return [];
  return grades.value.filter(item=>(item.schoolOrganizationId
    ?item.schoolOrganizationId===schoolId:item.parentId===schoolId)&&item.effectiveStatus==='ENABLED');
});
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
  const viewEpoch=classListViewEpoch,scope=classListScope();
  const session = getOrganizationSession();
  if (!session) {
    await leavePage(true);
    return;
  }
  loading.value = true;
  try {
    const capabilities = await getMiniappCapabilities();
    if(viewEpoch!==classListViewEpoch||scope!==classListScope()||getOrganizationSession()?.accessToken!==session.accessToken)return;
    if (!capabilities.organizationMiniappAuthEnabled || !capabilities.classManagementEnabled) {
      return leavePage(false);
    }
    await loadClasses(session.accessToken);
    if(viewEpoch!==classListViewEpoch||scope!==classListScope()||getOrganizationSession()?.accessToken!==session.accessToken)return;
    if(requestedEdit){const target=classes.value.find(item=>item.id===requestedEdit);if(target&&getOrganizationSession()?.accessToken===session.accessToken)openEdit(target);requestedEdit='';}
  } catch (error) {
    if(viewEpoch!==classListViewEpoch||scope!==classListScope()||getOrganizationSession()?.accessToken!==session.accessToken)return;
    if (error instanceof ApiError
        && (error.statusCode === 401 || error.statusCode === 403 || error.code === 'FEATURE_DISABLED')) {
      return leavePage(error.statusCode === 401);
    }
    showError('班级信息加载失败');
  } finally {
    if(viewEpoch===classListViewEpoch&&scope===classListScope())loading.value = false;
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
  if(submitting.value)return;
  const viewEpoch=classListViewEpoch,scope=classListScope();
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

  const current=()=>viewEpoch===classListViewEpoch&&scope===classListScope()&&getOrganizationSession()?.accessToken===session.accessToken;
  submitting.value = true;
  try {
    if (editingClass.value) {
      await updateManagedClass(
        session.accessToken, editingClass.value.id, normalizedName,
        sortOrder, editingClass.value.versionNo
      );
      if(!current())return;
      uni.showToast({ title: '班级已更新', icon: 'success' });
    } else {
      await createManagedClass(
        session.accessToken, selectedSchool.value!.id, normalizedName, sortOrder, selectedGrade.value!.id
      );
      if(!current())return;
      uni.showToast({ title: '班级已创建', icon: 'success' });
    }
    closeForm();
    await loadClasses(session.accessToken);
  } catch (error) {
    if(current())showError(error instanceof Error ? error.message : '保存失败');
  } finally {
    if(viewEpoch===classListViewEpoch)submitting.value = false;
  }
}


async function selectTeacherClass(event: { detail: { value: string } }): Promise<void> {
  if(submitting.value)return;
  const index=Number(event.detail.value);
  if(!Number.isInteger(index)||index<0||index>=activeClasses.value.length)return;
  teacherClassIndex.value=index;teacherIndex.value=-1;teachers.value=[];
  const session=getOrganizationSession(),selectedClass=selectedTeacherClass.value;
  if(!session||!selectedClass)return;
  const n=++classTeacherRequest,viewEpoch=classListViewEpoch,scope=classListScope();
  const current=()=>n===classTeacherRequest&&viewEpoch===classListViewEpoch&&scope===classListScope()&&getOrganizationSession()?.accessToken===session.accessToken&&selectedTeacherClass.value?.id===selectedClass.id;
  try {
    const candidates=await listOrganizationTeachers(session.accessToken,selectedClass.id);
    if(current())teachers.value=candidates;
  } catch(error) {
    if(current())showError(error instanceof Error?error.message:'教师候选加载失败');
  }
}

function selectTeacher(event: { detail: { value: string } }): void {
  teacherIndex.value = Number(event.detail.value);
}

async function updateTeacher(active: boolean): Promise<void> {
  if(submitting.value)return;
  const session=getOrganizationSession(),selectedClass=selectedTeacherClass.value,teacher=selectedTeacher.value;
  if(!session){await leavePage(true);return;}
  if(!selectedClass)return showError('请选择班级');
  if(!teacher)return showError('请选择教师');
  const viewEpoch=classListViewEpoch,scope=classListScope();
  const current=()=>viewEpoch===classListViewEpoch&&scope===classListScope()&&getOrganizationSession()?.accessToken===session.accessToken&&selectedTeacherClass.value?.id===selectedClass.id&&selectedTeacher.value?.userId===teacher.userId;
  submitting.value=true;
  try {
    if(!active){
      const confirmed=await showConfirm('确认解除教师班级？',`${teacher.displayName} 将不再关联 ${selectedClass.name}。`,'确认解除','#b42318');
      if(!confirmed||!current())return;
    }
    if(active)await bindOrganizationTeacher(session.accessToken,teacher.userId,selectedClass.id);
    else await unbindOrganizationTeacher(session.accessToken,teacher.userId,selectedClass.id);
    if(current())uni.showToast({title:active?'教师班级已绑定':'教师班级已解除',icon:'success'});
  } catch(error) {
    if(current())showError(error instanceof Error?error.message:'教师班级更新失败');
  } finally {
    if(viewEpoch===classListViewEpoch)submitting.value=false;
  }
}

async function loadClasses(accessToken: string): Promise<void> {
  const n=++classListRequest,viewEpoch=classListViewEpoch,scope=classListScope();
  const [schoolList, gradeList, classList, counts, headTeachers,campusResult] = await Promise.all([
    listManageableSchools(accessToken), listManageableGrades(accessToken), listManagedClasses(accessToken),
    listClassStudentCounts(accessToken).catch(() => [] as { organizationId: string; memberCount: number }[]),
    listClassHeadTeachers(accessToken).catch(() => [] as { organizationId: string; headTeacherName: string | null }[]),
    listManageableCampuses(accessToken).then(items=>({items,error:''}),()=>({items:[] as OrganizationCampus[],error:'校区筛选加载失败，请返回后重试'}))
  ]);
  if(n!==classListRequest||viewEpoch!==classListViewEpoch||scope!==classListScope()||getOrganizationSession()?.accessToken!==accessToken)return;
  schools.value = schoolList;grades.value = gradeList;classes.value = classList;
  listCampuses.value=campusResult.items;campusFilterError.value=campusResult.error;
  const countMap: Record<string, number> = {};
  for (const entry of counts) countMap[entry.organizationId] = entry.memberCount;
  classCounts.value = countMap;
  const nameMap: Record<string, string | null> = {};
  for (const entry of headTeachers) nameMap[entry.organizationId] = entry.headTeacherName;
  headTeacherNames.value = nameMap;
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
/* 班级卡布局（对照原型 03-06）：主体信息与操作分行，操作按钮不再与文本争宽度溢出屏幕 */
.class-card{background:#fff;border:1rpx solid #e5ece5;border-radius:20rpx;padding:20rpx;margin-bottom:16rpx}
.class-card-top{display:flex;align-items:center;gap:16rpx}
.class-card-top .class-main{min-width:0;flex:1}
.class-card .class-name,.class-card .class-meta,.class-card .class-code{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.status-badge{flex-shrink:0;font-size:22rpx;padding:8rpx 18rpx;border-radius:999rpx;white-space:nowrap}
.status-badge.normal{background:#e1f3e4;color:#137c53}
.status-badge.off{background:#fdeada;color:#b06c51}
.card-actions{display:flex;justify-content:flex-end;gap:12rpx;margin-top:14rpx;padding-top:14rpx;border-top:1rpx solid #f0f4ee}
.row-button{font-size:22rpx;padding:10rpx 16rpx;white-space:nowrap}
.row-button.danger{background:#b42318;color:#fff}
.row-button.enable{background:#167c5a;color:#fff}
</style>

<style scoped>
.class-list-page{min-height:100vh;box-sizing:border-box;padding:0 24rpx calc(32rpx + env(safe-area-inset-bottom));background:#fff;color:#192f27}
.class-navigation{margin:0 -24rpx;background:#fff}.class-nav-line{height:88rpx;position:relative;box-sizing:border-box;display:flex;align-items:center;justify-content:center;font-size:30rpx;font-weight:650}.class-nav-line>text{white-space:nowrap}.class-back{position:absolute;left:16rpx;top:0;width:56rpx;height:88rpx;padding:0;line-height:78rpx;font-size:52rpx;background:transparent}.class-add{position:absolute;top:0;padding:0;line-height:88rpx;font-size:25rpx;color:#17674d;background:transparent}
.class-list-filters{display:flex;gap:12rpx;padding:12rpx 0 18rpx}.class-list-filters picker{flex:1;min-width:0}.class-list-filters .class-filter-value{border:1rpx solid #dfe7e2;border-radius:12rpx;padding:12rpx 14rpx;font-size:24rpx;overflow:hidden;white-space:nowrap;text-overflow:ellipsis}.filter-error{display:block;font-size:23rpx;color:#b24a36;margin-bottom:12rpx}
.class-list-content{padding:0;background:transparent;border:0;border-radius:0}.class-list-content>.class-meta{font-size:23rpx;color:#77857c;margin:0 0 12rpx}.class-card{padding:16rpx 4rpx 8rpx;border:0;border-bottom:1rpx solid #e8eeea;border-radius:0;margin:0}.class-card-top{gap:14rpx}.class-badge{width:60rpx;height:60rpx;flex-shrink:0;border-radius:12rpx;background:#e6f2fa;margin-right:0}.class-card .class-name{font-size:27rpx;font-weight:650}.class-card .class-meta{font-size:22rpx;margin-top:5rpx;color:#7d8790}.class-card .status-badge{padding:4rpx 14rpx;font-size:22rpx}.class-card .card-actions{margin:4rpx 0 0;padding:0;min-height:0;border:0;gap:16rpx}.class-card .card-actions button{background:transparent;padding:2rpx 4rpx;font-size:21rpx;border-radius:0}.class-card .card-actions .danger{color:#af5f4e}.secondary-tools{margin-top:24rpx;padding-top:18rpx;border-top:1rpx solid #edf1ee;gap:10rpx}.secondary-tools button{font-size:22rpx;padding:8rpx 12rpx}.secondary-mode{margin-top:16rpx}.secondary-mode .mode-control button{font-size:23rpx;padding:10rpx}.form-band>.form-title{display:none}.form-band .picker-value{overflow:hidden;white-space:nowrap;text-overflow:ellipsis}
</style>

<style scoped>.class-detail-arrow{padding:0 0 0 4rpx;background:transparent;line-height:1;font-size:36rpx;color:#728379;flex-shrink:0}</style>
