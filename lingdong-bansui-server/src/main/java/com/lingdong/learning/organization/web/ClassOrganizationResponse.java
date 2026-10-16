package com.lingdong.learning.organization.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.domain.OrganizationEffectiveStatus;
import com.lingdong.learning.organization.domain.OrganizationStatus;

import java.time.LocalDateTime;

/** Web 与小程序共用的班级或学校候选响应。 */
public record ClassOrganizationResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        @JsonSerialize(using = ToStringSerializer.class) Long parentId,
        String code,
        String name,
        String typeCode,
        Integer sortOrder,
        OrganizationStatus status,
        OrganizationEffectiveStatus effectiveStatus,
        Integer versionNo,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        @JsonSerialize(using = ToStringSerializer.class) Long schoolOrganizationId,
        String schoolName,
        @JsonSerialize(using = ToStringSerializer.class) Long gradeOrganizationId,
        String gradeName,
        @JsonSerialize(using = ToStringSerializer.class) Long campusOrganizationId,
        String campusName,
        String namePath
) {
    static ClassOrganizationResponse from(Organization organization) { return from(organization,null,null); }
    static ClassOrganizationResponse from(Organization organization,Organization school,Organization grade) {
        return from(organization,school,grade,null,organization.name());
    }
    static ClassOrganizationResponse from(Organization organization,Organization school,Organization grade,Organization campus,String namePath) {
        return new ClassOrganizationResponse(
                organization.id(), organization.parentId(), organization.code(), organization.name(),
                organization.typeCode(), organization.sortOrder(), organization.status(),
                organization.effectiveStatus(), organization.versionNo(),
                organization.createdAt(), organization.updatedAt(),school==null?null:school.id(),school==null?null:school.name(),
                grade==null?null:grade.id(),grade==null?null:grade.name(),campus==null?null:campus.id(),campus==null?null:campus.name(),namePath);
    }
}
