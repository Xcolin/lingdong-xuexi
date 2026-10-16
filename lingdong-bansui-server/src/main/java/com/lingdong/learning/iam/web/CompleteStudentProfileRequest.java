package com.lingdong.learning.iam.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CompleteStudentProfileRequest(@NotNull Long organizationId, Long studentSchoolOrganizationId,
        @Size(max = 64) String gradeCode) {}
