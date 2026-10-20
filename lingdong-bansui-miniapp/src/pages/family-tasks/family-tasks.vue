<template>
  <view class="page has-tabbar prototype-workspace">
    <PixelPageHeader v-if="!editing" title="我的任务" />
    
    <button v-if="canCreate" :disabled="busy" @tap="edit()">发布任务</button>
    <text v-if="busy">正在处理任务</text><text v-if="error" class="error">{{ error }}</text>
    <view v-if="editing" class="card task-editor">
      <PixelPageHeader :title="confirming?'确认发布':scopeStep?'选择范围':original?'编辑草稿':'新建任务'" back intercept-back @back="backStep" />
      <text class="publish-step">{{confirming?'3 确认发布':scopeStep?'2 选择范围':'1 填写任务'}}</text><view v-if="!confirming">
      <view v-if="!scopeStep" class="form-section"><text class="form-section-title">任务信息</text>
        <view class="required-label">任务标题<text class="req-mark"> *</text></view>
        <input v-model="form.title" :disabled="busy" maxlength="20" placeholder="每天阅读30分钟" />
        <text class="char-count">{{ (form.title||'').length }}/20</text>
        <view class="required-label">任务描述</view>
        <textarea v-model="form.description" :disabled="busy" maxlength="100" placeholder="描述任务的具体要求，帮助孩子理解目标" />
        <text class="char-count">{{ (form.description||'').length }}/100</text>
        <view class="required-label">任务类型<text class="req-mark"> *</text></view>
        <view class="type-pills"><view v-for="t in taskTypeOptions" :key="t.value" class="type-pill" :class="{selected:form.taskType===t.value}" @tap="form.taskType=t.value">{{ t.label }}</view></view>
        <view class="required-label">任务日期<text class="req-mark"> *</text></view>
        <view class="date-range">
          <picker mode="date" :value="form.scheduledDate" :disabled="busy" @change="dateChanged"><view class="date-box">{{ form.scheduledDate }}</view></picker>
          <text class="date-sep">至</text>
          <picker mode="date" :value="form.endDate||form.scheduledDate" :start="form.scheduledDate" :disabled="busy" @change="form.endDate=value($event)"><view class="date-box">{{ form.endDate || '结束日期' }}</view></picker>
        </view>
        <view class="required-label">重复频率<text class="req-mark"> *</text></view>
        <view class="type-pills"><view v-for="f in frequencyOptions" :key="f.value" class="type-pill" :class="{selected:(form.recurrenceFrequency||'DAILY')===f.value}" @tap="frequencyPill(f.value)">{{ f.label }}</view></view>
        <template v-if="(form.recurrenceFrequency||'DAILY')!=='DAILY'">
          <view v-if="weekdayCapability" class="weekday-options"><button v-for="(label,index) in weekdayLabels" :key="label" :class="{selected:form.recurrenceWeekdays?.includes(index+1)}" :disabled="busy" @tap="toggleWeekday(index+1)">{{label}}</button></view>
          <picker mode="date" :value="form.recurrenceEndDate||form.scheduledDate" :start="form.scheduledDate" :disabled="busy" @change="form.recurrenceEndDate=value($event)"><view class="field">重复截止：{{form.recurrenceEndDate||'不设截止日期'}}</view></picker><button :disabled="busy" @tap="form.recurrenceEndDate=null">不设截止日期</button>
        </template>
        <view class="compact-row">
          <picker :range="['简单','中等','困难']" :value="form.difficultyLevel-1" :disabled="busy" @change="difficulty"><view class="compact-field">难度 {{ ['简单','中等','困难'][form.difficultyLevel-1] }}</view></picker>
          <input v-model="form.durationMinutes" :disabled="busy" type="number" class="compact-input" /><text class="compact-unit">分钟</text>
        </view>
      </view><view v-if="scopeStep" class="form-section"><text class="form-section-title">参与范围</text>
      <view class="required-label">选择参与的成长小主人<text class="req-mark"> *</text></view>
      <view v-for="student in students" :key="student.id" class="child-card" :class="{selected:form.studentIds.includes(student.id),disabled:student.relationshipRole!=='PRIMARY_GUARDIAN'}" @tap="toggleStudent(student)">
        <view class="child-avatar">{{ student.studentName.slice(0,1) }}</view>
        <view class="child-info">
          <text class="child-name">{{ student.studentName }}</text>
          <text class="child-meta">{{ childMeta(student) }}</text>
        </view>
        <view class="child-check" :class="{on:form.studentIds.includes(student.id)}">✓</view>
      </view>
      <text v-if="!students.some(item=>item.relationshipRole==='PRIMARY_GUARDIAN')">暂无可创建任务的主监护孩子</text>
      <view class="required-label">可见范围<text class="req-mark"> *</text></view>
      <text class="hint">仅限本校/本机构的成员，不可跨校选择</text>
      <view v-for="opt in visibilityOptions" :key="opt.value" class="visibility-option" :class="{selected:form.visibility===opt.value}" @tap="form.visibility=opt.value">
        <view class="visibility-radio" :class="{on:form.visibility===opt.value}"></view>
        <view class="visibility-body">
          <text class="visibility-title">{{ visibilityTitle(opt.value) }}</text>
          <text class="visibility-desc">{{ visibilityDesc(opt.value) }}</text>
        </view>
      </view>
      <textarea v-model="form.remark" :disabled="busy" maxlength="200" placeholder="任务备注（可选）" />

      </view></view><view v-else class="confirmation"><view class="confirm-hero"><view class="confirm-icon"><PrototypeIcon :name="typeIcons[form.taskType||'']||'book'" :size="26" /></view><view class="confirm-hero-main"><text class="confirm-title">{{form.title}}</text><text class="confirm-desc">{{form.description||'未填写任务描述'}}</text></view><view class="type-badge">{{ typeLabel }}</view></view><view class="confirm-table"><view class="confirm-row"><text class="confirm-label">任务日期</text><text class="confirm-value">{{ confirmDateRange }}</text></view><view class="confirm-row"><text class="confirm-label">重复频率</text><text class="confirm-value">{{ frequencySummary }}</text></view><view class="confirm-row"><text class="confirm-label">参与成员</text><text class="confirm-value">{{ memberSummary }}</text></view><view class="confirm-row"><text class="confirm-label">可见范围</text><text class="confirm-value">{{ visibilitySummary }}</text></view></view><view class="confirm-note">发布后，成长小主人即可在任务列表中看到，并可以按任务日期进行打卡。</view></view>
      <view class="publish-actions"><button :disabled="busy" @tap="backStep">{{confirming?'上一步':(scopeStep?'返回修改':'取消')}}</button><button v-if="!confirming" :disabled="busy||!canCreate||!students.some(x=>x.relationshipRole==='PRIMARY_GUARDIAN')" @tap="save(false)">保存草稿</button><button v-if="canPublish" class="main-action" :disabled="busy||!canCreate||!students.some(x=>x.relationshipRole==='PRIMARY_GUARDIAN')" @tap="nextStep">{{confirming?'确认发布':'下一步'}}</button></view>
    </view>
    <template v-else-if="loaded && !busy">
      <view class="status-tabs">
        <view v-for="tab in statusTabs" :key="tab.key" class="status-tab" :class="{active:statusTab===tab.key}" @tap="switchTab(tab.key)">{{ tab.label }}</view>
      </view>
      <view class="filter-row">
        <picker :range="typeFilterLabels" :disabled="busy" @change="typeFilterChanged"><view class="filter-chip">类型：{{ typeFilterLabels[typeFilterIndex] }}</view></picker>
        <picker :range="studentFilterLabels" :disabled="busy" @change="studentFilterChanged"><view class="filter-chip">孩子：{{ studentFilterLabels[studentFilterIndex] }}</view></picker>
        <view class="filter-chip muted">最新发布</view>
      </view>
      <text>共 {{ total }} 项家庭任务</text><view v-if="!total" class="task-empty"><view class="empty-mark">✓</view><text class="heading">还没有成长任务</text><text>从一个小目标开始，记录每一步进步</text></view>
      <view v-for="task in tasks" :key="task.id" class="card task-list-card" @tap="openDetail(task)"><view class="task-symbol"><PrototypeIcon :name="taskIcon(task)" :size="25" /></view>
        <view class="task-card-body">
          <view class="task-card-top"><text class="heading">{{ task.title }}</text>
            <text v-if="task.status==='DRAFT'" class="status-badge draft">草稿</text>
            <text v-else class="status-badge" :class="badgeClass(task)">{{ badgeLabel(task) }}</text></view>
          <text class="task-meta">{{ task.scheduledDate }} · {{ frequencyLabel(task) }}</text>
          <text v-if="task.memberNames" class="task-meta">{{ task.memberNames }}</text>
          <view v-if="task.status!=='DRAFT' && task.assignmentTotal>0" class="task-progress-row"><text class="task-progress">{{ task.assignmentCompleted }}/{{ task.assignmentTotal }} 次</text></view>
          <button v-if="task.status==='DRAFT'&&canCreate" @tap="edit(task.id)">编辑草稿</button>
          <button v-if="task.status==='DRAFT'&&canPublish" @tap="publish(task)">发布任务</button>
        </view>
      </view>
      <view v-if="total" class="paging"><button :disabled="page<=1" @tap="load(page-1)">上一页</button><text>第 {{ page }} 页</text><button :disabled="page*20>=total" @tap="load(page+1)">下一页</button></view>
    </template>
  <AppTabBar v-if="!editing" :items="PARENT_TABS" :active="1" />
  </view>
