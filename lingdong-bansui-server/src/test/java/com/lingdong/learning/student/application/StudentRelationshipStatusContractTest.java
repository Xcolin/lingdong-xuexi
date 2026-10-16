package com.lingdong.learning.student.application;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class StudentRelationshipStatusContractTest {
 @Test void statusTravelsWithoutInventingLegacyValues() throws Exception {
  var rowType=Class.forName("com.lingdong.learning.student.infrastructure.persistence.StudentOrganizationSummaryRow");
  var old=rowType.getConstructor(Long.class,String.class,String.class,Long.class,String.class,Long.class,String.class).newInstance(1L,"child",null,2L,"school",3L,"class");
  assertThat(rowType.getMethod("studentStatus").invoke(old)).isNull();
  var actual=rowType.getConstructor(Long.class,String.class,String.class,Long.class,String.class,Long.class,String.class,String.class).newInstance(1L,"child",null,2L,"school",3L,"class","ENABLED");
  assertThat(rowType.getMethod("studentStatus").invoke(actual)).isEqualTo("ENABLED");
 }
 @Test void realMybatisResultMapReadsDatabaseStatus() throws Exception {
  var ds=new org.h2.jdbcx.JdbcDataSource();ds.setURL("jdbc:h2:mem:relationship_status;DB_CLOSE_DELAY=-1");
  var config=new org.apache.ibatis.session.Configuration(new org.apache.ibatis.mapping.Environment("test",new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(),ds));
  String resource="mapper/student/StudentOrganizationMapper.xml";
  try(var input=getClass().getClassLoader().getResourceAsStream(resource)) {
   new org.apache.ibatis.builder.xml.XMLMapperBuilder(input,config,resource,config.getSqlFragments()).parse();
  }
  String sql="SELECT 1 AS student_id,'child' AS student_name,NULL AS grade_code,2 AS enrollment_organization_id,'school' AS enrollment_organization_name,3 AS current_class_organization_id,'class' AS current_class_organization_name,'DISABLED' AS student_status,'A001' AS student_number";
  var result=config.getResultMap("com.lingdong.learning.student.infrastructure.persistence.StudentOrganizationMapper.studentOrganizationSummaryMap");
  config.addMappedStatement(new org.apache.ibatis.mapping.MappedStatement.Builder(config,"statusProbe",new org.apache.ibatis.builder.StaticSqlSource(config,sql),org.apache.ibatis.mapping.SqlCommandType.SELECT).resultMaps(java.util.List.of(result)).build());
  try(var session=new org.apache.ibatis.session.SqlSessionFactoryBuilder().build(config).openSession()) {
   com.lingdong.learning.student.infrastructure.persistence.StudentOrganizationSummaryRow row=session.selectOne("statusProbe");
   assertThat(row.studentStatus()).isEqualTo("DISABLED");assertThat(row.studentNumber()).isEqualTo("A001");assertThat(row.studentId()).isEqualTo(1L);
  }
 }
 @Test void academicNumberIsExplicitAndLegacyRowsRemainEmpty() throws Exception {
  var type=Class.forName("com.lingdong.learning.student.infrastructure.persistence.StudentOrganizationSummaryRow");
  var old=type.getConstructor(Long.class,String.class,String.class,Long.class,String.class,Long.class,String.class,String.class).newInstance(1L,"child",null,2L,"school",3L,"class","ENABLED");
  assertThat(type.getMethod("studentNumber").invoke(old)).isNull();
  var current=type.getConstructor(Long.class,String.class,String.class,Long.class,String.class,Long.class,String.class,String.class,String.class).newInstance(1L,"child",null,2L,"school",3L,"class","ENABLED","A001");
  assertThat(type.getMethod("studentNumber").invoke(current)).isEqualTo("A001");
 }
}
