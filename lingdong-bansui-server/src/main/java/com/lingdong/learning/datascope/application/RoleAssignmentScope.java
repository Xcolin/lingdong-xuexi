package com.lingdong.learning.datascope.application;
import com.lingdong.learning.iam.domain.RoleDataScope;
/** One enabled role assignment and its declared organizational data scope. */
public record RoleAssignmentScope(Long roleId, RoleDataScope dataScope, Long organizationId) { }
