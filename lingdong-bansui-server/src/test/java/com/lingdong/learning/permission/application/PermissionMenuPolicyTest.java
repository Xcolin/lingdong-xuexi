package com.lingdong.learning.permission.application;
import com.lingdong.learning.menu.domain.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
class PermissionMenuPolicyTest {
 private MenuNode n(long id,Long parent,MenuType type,String code,MenuStatus status){return new MenuNode(id,code,code,type,parent,null,null,type==MenuType.DIRECTORY?null:code,true,1,status,0L);}
 @Test void disabledAncestorDisablesActionAndNecessaryRead(){
  var nodes=List.of(n(1,null,MenuType.DIRECTORY,"MODULE",MenuStatus.DISABLED),n(2,1L,MenuType.PAGE,"READ",MenuStatus.ENABLED),n(3,2L,MenuType.BUTTON,"WRITE",MenuStatus.ENABLED));
  assertThat(PermissionMenuPolicy.enabled("WRITE",nodes)).isFalse();
  assertThat(PermissionMenuPolicy.necessaryPages(Set.of("WRITE"),nodes)).isEmpty();
 }
 @Test void derivesOnlyContainingPageNeverSiblingWrite(){
  var nodes=List.of(n(1,null,MenuType.PAGE,"READ",MenuStatus.ENABLED),n(2,1L,MenuType.BUTTON,"WRITE",MenuStatus.ENABLED),n(3,1L,MenuType.BUTTON,"DELETE",MenuStatus.ENABLED));
  assertThat(PermissionMenuPolicy.necessaryPages(Set.of("WRITE"),nodes)).containsExactly("READ");
 }
 @Test void missingParentAndCycleFailClosed(){
  assertThat(PermissionMenuPolicy.enabled("READ",List.of(n(1,8L,MenuType.PAGE,"READ",MenuStatus.ENABLED)))).isFalse();
  assertThat(PermissionMenuPolicy.enabled("READ",List.of(n(1,2L,MenuType.PAGE,"READ",MenuStatus.ENABLED),n(2,1L,MenuType.DIRECTORY,"MODULE",MenuStatus.ENABLED)))).isFalse();
 }
}
