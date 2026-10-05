package com.lingdong.learning.menu.application;
import com.lingdong.learning.menu.domain.MenuStatus;
/** 批量新增按钮命令：整批原子提交，grantable 保留为兼容输入，全部按钮强制可授权。 */
public record MenuButtonBatchCommand(String code,String name,boolean grantable,String icon,Integer sortOrder,MenuStatus status) {}
