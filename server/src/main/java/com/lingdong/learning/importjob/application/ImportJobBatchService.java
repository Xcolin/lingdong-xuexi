package com.lingdong.learning.importjob.application;

import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.importjob.domain.ImportJobRecord;
import com.lingdong.learning.importjob.infrastructure.persistence.ImportJobMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/** 有限扫描排队作业，并隔离单个导入校验作业的处理失败。 */
@Service
public class ImportJobBatchService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImportJobBatchService.class);
    private static final int MAX_BATCH_SIZE = 100;

    private final ImportJobMapper jobMapper;
    private final ImportJobClaimService claimService;
    private final ImportJobResultService resultService;
    private final FeatureAccessService featureAccessService;
    private final int configuredBatchSize;

    public ImportJobBatchService(
            ImportJobMapper jobMapper,
            ImportJobClaimService claimService,
            ImportJobResultService resultService,
            FeatureAccessService featureAccessService,
            @Value("${lingdong.import-validation.batch-size:20}") int configuredBatchSize
    ) {
        this.jobMapper = jobMapper;
        this.claimService = claimService;
        this.resultService = resultService;
        this.featureAccessService = featureAccessService;
        this.configuredBatchSize = configuredBatchSize;
    }

    public int processQueuedJobs() {
        if (!requiredFeaturesEnabled()) {
            return 0;
        }
        int batchSize = Math.max(1, Math.min(configuredBatchSize, MAX_BATCH_SIZE));
        List<ImportJobRecord> candidates = jobMapper.findQueued(batchSize);
        int completedCount = 0;
        for (ImportJobRecord candidate : candidates) {
            ImportJobRecord claimed = claimService.claim(candidate.id(), candidate.versionNo());
            if (claimed == null) {
                continue;
            }
            try {
                resultService.process(claimed);
                completedCount++;
            } catch (RuntimeException exception) {
                markFailedAndContinue(claimed, exception);
            }
        }
        return completedCount;
    }

    private boolean requiredFeaturesEnabled() {
        return featureAccessService.isEnabled("DATA_IMPORT_VALIDATION", null)
                && featureAccessService.isEnabled("ATTACHMENT_SERVICE", null)
                && featureAccessService.isEnabled("IMPORT_EXPORT_TEMPLATE_MANAGEMENT", null);
    }

    private void markFailedAndContinue(ImportJobRecord job, RuntimeException processingFailure) {
        try {
            resultService.markSystemFailed(
                    job, "IMPORT_VALIDATION_SYSTEM_ERROR", "导入校验处理失败");
        } catch (RuntimeException statusFailure) {
            processingFailure.addSuppressed(statusFailure);
        }
        // 仅记录作业标识和异常类型，避免异常文本携带文件或个人信息。
        LOGGER.warn("导入校验作业处理失败，作业标识={}，异常类型={}",
                job.id(), processingFailure.getClass().getSimpleName());
    }
}
