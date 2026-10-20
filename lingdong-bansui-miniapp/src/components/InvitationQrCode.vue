<template><view class="qr-frame"><canvas :canvas-id="canvasId" class="qr-canvas"/><text v-if="error" class="qr-error">二维码未能生成，请复制邀请码</text></view></template>
<script setup lang="ts">
import {getCurrentInstance,nextTick,onBeforeUnmount,onMounted,watch,ref} from 'vue';
import {QrCode,Ecc} from '@/vendor/qrcodegen';
const props=defineProps<{value:string}>();
const canvasId=`invite-qr-${getCurrentInstance()?.uid||0}`;
const component=getCurrentInstance()?.proxy;
const error=ref('');let revision=0;
async function draw(value:string){
 const current=++revision;error.value='';if(!value)return;
 await nextTick();if(current!==revision)return;
 try{
  const qr=QrCode.encodeText(value,Ecc.MEDIUM),border=4,total=qr.size+border*2;
  const pixels=Math.round(uni.getSystemInfoSync().windowWidth*360/750);
  const canvas=uni.createCanvasContext(canvasId,component);
  canvas.setFillStyle('#ffffff');canvas.fillRect(0,0,pixels,pixels);canvas.setFillStyle('#172d23');
  for(let y=0;y<qr.size;y++)for(let x=0;x<qr.size;x++)if(qr.getModule(x,y)){
   const left=Math.round((x+border)*pixels/total),top=Math.round((y+border)*pixels/total);
   const right=Math.round((x+border+1)*pixels/total),bottom=Math.round((y+border+1)*pixels/total);
   canvas.fillRect(left,top,right-left,bottom-top);
  }
  if(current===revision)canvas.draw();
 }catch{if(current===revision)error.value='二维码生成失败';}
}
watch(()=>props.value,draw);
onMounted(()=>{void draw(props.value);});
onBeforeUnmount(()=>{revision++;});
/** 导出当前二维码到相册（保存图片操作）。 */
async function saveToAlbum(): Promise<void>{
 return new Promise((resolve,reject)=>{
  uni.canvasToTempFilePath({canvasId,success:r=>{uni.saveImageToPhotosAlbum({filePath:r.tempFilePath,success:()=>resolve(),fail:e=>reject(new Error(e.errMsg||'保存未完成'))});},fail:e=>reject(new Error(e.errMsg||'导出未完成'))},component);
 });
}
defineExpose({saveToAlbum});
</script>
<style scoped>.qr-frame{display:flex;flex-direction:column;align-items:center;margin:24rpx auto;background:#fff;border:1rpx solid #e3eadd;border-radius:20rpx;padding:12rpx;width:384rpx;box-sizing:border-box;}.qr-canvas{width:360rpx;height:360rpx;}.qr-error{display:block;font-size:22rpx;color:#a77654;line-height:1.5;margin-top:12rpx;}</style>
