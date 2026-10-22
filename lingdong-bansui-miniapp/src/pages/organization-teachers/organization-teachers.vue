<template>
  <view class="teacher-page" :class="{configuring:mode==='CLASSES'}"><view class="teacher-navigation"><NativeStatusSpacer/><view class="teacher-nav-line" :style="{paddingRight:(navReservedRight+76)+'px'}"><button class="teacher-back" aria-label="返回" @tap="backTeacherPage">‹</button><text>{{mode === 'LIST' ? '教师列表' : formTitle}}</text><button v-if="mode==='LIST'&&canCreate" class="teacher-add" :style="{right:navReservedRight+'px'}" @tap="openCreate">＋ 新增教师</button></view></view>
    <view v-if="mode === 'LIST'" class="search-band">
      <input v-model="keyword" class="search-input" maxlength="64" placeholder="搜索教师姓名或账号" confirm-type="search" @confirm="search" />
      <button class="search-button" @tap="search">查询</button>

    </view>

    <view v-if="mode==='LIST'" class="teacher-filter-row"><picker :range="subjectLabels" :value="subjectFilterIndex" :disabled="loading||!subjectOptions.length" @change="changeSubjectFilter"><view>{{subjectLabels[subjectFilterIndex]}} ⌄</view></picker><picker v-if="permissions.includes('CLASS_READ')" :range="campusLabels" :value="campusFilterIndex" :disabled="loading||!campuses.length" @change="changeCampusFilter"><view>{{campusLabels[campusFilterIndex]}} ⌄</view></picker><picker :range="statusFilterLabels" :value="statusFilterIndex" @change="changeStatusFilter"><view>{{statusFilterLabels[statusFilterIndex]}} ⌄</view></picker></view>
    <text v-if="subjectOptionsError" class="candidate-error">{{subjectOptionsError}}</text>
    <text v-if="subjectDisplayError" class="candidate-error">{{subjectDisplayError}}</text>
    <text v-if="candidateError" class="candidate-error">{{candidateError}}</text>
    <view v-if="loading" class="state-text">正在加载</view>
    <template v-else>
      <!-- 标题由顶部导航栏单行渲染，表单内不再重复输出 -->
      <view v-if="mode !== 'LIST'" class="teacher-form-band">
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
          <TeacherSubjectEditor v-if="selectedTeacher&&editorToken&&editorScope" :token="editorToken" :scope="editorScope" :teacher-id="selectedTeacher.id" :institution-id="selectedTeacher.schoolId" :editable="canUpdate" :options="subjectOptions" @saved="updateSubjectLabel" />
        </template>

        <template v-else-if="mode === 'PASSWORD'">
          <text class="field-label">新密码</text>
          <input v-model="password" class="text-input" maxlength="20" password placeholder="8 至 20 位字母和数字组合" />
        </template>

        <template v-else-if="mode === 'CLASSES'"><view class="teacher-config-profile"><PrototypeIcon name="user" :size="52"/><view><text class="teacher-name">{{selectedTeacher?.displayName}}</text><text class="teacher-account">{{selectedTeacher?.schoolName}}</text></view></view><view class="class-select-header"><text>选择班级（可多选）</text><checkbox-group @change="toggleAllTeacherClasses"><label><checkbox value="all" :checked="allTeacherClassesSelected" :disabled="submitting||!availableTeacherClasses.length" color="#184f40"/>全选本校班级</label></checkbox-group></view>
          <scroll-view class="class-tree-scroll" scroll-y><checkbox-group class="check-list" @change="selectTeacherClasses">
            <view v-for="(group,index) in teacherClassGroups" :key="group.id" class="class-tree-group"><text v-if="group.campusName&&(index===0||teacherClassGroups[index-1].campusId!==group.campusId)" class="class-campus-heading" @tap="toggleTeacherCampus(group.campusId)">{{collapsedTeacherCampuses.includes(group.campusId)?"›":"⌄"}} {{group.campusName}}</text><view v-if="group.gradeName" v-show="!collapsedTeacherCampuses.includes(group.campusId)" class="class-grade-controls"><button class="grade-select" :disabled="submitting" :aria-label="'选择'+group.gradeName" @tap.stop="toggleGradeSelection(group.id)">{{gradeSelected(group.id)?'☑':'☐'}}</button><text class="class-grade-heading" @tap="toggleTeacherGrade(group.id)">{{collapsedTeacherGrades.includes(group.id)?"›":"⌄"}} {{group.gradeName}}</text></view><label v-show="!collapsedTeacherCampuses.includes(group.campusId)&&!collapsedTeacherGrades.includes(group.id)" v-for="item in group.items" :key="item.id" class="check-row class-tree-row">
              <checkbox :disabled="submitting" :value="item.id" :checked="teacherClassIds.includes(item.id)" color="#167c5a" />
              <text>{{ item.name }}</text>
            </label></view>
          </checkbox-group></scroll-view>
          <view v-if="availableTeacherClasses.length === 0" class="empty-text">该学校暂无可用班级</view>
        </template>

        <view class="form-actions">
          <button class="cancel-button" :disabled="submitting" @tap="closeForm">取消</button>
          <button class="save-button" :disabled="submitting" @tap="saveForm">
            {{ submitting ? '正在保存' : '保存' }}
          </button>
        </view>
      </view>

      <view v-if="mode === 'LIST'" class="teacher-list-band">
        <view v-for="teacher in teachers" :key="teacher.id" class="teacher-row" @tap="openDetails(teacher)">
          <view class="teacher-head"><view class="teacher-avatar"><PrototypeIcon name="user" :size="32"/></view>
            <view class="teacher-main">
              <text class="teacher-name">{{ teacher.displayName }}<text v-if="teacherSubjectLabel(teacher)" class="teacher-subject"> · {{teacherSubjectLabel(teacher)}}</text></text>
              <text class="teacher-account">{{teacherCampusLabel(teacher,classes,grades,campuses)}} · {{teacherClassCount(teacher)??'—'}} 个班级</text>
            </view>
            <text :class="['status-text', teacher.status.toLowerCase()]">{{ statusName(teacher.status) }}</text>
          </view>
          <text class="detail-arrow">›</text>
        </view>
        <view v-if="teachers.length === 0" class="empty-text">暂无匹配教师</view>
      </view>

      <button v-if="teachers.length < total" class="load-more" :disabled="loadingMore" @tap="loadMore">
        {{ loadingMore ? '正在加载' : '加载更多' }}
      </button>
    </template>
    <view v-if="detailTeacher" class="teacher-overlay" @tap="detailTeacher=null"><view class="teacher-sheet" @tap.stop><view class="sheet-heading"><text>{{detailTeacher.displayName}}</text><button aria-label="关闭" @tap="detailTeacher=null">×</button></view><view v-for="teacher in [detailTeacher]" :key="teacher.id">          <text class="teacher-meta">{{ teacher.username }}<template v-if="teacher.mobile"> · {{ teacher.mobile }}</template></text>
          <view class="row-actions">
            <button v-if="canUpdate" class="row-button" @tap="openEdit(teacher)">编辑</button>
            <button v-if="canResetPassword" class="row-button" @tap="openPassword(teacher)">密码</button>
            <button v-if="canManageClass" class="row-button" @tap="openClasses(teacher)">班级</button>
            <button v-if="canChangeStatus && teacher.status !== 'ENABLED'" class="row-button enable" @tap="confirmStatus(teacher, 'ENABLED')">启用</button>
            <button v-if="canChangeStatus && teacher.status !== 'DISABLED'" class="row-button danger" @tap="confirmStatus(teacher, 'DISABLED')">停用</button>
            <button v-if="canChangeStatus && teacher.status !== 'LOCKED'" class="row-button warning" @tap="confirmStatus(teacher, 'LOCKED')">锁定</button>
          </view></view></view></view>
  </view>
