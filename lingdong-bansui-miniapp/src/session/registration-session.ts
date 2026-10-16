import type { StudentSession } from '@/api/auth';
import { request } from '@/api/http';
const key='lingdong.registration.session';
export function saveRegistrationSession(session:StudentSession){uni.setStorageSync(key,session);}
export function getRegistrationSession():StudentSession|null{const s=uni.getStorageSync(key) as StudentSession;return s?.accessToken?s:null;}
const aliases=['lingdong.parent.session','lingdong.organization.session'];
function synchronizeAliases(oldToken:string,renewed?:StudentSession){for(const alias of aliases){const stored=uni.getStorageSync(alias);if(stored?.accessToken!==oldToken)continue;if(renewed)uni.setStorageSync(alias,{...stored,...renewed});else uni.removeStorageSync(alias);}}
function clearMatching(session:StudentSession){if(getRegistrationSession()?.accessToken!==session.accessToken)return;uni.removeStorageSync(key);synchronizeAliases(session.accessToken);const selected=uni.getStorageSync('lingdong.selected-organization');if(selected?.accessToken===session.accessToken)uni.removeStorageSync('lingdong.selected-organization');}
let rotation:Promise<StudentSession|null>|null=null;
export async function resolveRegistrationSession():Promise<StudentSession|null>{
 const session=getRegistrationSession();if(!session)return null;
 if(Date.parse(session.accessExpiresAt)>Date.now()+30000)return session;
 if(rotation)return rotation;
 rotation=(async()=>{try{
  if(!(Date.parse(session.refreshExpiresAt)>Date.now()))throw new Error('登录已过期');
  const renewed=await request<StudentSession>('/auth/sessions/refresh',{method:'POST',data:{refreshToken:session.refreshToken}});
  if(getRegistrationSession()?.accessToken!==session.accessToken)return null;
  const selected=uni.getStorageSync('lingdong.selected-organization');saveRegistrationSession(renewed);synchronizeAliases(session.accessToken,renewed);
  if(selected?.accessToken===session.accessToken)uni.setStorageSync('lingdong.selected-organization',{...selected,accessToken:renewed.accessToken});
  return renewed;
 }catch{clearMatching(session);return null;}})();
 try{return await rotation;}finally{rotation=null;}
}
