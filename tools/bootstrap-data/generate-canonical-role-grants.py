"""Generate opt-in demo role grants from the current canonical model; never connect to a database."""
import json
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
MODEL = ROOT / "lingdong-bansui-server/src/main/resources/authorization-model.json"
OUTPUT = Path(__file__).parent / "migrations/V3__canonical_role_grants.sql"
MATRIX = ROOT / "docs/auth-demo-role-matrix.json"
model = json.loads(MODEL.read_text(encoding="utf-8"))
codes = {row["code"] for section in ("pages", "actions") for row in model[section]}
policies = {row["code"]: row for row in model["policies"]}
metadata = {row["code"]: row for section in ("pages", "actions") for row in model[section]}
def items(value):
 return [value] if isinstance(value,str) else list(value or [])
def dependencies(code):
 seen = set()
 def visit(current):
  if current in seen: return
  seen.add(current)
  for row in (metadata.get(current,{}),policies.get(current,{})):
   for needed in items(row.get("requiredReads")) + items(row.get("impliedReads")):
    visit(needed)
  page = metadata.get(current,{}).get("pagePermissionCode")
  if page and page != current: visit(page)
 visit(code)
 return sorted(seen)
def feature_groups(code):
 # Each dependency retains its own contextual OR group; unrelated groups remain ANDed.
 groups = []
 for dependency in dependencies(code):
  features = sorted(set(items(metadata.get(dependency,{}).get("features")) + items(policies.get(dependency,{}).get("features"))))
  ordinary = [f for f in features if ":" not in f]
  contextual = [f.split(":",1)[1] for f in features if ":" in f]
  groups.extend([[f] for f in ordinary])
  if contextual: groups.append(contextual)
 return sorted({tuple(sorted(group)) for group in groups})
roles = {
 "SYS_ADMIN": ("PLATFORM", "ALL"), "SYS_AUDITOR": ("PLATFORM", "ALL"),
 "ORG_ADMIN": ("ORGANIZATION", "SCHOOL"), "TEACHER": ("ORGANIZATION", "CLASS"),
 "PARENT": ("FAMILY", "SELF"), "PARENT_ADMIN": ("FAMILY", "FAMILY"),
 "STUDENT": ("STUDENT", "SELF"), "ORG_MEMBER": ("ORGANIZATION", "SELF"), "PLATFORM_USER": ("PLATFORM", "SELF")}
