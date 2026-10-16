package com.lingdong.learning.organization.application;
public record CreateClassCommand(Long schoolOrganizationId, Long gradeOrganizationId, String name, Integer sortOrder) {
 public CreateClassCommand(Long gradeOrganizationId,String name,Integer sortOrder) { this(null,gradeOrganizationId,name,sortOrder); }
}
