package com.lingdong.learning.auth.application;

import com.lingdong.learning.auth.infrastructure.config.ParentAccountFinalizationProperties;
import com.lingdong.learning.auth.infrastructure.persistence.ParentAccountLifecycleMapper;
import com.lingdong.learning.feature.application.FeatureAccessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** 有限批量扫描到期申请，并隔离单个账号终结失败。 */
@Service
public class ParentAccountFinalizationBatchService {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(ParentAccountFinalizationBatchService.class);
    private static final String FEATURE_CODE = "PARENT_ACCOUNT_LIFECYCLE";
    private static final int MAX_BATCH_SIZE = 1000;

    private final ParentAccountLifecycleMapper lifecycleMapper;
    private final ParentAccountFinalizationService finalizationService;
    private final FeatureAccessService featureAccessService;
    private final ParentAccountFinalizationProperties properties;
    private final Clock clock;

    public ParentAccountFinalizationBatchService(
            ParentAccountLifecycleMapper lifecycleMapper,
            ParentAccountFinalizationService finalizationService,
            FeatureAccessService featureAccessService,
            ParentAccountFinalizationProperties properties,
            Clock clock
    ) {
        this.lifecycleMapper = lifecycleMapper;
        this.finalizationService = finalizationService;
        this.featureAccessService = featureAccessService;
        this.properties = properties;
        this.clock = clock;
    }

    public int processDueCancellations() {
        if (!featureAccessService.isEnabled(FEATURE_CODE, null)) {
            return 0;
        }
        int batchSize = Math.max(1, Math.min(properties.getBatchSize(), MAX_BATCH_SIZE));
        List<Long> cancellationIds = lifecycleMapper.findDueFinalizationIds(
                LocalDateTime.now(clock), batchSize);
        int handledCount = 0;
        for (Long cancellationId : cancellationIds) {
            try {
                finalizationService.finalizeCancellation(cancellationId);
                handledCount++;
            } catch (RuntimeException exception) {
                // 仅记录申请标识和异常类型，避免异常文本携带个人信息。
                LOGGER.warn("家长注销申请终结失败，申请标识={}，异常类型={}",
                        cancellationId, exception.getClass().getSimpleName());
            }
        }
        return handledCount;
    }
}
