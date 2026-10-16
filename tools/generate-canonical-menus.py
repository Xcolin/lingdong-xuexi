"""Generate reviewed canonical menu SQL and client catalogs. Does not execute SQL."""
from pathlib import Path
import json
root=Path(__file__).resolve().parents[1]
res=root/'lingdong-bansui-server/src/main/resources'
model_path=res/'authorization-model.json'
if model_path.exists():
    canonical=json.loads(model_path.read_text(encoding='utf-8'))
    d={'pages':canonical['pages'],'actions':canonical['actions']}
    policy={'actions':canonical['policies']}
else:
    d=json.loads((root/'docs/auth-canonical-policy-draft.json').read_text(encoding='utf-8'))
    policy=json.loads((res/'authorization-action-policy.json').read_text(encoding='utf-8'))
modules=[('PERMISSION_MANAGEMENT','权限管理',['/iam','/users','/menu-management','/organizations']),('SYSTEM_SETTINGS','系统设置管理',['/dictionaries','/cache-management','/feature-management']),('IMPORT_EXPORT_MANAGEMENT','导入导出管理',['/import-export-templates','/import-jobs','/export-jobs']),('SYSTEM_OPERATIONS','系统运维',['/system-tasks','/interface-services','/attachment-management']),('EDUCATION_MANAGEMENT','教育管理',['/attendance-records','/learning-tasks','/exception-reports','/student-login','/parent-relationships','/teachers']),('GROWTH_MANAGEMENT','成长管理',['/growth-points','/rewards','/growth-reviews','/anonymous-ranks','/rank-preferences'])]
if model_path.exists():modules=[(m['code'],m['name'],m['routes']) for m in canonical['modules']]
q=lambda s:"'"+str(s).replace("'","''")+"'"
base=2201000000000000000
pages=d['pages']; page_codes={p['permissionCode'] for p in pages}
actions=[a for a in d['actions'] if a['code'] not in page_codes]
priority=lambda a:next((rank for terms,rank in [(['CREATE','SUBMIT'],10),(['UPDATE','MANAGE'],20),(['ASSIGN','ASSOCIATE','GRANT','CONFIGURE'],30),(['REVIEW'],40),(['STATUS','PASSWORD'],50),(['REMOVE','REVOKE','DELETE','UNBIND'],60),(['READ'],90)] if any(term in a['code'] for term in terms)),70)
actions.sort(key=lambda a:(next(i for i,p in enumerate(pages) if p['route']==a['route']),a['name'].split(' · ')[0],priority(a),a['code']))
# Actual source lookup is not a separate grant; actions are unique business API codes.
for a in actions:
 if a['code']=='REWARD_EXCHANGE_REVIEW':a.update(name='奖励兑换 · 审核兑换申请',label='审核兑换申请')
 if a['code']=='FEATURE_TOGGLE_MANAGE':a.update(name='功能开关 · 申请变更',label='申请变更')
 if a['code']=='FEATURE_TOGGLE_REVIEW':a.update(name='功能开关 · 审核变更',label='审核变更')
