package com.lingdong.learning.teacher.application;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import com.lingdong.learning.teacher.infrastructure.persistence.TeacherDirectoryCriteria;
import com.lingdong.learning.user.domain.UserStatus;
import java.util.*;
class TeacherCampusSqlContractTest {
 @Test void bothPageAndCountBindCampusPathWithoutInterpolatingIt() throws Exception {
  var criteria=TeacherDirectoryCriteria.class.getConstructor(String.class,Long.class,Long.class,UserStatus.class,boolean.class,List.class,int.class,int.class,String.class).newInstance(null,2L,null,null,false,List.of("/2/3/"),20,0,"/2/3/");
  var config=new org.apache.ibatis.session.Configuration();try(var in=getClass().getClassLoader().getResourceAsStream("mapper/teacher/TeacherManagementMapper.xml")){new org.apache.ibatis.builder.xml.XMLMapperBuilder(in,config,"teacher-campus-sql",config.getSqlFragments()).parse();}
  for(String method:List.of("findPage","count")){var sql=config.getMappedStatement("com.lingdong.learning.teacher.infrastructure.persistence.TeacherManagementMapper."+method).getBoundSql(Map.of("criteria",criteria));assertThat(sql.getSql()).contains("campus_filter").doesNotContain("/2/3/");assertThat(sql.getParameterMappings()).anyMatch(p->p.getProperty().equals("criteria.campusPath"));}
 }
}
