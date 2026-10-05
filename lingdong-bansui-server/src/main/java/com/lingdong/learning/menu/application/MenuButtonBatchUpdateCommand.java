package com.lingdong.learning.menu.application;
import com.lingdong.learning.menu.domain.MenuStatus;
/** 批量修改按钮命令：version 乐观锁，编码可修改并联动权限同步。 */
public record MenuButtonBatchUpdateCommand(Long id,Long version,String code,String name,boolean grantable,String icon,Integer sortOrder,MenuStatus status) {}
