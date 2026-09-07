package com.lingdong.learning.attachment.infrastructure.persistence;

import com.lingdong.learning.attachment.application.AttachmentFileLedgerView;
import com.lingdong.learning.attachment.domain.FileStatus;
import com.lingdong.learning.attachment.domain.ManagedFileRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ManagedFileMapper {
    int insert(@Param("file") ManagedFileRecord file);
    ManagedFileRecord findById(@Param("id") Long id);
    int markAvailable(@Param("id") Long id, @Param("contentSha256") String contentSha256);
    int markRetired(@Param("id") Long id);
    List<AttachmentFileLedgerView> findLedger(
            @Param("originalName") String originalName,
            @Param("moduleCode") String moduleCode,
            @Param("fileCategory") String fileCategory,
            @Param("status") FileStatus status,
            @Param("uploaderId") Long uploaderId,
            @Param("createdFrom") LocalDateTime createdFrom,
            @Param("createdTo") LocalDateTime createdTo
    );
}
