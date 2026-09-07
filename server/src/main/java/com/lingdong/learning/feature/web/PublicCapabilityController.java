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
    private static final String STUDENT_WECHAT_AUTH = "STUDENT_WECHAT_AUTH";
    private static final String PARENT_RELATIONSHIP_MANAGEMENT = "PARENT_RELATIONSHIP_MANAGEMENT";
    private static final String ORGANIZATION_MINIAPP_AUTH = "ORGANIZATION_MINIAPP_AUTH";
    private static final String ORGANIZATION_MANAGEMENT = "ORGANIZATION_MANAGEMENT";
    private static final String STUDENT_ORGANIZATION_RELATIONSHIP = "STUDENT_ORGANIZATION_RELATIONSHIP";
    private static final String ACCOUNT_SECURITY_MANAGEMENT = "ACCOUNT_SECURITY_MANAGEMENT";
    private static final String PARENT_ACCOUNT_LIFECYCLE = "PARENT_ACCOUNT_LIFECYCLE";
    private static final String PARENT_MOBILE_MANUAL_RECOVERY = "PARENT_MOBILE_MANUAL_RECOVERY";
    private static final String STUDENT_ACCOUNT_CANCELLATION = "STUDENT_ACCOUNT_CANCELLATION";
    private static final String LEARNING_TASK_MANAGEMENT = "LEARNING_TASK_MANAGEMENT";
    private static final String CLASS_MANAGEMENT = "CLASS_MANAGEMENT";
    private static final String COPY_PREVIOUS_DAY_TASK = "COPY_PREVIOUS_DAY_TASK";
    private static final String LEARNING_TASK_TEMPLATE = "LEARNING_TASK_TEMPLATE";
    private static final String GROWTH_POINT_QUERY = "GROWTH_POINT_QUERY";
    private static final String GROWTH_POINT_CORRECTION = "GROWTH_POINT_CORRECTION";
    private static final String REWARD_EXCHANGE = "REWARD_EXCHANGE";
    private static final String DAILY_GROWTH_REVIEW = "DAILY_GROWTH_REVIEW";
    private static final String PERIODIC_GROWTH_REPORT = "PERIODIC_GROWTH_REPORT";
    private static final String DICTIONARY_MANAGEMENT = "DICTIONARY_MANAGEMENT";
    private static final String CACHE_MANAGEMENT = "CACHE_MANAGEMENT";
    private static final String INTERFACE_SERVICE_MANAGEMENT = "INTERFACE_SERVICE_MANAGEMENT";
    private static final String ATTACHMENT_SERVICE = "ATTACHMENT_SERVICE";
    private static final String IMPORT_EXPORT_TEMPLATE_MANAGEMENT = "IMPORT_EXPORT_TEMPLATE_MANAGEMENT";
    private static final String DATA_IMPORT_VALIDATION = "DATA_IMPORT_VALIDATION";
    private static final String DATA_EXPORT = "DATA_EXPORT";
    private static final String STUDENT_BATCH_IMPORT = "STUDENT_BATCH_IMPORT";
    private static final String TEACHER_MANAGEMENT = "TEACHER_MANAGEMENT";
    private static final String STUDENT_EXCEPTION_REPORT = "STUDENT_EXCEPTION_REPORT";

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
                featureAccessService.isEnabled(STUDENT_WECHAT_AUTH, null),
                featureAccessService.isEnabled(PARENT_RELATIONSHIP_MANAGEMENT, null),
                MINIAPP_CLIENT.equals(client)
                        && featureAccessService.isEnabled(ORGANIZATION_MINIAPP_AUTH, null),
                WEB_CLIENT.equals(client)
                        && featureAccessService.isEnabled(ORGANIZATION_MANAGEMENT, null),
                featureAccessService.isEnabled(STUDENT_ORGANIZATION_RELATIONSHIP, null),
                featureAccessService.isEnabled(ACCOUNT_SECURITY_MANAGEMENT, null),
                featureAccessService.isEnabled(PARENT_ACCOUNT_LIFECYCLE, null),
                featureAccessService.isEnabled(PARENT_MOBILE_MANUAL_RECOVERY, null),
                featureAccessService.isEnabled(STUDENT_ACCOUNT_CANCELLATION, null),
                featureAccessService.isEnabled(LEARNING_TASK_MANAGEMENT, null),
                featureAccessService.isEnabled(CLASS_MANAGEMENT, null),
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
                featureAccessService.isEnabled(PERIODIC_GROWTH_REPORT, null),
                WEB_CLIENT.equals(client)
                        && featureAccessService.isEnabled(DICTIONARY_MANAGEMENT, null),
                WEB_CLIENT.equals(client)
                        && featureAccessService.isEnabled(CACHE_MANAGEMENT, null),
                WEB_CLIENT.equals(client)
                        && featureAccessService.isEnabled(INTERFACE_SERVICE_MANAGEMENT, null),
                featureAccessService.isEnabled(ATTACHMENT_SERVICE, null),
                WEB_CLIENT.equals(client)
                        && featureAccessService.isEnabled(IMPORT_EXPORT_TEMPLATE_MANAGEMENT, null)
                        && featureAccessService.isEnabled(ATTACHMENT_SERVICE, null),
                WEB_CLIENT.equals(client)
                        && featureAccessService.isEnabled(DATA_IMPORT_VALIDATION, null)
                        && featureAccessService.isEnabled(IMPORT_EXPORT_TEMPLATE_MANAGEMENT, null)
                        && featureAccessService.isEnabled(ATTACHMENT_SERVICE, null),
                WEB_CLIENT.equals(client)
                        && featureAccessService.isEnabled(DATA_EXPORT, null)
                        && featureAccessService.isEnabled(IMPORT_EXPORT_TEMPLATE_MANAGEMENT, null)
                        && featureAccessService.isEnabled(ATTACHMENT_SERVICE, null),
                WEB_CLIENT.equals(client)
                        && featureAccessService.isEnabled(STUDENT_BATCH_IMPORT, null)
                        && featureAccessService.isEnabled(DATA_IMPORT_VALIDATION, null)
                        && featureAccessService.isEnabled(IMPORT_EXPORT_TEMPLATE_MANAGEMENT, null)
                        && featureAccessService.isEnabled(ATTACHMENT_SERVICE, null),
                featureAccessService.isEnabled(TEACHER_MANAGEMENT, null),
                featureAccessService.isEnabled(STUDENT_EXCEPTION_REPORT, null),
                featureAccessService.isEnabled("ATTENDANCE_MANAGEMENT", null));
    }
}
