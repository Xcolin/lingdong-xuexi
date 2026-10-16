package com.lingdong.learning.learningtask.application;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.common.security.SelectedOrganizationContext;
import com.lingdong.learning.organization.application.OrganizationHierarchy;
/** Request selection narrows existing permissions; it never grants access. */
final class SelectedTaskInstitutionScope {
 private SelectedTaskInstitutionScope() {}
 static boolean allows(OrganizationMapper mapper, Organization candidate) {
  Organization root=SelectedOrganizationContext.organization();
  if(root==null)return true;
  var visited=new java.util.HashSet<Long>();
  while(root!=null&&!OrganizationHierarchy.isInstitutionRoot(root)){
   if(!visited.add(root.id())||root.parentId()==null)return false;
   root=mapper.findById(root.parentId());
  }
  return root!=null&&candidate!=null&&root.path()!=null&&candidate.path()!=null&&candidate.path().startsWith(root.path());
 }
}
