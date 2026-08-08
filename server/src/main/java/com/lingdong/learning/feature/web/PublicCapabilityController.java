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
    private static final String STUDENT_QR_LOGIN = "STUDENT_QR_LOGIN";
    private static final String LEARNING_TASK_MANAGEMENT = "LEARNING_TASK_MANAGEMENT";
    private static final String COPY_PREVIOUS_DAY_TASK = "COPY_PREVIOUS_DAY_TASK";
    private static final String LEARNING_TASK_TEMPLATE = "LEARNING_TASK_TEMPLATE";
    private static final String GROWTH_POINT_QUERY = "GROWTH_POINT_QUERY";
    private static final String GROWTH_POINT_CORRECTION = "GROWTH_POINT_CORRECTION";
    private static final String REWARD_EXCHANGE = "REWARD_EXCHANGE";
    private static final String DAILY_GROWTH_REVIEW = "DAILY_GROWTH_REVIEW";
    private static final String PERIODIC_GROWTH_REPORT = "PERIODIC_GROWTH_REPORT";

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
                featureAccessService.isEnabled(STUDENT_QR_LOGIN, null),
                featureAccessService.isEnabled(LEARNING_TASK_MANAGEMENT, null),
                WEB_CLIENT.equals(client)
                        && featureAccessService.isEnabled(COPY_PREVIOUS_DAY_TASK, null),
                WEB_CLIENT.equals(client)
                        && featureAccessService.isEnabled(LEARNING_TASK_MANAGEMENT, null)
                        && featureAccessService.isEnabled(LEARNING_TASK_TEMPLATE, null),
                featureAccessService.isEnabled(GROWTH_POINT_QUERY, null),
                WEB_CLIENT.equals(client)
                        && featureAccessService.isEnabled(GROWTH_POINT_CORRECTION, null),
                featureAccessService.isEnabled(REWARD_EXCHANGE, null),
                featureAccessService.isEnabled(DAILY_GROWTH_REVIEW, null),
                featureAccessService.isEnabled(PERIODIC_GROWTH_REPORT, null));
    }
}
