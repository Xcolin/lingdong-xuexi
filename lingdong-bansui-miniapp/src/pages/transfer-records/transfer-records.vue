<template>
 <view class="secondary-page">
  <PrototypePageHeader :title="detail?'申请详情':'转学申请'" icon="school"/>
  <text v-if="error" class="error">{{error}}</text><view v-if="loading" class="empty">正在加载</view>
  <template v-if="detail">
   <view class="section"><view class="section-heading"><text class="section-title">{{detail.display?.studentName||'成长小主人'}}</text><text class="badge" :class="{REJECTED:detail.executionBlocked||detail.status==='REJECTED'}">{{statusText(detail)}}</text></view><view class="detail-line"><text>当前入学机构</text><text>{{detail.display?.institutionName||'—'}}</text></view><view class="detail-line"><text>转学去向</text><text>{{detail.targetDescription}}</text></view><view class="detail-line"><text>预计日期</text><text>{{detail.effectiveDate}}</text></view><text class="field-label">申请原因</text><text class="reason-copy">{{detail.reason}}</text><text v-if="detail.display?.reviewReason" class="review-copy">审核意见：{{detail.display.reviewReason}}</text><text v-if="detail.executionBlocked" class="error">{{blockedText(detail.executionErrorCode)}}</text><view v-if="detail.display?.createdAt" class="detail-line"><text>申请时间</text><text>{{dateTime(detail.display.createdAt)}}</text></view><view v-if="detail.display?.reviewedAt" class="detail-line"><text>审核时间</text><text>{{dateTime(detail.display.reviewedAt)}}</text></view><view v-if="detail.display?.completedAt" class="detail-line"><text>完成时间</text><text>{{dateTime(detail.display.completedAt)}}</text></view></view>
   <view v-if="detail.display?.proofs?.length" class="section"><text class="section-title">证明材料</text><button v-for="proof in detail.display.proofs" :key="proof.id" class="proof-button" :disabled="busy" @tap="previewProof(proof)"><PrototypeIcon name="book" :size="34"/><text>{{proof.originalName}}</text><text>›</text></button></view>
   <view v-if="canReview&&detail.status==='PENDING'" class="section"><text class="field-label">审核意见</text><textarea v-model="reviewReason" maxlength="200" :disabled="busy" placeholder="驳回时请填写原因"/><view class="action-grid"><button class="secondary reject" :disabled="busy" @tap="act('reject')">驳回申请</button><button class="primary" :disabled="busy" @tap="act('approve')">通过申请</button></view></view>
   <view v-if="canReview&&detail.executionBlocked" class="section"><text class="field-label">驳回原因</text><textarea v-model="reviewReason" maxlength="200" :disabled="busy" placeholder="填写原因后，由当前主监护人重新申请"/><button class="secondary reject" :disabled="busy" @tap="act('reject-blocked')">驳回并允许重新申请</button></view>
   <button v-if="canWithdraw" class="secondary refresh" :disabled="busy" @tap="act('withdraw')">撤销申请</button><button class="secondary refresh" :disabled="busy" @tap="backToList">返回申请列表</button>
  </template>
  <template v-else-if="ready">
   <view class="filter-tabs"><button v-for="filter in filters" :key="filter.label" :class="{selected:status===filter.value}" :disabled="busy" @tap="changeStatus(filter.value)">{{filter.label}}</button></view>
   <view class="section"><view v-for="item in rows" :key="item.id" class="row" @tap="open(item)"><view class="person"><PrototypeIcon name="user" :size="44"/></view><view class="record-copy"><view class="record-title"><text>{{item.display?.studentName||'成长小主人'}}</text><text class="badge" :class="{REJECTED:item.executionBlocked||item.status==='REJECTED'}">{{statusText(item)}}</text></view><text class="meta">{{item.display?.institutionName||'—'}} · {{item.effectiveDate}}</text><text class="meta">{{item.targetDescription}}</text></view><PrototypeIcon name="arrow" :size="28" tone="muted"/></view><view v-if="!rows.length" class="empty">暂无申请</view></view>
   <view class="empty"><text v-if="loadingMore">正在加载更多</text><button v-else-if="appendError" @tap="loadMore">加载失败，点击重试</button><text v-else-if="rows.length &amp;&amp; !hasMore">已经到底了</text></view>
  </template>
  <button v-if="!detail" class="secondary refresh" :disabled="busy" @tap="load(1)">刷新</button>
 </view>
