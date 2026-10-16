package com.lingdong.learning.student.application;
public record StudentOrganizationClassOption(Long id,String name,Long schoolOrganizationId){
 public StudentOrganizationClassOption(Long id,String name){this(id,name,null);}
}
