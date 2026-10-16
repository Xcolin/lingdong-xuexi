package com.lingdong.learning.organization.web;
import jakarta.validation.constraints.*;
public record CreateGradeRequest(@NotNull @Positive Long schoolOrganizationId,@Positive Long campusOrganizationId,@NotBlank @Size(max=50) String name,@Min(0) Integer sortOrder) {}
