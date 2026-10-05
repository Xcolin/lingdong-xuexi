package com.lingdong.learning.menu.application;
/** 拖拽位置命令：目标父级与目标父下的插入序号（从 0 开始）。 */
public record MenuPositionCommand(Long parentId,Integer index,Long version) {}