</template>
<script setup lang="ts">
import PixelPageHeader from '@/components/pixel/PixelPageHeader.vue';
import PrototypeIcon from '@/components/PrototypeIcon.vue';
const confirming=ref(false);
const scopeStep=ref(false);
import AppTabBar from '@/components/AppTabBar.vue';
import { PARENT_TABS } from '@/config/tabbar';
import { ref,reactive,computed } from 'vue';
import { onShow,onHide,onUnload } from '@dcloudio/uni-app';
import { familyTaskApi,type FamilyTask,type FamilyTaskOverview,type FamilyStatusTab,type FamilyStudent } from '@/api/family-task';
import { canManageFamily,draftInput,recurrenceError,step1Error,type FamilyForm,type FamilyIdentity } from '@/models/family-task-draft';
import { getParentSession } from '@/session/parent-session';
import { getMiniappCapabilities } from '@/api/capability';
import { ApiError } from '@/api/http';
const tasks=ref<FamilyTaskOverview[]>([]),students=ref<FamilyStudent[]>([]),original=ref<FamilyTask|null>(null),user=ref<FamilyIdentity|null>(null);
const busy=ref(false),editing=ref(false),loaded=ref(false),error=ref(''),page=ref(1),total=ref(0);
const statusTab=ref<FamilyStatusTab>('ALL'),typeFilterIndex=ref(0),studentFilterIndex=ref(0);
const statusTabs=[{key:'ALL' as FamilyStatusTab,label:'全部'},{key:'IN_PROGRESS' as FamilyStatusTab,label:'进行中'},{key:'PENDING_REVIEW' as FamilyStatusTab,label:'待审核'},{key:'COMPLETED' as FamilyStatusTab,label:'已完成'}];
const typeFilterLabels=['全部类型','学习','生活','运动','兴趣','家务','其他'];
const typeFilterKeys=['','STUDY','LIFE','SPORT','HOBBY','CHORE','OTHER'];
const studentFilterLabels=computed(()=>['全部孩子',...students.value.map(s=>s.studentName)]);
const typeIcons:{[key:string]:string}={STUDY:'book',LIFE:'home',SPORT:'growth',HOBBY:'star',CHORE:'leaf',OTHER:'task'};
function taskIcon(task:FamilyTaskOverview){return typeIcons[task.taskType||'']||'book';}
function openDetail(task:FamilyTaskOverview){if(task.status==='DRAFT')return;uni.navigateTo({url:'/pages/family-task-detail/family-task-detail?id='+task.id});}
function frequencyLabel(task:FamilyTaskOverview){if(!task.recurrenceEnabled)return '单次';return ({DAILY:'每天',WEEKLY:'每周',CUSTOM:'自定义'})[task.recurrenceFrequency||'DAILY']||'重复';}
function badgeLabel(task:FamilyTaskOverview){if(task.assignmentPendingReview>0)return '待审核';if(task.assignmentTotal>0&&task.assignmentCompleted>=task.assignmentTotal)return '已完成';return '进行中';}
function badgeClass(task:FamilyTaskOverview){return badgeLabel(task)==='已完成'?'done':badgeLabel(task)==='待审核'?'pending':'doing';}
function switchTab(key:FamilyStatusTab){if(statusTab.value===key)return;statusTab.value=key;void load(1);}
function typeFilterChanged(event:Event){typeFilterIndex.value=Number(value(event));void load(1);}
function studentFilterChanged(event:Event){studentFilterIndex.value=Number(value(event));void load(1);}
const canCreate=computed(()=>!!user.value&&canManageFamily(user.value,'LEARNING_TASK_CREATE'));
const canPublish=computed(()=>!!user.value&&canManageFamily(user.value,'LEARNING_TASK_PUBLISH'));
const today=()=>new Date(Date.now()+8*3600000).toISOString().slice(0,10);
const form=reactive<FamilyForm>({title:'',difficultyLevel:2,durationMinutes:30,scheduledDate:today(),remark:'',studentIds:[],description:'',taskType:null,endDate:null,visibility:null});
let revision=0;
function valid(n:number,token:string){return n===revision&&getParentSession()?.accessToken===token;}
function clear(){tasks.value=[];students.value=[];original.value=null;user.value=null;editing.value=false;loaded.value=false;total.value=0;}
async function access(n:number,token:string,permission:string){const [identity,capability]=await Promise.all([familyTaskApi.me(token),getMiniappCapabilities()]);if(!valid(n,token))return false;if(!capability.learningTaskManagementEnabled||!canManageFamily(identity,permission)){clear();throw new Error('家庭任务未开启或无操作权限');}weekdayCapability.value=capability.taskWeekdayRecurrenceEnabled===true;user.value=identity;return true;}
function failed(cause:unknown){if(cause instanceof ApiError&&([401,403].includes(cause.statusCode)||cause.code==='FEATURE_DISABLED'))clear();error.value=cause instanceof Error?cause.message:'操作未完成，请刷新核对后重试';}
async function load(target=1){const n=++revision,token=getParentSession()?.accessToken;clear();error.value='';busy.value=true;try{if(!token)throw new Error('请先登录家长账号');if(!await access(n,token,'LEARNING_TASK_READ_MANAGED'))return;const taskType=typeFilterKeys[typeFilterIndex.value]||null;const selectedStudent=studentFilterIndex.value>0?students.value[studentFilterIndex.value-1]:null;const [result,options]=await Promise.all([familyTaskApi.listOverview(token,target,statusTab.value,taskType,selectedStudent?selectedStudent.id:null),familyTaskApi.students(token)]);if(valid(n,token)){students.value=options;tasks.value=result.items;page.value=result.page;total.value=result.total;loaded.value=true;}}catch(cause){if(n===revision)failed(cause);}finally{if(n===revision)busy.value=false;}}
function backStep(){if(confirming.value){confirming.value=false;scopeStep.value=true;}else if(scopeStep.value){scopeStep.value=false;}else {const n=revision,token=getParentSession()?.accessToken;if(!token)return;uni.showModal({title:'退出任务编辑',content:'未保存的修改将丢失，确认退出吗？',confirmText:'退出',success:result=>{if(result.confirm&&valid(n,token))editing.value=false;}});}}
function nextStep(){if(recurrenceError(form,weekdayCapability.value)){error.value=recurrenceError(form,weekdayCapability.value);return;}if(confirming.value){void save(true);return;}if(!scopeStep.value){const step1=step1Error(form);if(step1){error.value=step1;uni.showToast({title:step1,icon:'none'});return;}if(!Number.isFinite(Number(form.durationMinutes))||Number(form.durationMinutes)<=0){error.value='请填写有效执行时长';return;}error.value='';scopeStep.value=true;return;}confirmTask();}
async function edit(id?:string){scopeStep.value=false;confirming.value=false;const n=++revision,token=getParentSession()?.accessToken;if(!token){clear();return;}busy.value=true;error.value='';editing.value=false;original.value=null;try{if(!await access(n,token,'LEARNING_TASK_CREATE'))return;const [options,task]=await Promise.all([familyTaskApi.students(token),id?familyTaskApi.detail(token,id):Promise.resolve(null)]);if(!valid(n,token))return;if(task&&task.status!=='DRAFT')throw new Error('已发布任务不能编辑');students.value=options;original.value=task;Object.assign(form,{title:task?.title||'',difficultyLevel:task?.difficultyLevel||2,durationMinutes:task?.durationMinutes||30,scheduledDate:task?.scheduledDate||today(),remark:task?.remark||'',studentIds:task?task.targets.map(target=>target.targetId):[],recurrenceEnabled:task?.recurrenceEnabled??false,recurrenceFrequency:task?.recurrenceFrequency||'DAILY',recurrenceWeekdays:[...(task?.recurrenceWeekdays??[])],recurrenceEndDate:task?.recurrenceEndDate??null,description:task?.description||'',taskType:task?.taskType||null,endDate:task?.endDate??null,visibility:task?.visibility??null});editing.value=true;}catch(cause){if(valid(n,token))failed(cause);}finally{if(n===revision)busy.value=false;}}
function confirmTask(){if(recurrenceError(form,weekdayCapability.value)){error.value=recurrenceError(form,weekdayCapability.value);return;}if(!form.title.trim()||!form.studentIds.length||!Number.isInteger(Number(form.durationMinutes))||form.durationMinutes<1||form.durationMinutes>1440){error.value='请填写标题、有效时长并选择成长小主人';uni.showToast({title:'请完整填写任务并选择下发对象',icon:'none'});return;}if(!form.visibility){error.value='请选择可见范围';uni.showToast({title:'请选择可见范围',icon:'none'});return;}confirming.value=true;}
async function save(publishNow=false){if(recurrenceError(form,weekdayCapability.value)){error.value=recurrenceError(form,weekdayCapability.value);return;}if(busy.value)return;if(publishNow&&!canPublish.value){error.value='无发布权限';return;}if(!form.title.trim()||!form.studentIds.length||form.durationMinutes<1||form.durationMinutes>1440){error.value='请填写标题、有效时长并选择学生';return;}if(form.studentIds.some(id=>!students.value.some(student=>student.id===id&&student.relationshipRole==='PRIMARY_GUARDIAN'))){error.value='学生关系已变化，请重新选择当前主监护孩子';return;}const n=++revision,token=getParentSession()?.accessToken;if(!token){clear();return;}busy.value=true;error.value='';try{if(!await access(n,token,'LEARNING_TASK_CREATE'))return;const saved=await familyTaskApi.save(token,original.value?.id||null,draftInput(form,original.value||undefined));if(!valid(n,token))return;original.value=saved;if(publishNow){if(!await access(n,token,'LEARNING_TASK_PUBLISH'))return;await familyTaskApi.publish(token,saved.id);}if(valid(n,token)){confirming.value=false;await load(1);}}catch(cause){if(valid(n,token))failed(cause);}finally{if(n===revision)busy.value=false;}}
async function publish(task:FamilyTask|FamilyTaskOverview){if(busy.value)return;const n=++revision,token=getParentSession()?.accessToken;if(!token){clear();return;}busy.value=true;error.value='';try{const confirmed=await new Promise<boolean>(resolve=>uni.showModal({title:'确认发布家庭任务',content:`发布“${task.title}”后学生可认领执行。`,success:result=>resolve(result.confirm),fail:()=>resolve(false)}));if(!confirmed||!valid(n,token))return;if(!await access(n,token,'LEARNING_TASK_PUBLISH'))return;await familyTaskApi.publish(token,task.id);if(valid(n,token))await load(page.value);}catch(cause){if(valid(n,token))failed(cause);}finally{if(n===revision)busy.value=false;}}
function value(event:Event){return (event as unknown as {detail:{value:string}}).detail.value;}
const weekdayCapability=ref(false);
const weekdayLabels=['周一','周二','周三','周四','周五','周六','周日'];
const frequencyLabels={DAILY:'每天',WEEKLY:'每周',CUSTOM:'自定义星期'};
function recurrenceChanged(event:Event){form.recurrenceEnabled=(event as unknown as {detail:{value:boolean}}).detail.value;}
const taskTypeOptions=[{value:'STUDY',label:'学习'},{value:'LIFE',label:'生活'},{value:'SPORT',label:'运动'},{value:'HOBBY',label:'兴趣'},{value:'CHORE',label:'家务'},{value:'OTHER',label:'其他'}];
const frequencyOptions=[{value:'DAILY' as const,label:'每天'},{value:'WEEKLY' as const,label:'每周'},{value:'CUSTOM' as const,label:'自定义'}];
function frequencyPill(frequency:'DAILY'|'WEEKLY'|'CUSTOM'){form.recurrenceEnabled=true;form.recurrenceFrequency=frequency;if(frequency==='DAILY'){form.recurrenceWeekdays=[];return;}const weekday=new Date(form.scheduledDate+'T00:00:00Z').getUTCDay()||7;form.recurrenceWeekdays=[weekday];}
function frequencyChanged(event:Event){form.recurrenceFrequency=(['DAILY','WEEKLY','CUSTOM'] as const)[Number(value(event))];form.recurrenceWeekdays=form.recurrenceFrequency==='DAILY'?[]:[new Date(form.scheduledDate+'T00:00:00Z').getUTCDay()||7];}
function toggleWeekday(day:number){if(form.recurrenceFrequency==='WEEKLY'){form.recurrenceWeekdays=[day];return;}const days=form.recurrenceWeekdays??[];form.recurrenceWeekdays=days.includes(day)?days.filter(x=>x!==day):[...days,day].sort((a,b)=>a-b);}
function difficulty(event:Event){form.difficultyLevel=Number(value(event))+1;}
function dateChanged(event:Event){form.scheduledDate=value(event);}
function studentsChanged(event:Event){form.studentIds=(event as unknown as {detail:{value:string[]}}).detail.value;}
function toggleStudent(student:FamilyStudent){if(student.relationshipRole!=='PRIMARY_GUARDIAN'){uni.showToast({title:'副家长孩子不能创建任务',icon:'none'});return;}form.studentIds=form.studentIds.includes(student.id)?form.studentIds.filter(x=>x!==student.id):[...form.studentIds,student.id];}
function childMeta(student:FamilyStudent){const parts=[student.currentClassName,student.schoolName].filter(x=>!!x);return parts.length?parts.join(' '):'未分班';}
const visibilityOptions=[{value:'FAMILY_ONLY' as const},{value:'FAMILY_CLASS_PARTNER' as const},{value:'FAMILY_SCHOOL_PARTNER' as const},{value:'CUSTOM' as const}];
function firstSelectedStudent(){const id=form.studentIds[0];return students.value.find(student=>student.id===id)||null;}
function selectedSchool(){return firstSelectedStudent()?.schoolName||null;}
function visibilityTitle(value:string){return ({FAMILY_ONLY:'仅家庭',FAMILY_CLASS_PARTNER:'家庭 + 班级成长伙伴',FAMILY_SCHOOL_PARTNER:'家庭 + 本校成长伙伴',CUSTOM:'自定义范围'} as Record<string,string>)[value]||value;}
function visibilityDesc(value:string){if(value==='FAMILY_ONLY')return '仅自己可见';if(value==='FAMILY_CLASS_PARTNER'){const student=firstSelectedStudent();const school=selectedSchool();const cls=student?.currentClassName;return cls?`班级：${cls}${school?'（'+school+'）':''}`:'班级：选择孩子后展示';}
if(value==='FAMILY_SCHOOL_PARTNER'){const school=selectedSchool();return school?school:'学校：选择孩子后展示';}
return '可选择本校内的班级或成员';}
function hide(){revision++;clear();busy.value=false;}
const typeLabel=computed(()=>({STUDY:'学习',LIFE:'生活',SPORT:'运动',HOBBY:'兴趣',CHORE:'家务',OTHER:'其他'} as Record<string,string>)[form.taskType||'']||'学习');
const dotDate=(iso:string)=>iso.replace(/-/g,'.');
const confirmDateRange=computed(()=>{const end=form.endDate||form.scheduledDate;return form.scheduledDate===end?dotDate(form.scheduledDate):`${dotDate(form.scheduledDate)} – ${dotDate(end)}`;});
const frequencySummary=computed(()=>{if(!form.recurrenceEnabled)return '单次';const base=({DAILY:'每天',WEEKLY:'每周',CUSTOM:'自定义'} as Record<string,string>)[form.recurrenceFrequency||'DAILY']||'重复';if(form.recurrenceFrequency&&form.recurrenceFrequency!=='DAILY'&&form.recurrenceWeekdays?.length)return base+' · '+form.recurrenceWeekdays.map(d=>weekdayLabels[d-1]).join('、');return base;});
const memberSummary=computed(()=>form.studentIds.map(id=>{const s=students.value.find(x=>x.id===id);return s?(s.currentClassName?`${s.studentName}（${s.currentClassName}）`:s.studentName):id;}).join('、')||'未选择');
const visibilitySummary=computed(()=>form.visibility?`${visibilityTitle(form.visibility)}（${visibilityDesc(form.visibility)}）`:'未选择');
onShow(()=>load(1));onHide(hide);onUnload(hide);
</script>
<style lang="scss" scoped>
@import "@/styles/workspace-prototype.scss";

