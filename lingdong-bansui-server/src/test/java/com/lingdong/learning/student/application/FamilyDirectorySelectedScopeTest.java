package com.lingdong.learning.student.application;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import java.sql.DriverManager;import java.util.Map;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.mock.web.MockHttpServletRequest;
import com.lingdong.learning.common.security.SelectedOrganizationContext;
import com.lingdong.learning.organization.domain.Organization;
import static org.assertj.core.api.Assertions.*;import static org.mockito.Mockito.*;
class FamilyDirectorySelectedScopeTest {
 @Test void familyDirectoryUsesValidatedSelectedFamilyWithoutRequiringGuardianRelation() throws Exception {
  var config=new Configuration();for(String resource:new String[]{"mapper/student/ParentStudentMapper.xml","mapper/student/StudentOrganizationMapper.xml","mapper/student/StudentMapper.xml"})try(var in=Resources.getResourceAsStream(resource)){new XMLMapperBuilder(in,config,resource,config.getSqlFragments()).parse();}
  var statement=config.getMappedStatement("com.lingdong.learning.student.infrastructure.persistence.StudentMapper.count");
  var params=Map.of("query",new StudentDirectoryQuery(null,1L,false,false,true,0,20));
  try(var c=DriverManager.getConnection("jdbc:h2:mem:family_selected;MODE=MySQL")){var s=c.createStatement();
   s.execute("create table edu_student(id bigint,student_user_id bigint)");s.execute("create table sys_user_organization(user_id bigint,organization_id bigint)");s.execute("create table sys_organization(id bigint,organization_type varchar(30),status varchar(30),effective_status varchar(30))");s.execute("create table sys_organization_admin(user_id bigint,organization_id bigint)");s.execute("create table edu_student_organization(student_id bigint,organization_id bigint,relation_type varchar(30),status varchar(30))");
   s.execute("insert into edu_student values(10,100),(20,200)");s.execute("insert into sys_user_organization values(100,88),(200,99)");s.execute("insert into sys_organization values(88,'FAMILY','ENABLED','ENABLED'),(99,'FAMILY','ENABLED','ENABLED')");s.execute("insert into sys_organization_admin values(1,88),(1,99)");
   assertThat(count(c,statement,params)).isEqualTo(2);
   var request=new MockHttpServletRequest();var selected=mock(Organization.class);when(selected.id()).thenReturn(88L);when(selected.typeCode()).thenReturn("FAMILY");request.setAttribute(SelectedOrganizationContext.ATTRIBUTE,selected);RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
   try{assertThat(count(c,statement,params)).isEqualTo(1);when(selected.id()).thenReturn(77L);assertThat(count(c,statement,params)).isZero();}finally{RequestContextHolder.resetRequestAttributes();}
  }
 }
 private long count(java.sql.Connection c,org.apache.ibatis.mapping.MappedStatement statement,Map<String,StudentDirectoryQuery> params)throws Exception{var bound=statement.getBoundSql(params);try(var q=c.prepareStatement(bound.getSql())){new DefaultParameterHandler(statement,params,bound).setParameters(q);try(var r=q.executeQuery()){r.next();return r.getLong(1);}}}
}
