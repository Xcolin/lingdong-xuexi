/** Scope metadata must not imply sharing that the current access policy does not implement. */
export function familyTaskVisibilityAvailable(value:unknown):value is 'FAMILY_ONLY'{return value==='FAMILY_ONLY';}
export function selectFamilyTaskVisibility(form:{visibility?:string|null},value:unknown):boolean{
 if(!familyTaskVisibilityAvailable(value))return false;
 form.visibility=value;
 return true;
}
export function familyTaskVisibilitySummary(value:unknown):string{
 if(!value)return '按家庭关联与已授予权限访问';
 return familyTaskVisibilityAvailable(value)?'仅家庭（按家庭关联与已授予权限访问）':'家庭关联与已授予权限（保留原范围说明）';
}
