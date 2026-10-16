package com.lingdong.learning.organization.application;
import com.lingdong.learning.organization.domain.Organization;
import java.util.*;
/** Canonical preorder: siblings by sort order, then numeric identity. */
public final class OrganizationTreeOrder {
 private OrganizationTreeOrder() {}
 public static Comparator<Organization> comparator(List<Organization> all) {
  Map<Long,Organization> byId=new HashMap<>(); all.forEach(o -> byId.put(o.id(),o));
  return (a,b) -> compare(chain(a,byId),chain(b,byId));
 }
 private static List<Organization> chain(Organization node,Map<Long,Organization> all) {
  LinkedList<Organization> path=new LinkedList<>(); Set<Long> seen=new HashSet<>();
  while(node!=null && seen.add(node.id())) { path.addFirst(node); node=all.get(node.parentId()); } return path;
 }
 private static int compare(List<Organization> a,List<Organization> b) {
  for(int i=0;i<Math.min(a.size(),b.size());i++) {
   int c=Integer.compare(a.get(i).sortOrder()==null?Integer.MAX_VALUE:a.get(i).sortOrder(),b.get(i).sortOrder()==null?Integer.MAX_VALUE:b.get(i).sortOrder());
   if(c==0)c=Long.compare(a.get(i).id(),b.get(i).id()); if(c!=0)return c;
  } return Integer.compare(a.size(),b.size());
 }
}