personal = {"LEARNING_TASK_TEMPLATE_MANAGE_PERSONAL", "LEARNING_TASK_COPY_PREVIOUS_DAY", "STUDENT_ENROLLMENT_REQUEST_CREATE", "ANONYMOUS_CLASS_RANK_READ", "ANONYMOUS_RANK_PREFERENCE_MANAGE"}
review = {"FEATURE_TOGGLE_REVIEW", "CACHE_REVIEW", "INTERFACE_SERVICE_REVIEW", "EXPORT_SENSITIVE_REVIEW", "ORG_NODE_CHANGE_REVIEW"}
teacher = {"DASHBOARD_READ", "ORG_NODE_READ", "CLASS_READ", "STUDENT_READ", "TEACHER_READ", "STUDENT_LOGIN_QR_CREATE", "ATTENDANCE_READ", "ATTENDANCE_RECORD", "EXCEPTION_REPORT_READ", "EXCEPTION_REPORT_CREATE", "EXCEPTION_REPORT_HANDLE", "LEARNING_TASK_READ_MANAGED", "LEARNING_TASK_CREATE", "LEARNING_TASK_PUBLISH", "LEARNING_TASK_PROGRESS_READ", "LEARNING_TASK_TEMPLATE_READ", "TASK_ASSIGNMENT_REVIEW", "TASK_ASSIGNMENT_DEFER"}
parent = personal | {"DASHBOARD_READ", "ORG_NODE_READ", "ATTENDANCE_READ", "EXCEPTION_REPORT_READ", "EXCEPTION_REPORT_CREATE", "LEARNING_TASK_READ_MANAGED", "LEARNING_TASK_CREATE", "LEARNING_TASK_PUBLISH", "LEARNING_TASK_PROGRESS_READ", "LEARNING_TASK_TEMPLATE_READ", "TASK_ASSIGNMENT_REVIEW", "TASK_ASSIGNMENT_DEFER", "STUDENT_READ", "STUDENT_LOGIN_QR_CREATE", "STUDENT_WECHAT_UNBIND", "EXPORT_JOB_CREATE", "EXPORT_JOB_READ"}
parent_admin = {"IAM_USER_CREATE", "IAM_USER_UPDATE", "IAM_USER_LIST", "IAM_USER_ROLE_ASSIGN", "ORG_MEMBER_READ", "ORG_MEMBER_ASSOCIATE", "ORG_MEMBER_REMOVE", "ORG_ADMIN_ASSIGN", "ORG_ADMIN_REVOKE"}
org_prefixes = ("IAM_USER_", "ORG_MEMBER_", "ORG_ADMIN_", "CLASS_", "TEACHER_", "STUDENT_", "LEARNING_TASK_", "TASK_ASSIGNMENT_", "ATTENDANCE_", "EXCEPTION_REPORT_", "IMPORT_JOB_", "EXPORT_JOB_")
org_extra = {"DASHBOARD_READ", "ORG_NODE_READ", "ORG_NODE_CREATE", "ORG_NODE_UPDATE", "ORG_NODE_CHANGE_SUBMIT", "PARENT_MOBILE_MANUAL_RECOVERY_MANAGE", "IMPORT_EXPORT_TEMPLATE_READ", "ATTACHMENT_FILE_LEDGER_READ"}
org_excluded = personal | {"IAM_USER_PERMISSION_CONFIGURE", "EXPORT_SENSITIVE_REVIEW", "ORG_NODE_CHANGE_REVIEW"}
def desired(role, code):
 family = policies.get(code, {}).get("allowedUserTypes") == ["FAMILY"]
 if role == "SYS_ADMIN": return not family and code not in personal
 if role == "SYS_AUDITOR": return not family and code not in personal and (code.endswith("_READ") or code in {"IAM_USER_LIST", "LEARNING_TASK_READ_MANAGED"} | review)
 if role == "ORG_ADMIN": return not family and code not in org_excluded and (code.startswith(org_prefixes) or code in org_extra)
 if role == "TEACHER": return code in teacher
 if role == "PARENT": return family or code in parent
 if role == "PARENT_ADMIN": return family or code in parent | parent_admin
 if role == "STUDENT": return False  # Existing authentication supports student MINIAPP sessions only.
 return code == "DASHBOARD_READ"
def compatible(role, code):
 policy = policies.get(code, {})
 user_type, scope = roles[role]
 if policy.get("client") == "MINIAPP": return False
 if "teacherProfile" in policy.get("actorPrerequisites",[]) and role != "TEACHER": return False
 if policy.get("allowedUserTypes") and user_type not in policy["allowedUserTypes"]: return False
 if policy.get("allowedRoleCodes") and role not in policy["allowedRoleCodes"]: return False
 kinds = policy.get("scopeKinds", [])
 # CHILD/SELF/INVITATION are authentic relationships rather than organization write boundaries.
 org_kinds = set(kinds) - {"CHILD", "SELF", "INVITATION"}
 if org_kinds and scope not in org_kinds: return False
 return True
def quote(value): return "'" + value.replace("'", "''") + "'"
def enabled_feature(feature):
 return "EXISTS(SELECT 1 FROM sys_feature_toggle f WHERE f.feature_code=" + quote(feature) + " AND f.scope_key='GLOBAL' AND f.status='ENABLED')"
