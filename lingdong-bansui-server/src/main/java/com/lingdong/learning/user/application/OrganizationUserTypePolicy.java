package com.lingdong.learning.user.application;
import com.lingdong.learning.user.domain.UserType;
import java.util.Set;
public final class OrganizationUserTypePolicy {
 private OrganizationUserTypePolicy(){}
 public static void validate(String organizationType,UserType userType){
  Set<UserType> allowed="FAMILY".equals(organizationType)?Set.of(UserType.FAMILY,UserType.STUDENT):Set.of("SCHOOL","TRAINING_INSTITUTION","CAMPUS","GRADE","CLASS").contains(organizationType)?Set.of(UserType.ORGANIZATION,UserType.STUDENT):Set.of("REGION","CITY","COUNTRY").contains(organizationType)?Set.of(UserType.PLATFORM):Set.of(UserType.values());
  if(!allowed.contains(userType))throw new IllegalArgumentException("用户类型与当前组织不匹配");
 }
}