</template>
<script setup lang="ts">
import {ref,computed} from 'vue';
import {onShow,onHide,onUnload,onReachBottom} from '@dcloudio/uni-app';
import PrototypePageHeader from '@/components/PrototypePageHeader.vue';
import PrototypeIcon from '@/components/PrototypeIcon.vue';
import {getParentSession} from '@/session/parent-session';
import {getOrganizationSession} from '@/session/organization-session';
import {parentChildrenApi,type ChildIdentity} from '@/api/parent-children';
import {transferApplicationApi,downloadTransferProof,type TransferProposal,type TransferProof} from '@/api/transfer-application';
const rows=ref<TransferProposal[]>([]),detail=ref<TransferProposal|null>(null),identity=ref<ChildIdentity|null>(null),loading=ref(false),acting=ref(false),ready=ref(false),error=ref(''),reviewReason=ref(''),status=ref(''),page=ref(1),total=ref(0);
const loadingMore=ref(false),hasMore=ref(false),appendError=ref('');
const busy=computed(()=>loading.value||acting.value),filters=[{label:'全部',value:''},{label:'待审核',value:'PENDING'},{label:'已通过',value:'APPROVED'},{label:'已完成',value:'COMPLETED'}];
let revision=0,visible=false,token='',organizationId='',organizationType='';
const canReview=computed(()=>!!detail.value&&organizationType!=='FAMILY'&&!!identity.value?.permissionCodes.includes('STUDENT_TRANSFER_REVIEW')&&identity.value.userId!==detail.value.applicantUserId);
const canWithdraw=computed(()=>detail.value?.status==='PENDING'&&identity.value?.userId===detail.value.applicantUserId&&!!identity.value?.permissionCodes.includes('STUDENT_TRANSFER_REQUEST_CREATE')&&(organizationType==='FAMILY'||!!identity.value?.permissionCodes.includes('STUDENT_ORGANIZATION_MANAGE')));
function selected(){return uni.getStorageSync('lingdong.selected-organization') as {accessToken?:string;organizationId?:string;typeCode?:string}|undefined;}
function session(){return organizationType==='FAMILY'?getParentSession():getOrganizationSession();}
function current(n:number){const s=selected();return visible&&n===revision&&session()?.accessToken===token&&s?.accessToken===token&&s.organizationId===organizationId&&s.typeCode===organizationType;}
function statusText(item:TransferProposal){return item.executionBlocked?'待重新核验':({PENDING:'待审核',APPROVED:'待到期执行',REJECTED:'已驳回',WITHDRAWN:'已撤销',COMPLETED:'已完成'}[item.status]);}
function blockedText(code:string|null){return ({DELEGATE_AUTHORITY_CHANGED:'机构代办人的权限或管理范围已变化，执行已暂停。',GUARDIAN_CHANGED:'监护或家庭关系发生变化，请联系当前入学机构核验。',APPLICANT_PERMISSION_CHANGED:'申请人权限已变化，请联系当前入学机构核验。',REVIEW_AUTHORITY_CHANGED:'审核人的权限或管理范围已变化，执行已暂停。',ENROLLMENT_CHANGED:'入学关系已变化，执行已暂停。',SOURCE_UNAVAILABLE:'当前入学机构不可用，执行已暂停。',STUDENT_UNAVAILABLE:'孩子账号状态已变化，执行已暂停。'} as Record<string,string>)[code||'']||'申请执行已暂停，请联系当前入学机构核验。';}
function dateTime(value:string){return value.replace('T',' ').slice(0,19);}
async function load(target=1){if(acting.value)return;const n=++revision;loadingMore.value=false;hasMore.value=false;appendError.value='';page.value=1;total.value=0;loading.value=true;error.value='';rows.value=[];detail.value=null;identity.value=null;ready.value=false;let scopeChecked=false;try{
 const s=selected();organizationType=s?.typeCode||'';organizationId=s?.organizationId||'';token=session()?.accessToken||'';
 if(!['FAMILY','SCHOOL','TRAINING_INSTITUTION'].includes(organizationType)||!token||s?.accessToken!==token||!organizationId)throw new Error('请先选择对应家庭、学校或培训机构');scopeChecked=true;
 const requestToken=token,requestOrganization=organizationId,requestStatus=status.value; const me=await parentChildrenApi.me(requestToken);if(!current(n))return;if(!me.permissionCodes.includes('STUDENT_TRANSFER_VIEW'))throw new Error('当前账号没有申请查看权限');identity.value=me;
 const result=await transferApplicationApi.list(requestToken,requestOrganization,target,requestStatus||undefined);if(!current(n))return;rows.value=result.items;page.value=result.page;total.value=result.total;hasMore.value=result.items.length>0&&result.page*(result.pageSize||20)<result.total;ready.value=true;
 }catch(e){if((!scopeChecked&&visible&&n===revision)||current(n))error.value=e instanceof Error?e.message:'申请列表加载未完成';}finally{if(n===revision)loading.value=false;}}
