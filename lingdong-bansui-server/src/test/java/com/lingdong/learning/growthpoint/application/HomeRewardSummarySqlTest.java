package com.lingdong.learning.growthpoint.application;
import org.junit.jupiter.api.Test;import org.apache.ibatis.builder.xml.XMLMapperBuilder;import org.apache.ibatis.session.Configuration;import org.apache.ibatis.io.Resources;import java.util.Map;import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.*;
class HomeRewardSummarySqlTest {
@Test void actualMapperExcludesForeignSecondaryInactiveAndFinalStates() throws Exception {
var config=new Configuration();for(String resource:new String[]{"mapper/student/ParentStudentMapper.xml","mapper/growthpoint/GrowthRewardExchangeMapper.xml"}){try(var stream=Resources.getResourceAsStream(resource)){new XMLMapperBuilder(stream,config,resource,config.getSqlFragments()).parse();}}
var statement=config.getMappedStatement("com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthRewardExchangeMapper.countPendingByParent");
try(var connection=DriverManager.getConnection("jdbc:h2:mem:home_summary;MODE=MySQL")){var sql=connection.createStatement();sql.execute("create table growth_reward_exchange(student_id bigint,status varchar(40))");sql.execute("create table edu_student(id bigint,student_user_id bigint)");sql.execute("create table sys_user_organization(user_id bigint,organization_id bigint)");
sql.execute("create table edu_parent_student(student_id bigint,parent_user_id bigint,status varchar(30),relation_role varchar(30))");
sql.execute("insert into edu_parent_student values(10,1,'ACTIVE','PRIMARY_GUARDIAN'),(11,1,'ACTIVE','SECONDARY_GUARDIAN'),(12,2,'ACTIVE','PRIMARY_GUARDIAN'),(13,1,'INACTIVE','PRIMARY_GUARDIAN')");
sql.execute("insert into growth_reward_exchange values(10,'PENDING_APPROVAL'),(10,'PENDING_VERIFICATION'),(10,'VERIFIED'),(11,'PENDING_APPROVAL'),(12,'PENDING_APPROVAL'),(13,'PENDING_APPROVAL')");
for(String status:new String[]{"PENDING_APPROVAL","PENDING_VERIFICATION","VERIFIED"}){var bound=statement.getBoundSql(Map.of("parentUserId",1L,"status",status));try(var query=connection.prepareStatement(bound.getSql())){query.setString(1,status);query.setLong(2,1L);var result=query.executeQuery();result.next();assertThat(result.getLong(1)).isEqualTo(status.equals("VERIFIED")?0:1);}}
var request=new org.springframework.mock.web.MockHttpServletRequest();var selectedOrg=org.mockito.Mockito.mock(com.lingdong.learning.organization.domain.Organization.class);org.mockito.Mockito.when(selectedOrg.id()).thenReturn(99L);request.setAttribute(com.lingdong.learning.common.security.SelectedOrganizationContext.ATTRIBUTE,selectedOrg);org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(request));
try{sql.execute("insert into edu_student values(10,100)");sql.execute("insert into sys_user_organization values(100,88)");var bound=statement.getBoundSql(Map.of("parentUserId",1L,"status","PENDING_APPROVAL"));try(var query=connection.prepareStatement(bound.getSql())){query.setString(1,"PENDING_APPROVAL");query.setLong(2,1L);query.setLong(3,99L);var result=query.executeQuery();result.next();assertThat(result.getLong(1)).isZero();}sql.execute("insert into sys_user_organization values(100,99)");try(var query=connection.prepareStatement(bound.getSql())){query.setString(1,"PENDING_APPROVAL");query.setLong(2,1L);query.setLong(3,99L);var result=query.executeQuery();result.next();assertThat(result.getLong(1)).isEqualTo(1);}}finally{org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();}

}
}
}
