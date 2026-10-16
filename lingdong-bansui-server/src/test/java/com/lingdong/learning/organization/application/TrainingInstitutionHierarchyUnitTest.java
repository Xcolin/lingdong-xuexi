package com.lingdong.learning.organization.application;

import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TrainingInstitutionHierarchyUnitTest {
 private Organization node(long id, Long parent, String type) {
  return Organization.create(id,parent,parent==null?"ROOT":"PARENT:"+parent,"NODE_"+id,"节点",type,"/NODE_"+id+"/",0,null);
 }
 @Test void trainingRootSupportsDirectGradesAndCampusGrades() {
  var institution=node(1,null,"TRAINING_INSTITUTION");
  assertThatCode(() -> OrganizationHierarchy.requireParent("GRADE",institution)).doesNotThrowAnyException();
  assertThatCode(() -> OrganizationHierarchy.requireParent("CAMPUS",institution)).doesNotThrowAnyException();
  assertThatThrownBy(() -> OrganizationHierarchy.requireParent("CLASS",institution)).isInstanceOf(IllegalArgumentException.class);
  assertThatCode(() -> OrganizationHierarchy.requireParent("CLASS",node(2,1L,"GRADE"))).doesNotThrowAnyException();
 }
 @Test void educationalRootsCannotNestEvenThroughOtherOrganizationTypes() {
  var mapper=mock(OrganizationMapper.class);
  var institution=node(1,null,"TRAINING_INSTITUTION");
  var branch=node(2,1L,"CUSTOM"); when(mapper.findById(1L)).thenReturn(institution);
  for(String root:java.util.List.of("SCHOOL","TRAINING_INSTITUTION")) {
   assertThatThrownBy(() -> OrganizationHierarchy.requireParent(root,institution,mapper)).isInstanceOf(IllegalArgumentException.class);
   assertThatThrownBy(() -> OrganizationHierarchy.requireParent(root,branch,mapper)).isInstanceOf(IllegalArgumentException.class);
  }
 }
 @Test void legacySchoolAncestorContractFindsTrainingRootAndStopsCycles() {
  var mapper=mock(OrganizationMapper.class);
  var institution=node(1,null,"TRAINING_INSTITUTION"); var grade=node(2,1L,"GRADE"); var cls=node(3,2L,"CLASS");
  when(mapper.findById(1L)).thenReturn(institution); when(mapper.findById(2L)).thenReturn(grade);
  assertThat(OrganizationHierarchy.schoolOf(cls,mapper)).isEqualTo(institution);
  assertThat(OrganizationHierarchy.ancestor(cls,"SCHOOL",mapper)).isEqualTo(institution);
  var cycle=node(4,4L,"GRADE"); when(mapper.findById(4L)).thenReturn(cycle);
  assertThat(OrganizationHierarchy.schoolOf(cycle,mapper)).isNull();
 }
}
