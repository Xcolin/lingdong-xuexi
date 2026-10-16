interface TreeNode {id:string;parentId:string|null;name:string;sortOrder?:number}
type TypedTreeNode=TreeNode & {typeCode:string};
function belongsToTraining<T extends TypedTreeNode>(nodes:T[],node:T):boolean{
  let parent:T|undefined=node;const seen=new Set<string>();let training=false;
  while(parent&&!seen.has(parent.id)){seen.add(parent.id);if(parent.typeCode==='TRAINING_INSTITUTION'){training=true;break;}if(parent.typeCode==='SCHOOL')break;const parentId:string|null=parent.parentId;parent=nodes.find((item:T):boolean=>item.id===parentId);}
  return training;
}
export function hierarchyNodeLabel<T extends TypedTreeNode>(nodes:T[],node:T):string{
  const training=belongsToTraining(nodes,node);
  if(training&&node.typeCode==='GRADE')return '课程分类';
  if(training&&node.typeCode==='CLASS')return '课程班';
  return ({SCHOOL:'学校',TRAINING_INSTITUTION:'培训机构',CAMPUS:'校区',GRADE:'年级',CLASS:'班级'} as Record<string,string>)[node.typeCode]||node.typeCode;
}
export function hierarchyNodeSummary<T extends TypedTreeNode>(nodes:T[],node:T):string{
  const counts=hierarchyDescendantCounts(nodes,node.id);
  const training=belongsToTraining(nodes,node);
  const gradeLabel=training?'课程分类':'年级',classLabel=training?'课程班':'班级';
  if(node.typeCode==='GRADE')return `${counts.classes} 个${classLabel}`;
  if(['SCHOOL','TRAINING_INSTITUTION','CAMPUS'].includes(node.typeCode))return `${counts.grades} 个${gradeLabel} · ${counts.classes} 个${classLabel}`;
  return hierarchyNodeLabel(nodes,node);
}
export function hierarchyDescendantCounts<T extends TreeNode & {typeCode:string}>(nodes:T[],rootId:string){
  const children=new Map<string,T[]>();for(const node of nodes){const parent=node.parentId??'';if(!children.has(parent))children.set(parent,[]);children.get(parent)!.push(node);}
  const pending=[rootId],seen=new Set<string>([rootId]);let grades=0,classes=0;
  while(pending.length){for(const node of children.get(pending.pop()!)??[]){if(seen.has(node.id))continue;seen.add(node.id);pending.push(node.id);if(node.typeCode==='GRADE')grades++;if(node.typeCode==='CLASS')classes++;}}
  return {grades,classes};
}
export function hierarchyTreeRows<T extends TreeNode>(nodes:T[],rootId:string,expanded:string[]):{node:T;depth:number;hasChildren:boolean}[]{
  const children=new Map<string,T[]>();
  for(const node of nodes){const key=node.parentId??'';if(!children.has(key))children.set(key,[]);children.get(key)!.push(node);}
  for(const rows of children.values())rows.sort((a,b)=>(a.sortOrder??0)-(b.sortOrder??0)||a.name.localeCompare(b.name,'zh-CN')||a.id.localeCompare(b.id));
  const result:{node:T;depth:number;hasChildren:boolean}[]=[],seen=new Set<string>([rootId]),open=new Set(expanded);
  function visit(parent:string,depth:number){for(const node of children.get(parent)??[]){if(seen.has(node.id))continue;seen.add(node.id);result.push({node,depth,hasChildren:!!children.get(node.id)?.length});if(open.has(node.id))visit(node.id,depth+1);}}
  visit(rootId,0);return result;
}

