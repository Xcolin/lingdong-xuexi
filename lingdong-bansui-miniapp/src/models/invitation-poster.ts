import {QrCode,Ecc} from '@/vendor/qrcodegen';
export interface InvitationPosterData {inviteCode?:string;institutionName:string;className:string;expiresAt:string}
/** Render only the actual server-issued invitation; no additional server grant or token is embedded. */
export function createInvitationPoster(api:Pick<typeof uni,'createCanvasContext'|'canvasToTempFilePath'>,canvasId:string,data:InvitationPosterData,component?:any):Promise<string>{
 if(!data.inviteCode?.trim()||!data.className||!data.institutionName||!data.expiresAt)return Promise.reject(new Error('请先生成有效的班级邀请码'));
 return new Promise((resolve,reject)=>{try{
  const canvas=api.createCanvasContext(canvasId,component),qr=QrCode.encodeText(data.inviteCode!,Ecc.MEDIUM);
  canvas.setFillStyle('#f4f8f3');canvas.fillRect(0,0,640,880);canvas.setFillStyle('#155743');canvas.fillRect(0,0,640,124);
  canvas.setFillStyle('#ffffff');canvas.setTextAlign('center');canvas.setFontSize(30);canvas.fillText('灵动伴随 · 班级入学邀请',320,76);
  const label=(value:string,y:number,size:number)=>{canvas.setFontSize(size);let line='',lines:string[]=[];for(const char of value){if(canvas.measureText(line+char).width>548){lines.push(line);line=char;}else line+=char;}if(line)lines.push(line);for(let i=0;i<Math.min(lines.length,2);i++){let text=lines[i];if(i===1&&lines.length>2)text=text.slice(0,-1)+'…';canvas.fillText(text,320,y+i*(size+10));}};
  canvas.setFillStyle('#193b30');label(data.className,178,30);label(data.institutionName,252,24);
  const border=4,total=qr.size+border*2,side=360,left=140,top=326;
  canvas.setFillStyle('#ffffff');canvas.fillRect(left,top,side,side);canvas.setFillStyle('#172d23');
  for(let y=0;y<qr.size;y++)for(let x=0;x<qr.size;x++)if(qr.getModule(x,y)){const x1=Math.round((x+border)*side/total),y1=Math.round((y+border)*side/total);canvas.fillRect(left+x1,top+y1,Math.round((x+border+1)*side/total)-x1,Math.round((y+border+1)*side/total)-y1);}
  canvas.setFillStyle('#496b5c');canvas.setFontSize(23);canvas.fillText('有效期至 '+data.expiresAt.replace('T',' ').slice(0,19),320,742);
  canvas.setFontSize(21);canvas.fillText('使用灵动伴随填写班级邀请码申请加入',320,797);canvas.fillText('邀请码仅限本班级使用，请妥善保管',320,832);
  canvas.draw(false,()=>api.canvasToTempFilePath({canvasId,x:0,y:0,width:640,height:880,destWidth:1280,destHeight:1760,success:r=>resolve(r.tempFilePath),fail:e=>reject(new Error(e.errMsg||'海报生成失败'))},component));
 }catch(e){reject(e instanceof Error?e:new Error('海报生成失败'));}});
}
