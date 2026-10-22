interface FilterClass {id:string;parentId:string|null;gradeOrganizationId?:string|null;status:string}
interface FilterGrade {id:string;parentId:string|null}
/** Intersect actual server ancestry and selected filters without changing source order. */
export function filterClassList<T extends FilterClass>(rows:T[],grades:FilterGrade[],campusId:string,gradeId:string,status:string):T[]{
  const campusGrades=new Set(grades.filter(g=>g.parentId===campusId).map(g=>g.id));
  return rows.filter(row=>{
    const actualGrade=row.gradeOrganizationId||row.parentId;
    return (!campusId||!!actualGrade&&campusGrades.has(actualGrade))
      &&(!gradeId||actualGrade===gradeId)&&(!status||row.status===status);
  });
}
