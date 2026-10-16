import json,re
from pathlib import Path
inv=json.loads(Path('docs/auth-audit-frontend-inventory-2026-10-09.json').read_text(encoding='utf-8'))
M={}; evidence={}
def group(prefix,values):
 for k,v in values.items():M[prefix+'.'+str(k)]=v
for x in inv['catalog']:
 if re.fullmatch('[A-Z][A-Z0-9_]*',x['code']):M[x['code']]=x['code']
group('attachment-management.attachment-management-page',{1:'ATTACHMENT_RULE_READ',2:'ATTACHMENT_RULE_MANAGE',3:'ATTACHMENT_RULE_MANAGE',4:'ATTACHMENT_FILE_LEDGER_READ',5:'ATTACHMENT_FILE_LEDGER_READ',9:'ATTACHMENT_RULE_MANAGE'})
group('attendance-records.attendance-page',{5:'ATTENDANCE_READ',7:'ATTENDANCE_READ'})
group('attendance-records.attendance-record-drawer',{2:'ATTENDANCE_RECORD',3:None})
group('cache-management.cache-management-page',{2:'CACHE_REVIEW',4:'CACHE_MANAGE',7:'CACHE_MANAGE'})
group('dashboard.dashboard-page',{1:None,3:None,4:None,5:None,6:None,7:None})
group('dashboard.parent-account-lifecycle-panel',{i:'PARENT_ACCOUNT_LIFECYCLE_MANAGE' for i in range(1,10)})
group('dictionaries.dictionary-management-page',{2:'DICTIONARY_MANAGE',4:'DICTIONARY_MANAGE',5:'DICTIONARY_MANAGE',7:'DICTIONARY_MANAGE'})
group('exception-reports.exception-report-page',{3:'EXCEPTION_REPORT_READ',5:'EXCEPTION_REPORT_READ',8:None})
group('export-jobs.export-job-management-page',{2:'EXPORT_JOB_READ',3:'EXPORT_JOB_READ',4:'EXPORT_JOB_READ'})
group('export-jobs.export-job-review-panel',{2:'EXPORT_SENSITIVE_REVIEW',3:'EXPORT_SENSITIVE_REVIEW'})
group('feature-management.feature-management-page',{1:None,6:'FEATURE_TOGGLE_REVIEW',7:'FEATURE_TOGGLE_MANAGE',8:'FEATURE_TOGGLE_REVIEW',9:None})
group('growth-points.growth-point-page',{1:None,4:'GROWTH_POINT_CORRECT_CHILD',6:'GROWTH_POINT_CORRECT_CHILD'})
group('growth-reviews.create-growth-review-export',{1:'EXPORT_JOB_CREATE',2:None})
group('growth-reviews.growth-review-export-history',{1:'EXPORT_JOB_READ',3:'EXPORT_JOB_READ',4:'EXPORT_JOB_READ'})
group('growth-reviews.growth-review-page',{2:'GROWTH_REVIEW_SUPPLEMENT_CHILD',4:'GROWTH_REVIEW_SUPPLEMENT_CHILD'})
group('iam.iam-management-page',{7:'IAM_ROLE_CREATE',10:None,11:None})
group('import-export-templates.import-export-template-management-page',{1:'IMPORT_EXPORT_TEMPLATE_READ',2:'IMPORT_EXPORT_TEMPLATE_READ',3:'IMPORT_EXPORT_TEMPLATE_MANAGE',4:'IMPORT_EXPORT_TEMPLATE_MANAGE',7:'IMPORT_EXPORT_TEMPLATE_READ',9:None,10:'IMPORT_EXPORT_TEMPLATE_MANAGE',11:None,12:None,13:None,14:None})
group('import-jobs.import-job-management-page',{1:'IMPORT_JOB_READ',2:'IMPORT_JOB_READ',5:'IMPORT_JOB_READ',7:None,9:'STUDENT_IMPORT_EXECUTE',10:'STUDENT_IMPORT_CREDENTIAL_DOWNLOAD'})
group('interface-services.interface-service-management-page',{1:'INTERFACE_SERVICE_READ',2:'INTERFACE_SERVICE_MANAGE',3:'INTERFACE_SERVICE_MANAGE',5:'INTERFACE_SERVICE_REVIEW',9:'INTERFACE_SERVICE_MANAGE'})
group('learning-tasks.learning-task-editor-drawer',{1:'LEARNING_TASK_TEMPLATE_MANAGE_PERSONAL',2:'LEARNING_TASK_CREATE',3:None,4:None})
group('learning-tasks.learning-task-management-page',{1:'LEARNING_TASK_COPY_PREVIOUS_DAY',2:'LEARNING_TASK_TEMPLATE_READ',3:'LEARNING_TASK_PUBLISH',6:'LEARNING_TASK_READ_MANAGED'})
M.update({'learning-tasks.draft.edit':'LEARNING_TASK_CREATE','learning-tasks.progress':'LEARNING_TASK_PROGRESS_READ','learning-tasks.recurrence.stop':'LEARNING_TASK_PUBLISH'})
group('learning-tasks.previous-day-task-copy-modal',{1:None})
group('learning-tasks.task-review-drawer',{1:None,3:'TASK_ASSIGNMENT_REVIEW',4:'TASK_ASSIGNMENT_REVIEW'})
group('learning-tasks.task-review-queue',{2:'TASK_ASSIGNMENT_REVIEW'})
M.update({'learning-tasks.template.select':None,'learning-tasks.template.edit':'LEARNING_TASK_TEMPLATE_MANAGE_PERSONAL','learning-tasks.template.up':'LEARNING_TASK_TEMPLATE_MANAGE_PERSONAL','learning-tasks.template.down':'LEARNING_TASK_TEMPLATE_MANAGE_PERSONAL','learning-tasks.template.delete':'LEARNING_TASK_TEMPLATE_MANAGE_PERSONAL'})
group('learning-tasks.task-template-library-modal',{1:None,2:'LEARNING_TASK_TEMPLATE_MANAGE_PERSONAL'})
group('menu-management.menu-management-page',{3:'MENU_READ',5:'MENU_MANAGE',6:'MENU_MANAGE',7:'MENU_MANAGE',8:'MENU_MANAGE',9:'MENU_MANAGE'})
group('organizations.class-management-panel',{1:'CLASS_UPDATE',2:'CLASS_STATUS_CHANGE',7:['CLASS_CREATE','CLASS_UPDATE']})
group('organizations.organization-change-review-panel',{2:'ORG_NODE_CHANGE_REVIEW',5:'ORG_NODE_CHANGE_REVIEW'})
group('organizations.organization-management-page',{1:'ORG_TYPE_CREATE',2:'ORG_NODE_CREATE',4:'ORG_NODE_UPDATE',5:'ORG_NODE_UPDATE',6:'ORG_NODE_CHANGE_SUBMIT',7:'ORG_NODE_CHANGE_SUBMIT',8:'ORG_NODE_CHANGE_SUBMIT',10:'ORG_NODE_CHANGE_SUBMIT',11:'STUDENT_ORGANIZATION_MANAGE',12:'PARENT_MOBILE_MANUAL_RECOVERY_MANAGE',13:'STUDENT_ACCOUNT_CANCELLATION_MANAGE',14:'TEACHER_CLASS_ASSIGN',16:'ORG_TYPE_CREATE',18:'ORG_NODE_CREATE'})
group('organizations.parent-mobile-manual-recovery-drawer',{1:'PARENT_MOBILE_MANUAL_RECOVERY_MANAGE'})
M['PARENT_ACCOUNT_LIFECYCLE_MANAGE']='PARENT_MOBILE_MANUAL_RECOVERY_MANAGE'
group('organizations.student-account-cancellation-drawer',{2:'STUDENT_ACCOUNT_CANCELLATION_MANAGE'})
group('organizations.student-organization-lifecycle-drawer',{2:'STUDENT_ORGANIZATION_MANAGE'})
group('organizations.teacher-class-assignment-drawer',{2:'TEACHER_CLASS_ASSIGN',3:'TEACHER_CLASS_ASSIGN'})
group('rewards.reward-management-page',{2:'REWARD_CREATE',4:'REWARD_UPDATE',5:'REWARD_UPDATE',6:'REWARD_DELETE',8:'REWARD_EXCHANGE_REVIEW',9:'REWARD_EXCHANGE_REVIEW',11:['REWARD_CREATE','REWARD_UPDATE'],13:'REWARD_EXCHANGE_REVIEW'})
group('student-login.student-login-management-page',{5:None,6:'STUDENT_LOGIN_QR_CREATE'})
group('system-tasks.system-task-workbench',{3:'SYSTEM_TASK_READ',4:None,5:None})
group('teachers.teacher-management-page',{4:'TEACHER_READ',5:'TEACHER_CLASS_ASSIGN',9:['TEACHER_CREATE','TEACHER_UPDATE','TEACHER_PASSWORD_RESET','TEACHER_BATCH_MANAGE']})
group('users.user-management-page',{3:'IAM_USER_LIST',5:'IAM_USER_CREATE'})
M.update({'teachers.status.enabled':'TEACHER_STATUS_CHANGE','teachers.status.disabled':'TEACHER_STATUS_CHANGE','teachers.status.locked':'TEACHER_STATUS_CHANGE','rank-preferences.anonymous-rank-withdrawal-page.2':None,'GROWTH_REVIEW_PDF_EXPORT':'EXPORT_JOB_CREATE'})
confirms={
'exception-reports.exception-report-page.modal.1':'EXCEPTION_REPORT_CREATE',
'export-jobs.create-export-job-modal.modal.1':'EXPORT_JOB_CREATE','export-jobs.export-job-review-panel.modal.1':'EXPORT_SENSITIVE_REVIEW',
'learning-tasks.batch-publish-result-modal.modal.1':None,'learning-tasks.learning-task-management-page.confirm.1':'LEARNING_TASK_PUBLISH','learning-tasks.learning-task-management-page.confirm.2':'LEARNING_TASK_PUBLISH','learning-tasks.learning-task-management-page.confirm.3':'LEARNING_TASK_PUBLISH',
'learning-tasks.task-defer-queue.modal.1':'TASK_ASSIGNMENT_DEFER','learning-tasks.task-review-drawer.confirm.1':'TASK_ASSIGNMENT_REVIEW','learning-tasks.task-review-drawer.confirm.2':'TASK_ASSIGNMENT_REVIEW','learning-tasks.task-template-library-modal.confirm.1':'LEARNING_TASK_TEMPLATE_MANAGE_PERSONAL',
'organizations.class-management-panel.confirm.1':'CLASS_STATUS_CHANGE','organizations.organization-management-page.confirm.1':'ORG_NODE_UPDATE','organizations.student-account-cancellation-drawer.confirm.1':'STUDENT_ACCOUNT_CANCELLATION_MANAGE','organizations.student-organization-lifecycle-drawer.confirm.1':'STUDENT_ORGANIZATION_MANAGE',
'parent-relationships.parent-relationship-page.confirm.1':'SECONDARY_PARENT_UNBIND','parent-relationships.parent-relationship-page.confirm.2':'PRIMARY_PARENT_SELF_UNBIND','parent-relationships.parent-relationship-page.modal.1':['SECONDARY_PARENT_INVITE_CREATE','PRIMARY_PARENT_TRANSFER_CREATE'],
'student-login.student-login-management-page.confirm.1':'STUDENT_WECHAT_UNBIND','import-export-templates.import-export-template-management-page.modal.1':'IMPORT_EXPORT_TEMPLATE_MANAGE','import-jobs.import-job-management-page.confirm.1':'STUDENT_IMPORT_CREDENTIAL_DOWNLOAD','import-jobs.import-job-management-page.modal.1':'IMPORT_JOB_CREATE','import-jobs.import-job-management-page.modal.2':'STUDENT_IMPORT_EXECUTE'}
M.update({k+'.ok':v for k,v in confirms.items()})
M.update({'menu-management.batch-buttons.open':'MENU_MANAGE','menu-management.batch-buttons.save':'MENU_MANAGE','menu-management.batch-buttons.add':None,'menu-management.batch-buttons.remove':None,'iam.role-permission-tree.save':'IAM_ROLE_PERMISSION_GRANT','iam.role-permission-tree.retry':None,'iam.role-row.authorize':'IAM_ROLE_PERMISSION_GRANT','iam.role-row.users':'IAM_USER_ROLE_ASSIGN','iam.role-users.query':'IAM_USER_ROLE_ASSIGN','iam.role-users.save':'IAM_USER_ROLE_ASSIGN','users.row.authorize':'IAM_USER_PERMISSION_CONFIGURE','users.permission-tree.save':'IAM_USER_PERMISSION_CONFIGURE','users.permission-tree.retry':None,'iam.user-permission-tree.save':'IAM_USER_PERMISSION_CONFIGURE','iam.user-permission-tree.retry':None,'users.create-user.save':'IAM_USER_CREATE','organizations.create-user.save':'IAM_USER_CREATE','organizations.members.create':'IAM_USER_CREATE','iam.directory.query':None,'organizations.node.add-child':'ORG_NODE_CREATE','menu-management.node.add-child':'MENU_MANAGE','menu-management.node.delete':'MENU_MANAGE','users.reset-password.save':'IAM_USER_PASSWORD_SET'})
M.update(json.loads(Path('docs/auth-canonical-organization-map.json').read_text(encoding='utf-8'))['actionKeyToCanonicalPermission'])
aux=Path('lingdong-bansui-web/src/app/auxiliaryActions.ts').read_text(encoding='utf-8');M.update({x:None for x in re.findall(r'"([^"]+)"',aux)})
# Explicit controller evidence for each permission literal or local constant.
E={}
for p in Path('lingdong-bansui-server/src/main/java').rglob('*Controller.java'):
 s=p.read_text(encoding='utf-8');constants=dict(re.findall(r'(?:String)\s+(\w+)\s*=\s*"([A-Z][A-Z0-9_]*)"',s))
 for m in re.finditer(r'@RequirePermission\((.*?)\)',s,re.S):
  codes=re.findall(r'"([A-Z][A-Z0-9_]*)"',m.group(1));codes += [constants[x] for x in re.findall(r'\b[A-Z_]+\b',m.group(1)) if x in constants]
  for code in codes:E.setdefault(code,[]).append(str(p).replace('\\','/')+':'+str(s[:m.start()].count('\n')+1))
# Export create checks service/action dispatch and annotation on specialized export creation.
unknown=[x['code'] for x in inv['catalog'] if x['code'] not in M]
invalid=[(k,c) for k,v in M.items() for c in (v if isinstance(v,list) else [v]) if c and c not in E]
print('unmapped',unknown);print('unverified canonical',invalid)
rows=[]
for old,canonical in M.items():
 info=next((x for x in inv['catalog'] if x['code']==old),None)
 rows.append({'oldActionKey':old,'canonicalPermission':canonical,'source':info['source'] if info else None,'label':info['label'] if info else None,'kind':'contextual-api' if isinstance(canonical,list) else 'api' if canonical else 'local-or-self-service','controllerEvidence':{c:E.get(c,[]) for c in (canonical if isinstance(canonical,list) else [canonical]) if c}})
Path('docs/auth-canonical-ui-map.json').write_text(json.dumps({'version':1,'actionKeyToCanonicalPermission':M,'modalPrefixToCanonicalPermission':confirms,'actions':rows,'unmapped':unknown,'unverifiedCanonical':invalid},ensure_ascii=False,indent=2),encoding='utf-8')
