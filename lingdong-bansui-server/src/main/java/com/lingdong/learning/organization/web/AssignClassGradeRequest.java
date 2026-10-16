package com.lingdong.learning.organization.web;
import jakarta.validation.constraints.*;
public record AssignClassGradeRequest(@NotNull @Positive Long gradeOrganizationId,@NotNull @Positive Integer versionNo) {}
