<template>
  <view class="page has-tabbar">
    <text class="title">家庭任务</text>
    <button v-if="canCreate" :disabled="busy" @tap="edit()">新建家庭任务</button>
    <text v-if="busy">正在处理任务</text><text v-if="error" class="error">{{ error }}</text>
    <view v-if="editing" class="card">
      <text class="heading">{{ original ? '编辑草稿' : '新建任务' }}</text>
      <input v-model="form.title" :disabled="busy" maxlength="50" placeholder="任务标题" />
      <picker :range="['简单','中等','困难']" :value="form.difficultyLevel-1" :disabled="busy" @change="difficulty"><view class="field">难度：{{ ['简单','中等','困难'][form.difficultyLevel-1] }}</view></picker>
      <text>时长（分钟）</text><input v-model.number="form.durationMinutes" :disabled="busy" type="number" />
      <picker mode="date" :value="form.scheduledDate" :disabled="busy" @change="dateChanged"><view class="field">计划日期：{{ form.scheduledDate }}</view></picker>
      <text>任务学生</text>
      <checkbox-group @change="studentsChanged"><label v-for="student in students" :key="student.id" class="student"><checkbox :value="student.id" :checked="form.studentIds.includes(student.id)" :disabled="busy||student.relationshipRole!=='PRIMARY_GUARDIAN'" />{{ student.studentName }}{{ student.relationshipRole === 'SECONDARY_GUARDIAN' ? '（副家长不能创建）' : '' }}</label></checkbox-group>
      <text v-if="!students.some(item=>item.relationshipRole==='PRIMARY_GUARDIAN')">暂无可创建任务的主监护孩子</text>
      <textarea v-model="form.remark" :disabled="busy" maxlength="200" placeholder="任务备注（可选）" />
      <text v-if="original?.recurrenceEnabled">保留原每日重复配置，截止：{{ original.recurrenceEndDate || '未设置' }}</text>
      <button :disabled="busy||!canCreate" @tap="save">保存草稿</button><button :disabled="busy" @tap="editing=false">取消编辑</button>
    </view>
    <template v-else-if="loaded && !busy">
      <text>共 {{ total }} 项家庭任务</text><text v-if="!total">暂无家庭任务</text>
      <view v-for="task in tasks" :key="task.id" class="card"><text class="heading">{{ task.title }}</text><text>{{ task.scheduledDate }} · {{ task.status==='DRAFT'?'草稿':'已发布' }}</text>
        <button v-if="task.status==='DRAFT'&&canCreate" @tap="edit(task.id)">编辑草稿</button>
        <button v-if="task.status==='DRAFT'&&canPublish" @tap="publish(task)">发布任务</button>
      </view>
      <view v-if="total" class="paging"><button :disabled="page<=1" @tap="load(page-1)">上一页</button><text>第 {{ page }} 页</text><button :disabled="page*20>=total" @tap="load(page+1)">下一页</button></view>
    </template>
  <AppTabBar :items="PARENT_TABS" :active="1" />
  </view>
