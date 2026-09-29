package com.lingdong.learning.attachment.application;

import com.lingdong.learning.attachment.domain.FileStatus;
import com.lingdong.learning.attachment.domain.ManagedFileRecord;
import com.lingdong.learning.attachment.infrastructure.persistence.ManagedFileMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * 附件保留归档与销毁收敛（V54 遗留缺口的后续专项）。
 * 既有保留口径：删除仅解除业务可见关系；已归档业务中的附件保留可追溯记录；
 * 不设定附件保留期限——因此本服务不按时间清理任何附件，只对状态已 RETIRED
 * 但物理内容此前清理失败残留的文件做幂等重试。UPLOADING 滞留孤儿不含时间
 * 依据，不自动清理，待业务确认保留周期后另立专项。
 */
@Component
public class AttachmentRetentionCleanupService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AttachmentRetentionCleanupService.class);

    private final ManagedFileMapper fileMapper;
    private final AttachmentContentStorage contentStorage;
    private final int batchSize;

    public AttachmentRetentionCleanupService(
            ManagedFileMapper fileMapper,
            AttachmentContentStorage contentStorage,
            @Value("${lingdong.attachment.cleanup.batch-size:100}") int batchSize
    ) {
        this.fileMapper = Objects.requireNonNull(fileMapper, "附件文件Mapper不能为空");
        this.contentStorage = Objects.requireNonNull(contentStorage, "附件内容存储不能为空");
        this.batchSize = normalizedBatchSize(batchSize);
    }

    /** 收敛已退役文件的残留物理内容；单个失败不中断本轮，返回本轮成功清理数。 */
    public int cleanupResidualContent() {
        List<ManagedFileRecord> retiredFiles = fileMapper.findRetiredBatch(batchSize);
        int cleaned = 0;
        for (ManagedFileRecord file : retiredFiles) {
            if (file.status() != FileStatus.RETIRED) {
                continue;
            }
            try {
                contentStorage.delete(file.storageKey());
                cleaned++;
            } catch (RuntimeException exception) {
                LOGGER.warn("已退役附件残留内容清理失败，文件标识={}，异常类型={}",
                        file.id(), exception.getClass().getSimpleName());
            }
        }
        return cleaned;
    }

    private int normalizedBatchSize(int batchSize) {
        if (batchSize < 1 || batchSize > 1000) {
            throw new IllegalStateException("附件残留清理批大小必须为1至1000");
        }
        return batchSize;
    }
}
