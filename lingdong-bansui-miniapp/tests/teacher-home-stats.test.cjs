const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),vm=require('node:vm'),ts=require('typescript');
const tick=()=>new Promise(resolve=>setImmediate(resolve));
function deferred(){let resolve,reject;const state={completed:false};const promise=new Promise((a,b)=>{resolve=a;reject=b}).finally(()=>{state.completed=true;});return {promise,resolve,reject,state};}
function harness(permissions=['ATTENDANCE_READ','LEARNING_TASK_READ_MANAGED']){
 let show,hide;let token='a',org='one';const tasks=[],rosters=[];
 const mocks={vue:require('vue'),'@dcloudio/uni-app':{onShow:f=>show=f,onHide:f=>hide=f,onUnload:()=>{}},'@/api/auth':{},'@/api/capability':{getMiniappCapabilities:async()=>({learningTaskManagementEnabled:true})},'@/composables/use-attendance-entry':{useAttendanceEntry:()=>({})},'@/api/teacher-workbench':{getTeacherWorkbenchContext:async()=>({classes:[{classId:'1'},{classId:'2'}],permissionCodes:permissions})},'@/api/attendance':{getAttendanceRoster:()=>{const d=deferred();rosters.push(d);return d.promise;}},'@/api/managed-learning-task':{listManagedTasks:(...args)=>{const d=deferred();tasks.push({...d,args});return d.promise;}},'@/session/organization-session':{getOrganizationSession:()=>({accessToken:token}),clearOrganizationSession:()=>{}}};
 let src=require('@vue/compiler-sfc').parse(fs.readFileSync('src/pages/teacher-home/teacher-home.vue','utf8')).descriptor.scriptSetup.content;
 src+='\nexport {studentTotal,inProgressTotal,taskEnabled};';const exports={};
 vm.runInNewContext(ts.transpileModule(src,{compilerOptions:{module:ts.ModuleKind.CommonJS}}).outputText,{exports,require:n=>mocks[n]||{},uni:{getStorageSync:()=>({accessToken:token,organizationId:org}),reLaunch:()=>{}},Date,Set,Promise,JSON});
 return {exports,tasks,rosters,show:()=>show(),hide:()=>hide(),scope:()=>{org+='x';},account:()=>{token+='x';}};
}
async function finishRosters(h,start=0){h.rosters.slice(start).forEach(d=>d.resolve([{studentId:'same'}]));await tick();}
test('server total exceeds page and pupils are unique',{timeout:2000},async()=>{const h=harness();await h.show();await finishRosters(h);assert.equal(h.exports.studentTotal.value,1);assert.deepEqual(Array.from(h.tasks[0].args),['a',1,'TEACHER','PUBLISHED']);h.tasks[0].resolve({items:Array(20).fill({status:'PUBLISHED'}),total:37});await tick();assert.equal(h.exports.inProgressTotal.value,37);assert.equal(h.tasks[0].state.completed,true);});
for(const transition of ['hide','scope','account'])for(const failure of [false,true])test(`${transition}: stale ${failure?'failure':'success'} cannot overwrite`,{timeout:2000},async()=>{
 const h=harness();await h.show();await finishRosters(h);const old=h.tasks[0];h[transition]();assert.equal(transition==='hide'?h.exports.taskEnabled.value:false,false);await h.show();await finishRosters(h,2);h.tasks[1].resolve({items:[],total:37});await tick();failure?old.reject(new Error('old')):old.resolve({items:[],total:99});await tick();assert.equal(old.state.completed,true,'old request completion sentinel');assert.equal(h.tasks[1].state.completed,true,'new request completion sentinel');assert.equal(h.exports.inProgressTotal.value,37);assert.equal(h.exports.studentTotal.value,1);
});
test('hidden roster completion cannot restore stats',{timeout:2000},async()=>{const h=harness();await h.show();h.hide();await finishRosters(h);assert.equal(h.exports.studentTotal.value,null);assert.equal(h.exports.inProgressTotal.value,null);assert.equal(h.tasks.length,0);assert.equal(h.exports.taskEnabled.value,false);});
test('no permissions makes zero resource requests',{timeout:2000},async()=>{const h=harness([]);await h.show();await tick();assert.equal(h.tasks.length,0);assert.equal(h.rosters.length,0);assert.equal(h.exports.studentTotal.value,null);assert.equal(h.exports.inProgressTotal.value,null);});
