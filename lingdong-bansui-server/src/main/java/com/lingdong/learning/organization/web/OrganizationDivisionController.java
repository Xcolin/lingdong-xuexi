package com.lingdong.learning.organization.web;

import com.lingdong.learning.common.security.RequirePermission;
import com.lingdong.learning.dictionary.application.DictionaryQueryService;
import com.lingdong.learning.feature.application.FeatureAccessService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/** 组织表单使用的只读行政区划，不授予字典管理能力。 */
@RestController
public class OrganizationDivisionController {
    private final DictionaryQueryService dictionaries;
    private final FeatureAccessService features;

    public OrganizationDivisionController(DictionaryQueryService dictionaries, FeatureAccessService features) {
        this.dictionaries = dictionaries;
        this.features = features;
    }

    @RequirePermission(anyOf = {"ORG_NODE_READ", "ORG_NODE_CREATE", "ORG_NODE_UPDATE"})
    @GetMapping("/api/v1/organizations/admin-divisions")
    public List<DivisionOption> options() {
        features.requireEnabled("ORGANIZATION_MANAGEMENT", null);
        return dictionaries.findEnabledItems("ADMIN_DIVISION").stream()
                .map(item -> new DivisionOption(item.code(), item.name())).toList();
    }

    public record DivisionOption(String code, String name) { }
}
