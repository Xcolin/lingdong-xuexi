package com.lingdong.learning.attachment.infrastructure.persistence;

import com.lingdong.learning.attachment.domain.FileRelationRecord;
import com.lingdong.learning.attachment.domain.ManagedFileRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FileRelationMapper {
    int insert(@Param("relation") FileRelationRecord relation);
    FileRelationRecord findById(@Param("id") Long id);
    int markReleased(@Param("id") Long id);
    int countActiveByFileId(@Param("fileId") Long fileId);
    int countReadableByCurrentReviewer(@Param("fileId") Long fileId, @Param("userId") Long userId);
    List<ManagedFileRecord> findActiveFilesByBusiness(
            @Param("moduleCode") String moduleCode,
            @Param("businessId") Long businessId,
            @Param("relationType") String relationType
    );
}
