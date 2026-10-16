package com.lingdong.learning.student.application;

import com.lingdong.learning.common.security.SelectedOrganizationContext;
import com.lingdong.learning.organization.domain.Organization;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.sql.DriverManager;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class MiniappGrowthReviewFamilyScopeTest {
 @Test void sqlAllowsOnlySelectedEffectiveFamilyActualAdminAndPreservesGuardianWithoutSelection() throws Exception {
  var config=new Configuration();String resource="mapper/student/ParentStudentMapper.xml";
  try(var in=Resources.getResourceAsStream(resource)){new XMLMapperBuilder(in,config,resource,config.getSqlFragments()).parse();}
  try(var c=DriverManager.getConnection("jdbc:h2:mem:growth_family_scope;MODE=MySQL")) {
   var s=c.createStatement();
   s.execute("create table edu_student(id bigint,student_user_id bigint,student_name varchar(30))");
   s.execute("create table edu_parent_student(student_id bigint,parent_user_id bigint,status varchar(30))");
   s.execute("create table sys_user_organization(user_id bigint,organization_id bigint)");
   s.execute("create table sys_organization(id bigint,organization_type varchar(30),status varchar(30),effective_status varchar(30))");
   s.execute("create table sys_organization_admin(user_id bigint,organization_id bigint)");
   s.execute("insert into edu_student values(10,100,'A'),(20,200,'B'),(30,300,'guardian')");
   s.execute("insert into sys_organization values(88,'FAMILY','ENABLED','ENABLED'),(99,'FAMILY','ENABLED','ENABLED'),(77,'SCHOOL','ENABLED','ENABLED')");
   s.execute("insert into sys_user_organization values(100,88),(200,99),(1,99)");
   s.execute("insert into sys_organization_admin values(1,88)");
   s.execute("insert into edu_parent_student values(30,1,'ACTIVE')");
   try {
    assertThat(count(c,config,1L)).isEqualTo(1);
    select(88L);assertThat(count(c,config,1L)).isEqualTo(1);
    assertThat(exists(c,config,1L,10L)).isTrue();assertThat(exists(c,config,1L,20L)).isFalse();
    select(99L);assertThat(count(c,config,1L)).isZero(); // ordinary family membership is insufficient
    select(77L);assertThat(count(c,config,1L)).isZero();
    select(88L);s.execute("update sys_organization set effective_status='DISABLED' where id=88");assertThat(count(c,config,1L)).isZero();
    s.execute("update sys_organization set effective_status='ENABLED',status='DISABLED' where id=88");assertThat(count(c,config,1L)).isZero();
    s.execute("update sys_organization set status='ENABLED' where id=88");assertThat(count(c,config,2L)).isZero();
    s.execute("insert into edu_parent_student values(10,1,'ACTIVE')");assertThat(count(c,config,1L)).isEqualTo(1); // guardian + admin deduplicated
   } finally {RequestContextHolder.resetRequestAttributes();}
  }
 }
 private void select(long id) {
  var request=new MockHttpServletRequest();request.setAttribute(SelectedOrganizationContext.ATTRIBUTE,Organization.create(id,null,null,"test","test","FAMILY","/",0,null));
  RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
 }
 private long count(java.sql.Connection c,Configuration config,long parent)throws Exception {
  return execute(c,config,"findMiniappReviewChildren",Map.of("parentUserId",parent),false);
 }
 private boolean exists(java.sql.Connection c,Configuration config,long parent,long student)throws Exception {
  return execute(c,config,"existsMiniappReviewChild",Map.of("parentUserId",parent,"studentId",student),true)==1;
 }
 private long execute(java.sql.Connection c,Configuration config,String method,Map<String,Long> params,boolean scalar)throws Exception {
  var st=config.getMappedStatement("com.lingdong.learning.student.infrastructure.persistence.ParentStudentMapper."+method);var bound=st.getBoundSql(params);
  try(var p=c.prepareStatement(bound.getSql())){new DefaultParameterHandler(st,params,bound).setParameters(p);try(var r=p.executeQuery()){if(scalar){r.next();return r.getBoolean(1)?1:0;}long count=0;while(r.next())count++;return count;}}
 }
}
