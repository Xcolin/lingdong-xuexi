package com.lingdong.learning.menu.web;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.RequirePermission;
import com.lingdong.learning.menu.application.*;
import com.lingdong.learning.menu.domain.MenuNode;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.List;
@RestController
@RequestMapping("/api/v1")
public class MenuController {
    private final MenuApplicationService service;
    public MenuController(MenuApplicationService service) { this.service=service; }
    @GetMapping("/iam/menus") @RequirePermission("MENU_READ")
    public List<MenuNode> list(@AuthenticationPrincipal AuthenticatedUser user) { return service.list(user); }
    @GetMapping("/menus/current")
    public List<MenuNode> current(@AuthenticationPrincipal AuthenticatedUser user) { return service.current(user); }
    @PostMapping("/iam/menus") @RequirePermission("MENU_MANAGE") @ResponseStatus(HttpStatus.CREATED)
    public MenuNode create(@AuthenticationPrincipal AuthenticatedUser user,@RequestBody MenuWriteCommand command) { return service.create(user,command); }
    @PutMapping("/iam/menus/order") @RequirePermission("MENU_MANAGE")
    public List<MenuNode> order(@AuthenticationPrincipal AuthenticatedUser user,@RequestBody MenuOrderCommand command) { return service.order(user,command); }
    @PutMapping("/iam/menus/{id}") @RequirePermission("MENU_MANAGE")
    public MenuNode update(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable Long id,@RequestBody MenuWriteCommand command) { return service.update(user,id,command); }
    @PostMapping("/iam/menus/{id}/buttons:batch") @RequirePermission("MENU_MANAGE") @ResponseStatus(HttpStatus.CREATED)
    public List<MenuNode> createButtons(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable Long id,@RequestBody List<MenuButtonBatchCommand> commands) { return service.createButtons(user,id,commands); }
    @PutMapping("/iam/menus/buttons:batch") @RequirePermission("MENU_MANAGE")
    public List<MenuNode> updateButtons(@AuthenticationPrincipal AuthenticatedUser user,@RequestBody List<MenuButtonBatchUpdateCommand> commands) { return service.updateButtons(user,commands); }
    @PutMapping("/iam/menus/{id}/position") @RequirePermission("MENU_MANAGE")
    public MenuNode move(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable Long id,@RequestBody MenuPositionCommand command) { return service.move(user,id,command); }
}
