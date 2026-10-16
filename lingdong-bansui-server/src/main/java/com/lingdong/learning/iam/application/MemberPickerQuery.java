package com.lingdong.learning.iam.application;
import java.util.List;
public record MemberPickerQuery(Long targetOrganizationId, Long roleId, Long grantOrganizationId,
 boolean administrator, boolean assigned, boolean unbound, boolean selfRole, boolean allOrganizations,
 List<Long> organizationIds, Long schoolId, String schoolPath, String grantOrganizationPath, String keyword, int offset, int limit) { }
