package com.lingdong.learning.menu.application;

import com.lingdong.learning.menu.domain.MenuNode;
import com.lingdong.learning.menu.domain.MenuType;
import com.lingdong.learning.menu.domain.MenuStatus;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;

class MenuApplicationServiceTest {
    @Test void serializesIdentifiersAndVersionsAsStrings() throws Exception {
        var tree=new com.fasterxml.jackson.databind.ObjectMapper().readTree(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(node(1874244142494648800L,1874244142494648801L,MenuType.BUTTON,MenuStatus.ENABLED,null)));
        assertThat(tree.get("id").isTextual()).isTrue();
        assertThat(tree.get("parentId").isTextual()).isTrue();
        assertThat(tree.get("version").isTextual()).isTrue();
    }
    private MenuNode node(long id, Long parent, MenuType type, MenuStatus status, String permission) {
        return new MenuNode(id,"node-"+id,"节点",type,parent,type == MenuType.PAGE ? "/rewards" : null,null,permission,0,status,0L);
    }
    @Test void disabledAncestorHidesAllDescendants() {
        var nodes = List.of(node(1,null,MenuType.DIRECTORY,MenuStatus.DISABLED,null),node(2,1L,MenuType.PAGE,MenuStatus.ENABLED,null),node(3,2L,MenuType.BUTTON,MenuStatus.ENABLED,null));
        assertThat(MenuApplicationService.filterVisible(nodes,Set.of())).isEmpty();
    }
    @Test void deniedAncestorHidesUngatedChild() {
        var nodes = List.of(node(1,null,MenuType.PAGE,MenuStatus.ENABLED,"REWARD_READ"),node(2,1L,MenuType.BUTTON,MenuStatus.ENABLED,null));
        assertThat(MenuApplicationService.filterVisible(nodes,Set.of())).isEmpty();
        assertThat(MenuApplicationService.filterVisible(nodes,Set.of("REWARD_READ"))).hasSize(2);
    }
    @Test void cyclesCannotBecomeVisible() {
        var nodes = List.of(node(1,2L,MenuType.DIRECTORY,MenuStatus.ENABLED,null),node(2,1L,MenuType.DIRECTORY,MenuStatus.ENABLED,null));
        assertThat(MenuApplicationService.filterVisible(nodes,Set.of())).isEmpty();
    }
}
