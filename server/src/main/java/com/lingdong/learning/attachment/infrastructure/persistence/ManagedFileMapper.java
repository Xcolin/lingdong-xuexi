package com.lingdong.learning.attachment.infrastructure.persistence;

import com.lingdong.learning.attachment.domain.ManagedFileRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ManagedFileMapper {
    int insert(@Param("file") ManagedFileRecord file);
    ManagedFileRecord findById(@Param("id") Long id);
    int markAvailable(@Param("id") Long id);
}
