/** 只修改当前候选，保留未出现在本次候选中的既有绑定。 */
export function mergeTeacherClassSelection(current: string[], available: string[], selected: string[]): string[] {
  const candidates = new Set(available);
  if (selected.some(id => !candidates.has(id))) throw new Error('班级候选已变化，请重新选择');
  return [...new Set([...current.filter(id => !candidates.has(id)), ...selected])];
}

interface OrderedNode { id: string; name: string; sortOrder: number; parentId?: string | null }
export function toggleTeacherClassGroup(current:string[],available:string[],group:string[]):string[]{
  const candidates=new Set(available);
  if(group.some(id=>!candidates.has(id)))throw new Error('班级候选已变化，请重新选择');
  if(!group.length)return [...current];
  const members=new Set(group),remove=group.every(id=>current.includes(id));
  const selected=remove?current.filter(id=>candidates.has(id)&&!members.has(id)):[...current.filter(id=>candidates.has(id)),...group];
  return mergeTeacherClassSelection(current,available,selected);
}
export function groupTeacherClasses<T extends OrderedNode>(classes:T[],grades:OrderedNode[],campuses:OrderedNode[]) {
  const gradeMap=new Map(grades.map(item=>[item.id,item])),campusMap=new Map(campuses.map(item=>[item.id,item]));
  const groups=new Map<string,{id:string;gradeName:string;campusName:string;campusId:string;items:T[]}>();
  for(const item of sortTeacherClasses(classes,grades,campuses)){
    const grade=gradeMap.get(item.parentId??''),campus=campusMap.get(grade?.parentId??'');
    const key=item.parentId??'';
    if(!groups.has(key))groups.set(key,{id:key,gradeName:grade?.name??'',campusName:campus?.name??'',campusId:campus?.id??'',items:[]});
    groups.get(key)!.items.push(item);
  }
  return [...groups.values()];
}
export function sortTeacherClasses<T extends OrderedNode>(classes: T[], grades: OrderedNode[], campuses: OrderedNode[]): T[] {
  const gradeMap = new Map(grades.map(item => [item.id, item]));
  const campusMap = new Map(campuses.map(item => [item.id, item]));
  const compare = (a?: OrderedNode, b?: OrderedNode) => (a?.sortOrder ?? 0) - (b?.sortOrder ?? 0)
    || (a?.name ?? '').localeCompare(b?.name ?? '', 'zh-CN') || (a?.id ?? '').localeCompare(b?.id ?? '');
  return [...classes].sort((a, b) => {
    const ga = gradeMap.get(a.parentId ?? ''), gb = gradeMap.get(b.parentId ?? '');
    return compare(campusMap.get(ga?.parentId ?? ''), campusMap.get(gb?.parentId ?? ''))
      || compare(ga, gb) || compare(a, b);
  });
}
