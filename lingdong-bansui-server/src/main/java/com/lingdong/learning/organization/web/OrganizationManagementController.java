package com.lingdong.learning.organization.web;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.RequirePermission;
import com.lingdong.learning.organization.application.CreateOrganizationCommand;
import com.lingdong.learning.organization.application.CreateOrganizationTypeCommand;
import com.lingdong.learning.organization.application.MoveOrganizationCommand;
import com.lingdong.learning.organization.application.OrganizationChangeApplicationService;
import com.lingdong.learning.organization.application.OrganizationManagementApplicationService;
import com.lingdong.learning.organization.application.OrganizationOrderItem;
import com.lingdong.learning.organization.application.ReorderOrganizationsCommand;
import com.lingdong.learning.organization.application.UpdateOrganizationCommand;
import com.lingdong.learning.organization.domain.Organization;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 提供系统管理员专属的组织类型与组织树管理接口。 */
@RestController
@RequestMapping("/api/v1")
public class OrganizationManagementController {
    private final OrganizationManagementApplicationService organizationManagementApplicationService;
    private final OrganizationChangeApplicationService organizationChangeApplicationService;

    public OrganizationManagementController(
            OrganizationManagementApplicationService organizationManagementApplicationService,
            OrganizationChangeApplicationService organizationChangeApplicationService
    ) {
        this.organizationManagementApplicationService = organizationManagementApplicationService;
        this.organizationChangeApplicationService = organizationChangeApplicationService;
    }

    @RequirePermission("ORG_TYPE_READ")
    @GetMapping("/organization-types")
    public List<OrganizationTypeResponse> listOrganizationTypes(
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        return organizationManagementApplicationService.listOrganizationTypes(currentUser.userId()).stream()
                .map(OrganizationTypeResponse::from)
                .toList();
    }

    @RequirePermission("ORG_TYPE_CREATE")
    @PostMapping("/organization-types")
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizationTypeResponse createOrganizationType(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody CreateOrganizationTypeRequest request
    ) {
        return OrganizationTypeResponse.from(organizationManagementApplicationService.createOrganizationType(
                currentUser.userId(), new CreateOrganizationTypeCommand(request.code(), request.name(), request.sortOrder())
        ));
    }

    @RequirePermission("ORG_NODE_READ")
    @GetMapping("/organizations")
    public List<OrganizationTreeNodeResponse> listOrganizations(
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        return toTree(organizationManagementApplicationService.listOrganizations(currentUser.userId()));
    }

    @RequirePermission("ORG_NODE_CREATE")
    @PostMapping("/organizations")
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizationTreeNodeResponse createOrganization(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody CreateOrganizationRequest request
    ) {
        Organization organization = organizationManagementApplicationService.createOrganization(currentUser.userId(),
                new CreateOrganizationCommand(request.code(), request.name(), request.typeCode(), request.parentId(),
                        request.sortOrder(), request.adminDivisionCode()));
        return OrganizationTreeNodeResponse.from(organization);
    }

    @RequirePermission("ORG_NODE_UPDATE")
    @PutMapping("/organizations/{organizationId}")
    public OrganizationTreeNodeResponse updateOrganization(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long organizationId,
            @Valid @RequestBody UpdateOrganizationRequest request
    ) {
        Organization organization = organizationManagementApplicationService.updateOrganization(
                currentUser.userId(),
                new UpdateOrganizationCommand(
                        organizationId, request.name(), request.sortOrder(), request.versionNo(),
                        request.adminDivisionCode()));
        return OrganizationTreeNodeResponse.from(organization);
    }

    @RequirePermission("ORG_NODE_UPDATE")
    @PostMapping("/organizations/{organizationId}/enable")
    public OrganizationTreeNodeResponse enableOrganization(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long organizationId,
            @Valid @RequestBody EnableOrganizationRequest request
    ) {
        Organization organization = organizationManagementApplicationService.enableOrganization(
                currentUser.userId(), organizationId, request.versionNo());
        return OrganizationTreeNodeResponse.from(organization);
    }

