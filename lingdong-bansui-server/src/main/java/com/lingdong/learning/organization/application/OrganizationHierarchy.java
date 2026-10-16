package com.lingdong.learning.organization.application;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import java.util.HashSet;
/** Shared strict rules for new nodes and moves; existing school-direct classes remain readable. */
public final class OrganizationHierarchy {
 private OrganizationHierarchy() {}
 /** SCHOOL remains the API and role-scope name for both educational institution root types. */
 public static boolean isInstitutionRoot(String type) { return "SCHOOL".equals(type) || "TRAINING_INSTITUTION".equals(type); }
 public static boolean isInstitutionRoot(Organization node) { return node != null && isInstitutionRoot(node.typeCode()); }
 public static void requireParent(String type, Organization parent) {
  if (parent != null && "CLASS".equals(parent.typeCode()))
   throw new IllegalArgumentException("班级是末级组织，不能新增下级组织");
  if (parent != null && "GRADE".equals(parent.typeCode()) && !"CLASS".equals(type))
   throw new IllegalArgumentException("年级下只能管理班级");
  if (parent != null && isInstitutionRoot(type) && (isInstitutionRoot(parent) || java.util.Set.of("CAMPUS","GRADE","CLASS").contains(parent.typeCode())))
   throw new IllegalArgumentException("教育机构不能直接属于另一教育机构、校区、年级或班级");
  if (parent != null && "CAMPUS".equals(parent.typeCode()) && !"GRADE".equals(type))
   throw new IllegalArgumentException("校区下只能管理年级");
  if ("GRADE".equals(type) && (parent == null || !(isInstitutionRoot(parent) || "CAMPUS".equals(parent.typeCode()))))
   throw new IllegalArgumentException("年级必须直接属于教育机构或校区");
  if ("CAMPUS".equals(type) && !isInstitutionRoot(parent))
   throw new IllegalArgumentException("校区必须直接属于教育机构");
  if ("CLASS".equals(type) && (parent == null || !"GRADE".equals(parent.typeCode())))
   throw new IllegalArgumentException("班级必须直接属于年级");
 }
 public static void requireParent(String type, Organization parent, OrganizationMapper mapper) {
  requireParent(type,parent);
  if (isInstitutionRoot(type) && schoolOf(parent,mapper)!=null)
   throw new IllegalArgumentException("教育机构不能嵌套在另一教育机构内");
 }
 public static Organization ancestor(Organization node,String type,OrganizationMapper mapper) {
  var seen=new HashSet<Long>();
  while(node!=null && seen.add(node.id())) {
   if(type.equals(node.typeCode()) || "SCHOOL".equals(type) && isInstitutionRoot(node)) return node;
   node=node.parentId()==null ? null : mapper.findById(node.parentId());
  }
  return null;
 }
 public static Organization schoolOf(Organization node,OrganizationMapper mapper) { return ancestor(node,"SCHOOL",mapper); }
}
