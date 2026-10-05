package com.lingdong.learning.menu.application;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.menu.domain.MenuType;

/** 授权视图节点：仅页面与权限按钮，granted 表示该角色当前已获得对应权限。 */
public record MenuGrantViewNode(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String code,
        String name,
        MenuType type,
        @JsonSerialize(using = ToStringSerializer.class) Long parentId,
        boolean granted) { }
