package com.lingdong.learning.permission.application;
import java.util.List;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.menu.domain.MenuNode;
public record UserPermissionTree(List<MenuNode> menus,
 @JsonSerialize(contentUsing=ToStringSerializer.class) List<Long> inheritedPermissionIds,
 @JsonSerialize(contentUsing=ToStringSerializer.class) List<Long> inheritedDeniedPermissionIds) { }
