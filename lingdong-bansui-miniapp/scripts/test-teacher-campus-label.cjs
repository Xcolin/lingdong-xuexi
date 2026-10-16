const fs=require('fs'),vm=require('vm'),ts=require('typescript'),assert=require('node:assert/strict'),out={};
vm.runInNewContext(ts.transpileModule(fs.readFileSync('src/models/teacher-campus-label.ts','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2020}}).outputText,{exports:out});
const teacher={schoolId:'school',schoolName:'真实学校',classOrganizationIds:['c1','c2','c3','foreign']};
const classes=[{id:'c1',parentId:'g1',schoolOrganizationId:'school'},{id:'c2',parentId:'g1',schoolOrganizationId:'school'},{id:'c3',parentId:'g2',schoolOrganizationId:'school'},{id:'foreign',parentId:'g3',schoolOrganizationId:'other'}];
const grades=[{id:'g1',parentId:'campus1',schoolOrganizationId:'school'},{id:'g2',parentId:'campus2',schoolOrganizationId:'school'},{id:'g3',parentId:'foreignCampus',schoolOrganizationId:'other'}];
const campuses=[{id:'campus1',name:'东校区',schoolOrganizationId:'school',sortOrder:2},{id:'campus2',name:'西校区',schoolOrganizationId:'school',sortOrder:1},{id:'foreignCampus',name:'外校',schoolOrganizationId:'other',sortOrder:0}];
assert.equal(out.teacherCampusLabel(teacher,classes,grades,campuses),'西校区、东校区');
assert.equal(out.teacherCampusLabel(teacher,classes,[],campuses),'真实学校');
assert.equal(out.teacherCampusLabel({...teacher,classOrganizationIds:[]},classes,grades,campuses),'真实学校');
console.log('PASS actual teacher campus ancestry, sorting, deduplication and school fallback');

