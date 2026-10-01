package com.lingdong.learning.menu.application;
import java.util.List;
import java.util.Map;
public record MenuOrderCommand(Long parentId,List<Long> ids,Map<Long,Long> versions) {}
