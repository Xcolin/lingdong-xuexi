package com.lingdong.learning.iam.application;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserOrganizationMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest @ActiveProfiles("test") @Transactional
class UserDirectoryScopePersistenceTest {
 @Autowired JdbcTemplate jdbc;
 @Autowired UserMapper users;
 @Autowired UserOrganizationMapper memberships;
 @Autowired org.apache.ibatis.session.SqlSession sqlSession;
 @Test void identitiesAndIdentityFiltersRespectVisibleOrganizations() {
  long family=1874244142494700401L,school=family+1,user=family+10;
  for(long id:List.of(family,school))jdbc.update("insert into sys_organization(id,organization_code,organization_name,organization_type,organization_path,status) values(?,?,?,'FAMILY',?,'ENABLED')",id,"identity_"+id,"identity_"+id,"/identity/"+id+"/");
  jdbc.update("insert into sys_user(id,username,display_name,user_type,status) values(?,?,'identities','PERSONAL','ENABLED')",user,"identity_"+user);
  jdbc.update("insert into sys_role(id,role_code,role_name,role_type,data_scope,built_in,status) select ?,'PARENT_ADMIN','家庭管理员','BUILT_IN','FAMILY',1,'ENABLED' where not exists(select 1 from sys_role where role_code='PARENT_ADMIN')",user+20);
  Long parent=jdbc.queryForObject("select id from sys_role where role_code='PARENT_ADMIN'",Long.class);
  Long teacher=jdbc.queryForObject("select id from sys_role where role_code='TEACHER'",Long.class);
  jdbc.update("insert into sys_user_organization(id,user_id,organization_id) values(?,?,?)",user+1,user,family);
  jdbc.update("insert into sys_user_role(id,user_id,role_id,organization_id,organization_scope_key) values(?,?,?,?,?)",user+2,user,parent,family,"ORG:"+family);
  jdbc.update("insert into sys_user_role(id,user_id,role_id,organization_id,organization_scope_key) values(?,?,?,?,?)",user+3,user,teacher,school,"ORG:"+school);
  assertEquals(2,memberships.findRoleIdentities(List.of(user),null).size());
  var visible=memberships.findRoleIdentities(List.of(user),List.of(family));
  assertEquals(1,visible.size());assertEquals("PARENT_ADMIN",visible.get(0).roleCode());
  assertEquals(String.valueOf(family),visible.get(0).organizationId());
  assertTrue(memberships.findRoleIdentities(List.of(user),List.of()).isEmpty());
  var hiddenTeacher=new UserDirectoryQuery("identity_"+user,null,null,null,0,20,List.of(family),"TEACHER");
  assertEquals(0,users.count(hiddenTeacher));assertTrue(users.findPage(hiddenTeacher).isEmpty());
  var parentFilter=new UserDirectoryQuery("identity_"+user,null,null,null,0,20,List.of(family),"PARENT_ADMIN");
  assertEquals(1,users.count(parentFilter));assertEquals(user,users.findPage(parentFilter).get(0).id());
 }
 @Test void actualMembershipRootsExpandOnlyToDescendantsAndMergeMultipleRoots() {
  long root=1874244142494700201L,child=root+1,sibling=root+2,other=root+3,actor=root+10;
  for(long id:List.of(root,child,sibling,other))jdbc.update("insert into sys_organization(id,organization_code,organization_name,organization_type,organization_path,status) values(?,?,?,'CLASS',?,'ENABLED')",id,"scope_"+id,"scope_"+id,id==root?"/scope-a/":id==child?"/scope-a/child/":id==sibling?"/scope-ab/":"/scope-other/");
  jdbc.update("insert into sys_user(id,username,display_name,user_type,status) values(?,?,'actor','ORGANIZATION','ENABLED')",actor,"scope_"+actor);
  jdbc.update("insert into sys_user_organization(id,user_id,organization_id) values(?,?,?)",actor+1,actor,root);
  assertEquals(java.util.Set.of(root,child),new java.util.HashSet<>(memberships.findDirectoryOrganizationIds(actor)));
  jdbc.update("insert into sys_user_organization(id,user_id,organization_id) values(?,?,?)",actor+2,actor,other);
  sqlSession.clearCache();
  assertEquals(java.util.Set.of(root,child,other),new java.util.HashSet<>(memberships.findDirectoryOrganizationIds(actor)));
 }
 @Test void allScopeAuditorDoesNotReceiveSystemAdministratorBypass() {
  long actor=1874244142494700301L;
  jdbc.update("insert into sys_user(id,username,display_name,user_type,status) values(?,?,'auditor','PLATFORM','ENABLED')",actor,"scope_"+actor);
  Long auditor=jdbc.queryForObject("select id from sys_role where role_code='SYS_AUDITOR'",Long.class);
  jdbc.update("insert into sys_user_role(id,user_id,role_id,organization_scope_key) values(?,?,?,'GLOBAL')",actor+1,actor,auditor);
  assertFalse(memberships.isEnabledSystemAdmin(actor));
  assertTrue(memberships.findDirectoryOrganizationIds(actor).isEmpty());
  Long admin=jdbc.queryForObject("select id from sys_role where role_code='SYS_ADMIN'",Long.class);
  jdbc.update("insert into sys_user_role(id,user_id,role_id,organization_scope_key) values(?,?,?,'GLOBAL')",actor+2,actor,admin);
  sqlSession.clearCache();
  assertTrue(memberships.isEnabledSystemAdmin(actor));
 }
 @Test void filterAndVisibilityMustMatchTheSameMembership() {
  long a=1874244142494700001L,b=a+1,parent=a+2,user=a+10;
  for(long id:List.of(a,b,parent)) jdbc.update("insert into sys_organization(id,organization_code,organization_name,organization_type,organization_path,status) values(?,?,?,'CLASS',?,'ENABLED')",id,"directory_"+id,"directory_"+id,id==parent?"/directory/":id==a?"/directory/a/":"/outside/b/");
  jdbc.update("insert into sys_user(id,username,display_name,user_type,status) values(?,?,'directory','FAMILY','ENABLED')",user,"directory_"+user);
  jdbc.update("insert into sys_user_organization(id,user_id,organization_id) values(?,?,?)",user+1,user,a);
  jdbc.update("insert into sys_user_organization(id,user_id,organization_id) values(?,?,?)",user+2,user,b);
  var outside=new UserDirectoryQuery("directory_"+user,null,null,b,0,20,List.of(a));
  assertEquals(0,users.count(outside)); assertTrue(users.findPage(outside).isEmpty());
  var ancestor=new UserDirectoryQuery("directory_"+user,null,null,parent,0,20,List.of(a));
  assertEquals(1,users.count(ancestor)); assertEquals(user,users.findPage(ancestor).get(0).id());
  var empty=new UserDirectoryQuery("directory_"+user,null,null,null,0,20,List.of());
  assertEquals(0,users.count(empty)); assertTrue(users.findPage(empty).isEmpty());
  assertEquals(List.of("directory_"+a),memberships.findScopedNamesByUserIds(List.of(user),List.of(a)).stream().map(UserOrganizationMapper.OrganizationName::name).toList());
 }
}
