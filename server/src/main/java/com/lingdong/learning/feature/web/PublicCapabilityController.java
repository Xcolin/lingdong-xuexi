package com.lingdong.learning.feature.web;

import com.lingdong.learning.feature.application.FeatureAccessService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 提供前端隐藏停用功能入口所需的最小公开能力摘要。 */
@RestController
@RequestMapping("/api/v1/public/capabilities")
public class PublicCapabilityController {
    private static final String MINIAPP_CLIENT = "MINIAPP";
    private static final String WEB_CLIENT = "WEB";
    private static final String STUDENT_CODE_LOGIN = "STUDENT_CODE_LOGIN";
    private static final String LEARNING_TASK_MANAGEMENT = "LEARNING_TASK_MANAGEMENT";

    private final FeatureAccessService featureAccessService;

    public PublicCapabilityController(FeatureAccessService featureAccessService) {
        this.featureAccessService = featureAccessService;
    }

    @GetMapping
    public PublicCapabilityResponse capabilities(@RequestParam String client) {
        if (!MINIAPP_CLIENT.equals(client) && !WEB_CLIENT.equals(client)) {
            throw new IllegalArgumentException("不支持的客户端类型");
        }
        return new PublicCapabilityResponse(
                client,
                MINIAPP_CLIENT.equals(client) && featureAccessService.isEnabled(STUDENT_CODE_LOGIN, null),
                featureAccessService.isEnabled(LEARNING_TASK_MANAGEMENT, null));
    }
}
