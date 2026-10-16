package com.lingdong.learning.common.security;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.student.infrastructure.persistence.ParentStudentMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.LearningTaskOptionMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest @ActiveProfiles("test") @Transactional
class SelectedOrganizationPersistenceTest {
 @Autowired JdbcTemplate jdbc; @Autowired ParentStudentMapper parents; @Autowired LearningTaskOptionMapper options;
 @AfterEach void cleanup(){RequestContextHolder.resetRequestAttributes();}
 @Test void actualFamilyMembershipSeparatesStudentsAcrossAllParentQueries(){
  jdbc.update("insert into sys_user(id,username,display_name,user_type,status) values (88001,'selected_parent','家长','FAMILY','ENABLED'),(88002,'selected_child_a','孩子A','STUDENT','ENABLED'),(88003,'selected_child_b','孩子B','STUDENT','ENABLED')");
  jdbc.update("insert into sys_organization(id,parent_scope_key,organization_code,organization_name,organization_type,organization_path,status,effective_status) values (88004,'ROOT','SEL_A','家庭A','FAMILY','/SEL_A/','ENABLED','ENABLED'),(88005,'ROOT','SEL_B','家庭B','FAMILY','/SEL_B/','ENABLED','ENABLED')");
  jdbc.update("insert into edu_student(id,student_name,grade_code,student_user_id,status) values (88006,'孩子A','GRADE_3',88002,'ENABLED'),(88007,'孩子B','GRADE_3',88003,'ENABLED')");
  jdbc.update("insert into edu_parent_student(id,parent_user_id,student_id,relation_role,status,primary_scope_key) values (88008,88001,88006,'PRIMARY_GUARDIAN','ACTIVE','PRIMARY'),(88009,88001,88007,'PRIMARY_GUARDIAN','ACTIVE','PRIMARY')");
  jdbc.update("insert into sys_user_organization(id,user_id,organization_id) values (88010,88002,88004),(88011,88003,88005)");
  assertThat(parents.findActiveStudentsByParent(88001L)).hasSize(2);
  MockHttpServletRequest request=new MockHttpServletRequest();request.setAttribute(SelectedOrganizationContext.ATTRIBUTE,Organization.create(88004L,null,"ROOT","SEL_A","家庭A","FAMILY","/SEL_A/",100,null));RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
  assertThat(parents.findActiveStudentsByParent(88001L)).extracting(x->x.studentId()).containsExactly(88006L);
  assertThat(parents.countActiveStudentsByParent(88001L)).isEqualTo(1);
  assertThat(parents.existsActiveByParentAndStudent(88001L,88007L)).isFalse();
  assertThat(parents.existsActivePrimaryByParentAndStudent(88001L,88006L)).isTrue();
  assertThat(options.findFamilyStudentOptions(88001L,null)).hasSize(1);
 }
}