.page{padding:32rpx;min-height:calc(100vh - var(--window-top,0px));box-sizing:border-box;background:#f4f7f5;color:#1c2b28;font-family:system-ui,"Microsoft YaHei",sans-serif}.page text{display:block;margin:16rpx 0;overflow-wrap:anywhere}.title{font-size:38rpx;font-weight:700}.card{padding:24rpx;margin-top:24rpx;background:white;border-radius:12rpx}.heading{font-weight:600}.error{color:#a33c2e}.student{display:block;margin:20rpx 0}input,textarea,.field{width:100%;padding:18rpx;border:1px solid #dce4e1;margin:14rpx 0}input{height:80rpx}button{font-size:28rpx;margin:16rpx 0}.paging{display:flex;align-items:center;gap:12rpx;justify-content:space-between}



.page .card{background:#fffefa;border:1rpx solid #e0e8e1;border-radius:24rpx;padding:28rpx;}
.page .card button{border:1rpx solid #d4e3d9;border-radius:16rpx;color:#184F40;background:#f0f5f1;}
.page>.title{color:#193e32;font-size:36rpx;}
.page>button{background:#184F40;color:#fff;border-radius:18rpx;}
.page>.task-editor{position:fixed;inset:0;overflow-y:auto;z-index:110;background:#F6F8F5;padding:32rpx 28rpx calc(180rpx + env(safe-area-inset-bottom));margin:0;border-radius:0;}
.publish-step{font-size:25rpx;}
.publish-actions{border-top:1rpx solid #e0e8e1;}
.publish-actions button{margin:0!important;padding:0 10rpx;font-size:24rpx;}
.publish-actions .main-action{background:#184F40!important;color:white!important;}
.confirmation{background:transparent;border:0;padding:0;}.confirm-hero{display:flex;align-items:flex-start;gap:18rpx;background:#fff;border:1rpx solid #e2eee7;border-radius:24rpx;padding:26rpx;margin-bottom:22rpx;position:relative;}.confirm-icon{width:76rpx;height:76rpx;background:#e8f8ed;color:#086544;border-radius:20rpx;display:flex;align-items:center;justify-content:center;flex:none;}.confirm-hero-main{flex:1;min-width:0;}.confirm-title{display:block;font-size:30rpx;font-weight:700;color:#162b24;}.confirm-desc{display:block;margin-top:8rpx;font-size:24rpx;color:#78847e;line-height:1.6;overflow-wrap:anywhere;}.type-badge{flex:none;background:#e8f8ed;color:#086544;font-size:22rpx;padding:6rpx 18rpx;border-radius:999rpx;font-weight:600;}.confirm-table{background:#fff;border:1rpx solid #e2eee7;border-radius:24rpx;padding:6rpx 26rpx;margin-bottom:22rpx;}.confirm-row{display:flex;align-items:flex-start;gap:20rpx;padding:22rpx 0;border-bottom:1rpx solid #edf1ee;}.confirm-row:last-child{border-bottom:0;}.confirm-label{flex:none;width:150rpx;font-size:25rpx;color:#78847e;}.confirm-value{flex:1;font-size:25rpx;color:#1c2b28;line-height:1.6;overflow-wrap:anywhere;}.confirm-note{background:#f1f5f2;border-radius:20rpx;padding:22rpx 24rpx;font-size:24rpx;color:#78847e;line-height:1.7;}

.page>.task-editor{top:var(--window-top,0px);}.task-editor .heading{display:block;font-size:34rpx;margin:0 0 18rpx;}.task-editor .publish-step{margin-bottom:24rpx;}
.task-empty{background:#fffefa;border:1rpx solid #e0e8e1;border-radius:24rpx;padding:64rpx 24rpx;text-align:center;margin-top:24rpx;}.empty-mark{width:84rpx;height:84rpx;line-height:84rpx;border-radius:26rpx;background:#eaf2eb;color:#184F40;margin:0 auto;font-size:36rpx;}.task-empty>text{font-size:24rpx;color:#819487;}.task-empty>.heading{font-size:28rpx;color:#294b3a;}

.prototype-workspace{padding:0 28rpx 48rpx!important;min-height:calc(100vh - var(--window-top,0px));box-sizing:border-box;}
.prototype-workspace.has-tabbar{padding-bottom:160rpx!important;}
.prototype-header{margin:0 -28rpx 28rpx;padding:32rpx 32rpx 48rpx;display:flex;align-items:center;gap:18rpx;background:linear-gradient(115deg,#226d5c,#07573f);color:#fff;border-radius:0 0 30rpx 30rpx;}
.prototype-header-title{font-weight:700;letter-spacing:1rpx;}
.card,.section-band,.summary-band,.action-band,.heading-band{background:#fff!important;border:1rpx solid #e2eee7!important;border-radius:24rpx!important;box-shadow:none!important;margin-bottom:22rpx;padding:24rpx!important;}
input,textarea,.field{background:#fbfcfb!important;border:1rpx solid #dce5df!important;border-radius:14rpx!important;padding:18rpx!important;box-sizing:border-box;font-size:26rpx!important;}
input{min-height:76rpx;}textarea{width:100%;}
button{border-radius:18rpx!important;font-size:26rpx!important;}button::after{border:0;}
.heading,.section-title,.row-title,.task-title{color:#162b24;font-weight:650;font-size:28rpx;}
.row-meta,.task-meta,.hint{color:#78847e;font-size:24rpx;line-height:1.65;}
.list-row,.relationship-row,.task-row{border-bottom:1rpx solid #edf1ee;padding:24rpx 0;}
.current-tag,.read-tag,.source-badge{background:#e7f7e9;color:#168450;border-radius:10rpx;padding:6rpx 12rpx;font-size:22rpx;}
 .task-list-card{position:relative;padding-left:116rpx!important;}.task-symbol{position:absolute;left:24rpx;top:24rpx;width:70rpx;height:70rpx;background:#e8f8ed;color:#086544;border-radius:24rpx;display:flex;align-items:center;justify-content:center;}.publish-actions{position:sticky;background:#fff;z-index:2;}.task-editor>text{margin:18rpx 0 10rpx;}.publish-step{background:#edf8f1;padding:12rpx;border-radius:12rpx;color:#075d43;}
.form-section{padding:22rpx 0;border-top:1rpx solid #edf1ee;}.form-section-title{display:block;font-size:28rpx;font-weight:700;color:#075d43;margin-bottom:22rpx;}.form-section>text{display:block;margin:18rpx 0 10rpx;font-size:25rpx;}.student{padding:20rpx 14rpx;background:#f8fbf8;border-radius:16rpx;margin-bottom:12rpx;}.publish-actions{display:flex;flex-wrap:wrap;gap:12rpx;}.publish-actions button{flex:1;margin:0;font-size:23rpx!important;}
.prototype-workspace{background:#f7faf6!important;color:#172b25}.task-symbol{border-radius:50%!important;background:#e7f6ed!important}.task-card,.task-list-card{border:0!important;border-radius:22rpx!important;box-shadow:none!important}.task-title{font-size:30rpx!important}.primary-action{border-radius:44rpx!important}.prototype-header{background:#fff!important;color:#172b25!important;justify-content:center;border-radius:0!important;padding:28rpx!important}.prototype-header-title{font-size:34rpx}.publish-actions{position:fixed!important;bottom:0;left:0;right:0;padding:20rpx 24rpx calc(20rpx + env(safe-area-inset-bottom));box-sizing:border-box}.page>.task-editor{padding-bottom:180rpx!important}.task-list-card>.heading{display:block;margin-bottom:12rpx}.task-list-card>text:not(.heading){font-size:24rpx;color:#7c8981}.task-list-card button{display:inline-flex;margin:18rpx 12rpx 0 0;line-height:58rpx;padding:0 22rpx}.page-shell{padding:24rpx 28rpx 170rpx!important;box-sizing:border-box}.metadata-band .detail-row{min-height:0}.detail-symbol{background:#fff0d7!important;color:#eea12a!important}.action-band{border:0!important}.checkin-input{height:180rpx}.ld-heading{text-align:center;background:white;margin:0 -32rpx;padding:28rpx}.source-filter-inner{display:flex;width:100%;gap:0;background:#eff3ee;border-radius:24rpx}.source-button{flex:1;min-width:0;padding:0;box-shadow:none;background:transparent}.source-button.active{background:transparent!important;color:#075e43!important;border-bottom:4rpx solid #075e43}.task-row-top{justify-content:space-between}.task-meta{font-size:23rpx}.task-list-card{min-height:148rpx}
.weekday-options{display:flex;flex-wrap:wrap;gap:10rpx}.weekday-options button{padding:0 16rpx;font-size:23rpx!important}.weekday-options button.selected{background:#184F40!important;color:#fff!important}.recurrence-controls{margin-top:24rpx}

/* 02-02 任务列表：四状态 Tab + 筛选组 + 任务卡（图标/进度/成员/频率/徽标） */
.status-tabs{display:flex;background:#eef3ee;border-radius:24rpx;padding:8rpx;margin:20rpx 0 16rpx}
.status-tab{flex:1;text-align:center;padding:14rpx 0;font-size:26rpx;color:#5b6b60;border-radius:20rpx}
.status-tab.active{background:#fff;color:#075e43;font-weight:700;box-shadow:0 2rpx 8rpx rgba(7,94,67,0.08)}
.filter-row{display:flex;gap:12rpx;margin:0 0 6rpx}
.filter-chip{font-size:23rpx;color:#405148;background:#fff;border:1rpx solid #dfe8e2;border-radius:26rpx;padding:8rpx 20rpx}
.filter-chip.muted{color:#93a099;border-style:dashed}
.task-card-body{min-width:0}
.task-card-top{display:flex;align-items:flex-start;justify-content:space-between;gap:12rpx}
.status-badge{flex-shrink:0;font-size:22rpx;padding:6rpx 16rpx;border-radius:999rpx}
.status-badge.doing{background:#fff3e0;color:#c07423}
.status-badge.pending{background:#eef1ef;color:#6f7d74}
.status-badge.done{background:#e2f5e8;color:#1d8a52}
.status-badge.draft{background:#eef1ef;color:#6f7d74}
.task-progress-row{display:flex;justify-content:flex-end}
.task-progress{font-size:24rpx;color:#5f6f64;font-weight:600}

/* 02-03 发布向导步骤 1：标题/描述/类型六选/日期区间/频率三选 */
.required-label{display:flex;align-items:center;gap:4rpx;margin:20rpx 0 8rpx;font-size:27rpx;font-weight:650;color:#172b24}
.req-mark{color:#d0503a;font-weight:700}
.char-count{display:block;text-align:right;margin:4rpx 0 0;font-size:22rpx;color:#96a29b}
.type-pills{display:flex;flex-wrap:wrap;gap:16rpx;margin:10rpx 0 6rpx}
.type-pill{padding:14rpx 44rpx;border:1rpx solid #d8e2da;border-radius:999rpx;font-size:26rpx;color:#3d4f43;background:#fff}
.type-pill.selected{background:#1b5c46;color:#fff;border-color:#1b5c46;font-weight:650}
.date-range{display:flex;align-items:center;gap:16rpx;margin:10rpx 0 6rpx}
.date-box{flex:1;min-height:80rpx;line-height:80rpx;text-align:center;border:1rpx solid #d8e2da;border-radius:16rpx;background:#fbfcfb;font-size:26rpx;color:#26352c}
.date-sep{font-size:26rpx;color:#7b877f}
.compact-row{display:flex;align-items:center;gap:16rpx;margin-top:24rpx;padding-top:20rpx;border-top:1rpx dashed #e4ebe5}
.compact-field{padding:12rpx 24rpx;border:1rpx solid #d8e2da;border-radius:16rpx;font-size:24rpx;color:#3d4f43;background:#fbfcfb}
.compact-input{width:140rpx!important;min-height:64rpx!important;padding:8rpx 16rpx!important;font-size:24rpx!important;margin:0!important}
.compact-unit{font-size:24rpx;color:#7b877f}

/* 02-04 任务范围选择：孩子卡（头像/姓名/班级学校）+ 可见范围四选 */
.child-card{display:flex;align-items:center;gap:20rpx;padding:22rpx;margin:12rpx 0;background:#fbfdfb;border:1rpx solid #e2ebe4;border-radius:20rpx}
.child-card.selected{border-color:#1b5c46;background:#f2faf4}
.child-card.disabled{opacity:0.55}
.child-avatar{flex-shrink:0;width:84rpx;height:84rpx;line-height:84rpx;border-radius:50%;background:#e7f6ed;color:#086544;font-size:34rpx;font-weight:700;text-align:center}
.child-info{flex:1;min-width:0}
.child-name{display:block;font-size:29rpx;font-weight:650;color:#1d2e27}
.child-meta{display:block;margin-top:6rpx;font-size:23rpx;color:#78847e}
.child-check{flex-shrink:0;width:44rpx;height:44rpx;line-height:44rpx;border-radius:12rpx;border:1rpx solid #c9d6cc;text-align:center;font-size:26rpx;color:transparent}
.child-check.on{background:#1b5c46;border-color:#1b5c46;color:#fff}
.visibility-option{display:flex;align-items:flex-start;gap:18rpx;padding:22rpx;margin:12rpx 0;background:#fbfdfb;border:1rpx solid #e2ebe4;border-radius:20rpx}
.visibility-option.selected{border-color:#1b5c46;background:#f2faf4}
.visibility-radio{flex-shrink:0;width:34rpx;height:34rpx;margin-top:4rpx;border-radius:50%;border:2rpx solid #c9d6cc;box-sizing:border-box}
.visibility-radio.on{border:10rpx solid #1b5c46}
.visibility-body{flex:1;min-width:0}
.visibility-title{display:block;font-size:27rpx;font-weight:650;color:#1d2e27}
.visibility-desc{display:block;margin-top:6rpx;font-size:23rpx;color:#78847e}
</style>
