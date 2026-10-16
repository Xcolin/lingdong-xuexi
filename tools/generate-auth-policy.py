import json,re
from pathlib import Path
root=Path(__file__).resolve().parents[1]
res=root/'lingdong-bansui-server/src/main/resources'
old=json.loads((res/'web-menu-actions.json').read_text(encoding='utf-8-sig'))
map=json.loads((root/'docs/auth-canonical-ui-map.json').read_text(encoding='utf-8-sig'))['actionKeyToCanonicalPermission']
map.update(json.loads((root/'docs/auth-canonical-organization-map.json').read_text(encoding='utf-8-sig'))['actionKeyToCanonicalPermission'])
text=(root/'lingdong-bansui-web/src/app/menuCatalog.ts').read_text(encoding='utf-8-sig')
pages=json.loads(re.search(r'menuPageCatalog = (\[.*?\]) as const;',text,re.S).group(1))
reads=['DASHBOARD_READ','SYSTEM_TASK_READ','FEATURE_TOGGLE_READ','ATTENDANCE_READ','LEARNING_TASK_READ_MANAGED','EXCEPTION_REPORT_READ','GROWTH_POINT_READ_CHILD','REWARD_READ','GROWTH_REVIEW_READ_CHILD','ANONYMOUS_CLASS_RANK_READ','ANONYMOUS_RANK_PREFERENCE_MANAGE','STUDENT_READ','PARENT_RELATIONSHIP_READ','TEACHER_READ','IAM_USER_LIST','IAM_ROLE_READ','DICTIONARY_READ','CACHE_READ','INTERFACE_SERVICE_READ','ATTACHMENT_RULE_READ','IMPORT_EXPORT_TEMPLATE_READ','IMPORT_JOB_READ','EXPORT_JOB_READ','ORG_NODE_READ','MENU_READ']
# Validate these explicitly against controller/service evidence before use in migrations.
route_reads=dict(zip([p['route'] for p in pages],reads))
actions={}
for entry in old:
    mapped=map.get(entry['code'])
    if mapped is None: continue
    for code in mapped if isinstance(mapped,list) else [mapped]:
        if code in actions: continue
        actions[code]={**entry,'code':code}
family=json.loads((root/'docs/auth-canonical-family-map.json').read_text(encoding='utf-8-sig'))
meta={a['code']:a for a in family.get('actions',[])}
extra=[('CLASS_READ','班级管理 · 查询班级','/organizations'),('REWARD_EXCHANGE_READ','奖励兑换 · 查询兑换申请','/rewards'),('ORG_TYPE_READ','组织类型 · 查询组织类型','/organizations')]
for code,name,route in extra:
    actions.setdefault(code,dict(code=code,name=name,route=route,source='API controller',label=name.split(' · ')[-1]))

policy={'version':1,'pages':pages,'actions':[],'reviewStatus':'DRAFT_REQUIRES_API_VALIDATION'}
for page in pages: page['permissionCode']=route_reads[page['route']]
for code,a in actions.items():
    policy['actions'].append({**a,**meta.get(code,{}),'pagePermissionCode':route_reads.get(a['route']),'features':meta.get(code,{}).get('features',[])})
(root/'docs/auth-canonical-policy-draft.json').write_text(json.dumps(policy,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('Draft pages:',len(pages),'canonical actions:',len(actions))
