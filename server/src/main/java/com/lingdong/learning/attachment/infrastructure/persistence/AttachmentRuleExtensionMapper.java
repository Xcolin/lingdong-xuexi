package com.lingdong.learning.attachment.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AttachmentRuleExtensionMapper {
    int insert(@Param("id") Long id, @Param("ruleId") Long ruleId, @Param("extension") String extension);
    List<String> findExtensionsByRuleId(@Param("ruleId") Long ruleId);
}
