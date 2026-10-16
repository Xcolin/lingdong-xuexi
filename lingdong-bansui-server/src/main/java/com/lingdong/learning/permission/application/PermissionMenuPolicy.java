package com.lingdong.learning.permission.application;

import com.lingdong.learning.menu.domain.*;
import java.util.*;

/** Shared menu lifecycle decision; no disabled ancestor may expose an action or API. */
public final class PermissionMenuPolicy {
    private PermissionMenuPolicy() {}
    public static boolean enabled(String code,List<MenuNode> nodes) {
        Map<Long,MenuNode> index=new HashMap<>();nodes.forEach(n->index.put(n.id(),n));
        List<MenuNode> placements=nodes.stream().filter(n->Objects.equals(n.permissionCode(),code)).toList();
        if(placements.isEmpty()) return false; // Web actions require a live menu placement.
        return placements.stream().anyMatch(n->enabledPath(n,index));
    }
    static boolean enabledPath(MenuNode node,Map<Long,MenuNode> index) {
        Set<Long> seen=new HashSet<>();
        while(node!=null) {
            if(!seen.add(node.id()) || node.status()!=MenuStatus.ENABLED) return false;
            if(node.parentId()==null) return true;
            node=index.get(node.parentId());
            if(node==null) return false;
        }
        return false;
    }
    public static Set<String> necessaryPages(Set<String> explicit,List<MenuNode> nodes) {
        Map<Long,MenuNode> index=new HashMap<>();nodes.forEach(n->index.put(n.id(),n));
        Set<String> result=new HashSet<>();
        for(MenuNode node:nodes) {
            if(node.type()!=MenuType.BUTTON || !explicit.contains(node.permissionCode()) || !enabledPath(node,index)) continue;
            Set<Long> seen=new HashSet<>(); MenuNode parent=index.get(node.parentId());
            while(parent!=null && seen.add(parent.id())) {
                if(parent.type()==MenuType.PAGE && parent.permissionCode()!=null) result.add(parent.permissionCode());
                parent=index.get(parent.parentId());
            }
        }
        return result;
    }
}
