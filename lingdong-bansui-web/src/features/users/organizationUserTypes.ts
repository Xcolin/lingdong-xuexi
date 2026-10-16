export type OrganizationUserType = 'PLATFORM' | 'ORGANIZATION' | 'TEACHER' | 'FAMILY' | 'STUDENT';
const options: {value: OrganizationUserType; label: string}[] = [
  {value:'PLATFORM',label:'平台账号'}, {value:'ORGANIZATION',label:'机构账号'},
  {value:'TEACHER',label:'教师账号'}, {value:'FAMILY',label:'家长账号'}, {value:'STUDENT',label:'学生账号'}
];
export function organizationUserTypeOptions(typeCode?: string) {
  const allowed: OrganizationUserType[] = typeCode === 'FAMILY' ? ['FAMILY','STUDENT']
    : ['SCHOOL','TRAINING_INSTITUTION','CAMPUS','GRADE','CLASS'].includes(typeCode ?? '') ? ['ORGANIZATION','TEACHER','STUDENT']
    : ['REGION','CITY','COUNTRY'].includes(typeCode ?? '') ? ['PLATFORM'] : options.map(option=>option.value);
  return options.filter(option=>allowed.includes(option.value));
}
export function defaultOrganizationUserType(typeCode?: string): OrganizationUserType {
  return organizationUserTypeOptions(typeCode)[0].value;
}