</template>

<script setup lang="ts">
import {teacherCampusLabel} from '@/models/teacher-campus-label';
import {teacherNavReservedRight} from '@/models/teacher-nav-layout';
import NativeStatusSpacer from '@/components/NativeStatusSpacer.vue';
import TeacherSubjectEditor from '@/components/TeacherSubjectEditor.vue';
import {mergeTeacherClassSelection,sortTeacherClasses,groupTeacherClasses,toggleTeacherClassGroup} from "@/models/teacher-class-selection";
import {classBelongsToSchool} from "@/models/class-school";
import PrototypeIcon from '@/components/PrototypeIcon.vue';
import { computed, ref } from 'vue';
import { onShow,onHide,onUnload } from '@dcloudio/uni-app';
import { getMiniappCapabilities } from '@/api/capability';
import { ApiError } from '@/api/http';
import {
  listManageableSchools, listManagedClasses, listManageableGrades, type OrganizationClass
} from '@/api/organization-classes';
import { getOrganizationWorkbenchContext } from '@/api/organization-workbench';
import {
  listTeacherCampuses,type TeacherCampusOption,
  listTeacherSubjectOptions,type TeacherSubjectOption,
  listTeacherSubjectSummaries,type TeacherSubject,
  changeOrganizationTeacherClasses,
  changeOrganizationTeacherStatus,
  createOrganizationTeacher,
  listOrganizationTeachers,
  resetOrganizationTeacherPassword,
  updateOrganizationTeacher,
  type OrganizationTeacher,
  type TeacherStatus
} from '@/api/teacher-management';
import { getOrganizationSession } from '@/session/organization-session';

