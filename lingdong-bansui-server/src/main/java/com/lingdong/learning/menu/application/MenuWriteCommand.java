package com.lingdong.learning.menu.application;
import com.lingdong.learning.menu.domain.*;
/** 菜单写命令：权限编码由编码派生，不再允许外部绑定；grantable 保留为兼容输入，页面与全部按钮强制可授权。 */
public record MenuWriteCommand(String code,String name,MenuType type,Long parentId,String route,String icon,boolean grantable,Integer sortOrder,MenuStatus status,Long version) {}
