package com.lingdong.learning.exportjob.application;

import com.lingdong.learning.exportjob.domain.ExportJobRecord;
import com.lingdong.learning.exportjob.infrastructure.config.ExportJobProperties;
import com.lingdong.learning.exportjob.infrastructure.persistence.ExportJobMapper;
import com.lingdong.learning.feature.application.FeatureAccessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/** 有限扫描排队作业，并隔离单个导出作业的处理失败。 */
@Service
public class ExportJobBatchService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExportJobBatchService.class);
    private static final int MAX_BATCH_SIZE = 100;

    private final ExportJobMapper jobMapper;
    private final ExportJobClaimService claimService;
    private final ExportJobExecutionService executionService;
    private final FeatureAccessService featureAccessService;
    private final ExportJobProperties properties;

    public ExportJobBatchService(
            ExportJobMapper jobMapper,
            ExportJobClaimService claimService,
            ExportJobExecutionService executionService,
            FeatureAccessService featureAccessService,
            ExportJobProperties properties
    ) {
        this.jobMapper = jobMapper;
        this.claimService = claimService;
        this.executionService = executionService;
        this.featureAccessService = featureAccessService;
        this.properties = properties;
    }

    public int processQueuedJobs() {
        if (!requiredFeaturesEnabled()) {
            return 0;
        }
        int batchSize = Math.max(1, Math.min(properties.getBatchSize(), MAX_BATCH_SIZE));
        List<ExportJobRecord> candidates = jobMapper.findQueued(batchSize);
        int completed = 0;
        for (ExportJobRecord candidate : candidates) {
            try {
                ExportJobRecord claimed = claimService.claim(candidate.id(), candidate.versionNo());
                if (claimed != null && executionService.execute(claimed)) {
                    completed++;
                }
            } catch (RuntimeException exception) {
                // 日志只保留作业标识和异常类型，避免导出内容进入日志。
                LOGGER.warn("导出作业处理失败，作业标识={}，异常类型={}",
                        candidate.id(), exception.getClass().getSimpleName());
            }
        }
        return completed;
    }

    private boolean requiredFeaturesEnabled() {
        return featureAccessService.isEnabled("DATA_EXPORT", null)
                && featureAccessService.isEnabled("IMPORT_EXPORT_TEMPLATE_MANAGEMENT", null)
                && featureAccessService.isEnabled("ATTACHMENT_SERVICE", null);
    }
}
