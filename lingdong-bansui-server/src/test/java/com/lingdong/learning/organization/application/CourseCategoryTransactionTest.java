package com.lingdong.learning.organization.application;
import org.junit.jupiter.api.*;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.context.annotation.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.domain.OrganizationEffectiveStatus;
import com.lingdong.learning.dictionary.application.DictionaryQueryService;
import com.lingdong.learning.dictionary.domain.DictionaryItem;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringJUnitConfig(CourseCategoryTransactionTest.Config.class)
class CourseCategoryTransactionTest {
 @Configuration @EnableTransactionManagement static class Config {
 @Bean javax.sql.DataSource source(){return new DriverManagerDataSource("jdbc:h2:mem:course_category_tx;DB_CLOSE_DELAY=-1","sa","");}
 @Bean org.springframework.transaction.PlatformTransactionManager transactionManager(javax.sql.DataSource ds){return new DataSourceTransactionManager(ds);}
 @Bean JdbcTemplate jdbc(javax.sql.DataSource ds){return new JdbcTemplate(ds);}
 @Bean ClassManagementApplicationService classes(){return mock(ClassManagementApplicationService.class);}
 @Bean CourseCategoryDictionary dictionary(){var q=mock(DictionaryQueryService.class);when(q.findEnabledItems("FIXTURE_STAGE")).thenReturn(List.of(DictionaryItem.enabled(1L,11L,"S1","Stage",0,false)));when(q.findEnabledItems("FIXTURE_SUBJECT")).thenReturn(List.of(DictionaryItem.enabled(2L,12L,"A1","Subject",0,false)));return new CourseCategoryDictionary(q,"FIXTURE_STAGE","FIXTURE_SUBJECT");}
 @Bean CourseCategoryService service(ClassManagementApplicationService c,CourseCategoryDictionary d,JdbcTemplate j){return new CourseCategoryService(c,d,j,new com.fasterxml.jackson.databind.ObjectMapper());}
 }
 @Autowired CourseCategoryService service;@Autowired JdbcTemplate jdbc;@Autowired ClassManagementApplicationService classes;
 @BeforeEach void setup(){reset(classes);jdbc.execute("DROP TABLE IF EXISTS org_course_category_metadata");jdbc.execute("DROP TABLE IF EXISTS sys_organization");jdbc.execute("CREATE TABLE sys_organization(id BIGINT PRIMARY KEY)");var script=new org.springframework.core.io.ClassPathResource("db/migration/V128__course_category_metadata.sql");new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(script).execute(jdbc.getDataSource());}
 private Organization fixture(String rootType){var node=mock(Organization.class);when(node.id()).thenReturn(42L);when(node.effectiveStatus()).thenReturn(OrganizationEffectiveStatus.ENABLED);var root=mock(Organization.class);when(root.typeCode()).thenReturn(rootType);when(classes.ancestor(node,"SCHOOL")).thenReturn(root);when(classes.createGrade(null,10L,null,"Course",0)).thenAnswer(inv->{jdbc.update("INSERT INTO sys_organization(id) VALUES (42)");return node;});when(classes.listGrades(null,null)).thenReturn(List.of(node));return node;}
 @Test void persistsAndReadsRealMetadata(){fixture("TRAINING_INSTITUTION");var data=new CourseCategoryService.Metadata(List.of("S1"),"A1","description");service.create(null,10L,null,"Course",0,data);assertEquals(data,service.read(null,42L));verify(classes).createGrade(null,10L,null,"Course",0);}
 @Test void schoolMetadataRejectionRollsBackOrganizationCreation(){fixture("SCHOOL");assertThrows(IllegalArgumentException.class,()->service.create(null,10L,null,"Course",0,new CourseCategoryService.Metadata(List.of("S1"),"A1","")));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sys_organization",Integer.class));}
 @Test void metadataStorageFailureRollsBackOrganizationCreation(){fixture("TRAINING_INSTITUTION");jdbc.execute("ALTER TABLE org_course_category_metadata ADD CONSTRAINT fixture_reject CHECK(subject_code <> 'A1')");assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->service.create(null,10L,null,"Course",0,new CourseCategoryService.Metadata(List.of("S1"),"A1","")));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sys_organization",Integer.class));}
}
