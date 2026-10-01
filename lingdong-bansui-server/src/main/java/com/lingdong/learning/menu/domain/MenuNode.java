package com.lingdong.learning.menu.domain;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
public record MenuNode(
    @JsonSerialize(using=ToStringSerializer.class) Long id,
    String code, String name, MenuType type,
    @JsonSerialize(using=ToStringSerializer.class) Long parentId,
    String route, String icon, String permissionCode, int sortOrder, MenuStatus status,
    @JsonSerialize(using=ToStringSerializer.class) Long version
) {}
