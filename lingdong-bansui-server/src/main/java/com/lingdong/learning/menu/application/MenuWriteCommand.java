package com.lingdong.learning.menu.application;
import com.lingdong.learning.menu.domain.*;
public record MenuWriteCommand(String code,String name,MenuType type,Long parentId,String route,String icon,String permissionCode,Integer sortOrder,MenuStatus status,Long version) {}
