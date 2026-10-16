function version(value:number){if(!Number.isInteger(value)||value<0)throw Error('配置版本无效，请刷新');return value;}
export function headTeacherDraft(classId:string,teacherId:string|null,versionNo:number,teachers:{userId:string;classOrganizationIds:string[]}[]){
 if(!/^[1-9]\d{0,18}$/.test(classId))throw Error('当前班级无效');
 if(teacherId!==null&&!teachers.some(t=>t.userId===teacherId&&t.classOrganizationIds.includes(classId)))throw Error('请选择当前班级任教教师');
 return {headTeacherUserId:teacherId,versionNo:version(versionNo)};
}
export function studentNumberDraft(value:string,versionNo:number){const normalized=value.trim();if(normalized.length>64||/[\u0000-\u001f\u007f-\u009f]/.test(normalized))throw Error('学号最多64个有效字符');return {studentNumber:normalized||null,versionNo:version(versionNo)};}
