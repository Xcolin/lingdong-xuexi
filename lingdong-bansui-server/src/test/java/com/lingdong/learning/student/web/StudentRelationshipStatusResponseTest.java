package com.lingdong.learning.student.web;
import com.lingdong.learning.student.application.StudentOrganizationRelationshipSummary;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
class StudentRelationshipStatusResponseTest {
 @Test void responsePreservesRealStatusAndStringIdentifiers() {
  var summary=new StudentOrganizationRelationshipSummary(9007199254740993L,"child",null,2L,"school",3L,"class","SCHOOL",List.of(3L),List.of("class"),"DISABLED");
  var response=StudentOrganizationRelationshipSummaryResponse.from(summary);
  assertThat(response.studentStatus()).isEqualTo("DISABLED");assertThat(response.studentId()).isEqualTo("9007199254740993");
 }
 @Test void legacySummaryDoesNotInventNormalStatus() {
  var summary=new StudentOrganizationRelationshipSummary(1L,"child",null,2L,"school",3L,"class");
  assertThat(StudentOrganizationRelationshipSummaryResponse.from(summary).studentStatus()).isNull();
 }
 @Test void legacyResponseConstructorRemainsAvailable() throws Exception {
  var constructor=StudentOrganizationRelationshipSummaryResponse.class.getConstructor(String.class,String.class,String.class,String.class,String.class,String.class,String.class,String.class,List.class,List.class);
  var response=constructor.newInstance("1","child",null,"2","school","3","class","SCHOOL",List.of("3"),List.of("class"));
  assertThat(response.studentStatus()).isNull();
 }
 @Test void academicNumberComesFromExactEnrollmentSummary(){
  var summary=new StudentOrganizationRelationshipSummary(1L,"child",null,2L,"school",3L,"class","SCHOOL",List.of(3L),List.of("class"),"ENABLED","A001");
  var response=StudentOrganizationRelationshipSummaryResponse.from(summary);assertThat(response.studentNumber()).isEqualTo("A001");assertThat(response.enrollmentOrganizationId()).isEqualTo("2");
  assertThat(StudentOrganizationRelationshipSummaryResponse.from(new StudentOrganizationRelationshipSummary(1L,"child",null,2L,"school",3L,"class")).studentNumber()).isNull();
 }
}
