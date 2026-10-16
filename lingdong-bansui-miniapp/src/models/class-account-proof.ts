export interface AccountProofDraft {studentAccount:string;loginCode:string;consent:boolean;deviceId:string;deviceName:string;captchaRequired?:boolean;captchaChallengeId?:string;captchaAnswer?:string}
export function classAccountProof(value:AccountProofDraft){
 const studentAccount=value.studentAccount.trim();
 if(!/^[A-Za-z0-9_.@-]{1,64}$/.test(studentAccount))throw Error('请输入有效的成长小主人账号');
 if(!/^\d{4}$/.test(value.loginCode))throw Error('请输入4位登录码');
 if(!value.consent)throw Error('请阅读并同意用户协议与隐私条款');
 if(!value.deviceId.trim()||value.deviceId.length>128||!value.deviceName.trim()||value.deviceName.length>100)throw Error('设备信息无效，请重新进入');
 if(value.captchaRequired&&(!value.captchaChallengeId||!value.captchaAnswer?.trim()))throw Error('请输入图形验证码');
 return {studentAccount,loginCode:value.loginCode,consent:true,deviceId:value.deviceId,deviceName:value.deviceName,...(value.captchaRequired?{captchaChallengeId:value.captchaChallengeId,captchaAnswer:value.captchaAnswer!.trim()}:{})};
}
