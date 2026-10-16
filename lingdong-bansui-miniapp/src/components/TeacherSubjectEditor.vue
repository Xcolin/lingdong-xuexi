<template>
  <view class="subject-editor">
    <text class="subject-label">任教学科</text>
    <text v-if="loading" class="subject-state">正在读取</text>
    <template v-else-if="ready">
      <picker :range="labels" :value="selection" :disabled="!editable||saving||uncertain" @change="choose">
        <view class="subject-value">{{ selectedLabel }}<text v-if="editable">⌄</text></view>
      </picker>
      <button v-if="editable" class="subject-save" :disabled="saving||uncertain||!validDraft" :loading="saving" @tap="save">保存学科</button>
    </template>
    <text v-if="error" class="subject-error">{{ error }}</text>
    <button v-if="error" class="subject-reload" :disabled="saving" @tap="load">重新读取</button>
  </view>
</template>
<script setup lang="ts">
import {ref,computed,watch,onBeforeUnmount} from 'vue';
import {readTeacherSubject,updateTeacherSubject,type TeacherSubjectOption,type TeacherSubject} from '@/api/teacher-management';
import {getOrganizationSession} from '@/session/organization-session';
const props=defineProps<{token:string;scope:string;teacherId:string;institutionId:string;editable:boolean;options:TeacherSubjectOption[]}>();
const emit=defineEmits<{(e:'saved',value:TeacherSubject):void}>();
const draft=ref<string|null>(null),ready=ref(false),loading=ref(false),saving=ref(false),uncertain=ref(false),error=ref('');
let versionNo=0,revision=0,alive=true;
const labels=computed(()=>['未设置',...props.options.map(item=>item.name)]);
const selection=computed(()=>draft.value===null?0:props.options.findIndex(item=>item.code===draft.value)+1);
const selectedLabel=computed(()=>draft.value===null?'未设置':props.options.find(item=>item.code===draft.value)?.name||draft.value);
const validDraft=computed(()=>draft.value===null||props.options.some(item=>item.code===draft.value));
function scopeValid(){const s=uni.getStorageSync('lingdong.selected-organization') as {accessToken?:string;organizationId?:string}|undefined;return alive&&!!props.token&&getOrganizationSession()?.accessToken===props.token&&s?.accessToken===props.token&&s?.organizationId===props.scope;}
function current(n:number){return n===revision&&scopeValid();}
function choose(event:{detail:{value:string|number}}){if(!props.editable||saving.value||uncertain.value)return;const index=Number(event.detail.value);if(!Number.isInteger(index)||index<0||index>props.options.length)return;draft.value=index===0?null:props.options[index-1].code;}
function validate(value:TeacherSubject){if(value.teacherUserId!==props.teacherId||value.institutionOrganizationId!==props.institutionId||!Number.isInteger(value.versionNo)||value.versionNo<0)throw Error('学科响应范围或版本不一致');}
async function load(){
  if(saving.value)return;const n=++revision;ready.value=false;loading.value=true;error.value='';
  try{if(!scopeValid())throw Error('组织或账号已变化，请重新进入');const value=await readTeacherSubject(props.token,props.teacherId,props.institutionId);if(!current(n))return;validate(value);versionNo=value.versionNo;draft.value=value.subjectCode;ready.value=true;uncertain.value=false;}
  catch(e){if(current(n))error.value=e instanceof Error?e.message:'学科读取失败';}
  finally{if(n===revision)loading.value=false;}
}
async function save(){
  if(!ready.value||loading.value||saving.value||uncertain.value||!props.editable||!validDraft.value||!scopeValid())return;
  const n=revision;const expected=versionNo;saving.value=true;error.value='';
  try{const value=await updateTeacherSubject(props.token,props.teacherId,props.institutionId,{subjectCode:draft.value,versionNo:expected});if(!current(n))return;validate(value);if(value.versionNo!==expected+1)throw Error('保存结果待核对，请重新读取');versionNo=value.versionNo;draft.value=value.subjectCode;emit('saved',value);}
  catch(e){if(current(n)){uncertain.value=true;error.value=e instanceof Error?e.message:'保存结果待确认，请重新读取';}}
  finally{if(n===revision)saving.value=false;}
}
watch(()=>[props.token,props.scope,props.teacherId,props.institutionId],()=>{++revision;ready.value=false;saving.value=false;uncertain.value=false;draft.value=null;void load();},{immediate:true});
onBeforeUnmount(()=>{alive=false;++revision;ready.value=false;draft.value=null;});
</script>
<style scoped>
.subject-editor{margin-top:24rpx;padding-top:22rpx;border-top:1rpx solid #e3ebe6}.subject-label{display:block;font-size:27rpx;color:#5c7868;margin-bottom:14rpx}.subject-value{height:76rpx;display:flex;align-items:center;justify-content:space-between;padding:0 20rpx;border:1rpx solid #dce7df;border-radius:16rpx;background:#f5f9f6;color:#224e3c;font-size:28rpx}.subject-save,.subject-reload{margin:18rpx 0 0;font-size:26rpx;line-height:70rpx;border-radius:16rpx;background:#155e45;color:#fff}.subject-reload{background:#eef5ef;color:#315f48}.subject-error{display:block;color:#b35145;font-size:25rpx;margin-top:14rpx}.subject-state{font-size:27rpx;color:#6b8173}button::after{border:0}
</style>