async function loadMore(){
 if(loadingMore.value||busy.value||!ready.value||detail.value||!hasMore.value||!current(revision))return;
 const n=revision,requestToken=token,requestOrganization=organizationId,requestStatus=status.value,target=page.value+1;
 loadingMore.value=true;appendError.value='';
 try{const result=await transferApplicationApi.list(requestToken,requestOrganization,target,requestStatus||undefined);if(!current(n))return;
 const previousCount=rows.value.length;const ids=new Set(rows.value.map(row=>row.id));rows.value=[...rows.value,...result.items.filter(row=>{if(ids.has(row.id))return false;ids.add(row.id);return true;})];page.value=result.page;total.value=result.total;hasMore.value=rows.value.length>previousCount&&result.page*(result.pageSize||20)<result.total;
 }catch(e){if(current(n))appendError.value=e instanceof Error?e.message:'加载未完成';}finally{if(n===revision)loadingMore.value=false;}
}
onReachBottom(()=>{if(!appendError.value)void loadMore();});
async function open(row:TransferProposal){if(busy.value||!current(revision))return;const n=revision;loading.value=true;error.value='';try{const result=await transferApplicationApi.read(token,organizationId,row.id);if(current(n)){detail.value=result;reviewReason.value='';}}catch(e){if(current(n))error.value=e instanceof Error?e.message:'申请详情读取未完成';}finally{if(n===revision)loading.value=false;}}
async function act(action:'approve'|'reject'|'withdraw'|'reject-blocked'){
 if(busy.value||!detail.value||!current(revision))return;if(action==='withdraw'?!canWithdraw.value:!canReview.value)return;
 if((action==='reject'||action==='reject-blocked')&&!reviewReason.value.trim()){error.value='请填写驳回原因';return;}
 const n=revision,row=detail.value;acting.value=true;error.value='';try{const confirmed=await uni.showModal({title:action==='approve'?'通过申请':action==='withdraw'?'撤销申请':'驳回申请',content:action==='approve'?'批准后将在预计日期复核并办理当前机构转出。':'确认执行此操作？',confirmText:'确认'});if(!confirmed.confirm||!current(n))return;const result=await transferApplicationApi.action(token,organizationId,row.id,action,row.versionNo,reviewReason.value.trim()||undefined);if(current(n)){detail.value=result;reviewReason.value='';}}catch(e){if(current(n))error.value=e instanceof Error?e.message:'操作结果尚未确认，请刷新核对';}finally{if(n===revision)acting.value=false;}}
async function previewProof(proof:TransferProof){if(busy.value||!detail.value||!current(revision))return;const n=revision;acting.value=true;error.value='';try{const path=await downloadTransferProof(token,organizationId,detail.value.id,proof.id);if(!current(n))return;if(proof.contentType.startsWith('image/'))uni.previewImage({urls:[path]});else await uni.openDocument({filePath:path,fileType:'pdf',showMenu:true});}catch(e){if(current(n))error.value=e instanceof Error?e.message:'材料打开未完成';}finally{if(n===revision)acting.value=false;}}
function changeStatus(value:string){if(busy.value)return;status.value=value;void load(1);}function backToList(){void load(1);}
function hide(){visible=false;++revision;loadingMore.value=false;hasMore.value=false;appendError.value='';rows.value=[];detail.value=null;identity.value=null;reviewReason.value='';acting.value=false;loading.value=false;ready.value=false;}
onShow(()=>{visible=true;void load(1);});onHide(hide);onUnload(hide);
</script>
<style scoped lang="scss">
@import '@/styles/secondary-prototype.scss';
.record-copy{flex:1;min-width:0;}.record-title{display:flex;align-items:center;justify-content:space-between;gap:16rpx;font-size:29rpx;font-weight:650;}.record-copy .meta{display:block;margin-top:8rpx;}.reason-copy,.review-copy{display:block;white-space:pre-wrap;line-height:1.7;font-size:28rpx;}.review-copy{margin-top:20rpx;color:#6c7f74;}.filter-tabs{display:flex;gap:12rpx;margin-bottom:22rpx;}.filter-tabs button{flex:1;padding:0;line-height:66rpx;font-size:25rpx;border-radius:30rpx;background:#edf3ee;color:#6f8075;}.filter-tabs .selected{background:#195c43;color:white;}.proof-button{display:flex;align-items:center;gap:16rpx;padding:18rpx 0;background:transparent;color:#345b4d;font-size:27rpx;}.proof-button>text:first-of-type{flex:1;text-align:left;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;}.proof-button::after{border:0;}.reject{color:#ba5348;border-color:#e8b9b1;}.section textarea{width:100%;min-height:130rpx;background:#f4f7f3;border-radius:16rpx;font-size:28rpx;padding:16rpx;box-sizing:border-box;margin-bottom:20rpx;}
</style>




