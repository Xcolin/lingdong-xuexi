package com.lingdong.learning.attachment.infrastructure.persistence;

import com.lingdong.learning.attachment.domain.FileRelationRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FileRelationMapper {
    int insert(@Param("relation") FileRelationRecord relation);
    FileRelationRecord findById(@Param("id") Long id);
    int markReleased(@Param("id") Long id);
}