sql = ["-- Generated from authorization-model.json by tools/bootstrap-data/generate-canonical-role-grants.py.",
 "-- Opt-in demo migration only. Does not enable features. Pure MINIAPP grants are preserved.",
 "-- BOTH permissions share a single grant row: canonical Web/BOTH grants are rebuilt together.",
 "DELETE rp FROM sys_role_permission rp JOIN sys_permission p ON p.id=rp.permission_id WHERE p.client_type IN ('WEB','BOTH');",
 "UPDATE sys_role SET data_scope=CASE role_code " + " ".join("WHEN " + quote(role) + " THEN " + quote(scope) for role, (_,scope) in roles.items()) + " END WHERE role_code IN (" + ",".join(map(quote,roles)) + ");",
 "CREATE TABLE seed_disabled_menu (id BIGINT PRIMARY KEY);",
 "INSERT INTO seed_disabled_menu(id) WITH RECURSIVE disabled AS (SELECT id FROM sys_menu WHERE status<>'ENABLED' UNION ALL SELECT child.id FROM sys_menu child JOIN disabled parent ON child.parent_id=parent.id) SELECT DISTINCT id FROM disabled;" ]
matrix = {}
serial = 2190000000000900000
for role in roles:
 selected = sorted(code for code in codes if desired(role,code) and compatible(role,code))
 matrix[role] = {"userType": roles[role][0], "dataScope": roles[role][1], "candidateCount": len(selected), "candidateCodes": selected, "webLoginApplicable": role != "STUDENT", "dependencyFeatures": {code: feature_groups(code) for code in selected}}
 sql.append("-- " + role + ": " + str(len(selected)) + " compatible candidates; enabled feature/menu conditions checked at execution.")
 for code in selected:
  serial += 1
  policy = policies.get(code,{})
  conditions = ["r.role_code="+quote(role), "r.status='ENABLED'", "p.permission_code="+quote(code), "p.client_type IN ('WEB','BOTH')", "p.status='ENABLED'", "NOT EXISTS(SELECT 1 FROM sys_menu m JOIN seed_disabled_menu disabled ON disabled.id=m.id WHERE m.permission_code=p.permission_code)"]
  for dependency in dependencies(code):
   if dependency != code:
    conditions.append("EXISTS(SELECT 1 FROM sys_permission dep WHERE dep.permission_code="+quote(dependency)+" AND dep.status='ENABLED' AND dep.client_type IN ('WEB','BOTH'))")
    conditions.append("NOT EXISTS(SELECT 1 FROM sys_menu m JOIN seed_disabled_menu disabled ON disabled.id=m.id WHERE m.permission_code="+quote(dependency)+")")
  for group in feature_groups(code):
   conditions.append("("+" OR ".join(enabled_feature(feature) for feature in group)+")")
  sql.append("INSERT INTO sys_role_permission(id,role_id,permission_id,effect) SELECT " + str(serial) + ",r.id,p.id,'ALLOW' FROM sys_role r CROSS JOIN sys_permission p WHERE " + " AND ".join(conditions) + ";")
sql.append("DROP TABLE seed_disabled_menu;")
OUTPUT.write_text("\n".join(sql)+"\n",encoding="utf-8")
MATRIX.parent.mkdir(exist_ok=True)
MATRIX.write_text(json.dumps({"modelVersion":model["version"],"roles":matrix,"limitations":["Counts are candidates, actual inserted grants depend on runtime feature/menu/permission state.","No feature is enabled by this migration. Conditions include the action, associated PAGE, and recursively required/implied READ dependencies.","Pure MINIAPP grants are untouched; shared BOTH rows are rebuilt according to Web matrix.","Ordinary PLATFORM/ORGANIZATION SELF identities get dashboard only; no organizational write actions. STUDENT has zero Web grants because student Web authentication is not supported.","Child/guardian/teacher membership, onboarding, domain state and target scope remain runtime conditions.","Student demo identity needs a genuine issued student credential through existing administrator API; no fabricated credential hash is seeded."]},ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
print(json.dumps({role:row["candidateCount"] for role,row in matrix.items()}))
