export interface RegistrationFields { username:string;displayName:string;password:string;confirmation:string;channel:'EMAIL'|'SMS';destination:string;code:string;accepted:boolean }
export function accountError(f:RegistrationFields):string {
 if(!/^[A-Za-z0-9_][A-Za-z0-9_.-]{2,63}$/.test(f.username.trim()))return '账号需为3至64位字母、数字或下划线';
 if(!f.displayName.trim())return '请填写姓名';
 if(!/^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d]{8,20}$/.test(f.password))return '密码需为8至20位字母和数字组合';
 if(f.password!==f.confirmation)return '两次密码不一致';
 return '';
}
export function contactError(f:RegistrationFields):string {
 if(f.channel==='EMAIL'?!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(f.destination.trim()):!/^1\d{10}$/.test(f.destination.trim()))return '请填写有效联系方式';
 if(!/^\d{6}$/.test(f.code.trim()))return '请填写6位验证码';
 return '';
}
export function registrationPayload(f:RegistrationFields,agreementVersion:string,deviceId:string,deviceName:string) {
 return {username:f.username.trim(),displayName:f.displayName.trim(),password:f.password,passwordConfirmation:f.confirmation,channel:f.channel,destination:f.destination.trim(),code:f.code.trim(),agreementAccepted:f.accepted,agreementVersion,clientType:'MINIAPP',deviceId,deviceName};
}

export function advanceRegistrationStep(step:number,f:RegistrationFields,busy:boolean,ready:boolean):{step:number;error:string} {
 if(busy||!ready)return {step,error:''};
 const error=step===1?accountError(f):contactError(f);
 return {step:error?step:Math.min(3,step+1),error};
}
