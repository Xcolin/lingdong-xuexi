package com.lingdong.learning.growthpoint.application;

import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthPointLifecycleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/** 按学生雪花标识游标分批处理到期沉睡周期，隔离单学生异常。 */
@Service
public class GrowthPointDormancyBatchService {
    private static final Logger LOGGER = LoggerFactory.getLogger(GrowthPointDormancyBatchService.class);
    private static final String FEATURE_CODE = "POINT_LIFECYCLE";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final GrowthPointLifecycleMapper lifecycleMapper;
    private final GrowthPointDormancyService dormancyService;
    private final FeatureAccessService featureAccessService;
    private final Clock clock;
    private final int batchSize;

    public GrowthPointDormancyBatchService(
            GrowthPointLifecycleMapper lifecycleMapper,
            GrowthPointDormancyService dormancyService,
            FeatureAccessService featureAccessService,
            Clock clock,
            @Value("${lingdong.point-lifecycle.batch-size:100}") int batchSize
    ) {
        if (batchSize < 1 || batchSize > 1_000) {
            throw new IllegalArgumentException("积分生命周期批次大小必须在 1 至 1000 之间");
        }
        this.lifecycleMapper = lifecycleMapper;
        this.dormancyService = dormancyService;
        this.featureAccessService = featureAccessService;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    public int processDueStudents() {
        if (!featureAccessService.isEnabled(FEATURE_CODE, null)) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now(clock.withZone(BUSINESS_ZONE));
        int processed = 0;
        long afterId = 0L;
        List<Long> studentIds;
        do {
            studentIds = lifecycleMapper.findDueDormancyStudentIdsAfter(afterId, now, batchSize);
            for (Long studentId : studentIds) {
                afterId = studentId;
                try {
                    dormancyService.processStudent(studentId, now);
                    processed++;
                } catch (RuntimeException exception) {
                    LOGGER.warn("学生积分生命周期处理失败，已隔离并继续。studentId={}", studentId, exception);
                }
            }
        } while (studentIds.size() == batchSize);
        return processed;
    }
}
