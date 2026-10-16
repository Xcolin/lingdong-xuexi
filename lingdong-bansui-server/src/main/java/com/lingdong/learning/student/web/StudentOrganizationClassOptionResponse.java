package com.lingdong.learning.student.web;

import com.lingdong.learning.student.application.StudentOrganizationClassOption;

/** 机构管理员班级候选项响应。 */
public record StudentOrganizationClassOptionResponse(String id, String name,String schoolOrganizationId) {
    static StudentOrganizationClassOptionResponse from(StudentOrganizationClassOption option) {
        return new StudentOrganizationClassOptionResponse(option.id().toString(), option.name(),option.schoolOrganizationId()==null?null:option.schoolOrganizationId().toString());
    }
}
