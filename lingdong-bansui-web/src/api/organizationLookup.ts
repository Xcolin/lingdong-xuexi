import type { OrganizationNode } from './organization';
/** Disabled ancestor rows provide paths only and cannot become operation targets. */
export function lookupOrganizationTree(rows: {id:string;parentId:string|null;name:string;typeCode:string;disabled:boolean}[]): OrganizationNode[] {
  const nodes = new Map(rows.map((row,index)=>[row.id,{...row,code:row.id,sortOrder:index,status:row.disabled?'DISABLED':'ENABLED',effectiveStatus:row.disabled?'DISABLED':'ENABLED',children:[]} as unknown as OrganizationNode]));
  const roots: OrganizationNode[]=[];
  for(const row of rows){const node=nodes.get(row.id)!;const parent=row.parentId?nodes.get(row.parentId):undefined;if(parent)parent.children.push(node);else roots.push(node);}
  return roots;
}
