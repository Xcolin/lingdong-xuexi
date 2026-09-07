package com.lingdong.learning.dictionary.web;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.RequirePermission;
import com.lingdong.learning.dictionary.application.CreateDictionaryItemCommand;
import com.lingdong.learning.dictionary.application.CreateDictionaryTypeCommand;
import com.lingdong.learning.dictionary.application.DictionaryApplicationService;
import com.lingdong.learning.dictionary.application.UpdateDictionaryItemCommand;
import com.lingdong.learning.dictionary.application.UpdateDictionaryTypeCommand;
import com.lingdong.learning.feature.application.FeatureAccessService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 提供受功能开关和动态 RBAC 共同保护的数据字典 Web 管理接口。 */
@RestController
@RequestMapping("/api/v1/dictionaries")
public class DictionaryManagementController {
    private static final String FEATURE_CODE = "DICTIONARY_MANAGEMENT";

    private final DictionaryApplicationService dictionaryApplicationService;
    private final FeatureAccessService featureAccessService;

    public DictionaryManagementController(
            DictionaryApplicationService dictionaryApplicationService,
            FeatureAccessService featureAccessService
    ) {
        this.dictionaryApplicationService = dictionaryApplicationService;
        this.featureAccessService = featureAccessService;
    }

    @RequirePermission("DICTIONARY_READ")
    @GetMapping("/types")
    public List<DictionaryTypeResponse> listTypes(
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        requireFeature();
        return dictionaryApplicationService.listTypes(currentUser.userId()).stream()
                .map(DictionaryTypeResponse::from)
                .toList();
    }

    @RequirePermission("DICTIONARY_MANAGE")
    @PostMapping("/types")
    @ResponseStatus(HttpStatus.CREATED)
    public DictionaryTypeResponse createType(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody CreateDictionaryTypeRequest request
    ) {
        requireFeature();
        return DictionaryTypeResponse.from(dictionaryApplicationService.createType(
                new CreateDictionaryTypeCommand(
                        currentUser.userId(), request.code(), request.name(), request.sortOrder())));
    }

    @RequirePermission("DICTIONARY_MANAGE")
    @PutMapping("/types/{typeId}")
    public DictionaryTypeResponse updateType(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long typeId,
            @Valid @RequestBody UpdateDictionaryTypeRequest request
    ) {
        requireFeature();
        return DictionaryTypeResponse.from(dictionaryApplicationService.updateType(
                new UpdateDictionaryTypeCommand(
                        currentUser.userId(), typeId, request.name(), request.sortOrder(), request.status())));
    }

    @RequirePermission("DICTIONARY_READ")
    @GetMapping("/types/{typeId}/items")
    public List<DictionaryItemResponse> listItems(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long typeId
    ) {
        requireFeature();
        return dictionaryApplicationService.listItems(currentUser.userId(), typeId).stream()
                .map(DictionaryItemResponse::from)
                .toList();
    }

    @RequirePermission("DICTIONARY_MANAGE")
    @PostMapping("/types/{typeId}/items")
    @ResponseStatus(HttpStatus.CREATED)
    public DictionaryItemResponse createItem(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long typeId,
            @Valid @RequestBody CreateDictionaryItemRequest request
    ) {
        requireFeature();
        return DictionaryItemResponse.from(dictionaryApplicationService.createItem(
                new CreateDictionaryItemCommand(
                        currentUser.userId(), typeId, request.code(), request.name(),
                        request.sortOrder(), request.defaultItem())));
    }

    @RequirePermission("DICTIONARY_MANAGE")
    @PutMapping("/items/{itemId}")
    public DictionaryItemResponse updateItem(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long itemId,
            @Valid @RequestBody UpdateDictionaryItemRequest request
    ) {
        requireFeature();
        return DictionaryItemResponse.from(dictionaryApplicationService.updateItem(
                new UpdateDictionaryItemCommand(
                        currentUser.userId(), itemId, request.name(), request.sortOrder(),
                        request.defaultItem(), request.status())));
    }

    private void requireFeature() {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
    }
}

