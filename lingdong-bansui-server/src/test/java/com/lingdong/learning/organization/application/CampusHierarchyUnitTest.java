package com.lingdong.learning.organization.application;
import com.lingdong.learning.organization.domain.Organization;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class CampusHierarchyUnitTest {
 private Organization node(String type) { return Organization.create(1L,null,"ROOT","NODE","节点",type,"/NODE/",0,null); }
 @Test void campusOnlyBelongsToSchoolAndContainsOnlyGrades() {
  assertThatCode(() -> OrganizationHierarchy.requireParent("CAMPUS",node("SCHOOL"))).doesNotThrowAnyException();
  assertThatCode(() -> OrganizationHierarchy.requireParent("GRADE",node("CAMPUS"))).doesNotThrowAnyException();
  for(String type:new String[]{"REGION","SCHOOL","CLASS","CAMPUS"}) assertThatThrownBy(() -> OrganizationHierarchy.requireParent(type,node("CAMPUS"))).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(() -> OrganizationHierarchy.requireParent("CAMPUS",null)).isInstanceOf(IllegalArgumentException.class);
 }

 @Test void preorderUsesWholeAncestorChainAndNumericIds() {
  var school=Organization.create(1L,null,"ROOT","ZZZ","school","SCHOOL","/ZZZ/",0,null);
  var direct=Organization.create(20L,1L,"PARENT:1","AAA","grade","GRADE","/ZZZ/AAA/",2,null);
  var campus=Organization.create(30L,1L,"PARENT:1","YYY","campus","CAMPUS","/ZZZ/YYY/",1,null);
  var grade=Organization.create(40L,30L,"PARENT:30","BBB","grade","GRADE","/ZZZ/YYY/BBB/",0,null);
  var cls=Organization.create(9L,40L,"PARENT:40","CCC","class","CLASS","/ZZZ/YYY/BBB/CCC/",0,null);
  var all=java.util.List.of(direct,cls,school,grade,campus);
  assertThat(all.stream().sorted(OrganizationTreeOrder.comparator(all)).map(Organization::id).toList()).containsExactly(1L,30L,40L,9L,20L);
 }
}