const navReservedRight=ref(12);
// #ifdef MP-WEIXIN
try{navReservedRight.value=teacherNavReservedRight(uni.getSystemInfoSync().windowWidth,uni.getMenuButtonBoundingClientRect());}catch{navReservedRight.value=112;}
// #endif
type FormMode = 'LIST' | 'CREATE' | 'EDIT' | 'PASSWORD' | 'CLASSES';

const statusFilterLabels=['全部状态','启用','停用','锁定'];const statusFilterValues:(TeacherStatus|undefined)[]=[undefined,'ENABLED','DISABLED','LOCKED'];const statusFilterIndex=ref(0);
function changeStatusFilter(event:{detail:{value:string|number}}){if(loading.value||loadingMore.value)return;statusFilterIndex.value=Number(event.detail.value);void search();}
const campuses=ref<TeacherCampusOption[]>([]),campusFilterIndex=ref(0),candidateError=ref('');const campusLabels=computed(()=>['全部校区',...campuses.value.map(c=>c.name)]);
function changeCampusFilter(event:{detail:{value:string|number}}){if(loading.value||loadingMore.value)return;const index=Number(event.detail.value);if(!Number.isInteger(index)||index<0||index>campuses.value.length)return;campusFilterIndex.value=index;void search();}
const subjectOptions=ref<TeacherSubjectOption[]>([]),subjectFilterIndex=ref(0),subjectOptionsError=ref('');
const subjectLabels=computed(()=>['全部学科',...subjectOptions.value.map(item=>item.name)]);
function changeSubjectFilter(event:{detail:{value:string|number}}){
  if(loading.value||loadingMore.value)return;
  const index=Number(event.detail.value);
  if(!Number.isInteger(index)||index<0||index>subjectOptions.value.length)return;
  subjectFilterIndex.value=index;void search();
}
async function loadSubjectOptions(accessToken:string):Promise<void>{
  const epoch=viewEpoch,scope=teacherScope();
  try{
    const result=await listTeacherSubjectOptions(accessToken);
    if(!viewVisible||epoch!==viewEpoch||scope!==teacherScope()||getOrganizationSession()?.accessToken!==accessToken)return;
    subjectOptions.value=result;subjectFilterIndex.value=0;subjectOptionsError.value='';
  }catch{
    if(!viewVisible||epoch!==viewEpoch||scope!==teacherScope()||getOrganizationSession()?.accessToken!==accessToken)return;
    subjectOptions.value=[];subjectFilterIndex.value=0;subjectOptionsError.value='学科选项加载失败，请重新进入后重试';
  }
}
const PAGE_SIZE = 20;
const detailTeacher=ref<OrganizationTeacher|null>(null);let loadedTeacherContext='';
const teacherSubjects=ref<Record<string,string>>({}),teacherClassCounts=ref<Record<string,number>>({}),subjectDisplayError=ref('');
function teacherClassCount(teacher:OrganizationTeacher):number|undefined{return teacherClassCounts.value[teacher.id+'|'+teacher.schoolId];}
function teacherSubjectLabel(teacher:OrganizationTeacher){return teacherSubjects.value[teacher.id+'|'+teacher.schoolId]||'';}
function updateSubjectLabel(value:TeacherSubject){
  if(!viewVisible||loadedTeacherContext!==getOrganizationSession()?.accessToken+'|'+teacherScope()||!teachers.value.some(item=>item.id===value.teacherUserId&&item.schoolId===value.institutionOrganizationId))return;
  teacherSubjects.value={...teacherSubjects.value,[value.teacherUserId+'|'+value.institutionOrganizationId]:value.subjectCode?(subjectOptions.value.find(item=>item.code===value.subjectCode)?.name||value.subjectCode):''};
}
const teachers = ref<OrganizationTeacher[]>([]);
const schools = ref<OrganizationClass[]>([]);
const classes = ref<OrganizationClass[]>([]);
const grades = ref<OrganizationClass[]>([]);
const permissions = ref<string[]>([]);
const loading = ref(true);
const loadingMore = ref(false);
const submitting = ref(false);
const total = ref(0);
const page = ref(1);
const keyword = ref('');
const mode = ref<FormMode>('LIST');
const selectedTeacher = ref<OrganizationTeacher | null>(null);
const editorToken=ref(''),editorScope=ref('');
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
  (item) => item.status === 'ENABLED' && classBelongsToSchool(item, selectedSchool.value?.id)
));
const availableTeacherClasses = computed(() => sortTeacherClasses(classes.value.filter(
  (item) => item.status === 'ENABLED' && classBelongsToSchool(item, selectedTeacher.value?.schoolId)
),grades.value,campuses.value));
const collapsedTeacherCampuses=ref<string[]>([]);
function toggleTeacherCampus(id:string){if(submitting.value||!id)return;collapsedTeacherCampuses.value=collapsedTeacherCampuses.value.includes(id)?collapsedTeacherCampuses.value.filter(value=>value!==id):[...collapsedTeacherCampuses.value,id];}
const collapsedTeacherGrades=ref<string[]>([]);
function toggleTeacherGrade(id:string){if(submitting.value)return;collapsedTeacherGrades.value=collapsedTeacherGrades.value.includes(id)?collapsedTeacherGrades.value.filter(value=>value!==id):[...collapsedTeacherGrades.value,id];}
const teacherClassGroups=computed(()=>groupTeacherClasses(availableTeacherClasses.value,grades.value,campuses.value));
function gradeSelected(id:string){const group=teacherClassGroups.value.find(item=>item.id===id);return !!group?.items.length&&group.items.every(item=>teacherClassIds.value.includes(item.id));}
function toggleGradeSelection(id:string){
  if(submitting.value)return;
  const group=teacherClassGroups.value.find(item=>item.id===id);if(!group)return;
  try{teacherClassIds.value=toggleTeacherClassGroup(teacherClassIds.value,availableTeacherClasses.value.map(item=>item.id),group.items.map(item=>item.id));}catch(e){showError(e instanceof Error?e.message:'班级候选已变化');}
}
const formTitle = computed(() => {
  if (mode.value === 'CREATE') return '新增教师';
  if (mode.value === 'EDIT') return `编辑${selectedTeacher.value?.displayName || '教师'}`;
  if (mode.value === 'PASSWORD') return `重置${selectedTeacher.value?.displayName || '教师'}的密码`;
  if (mode.value === 'CLASSES') return '配置教师班级';
  return '';
});

