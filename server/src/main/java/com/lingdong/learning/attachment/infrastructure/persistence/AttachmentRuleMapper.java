package com.lingdong.learning.attachment.infrastructure.persistence;

import com.lingdong.learning.attachment.domain.AttachmentRuleRecord;
import com.lingdong.learning.attachment.domain.AttachmentRuleStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AttachmentRuleMapper {
    int insert(@Param("rule") AttachmentRuleRecord rule);
    AttachmentRuleRecord findById(@Param("id") Long id);
    AttachmentRuleRecord findByModuleAndCategory(@Param("moduleCode") String moduleCode, @Param("fileCategory") String fileCategory);
    int updateStatus(@Param("id") Long id, @Param("status") AttachmentRuleStatus status);
}
