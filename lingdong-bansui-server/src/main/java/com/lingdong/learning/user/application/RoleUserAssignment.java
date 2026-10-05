package com.lingdong.learning.user.application;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
public record RoleUserAssignment(@JsonSerialize(using=ToStringSerializer.class) Long userId,
 @JsonSerialize(using=ToStringSerializer.class) Long organizationId) { }