let viewEpoch=0,teacherListRevision=0,viewVisible=true;
function teacherScope(){const s=uni.getStorageSync('lingdong.selected-organization') as {accessToken?:string;organizationId?:string}|undefined;return `${s?.accessToken||''}:${s?.organizationId||''}`;}
function hideTeacherPage(){submitting.value=false;teacherClassCounts.value={};teacherSubjects.value={};subjectDisplayError.value='';subjectOptions.value=[];subjectFilterIndex.value=0;subjectOptionsError.value='';campuses.value=[];campusFilterIndex.value=0;candidateError.value='';detailTeacher.value=null;loadedTeacherContext='';viewVisible=false;++viewEpoch;teachers.value=[];permissions.value=[];schools.value=[];classes.value=[];grades.value=[];loading.value=false;loadingMore.value=false;closeForm();}
onHide(hideTeacherPage);onUnload(hideTeacherPage);
onShow(async () => {
  viewVisible=true;const shownEpoch=++viewEpoch,shownScope=teacherScope();
  const session = getOrganizationSession();
  if (!session) return leavePage();
  loading.value = true;
  try {
    const [capabilities, context] = await Promise.all([
      getMiniappCapabilities(),getOrganizationWorkbenchContext(session.accessToken)
    ]);
    if(!viewVisible||viewEpoch!==shownEpoch||teacherScope()!==shownScope||getOrganizationSession()?.accessToken!==session.accessToken)return;
    if (!capabilities.organizationMiniappAuthEnabled
        || !capabilities.teacherManagementEnabled
        || !context.permissionCodes.includes('TEACHER_READ')) {
      return leavePage();
    }
    candidateError.value='';
    const [loadedSchools,loadedClasses,loadedCampuses,loadedGrades]=context.permissionCodes.includes('CLASS_READ')?await Promise.all([listManageableSchools(session.accessToken),listManagedClasses(session.accessToken),listTeacherCampuses(session.accessToken),listManageableGrades(session.accessToken)]).catch(()=>{if(viewVisible&&viewEpoch===shownEpoch)candidateError.value='组织筛选候选加载失败，请重新进入后重试';return [[],[],[],[]] as [OrganizationClass[],OrganizationClass[],TeacherCampusOption[],OrganizationClass[]];}):[[],[],[],[]];
    if(!viewVisible||viewEpoch!==shownEpoch||teacherScope()!==shownScope||getOrganizationSession()?.accessToken!==session.accessToken)return;
    permissions.value = context.permissionCodes;
    schools.value = loadedSchools.filter((item) => item.effectiveStatus === 'ENABLED');
    classes.value = loadedClasses;
    grades.value = loadedGrades;
    const selection=uni.getStorageSync('lingdong.selected-organization') as {organizationId?:string;typeCode?:string};
    campuses.value=loadedCampuses.filter(c=>c.typeCode==='CAMPUS'&&c.status==='ENABLED'&&c.effectiveStatus==='ENABLED'&&(!['SCHOOL','TRAINING_INSTITUTION'].includes(selection?.typeCode||'')||c.schoolOrganizationId===selection.organizationId)).sort((a,b)=>a.sortOrder-b.sortOrder||a.name.localeCompare(b.name,'zh-CN'));
    campusFilterIndex.value=0;
    await Promise.all([loadTeachers(session.accessToken, 1, false),loadSubjectOptions(session.accessToken)]);
  } catch (error) {
    if (isAccessFailure(error)) return leavePage();
    showError('教师信息加载失败');
  } finally {
    loading.value = false;
  }
});

