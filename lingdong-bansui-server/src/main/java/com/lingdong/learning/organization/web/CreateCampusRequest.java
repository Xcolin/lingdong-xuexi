package com.lingdong.learning.organization.web;
import jakarta.validation.constraints.*;
public record CreateCampusRequest(@NotNull @Positive Long schoolOrganizationId,@NotBlank @Size(max=50) String name,@Min(0) Integer sortOrder) {}
