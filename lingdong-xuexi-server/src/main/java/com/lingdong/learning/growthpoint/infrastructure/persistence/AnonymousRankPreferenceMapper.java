package com.lingdong.learning.growthpoint.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AnonymousRankPreferenceMapper {
    record Row(Long id, Long parentId, Long studentId, Long classId, boolean enabled, long version) { }
    Row find(@Param("parentId") Long parentId, @Param("studentId") Long studentId, @Param("classId") Long classId);
    Row findForUpdate(@Param("parentId") Long parentId, @Param("studentId") Long studentId, @Param("classId") Long classId);
    java.util.List<Row> findEnabledByParent(@Param("parentId") Long parentId);
    int insert(@Param("row") Row row);
    int update(@Param("row") Row row, @Param("expectedVersion") long expectedVersion);
}