async function loadTeachers(accessToken: string, targetPage: number, append: boolean): Promise<void> {
  const requestSeq=++teacherListRevision,requestEpoch=viewEpoch,requestScope=teacherScope();
  const result = await listOrganizationTeachers(accessToken, keyword.value, targetPage, PAGE_SIZE,{subjectCode:subjectOptions.value[subjectFilterIndex.value-1]?.code,status:statusFilterValues[statusFilterIndex.value],campusOrganizationId:campuses.value[campusFilterIndex.value-1]?.id});
  if(!viewVisible||requestSeq!==teacherListRevision||requestEpoch!==viewEpoch||requestScope!==teacherScope()||getOrganizationSession()?.accessToken!==accessToken)return;
  detailTeacher.value=null;loadedTeacherContext=accessToken+'|'+requestScope;
  teachers.value = append ? [...teachers.value, ...result.items] : result.items;
  total.value = result.total;
  page.value = result.page;
  if(!append){teacherSubjects.value={};teacherClassCounts.value={};}
  subjectDisplayError.value='';
  if(result.items.length){
    const current=()=>viewVisible&&requestSeq===teacherListRevision&&requestEpoch===viewEpoch&&requestScope===teacherScope()&&getOrganizationSession()?.accessToken===accessToken;
    try{
      const summaries=await listTeacherSubjectSummaries(accessToken,result.items.map(item=>({teacherUserId:item.id,institutionOrganizationId:item.schoolId})));
      if(!current())return;
      const allowed=new Set(result.items.map(item=>item.id+'|'+item.schoolId));
      const next={...teacherSubjects.value},nextCounts={...teacherClassCounts.value};
      for(const summary of summaries){const key=summary.teacherUserId+'|'+summary.institutionOrganizationId;if(allowed.has(key)){next[key]=summary.subjectName||summary.subjectCode||'';if(Number.isInteger(summary.classCount)&&summary.classCount>=0)nextCounts[key]=summary.classCount;}}
      teacherSubjects.value=next;teacherClassCounts.value=nextCounts;
    }catch{if(current())subjectDisplayError.value='学科与班级数加载失败，请重新查询';}
  }
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

function openDetails(teacher:OrganizationTeacher){if(!viewVisible||loadedTeacherContext!==getOrganizationSession()?.accessToken+'|'+teacherScope()||!teachers.value.some(t=>t.id===teacher.id))return;detailTeacher.value=teacher;}
function backTeacherPage(){if(mode.value!=='LIST'){closeForm();return;}uni.navigateBack({fail:()=>uni.reLaunch({url:'/pages/organization-home/organization-home'})});}
function openCreate(): void {
  resetForm();
  mode.value = 'CREATE';
}

function openEdit(teacher: OrganizationTeacher): void {
  resetForm();
  const session=getOrganizationSession();const selected=uni.getStorageSync('lingdong.selected-organization') as {accessToken?:string;organizationId?:string}|undefined;
  if(!session||!selected?.organizationId||selected.accessToken!==session.accessToken)return;
  editorToken.value=session.accessToken;editorScope.value=selected.organizationId;
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
  collapsedTeacherGrades.value=[];collapsedTeacherCampuses.value=[];
  editorToken.value='';editorScope.value='';
  detailTeacher.value=null;
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

const allTeacherClassesSelected=computed(()=>availableTeacherClasses.value.length>0&&availableTeacherClasses.value.every(c=>teacherClassIds.value.includes(c.id)));
function toggleAllTeacherClasses(event:{detail:{value:string[]}}){if(submitting.value)return;const available=availableTeacherClasses.value.map(c=>c.id);teacherClassIds.value=mergeTeacherClassSelection(teacherClassIds.value,available,event.detail.value.includes('all')?available:[]);}
function selectTeacherClasses(event: { detail: { value: string[] } }): void {
  if(submitting.value)return;
  try { teacherClassIds.value=mergeTeacherClassSelection(teacherClassIds.value,availableTeacherClasses.value.map(c=>c.id),event.detail.value); }
  catch(e){uni.showToast({title:e instanceof Error?e.message:'班级选择失败',icon:'none'});}
}

function toggleClearMobile(event: Event): void {
  clearMobile.value = (event as CustomEvent<{ value: boolean }>).detail.value;
  if (clearMobile.value) mobile.value = '';
}

async function saveForm(): Promise<void> {
  if(submitting.value)return;
  const session = getOrganizationSession();
  if (!session) return leavePage();
  if(!viewVisible)return;
  const epoch=viewEpoch,scope=teacherScope();
  const current=()=>viewVisible&&epoch===viewEpoch&&scope===teacherScope()&&getOrganizationSession()?.accessToken===session.accessToken;
  submitting.value = true;
  try {
    if (mode.value === 'CREATE') await saveCreate(session.accessToken);
    else if (mode.value === 'EDIT') await saveEdit(session.accessToken);
    else if (mode.value === 'PASSWORD') await savePassword(session.accessToken);
    else if (mode.value === 'CLASSES') await saveClasses(session.accessToken);
    else return;
    if(!current())return;
    uni.showToast({ title: '保存成功', icon: 'success' });
    closeForm();
    await loadTeachers(session.accessToken, 1, false);
  } catch (error) {
    if(current())showError(error instanceof Error ? error.message : '保存失败');
  } finally {
    if(current())submitting.value = false;
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
  const additions=teacherClassIds.value.filter(id=>!before.has(id));
  const removals=teacher.classOrganizationIds.filter(id=>!after.has(id));
  if(additions.length||removals.length)await changeOrganizationTeacherClasses(accessToken,teacher.id,additions,removals);
}

async function confirmStatus(teacher: OrganizationTeacher, status: TeacherStatus): Promise<void> {
  const session = getOrganizationSession();
  if (!session) return leavePage();
  if(!viewVisible||submitting.value)return;
  const epoch=viewEpoch,scope=teacherScope();
  const current=()=>viewVisible&&epoch===viewEpoch&&scope===teacherScope()&&getOrganizationSession()?.accessToken===session.accessToken;
  const result = await uni.showModal({
    title: `确认${statusName(status)}教师？`,
    content: status === 'ENABLED' ? '恢复后教师可以重新登录。' : '操作后教师现有登录会话将立即失效。',
    confirmText: `确认${statusName(status)}`,
    confirmColor: status === 'ENABLED' ? '#167c5a' : '#b42318'
  });
  if (!result.confirm||!current()||submitting.value) return;
  submitting.value=true;
  try {
    await changeOrganizationTeacherStatus(session.accessToken, teacher.id, status);
    if(!current())return;
    uni.showToast({ title: `已${statusName(status)}`, icon: 'success' });
    await loadTeachers(session.accessToken, 1, false);
  } catch (error) {
    if(current())showError(error instanceof Error ? error.message : '状态更新失败');
  } finally {if(current())submitting.value=false;}
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

<style scoped>
.teacher-page{min-height:calc(100vh - var(--window-top,0px));box-sizing:border-box;background:#f5f8f4;color:#182d26;padding:20rpx 28rpx 150rpx}.teacher-page{padding-bottom:48rpx}.ld-topbar{display:flex;align-items:center;gap:18rpx;background:linear-gradient(120deg,#21664f,#0b4b3d);margin:-24rpx -24rpx 28rpx;padding:28rpx 26rpx 50rpx;color:white;border-radius:24rpx 24rpx 0 0}.ld-topbar-brand{display:flex;align-items:center;gap:18rpx;flex:1}.ld-topbar-name{display:block;font-size:32rpx;font-weight:650}.ld-topbar-meta{display:block;font-size:23rpx;opacity:.8;margin-top:8rpx}.ld-topbar-logo,.profile-avatar{width:80rpx;height:80rpx;border-radius:50%;background:#ffffff25;display:flex;align-items:center;justify-content:center}.profile-copy{flex:1}.ld-card,.ld-group,.teacher-form-band,.band,.search-band{background:#fff;border:1rpx solid #e5ece5;border-radius:24rpx;padding:24rpx;margin-bottom:20rpx}.workspace-intro{background:#fff;border-radius:24rpx;padding:30rpx 24rpx;margin-bottom:20rpx}.workspace-intro-title{display:block;font-size:34rpx;font-weight:700}.workspace-intro-caption{display:block;font-size:24rpx;color:#778078;margin-top:10rpx}.workspace-shortcuts{display:flex;flex-direction:column;gap:16rpx;margin-bottom:16rpx}.workspace-shortcut{display:flex;align-items:center;flex-wrap:wrap;text-align:left;background:#fff;border-radius:24rpx;padding:22rpx 26rpx;font-size:30rpx;font-weight:600;color:#182d26;min-height:120rpx}.workspace-shortcut>text{width:100%;padding-left:80rpx;font-size:23rpx;font-weight:400;color:#7b837d}.shortcut-symbol,.ld-cell-icon,.class-badge{width:64rpx;height:64rpx;border-radius:14rpx;background:#138655;display:flex;align-items:center;justify-content:center;margin-right:18rpx}.ld-heading{margin:28rpx 0 18rpx}.ld-heading-title,.form-title,.section-title{display:block;font-size:30rpx;font-weight:650}.ld-heading-sub{display:block;font-size:23rpx;color:#7b837d;margin-top:8rpx}.ld-cell,.class-row,.teacher-row,.organization-row{display:flex;align-items:center;padding:20rpx 0;border-bottom:1rpx solid #edf1eb;text-align:left;background:#fff;color:#182d26}.ld-cell:last-child,.class-row:last-child,.teacher-row:last-child{border-bottom:0}.ld-cell-body,.class-main,.organization-main{flex:1}.ld-cell-title,.class-name,.organization-name{display:block;font-size:29rpx;font-weight:600}.ld-cell-sub,.school-name,.organization-type{display:block;font-size:23rpx;color:#7d847e;margin-top:8rpx}.ld-cell-arrow{font-size:34rpx;color:#65726a}.prototype-section-header{display:flex;align-items:center;justify-content:center;font-size:32rpx;font-weight:700;margin-bottom:20rpx;gap:12rpx}.prototype-section-header>.prototype-icon{display:none}.mode-control,.search-band,.action-band,.form-actions,.paging{display:flex;gap:14rpx;align-items:center;flex-wrap:wrap;margin-bottom:24rpx}.mode-control button{flex:1}.active{background:#e1f3e4;color:#0c7748}.field-label{display:block;margin:24rpx 0 12rpx;font-size:26rpx}.text-input,.search-input,.picker-value{background:#f8faf7;border:1rpx solid #e0e7df;border-radius:16rpx;padding:18rpx;min-height:72rpx;box-sizing:border-box;font-size:26rpx}.search-input{flex:1;min-width:180rpx}.check-row{display:flex;align-items:center;gap:12rpx;padding:16rpx 0}.state-text{font-size:26rpx;color:#7a837c;padding:24rpx}.review-card{display:flex;align-items:center;gap:20rpx}.review-info{flex:1}.ld-card-title{display:block;font-size:30rpx;font-weight:650}.review-count{display:block;margin-top:12rpx;font-size:24rpx;color:#718077}button{margin:0;font-size:25rpx;line-height:1.5;background:#eaf2e9;color:#21563e;padding:14rpx 20rpx;border-radius:16rpx}button::after{border:0}.primary-button,.save-button,.submit-button,.create-button{background:#137c53;color:white}.logout-button{margin-top:30rpx;color:#b65c50}.ld-logout,.space-switch{font-size:23rpx;flex-shrink:0}.org-badge{margin-right:18rpx;background:#e4f2e4;border-radius:14rpx;padding:16rpx}.teacher-row{display:block}.teacher-head{display:flex;align-items:center;gap:18rpx}.teacher-main{flex:1}.teacher-avatar{width:76rpx;height:76rpx;border-radius:50%;background:#e8f1e8;display:flex;align-items:center;justify-content:center}.teacher-name{font-size:30rpx;font-weight:650}.teacher-account,.teacher-meta,.class-meta,.class-code{display:block;font-size:23rpx;color:#7d847e;margin-top:8rpx}.row-actions{display:flex;gap:10rpx;flex-wrap:wrap;margin-top:16rpx}.status-text{border-radius:20rpx;padding:4rpx 16rpx;background:#dff5e4;color:#16834f;font-size:23rpx}.error{color:#a43b31}.check-list{margin:18rpx 0}
</style>

<style scoped>.teacher-list-band{background:#fff;border:1rpx solid #e5ece5;border-radius:22rpx;padding:0 22rpx}.teacher-row{padding:24rpx 0}.teacher-head{gap:20rpx}.teacher-name{font-size:29rpx}.teacher-account{font-size:23rpx;margin-top:6rpx}.teacher-meta{margin-left:96rpx;margin-top:8rpx;font-size:22rpx}.row-actions{margin-left:96rpx;gap:10rpx;margin-top:14rpx}.row-actions button{font-size:22rpx;padding:8rpx 14rpx;border-radius:12rpx}.status-text.disabled,.status-text.locked{background:#fff0d4;color:#a87426}.teacher-row:last-child{border-bottom:0}.search-band{padding:16rpx;gap:12rpx}.search-input{background:#f7faf7}</style>

<style scoped>.teacher-config-profile{display:flex;align-items:center;gap:22rpx;background:#f0f5f1;padding:22rpx;border-radius:18rpx;margin:22rpx 0}.class-select-header{display:flex;align-items:center;justify-content:space-between;gap:10rpx;font-size:24rpx;margin:26rpx 0 12rpx}.class-select-header label{display:flex;align-items:center;font-size:22rpx;color:#6b7c70}.class-select-header checkbox{transform:scale(.75)}.check-row{border-bottom:1rpx solid #edf1ed}.check-row:last-child{border-bottom:0}</style>

<style scoped>.teacher-row{position:relative;padding:24rpx 30rpx 24rpx 0;border-bottom:1rpx solid #e4ebe6}.detail-arrow{position:absolute;right:3rpx;top:35rpx;font-size:40rpx;color:#98aaa0}.teacher-overlay{position:fixed;inset:0;background:rgba(20,46,34,.28);z-index:30;display:flex;align-items:flex-end}.teacher-sheet{width:100%;box-sizing:border-box;background:#fff;border-radius:28rpx 28rpx 0 0;padding:30rpx 30rpx calc(32rpx + env(safe-area-inset-bottom))}.sheet-heading{display:flex;align-items:center;justify-content:space-between;font-size:32rpx;font-weight:650;color:#214e3b}.sheet-heading button{margin:0;background:transparent;font-size:42rpx;padding:0 12rpx}.sheet-heading button::after{border:0}</style>
<style scoped>.teacher-head{min-width:0}.teacher-main{min-width:0;flex:1}.teacher-name,.teacher-account{display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.status-text{flex-shrink:0;white-space:nowrap;word-break:keep-all;min-width:62rpx;text-align:center}</style>

<style scoped>.teacher-page{padding:0 0 calc(32rpx + env(safe-area-inset-bottom));background:#fff;min-height:100vh}.teacher-navigation{background:#fff}.teacher-nav-line{height:88rpx;display:flex;align-items:center;justify-content:center;position:relative;font-size:30rpx;font-weight:650;color:#1d372c}.teacher-back{position:absolute;left:16rpx;top:0;margin:0;padding:0;width:56rpx;height:88rpx;line-height:78rpx;font-size:54rpx;background:transparent}.teacher-add{position:absolute;right:20rpx;top:0;margin:0;padding:0;font-size:25rpx;line-height:88rpx;background:transparent;color:#17674d}.teacher-nav-line button::after{border:0}.search-band,.teacher-list-band,.teacher-form-band{margin-left:26rpx;margin-right:26rpx}.search-band{border:0;padding:12rpx 0;margin-bottom:14rpx}.teacher-list-band{border:0;border-radius:0;padding:0}.teacher-avatar{width:70rpx;height:70rpx;flex-shrink:0}.teacher-account{max-width:100%}.teacher-sheet .teacher-meta{margin-left:0;overflow-wrap:anywhere}</style>

<style scoped>.teacher-nav-line{box-sizing:border-box}.teacher-nav-line>text{max-width:100%;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.teacher-add{width:76px;text-align:right}</style>

<style scoped>.teacher-filter-row{display:flex;gap:16rpx;margin:8rpx 26rpx 18rpx}.teacher-filter-row picker{flex:1;max-width:220rpx;border:1rpx solid #e1e7e3;border-radius:12rpx;padding:14rpx;font-size:25rpx;color:#53675b;background:#fff}</style>

<style scoped>.candidate-error{display:block;margin:10rpx 26rpx;color:#a45c48;font-size:24rpx}</style>
<style scoped>.teacher-filter-row picker{min-width:0;height:66rpx;box-sizing:border-box;padding:0 14rpx;line-height:64rpx}.teacher-filter-row picker>view{display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;min-width:0}</style>
<style scoped>.teacher-subject{font-size:25rpx;font-weight:400;color:#6b8678}</style>


<style scoped>.class-tree-group{padding:8rpx 0}.class-campus-heading,.class-grade-heading{display:block;font-size:27rpx;color:#244f3e;padding:12rpx 0}.class-grade-heading{padding-left:28rpx}.class-tree-row{padding-left:52rpx}</style>

<style scoped>.class-grade-controls{display:flex;align-items:center;padding-left:24rpx}.class-grade-controls .class-grade-heading{padding-left:8rpx}.grade-select{margin:0;padding:0;width:44rpx;line-height:48rpx;background:transparent;color:#17674d;font-size:34rpx}.grade-select::after{border:0}</style>

<style scoped>
.configuring .teacher-form-band{border:0;padding:12rpx 0;background:transparent}
.configuring .teacher-config-profile{padding:20rpx;border-radius:18rpx;gap:18rpx}
.teacher-config-profile>view{flex:1;min-width:0;overflow:hidden}
.teacher-config-profile .teacher-name,.teacher-config-profile .teacher-account{display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.configuring .class-tree-scroll{height:46vh;border:1rpx solid #e3ece6;border-radius:18rpx;box-sizing:border-box;margin:18rpx 0}
.configuring .check-list{margin:0;padding:12rpx 18rpx;box-sizing:border-box}
.configuring .class-campus-heading{white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.configuring .class-grade-controls{min-width:0}
.configuring .grade-select{flex-shrink:0}
.configuring .class-grade-heading{flex:1;min-width:0;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.configuring .class-tree-row{min-width:0;box-sizing:border-box;padding-left:44rpx}
.configuring .class-tree-row checkbox{flex-shrink:0}
.configuring .class-tree-row>text{flex:1;min-width:0;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;font-size:26rpx}
.configuring .form-actions{flex-direction:column;gap:12rpx;margin-top:20rpx}
.configuring .form-actions button{width:100%;box-sizing:border-box;min-height:80rpx}
.configuring .form-actions .save-button{order:-1;background:linear-gradient(120deg,#1a7657,#10503f)}
</style>
