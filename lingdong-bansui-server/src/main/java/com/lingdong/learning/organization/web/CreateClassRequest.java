package com.lingdong.learning.organization.web;
import jakarta.validation.constraints.*;
public record CreateClassRequest(@Positive Long schoolOrganizationId,@NotNull @Positive Long gradeOrganizationId,
 @NotBlank @Size(max=50) String name,@Min(0) Integer sortOrder) {}