</template>
<script setup lang="ts">
import AppTabBar from '@/components/AppTabBar.vue';
import { PARENT_TABS } from '@/config/tabbar';
import { ref,reactive,computed } from 'vue';
import { onShow,onHide,onUnload } from '@dcloudio/uni-app';
import { familyTaskApi,type FamilyTask,type FamilyStudent } from '@/api/family-task';
import { canManageFamily,draftInput,type FamilyForm,type FamilyIdentity } from '@/models/family-task-draft';
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
async function access(n:number,token:string,permission:string){const [identity,capability]=await Promise.all([familyTaskApi.me(token),getMiniappCapabilities()]);if(!valid(n,token))return false;if(!capability.learningTaskManagementEnabled||!canManageFamily(identity,permission)){clear();throw new Error('家庭任务未开启或无操作权限');}user.value=identity;return true;}
function failed(cause:unknown){if(cause instanceof ApiError&&([401,403].includes(cause.statusCode)||cause.code==='FEATURE_DISABLED'))clear();error.value=cause instanceof Error?cause.message:'操作未完成，请刷新核对后重试';}
async function load(target=1){const n=++revision,token=getParentSession()?.accessToken;clear();error.value='';busy.value=true;try{if(!token)throw new Error('请先登录家长账号');if(!await access(n,token,'LEARNING_TASK_READ_MANAGED'))return;const result=await familyTaskApi.list(token,target);if(valid(n,token)){tasks.value=result.items;page.value=result.page;total.value=result.total;loaded.value=true;}}catch(cause){if(n===revision)failed(cause);}finally{if(n===revision)busy.value=false;}}
async function edit(id?:string){const n=++revision,token=getParentSession()?.accessToken;if(!token){clear();return;}busy.value=true;error.value='';editing.value=false;original.value=null;try{if(!await access(n,token,'LEARNING_TASK_CREATE'))return;const [options,task]=await Promise.all([familyTaskApi.students(token),id?familyTaskApi.detail(token,id):Promise.resolve(null)]);if(!valid(n,token))return;if(task&&task.status!=='DRAFT')throw new Error('已发布任务不能编辑');students.value=options;original.value=task;Object.assign(form,{title:task?.title||'',difficultyLevel:task?.difficultyLevel||2,durationMinutes:task?.durationMinutes||30,scheduledDate:task?.scheduledDate||today(),remark:task?.remark||'',studentIds:task?task.targets.map(target=>target.targetId):[]});editing.value=true;}catch(cause){if(valid(n,token))failed(cause);}finally{if(n===revision)busy.value=false;}}
async function save(){if(busy.value)return;if(!form.title.trim()||!form.studentIds.length||form.durationMinutes<1||form.durationMinutes>1440){error.value='请填写标题、有效时长并选择学生';return;}if(form.studentIds.some(id=>!students.value.some(student=>student.id===id&&student.relationshipRole==='PRIMARY_GUARDIAN'))){error.value='学生关系已变化，请重新选择当前主监护孩子';return;}const n=++revision,token=getParentSession()?.accessToken;if(!token){clear();return;}busy.value=true;error.value='';try{if(!await access(n,token,'LEARNING_TASK_CREATE'))return;await familyTaskApi.save(token,original.value?.id||null,draftInput(form,original.value||undefined));if(valid(n,token))await load(1);}catch(cause){if(valid(n,token))failed(cause);}finally{if(n===revision)busy.value=false;}}
async function publish(task:FamilyTask){if(busy.value)return;const n=++revision,token=getParentSession()?.accessToken;if(!token){clear();return;}busy.value=true;error.value='';try{const confirmed=await new Promise<boolean>(resolve=>uni.showModal({title:'确认发布家庭任务',content:`发布“${task.title}”后学生可认领执行。`,success:result=>resolve(result.confirm),fail:()=>resolve(false)}));if(!confirmed||!valid(n,token))return;if(!await access(n,token,'LEARNING_TASK_PUBLISH'))return;await familyTaskApi.publish(token,task.id);if(valid(n,token))await load(page.value);}catch(cause){if(valid(n,token))failed(cause);}finally{if(n===revision)busy.value=false;}}
function value(event:Event){return (event as unknown as {detail:{value:string}}).detail.value;}
function difficulty(event:Event){form.difficultyLevel=Number(value(event))+1;}
function dateChanged(event:Event){form.scheduledDate=value(event);}
function studentsChanged(event:Event){form.studentIds=(event as unknown as {detail:{value:string[]}}).detail.value;}
function hide(){revision++;clear();busy.value=false;}
onShow(()=>load(1));onHide(hide);onUnload(hide);
</script>
<style scoped>
.page{padding:32rpx;min-height:100vh;box-sizing:border-box;background:#f4f7f5;color:#1c2b28;font-family:system-ui,"Microsoft YaHei",sans-serif}.page text{display:block;margin:16rpx 0;overflow-wrap:anywhere}.title{font-size:38rpx;font-weight:700}.card{padding:24rpx;margin-top:24rpx;background:white;border-radius:12rpx}.heading{font-weight:600}.error{color:#a33c2e}.student{display:block;margin:20rpx 0}input,textarea,.field{box-sizing:border-box;width:100%;padding:18rpx;border:1px solid #dce4e1;margin:14rpx 0}input{height:80rpx}button{font-size:28rpx;margin:16rpx 0}.paging{display:flex;align-items:center;gap:12rpx;justify-content:space-between}
</style>