ids={p['permissionCode']:base+1000+i for i,p in enumerate(pages)}
ids.update({a['code']:base+2000+i for i,a in enumerate(actions)})
codes=list(ids); in_codes=','.join(q(c) for c in codes)
sql=['-- Generated canonical Web menu model. Only run through an explicitly authorized rebuild/migration.','UPDATE sys_permission SET parent_id=NULL;','UPDATE sys_menu SET parent_id=NULL;','DELETE FROM sys_menu;']
# Retain real mobile API resources; obsolete Web aliases and shadow grants are removed.
sql += [f"DELETE FROM sys_role_permission WHERE permission_id IN (SELECT id FROM sys_permission WHERE client_type='WEB' AND permission_code NOT IN ({in_codes}));",f"DELETE FROM sys_user_permission WHERE permission_id IN (SELECT id FROM sys_permission WHERE client_type='WEB' AND permission_code NOT IN ({in_codes}));",f"DELETE FROM sys_permission WHERE client_type='WEB' AND permission_code NOT IN ({in_codes});",f"UPDATE sys_permission SET client_type='MINIAPP' WHERE client_type='BOTH' AND permission_code NOT IN ({in_codes});"]
module_ids={route:base+100+i for i,(_,_,routes) in enumerate(modules) for route in routes}
for i,(code,name,routes) in enumerate(modules):sql.append(f"INSERT INTO sys_menu(id,code,name,type,sort_order,status,version,grantable) VALUES({base+100+i},{q(code)},{q(name)},'DIRECTORY',{(i+1)*100},'ENABLED',0,0);")
for i,p in enumerate(pages):
 code=p['permissionCode'];pid=ids[code];parent=module_ids.get(p['route']);parent_sql=str(parent) if parent else 'NULL'
 sql.append(f"INSERT INTO sys_permission(id,permission_code,permission_name,resource_type,client_type,status) VALUES({pid},{q(code)},{q(p['name'])},'PAGE','WEB','ENABLED') ON DUPLICATE KEY UPDATE permission_name=VALUES(permission_name),resource_type='PAGE',status='ENABLED';")
 sql.append(f"INSERT INTO sys_menu(id,code,name,type,parent_id,route,icon,permission_code,grantable,sort_order,status,version) VALUES({pid},{q(code)},{q(p['name'])},'PAGE',{parent_sql},{q(p['route'])},{q(p['icon'])},{q(code)},1,{i*100},'ENABLED',0);")
for i,a in enumerate(actions):
 code=a['code'];page=next(p for p in pages if p['route']==a['route']);pid=ids[code];parent=ids[page['permissionCode']]
 sql.append(f"INSERT INTO sys_permission(id,permission_code,permission_name,resource_type,client_type,status) VALUES({pid},{q(code)},{q(a['name'])},'BUTTON','WEB','ENABLED') ON DUPLICATE KEY UPDATE permission_name=VALUES(permission_name),resource_type='BUTTON',status='ENABLED';")
 sql.append(f"UPDATE sys_permission SET parent_id=(SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code={q(page['permissionCode'])}) parent_permission) WHERE permission_code={q(code)};")
 sql.append(f"INSERT INTO sys_menu(id,code,name,type,parent_id,permission_code,grantable,sort_order,status,version) VALUES({pid},{q(code)},{q(a['name'])},'BUTTON',{parent},{q(code)},1,{i*10},'ENABLED',0);")
(root/'docs/auth-canonical-menu-rebuild-draft.sql').write_text('\n'.join(sql)+'\n',encoding='utf-8')
(root/'docs/auth-canonical-menu-model.json').write_text(json.dumps({'pages':pages,'actions':actions,'policies':policy['actions']},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('Reviewed draft:',len(pages),'pages',len(actions),'buttons. SQL not executed.')

if '--publish' in __import__('sys').argv or '--catalogs-only' in __import__('sys').argv:
    model={'version':1,'modules':[dict(code=c,name=n,routes=r) for c,n,r in modules],'pages':[{**p,'code':p['permissionCode']} for p in pages],'actions':actions,'policies':policy['actions']}
    (res/'authorization-model.json').write_text(json.dumps(model,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    (res/'web-menu-actions.json').write_text(json.dumps(actions,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    ts='/** Generated from server authorization-model.json; edit that model, then regenerate. */\n'
    ts+='export const menuPageCatalog = '+json.dumps(model['pages'],ensure_ascii=False,indent=2)+' as const;\n\n'
    ts+='export const menuActionCatalog = '+json.dumps(actions,ensure_ascii=False,indent=2)+' as const;\n'
    (root/'lingdong-bansui-web/src/app/menuCatalog.ts').write_text(ts,encoding='utf-8')
    if '--publish' in __import__('sys').argv:
        (res/'db/migration/V118__canonical_web_authorization_model.sql').write_text('\n'.join(sql)+'\n',encoding='utf-8')
    print('Published catalogs. Historical migration is unchanged with --catalogs-only. No SQL executed.')
