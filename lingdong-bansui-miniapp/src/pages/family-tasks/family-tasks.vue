<template>
  <view class="page has-tabbar prototype-workspace">
    <PixelPageHeader v-if="!editing" title="我的任务" />
    
    <button v-if="canCreate" :disabled="busy" @tap="edit()">发布任务</button>
    <text v-if="busy">正在处理任务</text><text v-if="error" class="error">{{ error }}</text>
    <view v-if="editing" class="card task-editor">
      <PixelPageHeader :title="confirming?'确认发布':scopeStep?'选择范围':original?'编辑草稿':'新建任务'" back intercept-back @back="backStep" />
      <text class="publish-step">{{confirming?'3 确认发布':scopeStep?'2 选择范围':'1 填写任务'}}</text><view v-if="!confirming">
      <view v-if="!scopeStep" class="form-section"><text class="form-section-title">任务信息</text><text>任务标题</text><input v-model="form.title" :disabled="busy" maxlength="50" placeholder="任务标题" />
      <picker :range="['简单','中等','困难']" :value="form.difficultyLevel-1" :disabled="busy" @change="difficulty"><view class="field">难度：{{ ['简单','中等','困难'][form.difficultyLevel-1] }}</view></picker>
      <text>时长（分钟）</text><input v-model.number="form.durationMinutes" :disabled="busy" type="number" />
      <picker mode="date" :value="form.scheduledDate" :disabled="busy" @change="dateChanged"><view class="field">任务日期：{{ form.scheduledDate }}</view></picker>
      <view class="recurrence-controls"><label>重复任务 <switch :checked="!!form.recurrenceEnabled" :disabled="busy" @change="recurrenceChanged" /></label>
      <template v-if="form.recurrenceEnabled"><picker :range="weekdayCapability?['每天','每周','自定义星期']:['每天']" :value="['DAILY','WEEKLY','CUSTOM'].indexOf(form.recurrenceFrequency||'DAILY')" :disabled="busy" @change="frequencyChanged"><view class="field">重复频率：{{frequencyLabels[form.recurrenceFrequency||'DAILY']}}</view></picker>
      <view v-if="form.recurrenceFrequency!=='DAILY'" class="weekday-options"><button v-for="(label,index) in weekdayLabels" :key="label" :class="{selected:form.recurrenceWeekdays?.includes(index+1)}" :disabled="busy" @tap="toggleWeekday(index+1)">{{label}}</button></view>
      <picker mode="date" :value="form.recurrenceEndDate||form.scheduledDate" :start="form.scheduledDate" :disabled="busy" @change="form.recurrenceEndDate=value($event)"><view class="field">重复截止：{{form.recurrenceEndDate||'不设截止日期'}}</view></picker><button :disabled="busy" @tap="form.recurrenceEndDate=null">不设截止日期</button></template></view>
      </view><view v-if="scopeStep" class="form-section"><text class="form-section-title">参与范围</text><text>选择成长小主人</text>
      <checkbox-group @change="studentsChanged"><label v-for="student in students" :key="student.id" class="student"><checkbox :value="student.id" :checked="form.studentIds.includes(student.id)" :disabled="busy||student.relationshipRole!=='PRIMARY_GUARDIAN'" />{{ student.studentName }}{{ student.relationshipRole === 'SECONDARY_GUARDIAN' ? '（副家长不能创建）' : '' }}</label></checkbox-group>
      <text v-if="!students.some(item=>item.relationshipRole==='PRIMARY_GUARDIAN')">暂无可创建任务的主监护孩子</text>
      <textarea v-model="form.remark" :disabled="busy" maxlength="200" placeholder="任务备注（可选）" />

      </view></view><view v-else class="confirmation"><view class="confirmation-banner"><PrototypeIcon name="check" :size="24" /><text>请核对任务信息后发布</text></view><text class="heading">{{form.title}}</text><text>任务日期：{{form.scheduledDate}}</text><text>难度：{{['简单','中等','困难'][form.difficultyLevel-1]}} · {{form.durationMinutes}}分钟</text><text>下发对象：{{students.filter(x=>form.studentIds.includes(x.id)).map(x=>x.studentName).join('、')}}</text><text>任务要求：{{form.remark||'未填写备注'}}</text></view>
      <view class="publish-actions"><button :disabled="busy" @tap="backStep">{{confirming||scopeStep?'返回修改':'取消'}}</button><button :disabled="busy||!canCreate||!students.some(x=>x.relationshipRole==='PRIMARY_GUARDIAN')" @tap="save(false)">保存草稿</button><button v-if="canPublish" class="main-action" :disabled="busy||!canCreate||!students.some(x=>x.relationshipRole==='PRIMARY_GUARDIAN')" @tap="nextStep">{{confirming?'发布任务':'下一步'}}</button></view>
    </view>
    <template v-else-if="loaded && !busy">
      <text>共 {{ total }} 项家庭任务</text><view v-if="!total" class="task-empty"><view class="empty-mark">✓</view><text class="heading">还没有成长任务</text><text>从一个小目标开始，记录每一步进步</text></view>
      <view v-for="task in tasks" :key="task.id" class="card task-list-card"><view class="task-symbol"><PrototypeIcon name="book" :size="25" /></view><text class="heading">{{ task.title }}</text><text>{{ task.scheduledDate }} · {{ task.status==='DRAFT'?'草稿':'已发布' }}</text>
        <button v-if="task.status==='DRAFT'&&canCreate" @tap="edit(task.id)">编辑草稿</button>
        <button v-if="task.status==='DRAFT'&&canPublish" @tap="publish(task)">发布任务</button>
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
import { familyTaskApi,type FamilyTask,type FamilyStudent } from '@/api/family-task';
import { canManageFamily,draftInput,recurrenceError,type FamilyForm,type FamilyIdentity } from '@/models/family-task-draft';
import { getParentSession } from '@/session/parent-session';
import { getMiniappCapabilities } from '@/api/capability';
import { ApiError } from '@/api/http';
const tasks=ref<FamilyTask[]>([]),students=ref<FamilyStudent[]>([]),original=ref<FamilyTask|null>(null),user=ref<FamilyIdentity|null>(null);
const busy=ref(false),editing=ref(false),loaded=ref(false),error=ref(''),page=ref(1),total=ref(0);
const canCreate=computed(()=>!!user.value&&canManageFamily(user.value,'LEARNING_TASK_CREATE'));
const canPublish=computed(()=>!!user.value&&canManageFamily(user.value,'LEARNING_TASK_PUBLISH'));
const today=()=>new Date(Date.now()+8*3600000).toISOString().slice(0,10);
const form=reactive<FamilyForm>({title:'',difficultyLevel:2,durationMinutes:30,scheduledDate:today(),remark:'',studentIds:[]});
let revision=0;
function valid(n:number,token:string){return n===revision&&getParentSession()?.accessToken===token;}
function clear(){tasks.value=[];students.value=[];original.value=null;user.value=null;editing.value=false;loaded.value=false;total.value=0;}
async function access(n:number,token:string,permission:string){const [identity,capability]=await Promise.all([familyTaskApi.me(token),getMiniappCapabilities()]);if(!valid(n,token))return false;if(!capability.learningTaskManagementEnabled||!canManageFamily(identity,permission)){clear();throw new Error('家庭任务未开启或无操作权限');}weekdayCapability.value=capability.taskWeekdayRecurrenceEnabled===true;user.value=identity;return true;}
function failed(cause:unknown){if(cause instanceof ApiError&&([401,403].includes(cause.statusCode)||cause.code==='FEATURE_DISABLED'))clear();error.value=cause instanceof Error?cause.message:'操作未完成，请刷新核对后重试';}
async function load(target=1){const n=++revision,token=getParentSession()?.accessToken;clear();error.value='';busy.value=true;try{if(!token)throw new Error('请先登录家长账号');if(!await access(n,token,'LEARNING_TASK_READ_MANAGED'))return;const result=await familyTaskApi.list(token,target);if(valid(n,token)){tasks.value=result.items;page.value=result.page;total.value=result.total;loaded.value=true;}}catch(cause){if(n===revision)failed(cause);}finally{if(n===revision)busy.value=false;}}
function backStep(){if(confirming.value){confirming.value=false;scopeStep.value=true;}else if(scopeStep.value){scopeStep.value=false;}else {const n=revision,token=getParentSession()?.accessToken;if(!token)return;uni.showModal({title:'退出任务编辑',content:'未保存的修改将丢失，确认退出吗？',confirmText:'退出',success:result=>{if(result.confirm&&valid(n,token))editing.value=false;}});}}
function nextStep(){if(recurrenceError(form,weekdayCapability.value)){error.value=recurrenceError(form,weekdayCapability.value);return;}if(confirming.value){void save(true);return;}if(!scopeStep.value){if(!form.title.trim()||!Number.isFinite(Number(form.durationMinutes))||Number(form.durationMinutes)<=0){error.value='请填写任务标题和有效时长';return;}error.value='';scopeStep.value=true;return;}confirmTask();}
async function edit(id?:string){scopeStep.value=false;confirming.value=false;const n=++revision,token=getParentSession()?.accessToken;if(!token){clear();return;}busy.value=true;error.value='';editing.value=false;original.value=null;try{if(!await access(n,token,'LEARNING_TASK_CREATE'))return;const [options,task]=await Promise.all([familyTaskApi.students(token),id?familyTaskApi.detail(token,id):Promise.resolve(null)]);if(!valid(n,token))return;if(task&&task.status!=='DRAFT')throw new Error('已发布任务不能编辑');students.value=options;original.value=task;Object.assign(form,{title:task?.title||'',difficultyLevel:task?.difficultyLevel||2,durationMinutes:task?.durationMinutes||30,scheduledDate:task?.scheduledDate||today(),remark:task?.remark||'',studentIds:task?task.targets.map(target=>target.targetId):[],recurrenceEnabled:task?.recurrenceEnabled??false,recurrenceFrequency:task?.recurrenceFrequency||'DAILY',recurrenceWeekdays:[...(task?.recurrenceWeekdays??[])],recurrenceEndDate:task?.recurrenceEndDate??null});editing.value=true;}catch(cause){if(valid(n,token))failed(cause);}finally{if(n===revision)busy.value=false;}}
function confirmTask(){if(recurrenceError(form,weekdayCapability.value)){error.value=recurrenceError(form,weekdayCapability.value);return;}if(!form.title.trim()||!form.studentIds.length||!Number.isInteger(Number(form.durationMinutes))||form.durationMinutes<1||form.durationMinutes>1440){error.value='请填写标题、有效时长并选择成长小主人';uni.showToast({title:'请完整填写任务并选择下发对象',icon:'none'});return;}confirming.value=true;}
async function save(publishNow=false){if(recurrenceError(form,weekdayCapability.value)){error.value=recurrenceError(form,weekdayCapability.value);return;}if(busy.value)return;if(publishNow&&!canPublish.value){error.value='无发布权限';return;}if(!form.title.trim()||!form.studentIds.length||form.durationMinutes<1||form.durationMinutes>1440){error.value='请填写标题、有效时长并选择学生';return;}if(form.studentIds.some(id=>!students.value.some(student=>student.id===id&&student.relationshipRole==='PRIMARY_GUARDIAN'))){error.value='学生关系已变化，请重新选择当前主监护孩子';return;}const n=++revision,token=getParentSession()?.accessToken;if(!token){clear();return;}busy.value=true;error.value='';try{if(!await access(n,token,'LEARNING_TASK_CREATE'))return;const saved=await familyTaskApi.save(token,original.value?.id||null,draftInput(form,original.value||undefined));if(!valid(n,token))return;original.value=saved;if(publishNow){if(!await access(n,token,'LEARNING_TASK_PUBLISH'))return;await familyTaskApi.publish(token,saved.id);}if(valid(n,token)){confirming.value=false;await load(1);}}catch(cause){if(valid(n,token))failed(cause);}finally{if(n===revision)busy.value=false;}}
async function publish(task:FamilyTask){if(busy.value)return;const n=++revision,token=getParentSession()?.accessToken;if(!token){clear();return;}busy.value=true;error.value='';try{const confirmed=await new Promise<boolean>(resolve=>uni.showModal({title:'确认发布家庭任务',content:`发布“${task.title}”后学生可认领执行。`,success:result=>resolve(result.confirm),fail:()=>resolve(false)}));if(!confirmed||!valid(n,token))return;if(!await access(n,token,'LEARNING_TASK_PUBLISH'))return;await familyTaskApi.publish(token,task.id);if(valid(n,token))await load(page.value);}catch(cause){if(valid(n,token))failed(cause);}finally{if(n===revision)busy.value=false;}}
function value(event:Event){return (event as unknown as {detail:{value:string}}).detail.value;}
const weekdayCapability=ref(false);
const weekdayLabels=['周一','周二','周三','周四','周五','周六','周日'];
const frequencyLabels={DAILY:'每天',WEEKLY:'每周',CUSTOM:'自定义星期'};
function recurrenceChanged(event:Event){form.recurrenceEnabled=(event as unknown as {detail:{value:boolean}}).detail.value;}
function frequencyChanged(event:Event){form.recurrenceFrequency=(['DAILY','WEEKLY','CUSTOM'] as const)[Number(value(event))];form.recurrenceWeekdays=form.recurrenceFrequency==='DAILY'?[]:[new Date(form.scheduledDate+'T00:00:00Z').getUTCDay()||7];}
function toggleWeekday(day:number){if(form.recurrenceFrequency==='WEEKLY'){form.recurrenceWeekdays=[day];return;}const days=form.recurrenceWeekdays??[];form.recurrenceWeekdays=days.includes(day)?days.filter(x=>x!==day):[...days,day].sort((a,b)=>a-b);}
function difficulty(event:Event){form.difficultyLevel=Number(value(event))+1;}
function dateChanged(event:Event){form.scheduledDate=value(event);}
function studentsChanged(event:Event){form.studentIds=(event as unknown as {detail:{value:string[]}}).detail.value;}
function hide(){revision++;clear();busy.value=false;}
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
.confirmation{border:1rpx solid #e0e8e1;background:#fffefa;border-radius:24rpx;padding:28rpx;}

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
.form-section{padding:22rpx 0;border-top:1rpx solid #edf1ee;}.form-section-title{display:block;font-size:28rpx;font-weight:700;color:#075d43;margin-bottom:22rpx;}.form-section>text{display:block;margin:18rpx 0 10rpx;font-size:25rpx;}.student{padding:20rpx 14rpx;background:#f8fbf8;border-radius:16rpx;margin-bottom:12rpx;}.confirmation-banner{display:flex;align-items:center;gap:14rpx;color:#075d43;background:#ecf8ef;padding:20rpx;border-radius:16rpx;}.confirmation>text{display:block;padding:22rpx 0;border-bottom:1rpx solid #edf1ee;}.publish-actions{display:flex;flex-wrap:wrap;gap:12rpx;}.publish-actions button{flex:1;margin:0;font-size:23rpx!important;}
.prototype-workspace{background:#f7faf6!important;color:#172b25}.task-symbol{border-radius:50%!important;background:#e7f6ed!important}.task-card,.task-list-card{border:0!important;border-radius:22rpx!important;box-shadow:none!important}.task-title{font-size:30rpx!important}.primary-action{border-radius:44rpx!important}.prototype-header{background:#fff!important;color:#172b25!important;justify-content:center;border-radius:0!important;padding:28rpx!important}.prototype-header-title{font-size:34rpx}.publish-actions{position:fixed!important;bottom:0;left:0;right:0;padding:20rpx 24rpx calc(20rpx + env(safe-area-inset-bottom));box-sizing:border-box}.page>.task-editor{padding-bottom:180rpx!important}.task-list-card>.heading{display:block;margin-bottom:12rpx}.task-list-card>text:not(.heading){font-size:24rpx;color:#7c8981}.task-list-card button{display:inline-flex;margin:18rpx 12rpx 0 0;line-height:58rpx;padding:0 22rpx}.page-shell{padding:24rpx 28rpx 170rpx!important;box-sizing:border-box}.metadata-band .detail-row{min-height:0}.detail-symbol{background:#fff0d7!important;color:#eea12a!important}.action-band{border:0!important}.checkin-input{height:180rpx}.ld-heading{text-align:center;background:white;margin:0 -32rpx;padding:28rpx}.source-filter-inner{display:flex;width:100%;gap:0;background:#eff3ee;border-radius:24rpx}.source-button{flex:1;min-width:0;padding:0;box-shadow:none;background:transparent}.source-button.active{background:transparent!important;color:#075e43!important;border-bottom:4rpx solid #075e43}.task-row-top{justify-content:space-between}.task-meta{font-size:23rpx}.task-list-card{min-height:148rpx}
.weekday-options{display:flex;flex-wrap:wrap;gap:10rpx}.weekday-options button{padding:0 16rpx;font-size:23rpx!important}.weekday-options button.selected{background:#184F40!important;color:#fff!important}.recurrence-controls{margin-top:24rpx}
</style>
