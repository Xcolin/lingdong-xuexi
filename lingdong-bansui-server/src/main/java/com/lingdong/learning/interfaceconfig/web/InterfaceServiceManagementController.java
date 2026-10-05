package com.lingdong.learning.interfaceconfig.web;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.RequirePermission;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.interfaceconfig.application.CreateInterfaceServiceAuthorizationChangeCommand;
import com.lingdong.learning.interfaceconfig.application.CreateInterfaceServiceChangeCommand;
import com.lingdong.learning.interfaceconfig.application.CreateInterfaceServiceDisableCommand;
import com.lingdong.learning.interfaceconfig.application.CreateInterfaceServiceEnableCommand;
import com.lingdong.learning.interfaceconfig.application.InterfaceServiceApplicationService;
import com.lingdong.learning.interfaceconfig.application.InterfaceServiceChangeView;
import com.lingdong.learning.interfaceconfig.domain.InterfaceCallResult;
import com.lingdong.learning.interfaceconfig.domain.InterfacePurpose;
import com.lingdong.learning.interfaceconfig.domain.InterfaceService;
import com.lingdong.learning.interfaceconfig.domain.InterfaceServiceStatus;
import com.lingdong.learning.user.application.UserDisplayNameResolver;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 提供受功能开关、动态 RBAC 和固定职责分离共同保护的接口服务管理 API。 */
@RestController
@RequestMapping("/api/v1/interface-services")
public class InterfaceServiceManagementController {
    private static final String FEATURE_CODE = "INTERFACE_SERVICE_MANAGEMENT";

    private final InterfaceServiceApplicationService applicationService;
    private final FeatureAccessService featureAccessService;
    private final UserDisplayNameResolver userNames;

    public InterfaceServiceManagementController(
            InterfaceServiceApplicationService applicationService,
            FeatureAccessService featureAccessService,
            UserDisplayNameResolver userNames
    ) {
        this.applicationService = applicationService;
        this.featureAccessService = featureAccessService;
        this.userNames = userNames;
    }