    /** 拖拽同级排序直接生效：条目顺序即目标顺序，需完整提交同级节点集合。 */
    @RequirePermission("ORG_NODE_UPDATE")
    @PutMapping("/organizations/order")
    public List<OrganizationTreeNodeResponse> reorderOrganizations(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody ReorderOrganizationsRequest request
    ) {
        return organizationChangeApplicationService.directReorder(new ReorderOrganizationsCommand(
                        currentUser.userId(), request.parentId(),
                        request.items().stream()
                                .map(item -> new OrganizationOrderItem(item.organizationId(), item.expectedVersion()))
                                .toList()))
                .stream()
                .map(OrganizationTreeNodeResponse::from)
                .toList();
    }

    /** 拖拽改父级直接生效：复用既有路径重建与唯一名校验，不经申请-审核流。 */
    @RequirePermission("ORG_NODE_UPDATE")
    @PutMapping("/organizations/{organizationId}/position")
    public OrganizationTreeNodeResponse moveOrganization(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long organizationId,
            @Valid @RequestBody MoveOrganizationPositionRequest request
    ) {
        Organization organization = organizationChangeApplicationService.directMove(new MoveOrganizationCommand(
                currentUser.userId(), organizationId, request.targetParentId(), request.expectedVersion()));
        return OrganizationTreeNodeResponse.from(organization);
    }

    public record ReorderOrderItemRequest(Long organizationId, Integer expectedVersion) { }
    public record ReorderOrganizationsRequest(Long parentId,
            @jakarta.validation.constraints.NotEmpty List<ReorderOrderItemRequest> items) { }
    public record MoveOrganizationPositionRequest(Long targetParentId, Integer expectedVersion) { }

    /** 构建组织树，不容忍迁移或手工数据造成的断裂父子关系；同级展示顺序以拖拽排序写入的 sort_order 为准。 */
    private List<OrganizationTreeNodeResponse> toTree(List<Organization> organizations) {
        Map<Long, OrganizationTreeNodeBuilder> nodes = new LinkedHashMap<>();
        for (Organization organization : organizations) {
            nodes.put(organization.id(), new OrganizationTreeNodeBuilder(organization));
        }

        List<OrganizationTreeNodeBuilder> roots = new ArrayList<>();
        for (OrganizationTreeNodeBuilder node : nodes.values()) {
            if (node.organization.parentId() == null) {
                roots.add(node);
                continue;
            }
            OrganizationTreeNodeBuilder parent = nodes.get(node.organization.parentId());
            if (parent == null) {
                throw new IllegalStateException("组织树数据不完整");
            }
            parent.children.add(node);
        }
        Comparator<OrganizationTreeNodeBuilder> displayOrder = Comparator
                .comparing((OrganizationTreeNodeBuilder node) -> node.organization.sortOrder() == null
                        ? Integer.MAX_VALUE
                        : node.organization.sortOrder())
                .thenComparing(node -> node.organization.path());
        roots.sort(displayOrder);
        for (OrganizationTreeNodeBuilder node : nodes.values()) {
            node.children.sort(displayOrder);
        }
        return roots.stream().map(OrganizationTreeNodeBuilder::toResponse).toList();
    }

    /** 内部可变节点仅在一次请求的树构造期间使用，响应对象保持不可变。 */
    private static final class OrganizationTreeNodeBuilder {
        private final Organization organization;
        private final List<OrganizationTreeNodeBuilder> children = new ArrayList<>();

        private OrganizationTreeNodeBuilder(Organization organization) {
            this.organization = organization;
        }

        private OrganizationTreeNodeResponse toResponse() {
            return OrganizationTreeNodeResponse.from(organization, children.stream()
                    .map(OrganizationTreeNodeBuilder::toResponse)
                    .toList());
        }
    }
}
