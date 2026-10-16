package com.lingdong.learning.learningtask.application;
import java.util.List;
/** 可选教师、活动授权班级及组织全路径。 */
public record TeacherOption(Long userId, String displayName, List<Long> classOrganizationIds, List<String> organizationNames) {
    public TeacherOption { classOrganizationIds=List.copyOf(classOrganizationIds); organizationNames=List.copyOf(organizationNames); }
    public TeacherOption(Long userId,String displayName,List<Long> classOrganizationIds) { this(userId,displayName,classOrganizationIds,List.of()); }
}