    @RequirePermission("INTERFACE_SERVICE_READ")
    @GetMapping
    public List<InterfaceServiceResponse> listServices(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) String callerName,
            @RequestParam(required = false) InterfaceServiceStatus status,
            @RequestParam(required = false) InterfacePurpose purpose,
            @RequestParam(required = false) Long ownerId,
            @RequestParam(required = false) Integer limit
    ) {
        requireFeature();
        List<InterfaceService> services = applicationService.listServices(
                currentUser.userId(), serviceName, callerName, status, purpose, ownerId, limit);
        Map<Long, String> names = userNames.resolveAll(services.stream().map(InterfaceService::ownerId).toList());
        return services.stream()
                .map(service -> InterfaceServiceResponse.from(service, id -> userNames.nameOf(names, id)))
                .toList();
    }

    @RequirePermission("INTERFACE_SERVICE_READ")
    @GetMapping("/changes")
    public List<InterfaceServiceChangeResponse> listChanges(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(required = false) Integer limit
    ) {
        requireFeature();
        return toChangeResponses(applicationService.listChanges(currentUser.userId(), limit));
    }

    @RequirePermission("INTERFACE_SERVICE_READ")
    @GetMapping("/call-logs")
    public List<InterfaceServiceCallLogResponse> listCallLogs(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(required = false) Long serviceId,
            @RequestParam(required = false) InterfaceCallResult result,
            @RequestParam(required = false) Integer limit
    ) {
        requireFeature();
        return applicationService.listCallLogs(currentUser.userId(), serviceId, result, limit).stream()
                .map(InterfaceServiceCallLogResponse::from).toList();
    }

    @RequirePermission("INTERFACE_SERVICE_REVIEW")
    @GetMapping("/review-queue")
    public List<InterfaceServiceChangeResponse> listReviewQueue(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(required = false) Integer limit
    ) {
        requireFeature();
        return toChangeResponses(applicationService.listPendingReviews(currentUser.userId(), limit));
    }

    @RequirePermission("INTERFACE_SERVICE_MANAGE")
    @PostMapping("/registration-submissions")
    @ResponseStatus(HttpStatus.CREATED)
    public InterfaceServiceSubmissionResponse submitRegistration(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody CreateInterfaceServiceRegistrationRequest request
    ) {
        requireFeature();
        return InterfaceServiceSubmissionResponse.from(applicationService.createAndSubmitRegistration(
                new CreateInterfaceServiceChangeCommand(
                        currentUser.userId(), request.serviceName(), request.direction(), request.purpose(),
                        request.callerName(), request.authorizationScope(), request.authorizationScopeValue(),
                        request.ownerId(), request.title(), request.description()
                )));
    }

    @RequirePermission("INTERFACE_SERVICE_MANAGE")
    @PostMapping("/{serviceId}/enable-submissions")
    @ResponseStatus(HttpStatus.CREATED)
    public InterfaceServiceSubmissionResponse submitEnable(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long serviceId,
            @Valid @RequestBody CreateInterfaceServiceStatusSubmissionRequest request
    ) {
        requireFeature();
        return InterfaceServiceSubmissionResponse.from(applicationService.createAndSubmitEnable(
                new CreateInterfaceServiceEnableCommand(
                        currentUser.userId(), serviceId, request.title(), request.description())
        ));
    }

    @RequirePermission("INTERFACE_SERVICE_MANAGE")
    @PostMapping("/{serviceId}/disable-submissions")
    @ResponseStatus(HttpStatus.CREATED)
    public InterfaceServiceSubmissionResponse submitDisable(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long serviceId,
            @Valid @RequestBody CreateInterfaceServiceStatusSubmissionRequest request
    ) {
        requireFeature();
        return InterfaceServiceSubmissionResponse.from(applicationService.createAndSubmitDisable(
                new CreateInterfaceServiceDisableCommand(
                        currentUser.userId(), serviceId, request.title(), request.description())
        ));
    }

    @RequirePermission("INTERFACE_SERVICE_MANAGE")
    @PostMapping("/{serviceId}/authorization-submissions")
    @ResponseStatus(HttpStatus.CREATED)
    public InterfaceServiceSubmissionResponse submitAuthorization(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long serviceId,
            @Valid @RequestBody CreateInterfaceServiceAuthorizationSubmissionRequest request
    ) {
        requireFeature();
        return InterfaceServiceSubmissionResponse.from(applicationService.createAndSubmitAuthorization(
                new CreateInterfaceServiceAuthorizationChangeCommand(
                        currentUser.userId(), serviceId, request.authorizationScope(),
                        request.authorizationScopeValue(), request.title(), request.description())
        ));
    }

    @RequirePermission("INTERFACE_SERVICE_REVIEW")
    @PostMapping("/review-tasks/{taskId}/approve")
    public InterfaceServiceTaskResponse approve(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long taskId,
            @Valid @RequestBody InterfaceServiceReviewRequest request
    ) {
        requireFeature();
        return InterfaceServiceTaskResponse.from(
                applicationService.approveAndApply(taskId, currentUser.userId(), request.comment()), userNames::resolve);
    }

    @RequirePermission("INTERFACE_SERVICE_REVIEW")
    @PostMapping("/review-tasks/{taskId}/reject")
    public InterfaceServiceTaskResponse reject(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long taskId,
            @Valid @RequestBody InterfaceServiceReviewRequest request
    ) {
        requireFeature();
        return InterfaceServiceTaskResponse.from(
                applicationService.reject(taskId, currentUser.userId(), request.comment()), userNames::resolve);
    }

    private List<InterfaceServiceChangeResponse> toChangeResponses(List<InterfaceServiceChangeView> views) {
        List<Long> ids = new ArrayList<>();
        for (InterfaceServiceChangeView view : views) {
            ids.add(view.ownerId());
            ids.add(view.submittedBy());
            ids.add(view.reviewedBy());
        }
        Map<Long, String> names = userNames.resolveAll(ids);
        return views.stream()
                .map(view -> InterfaceServiceChangeResponse.from(view, id -> userNames.nameOf(names, id)))
                .toList();
    }

    private void requireFeature() {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
    }
}
