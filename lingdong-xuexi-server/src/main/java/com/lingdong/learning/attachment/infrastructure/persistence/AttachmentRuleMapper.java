package com.lingdong.learning.attachment.infrastructure.persistence;

import com.lingdong.learning.attachment.domain.AttachmentRuleRecord;
import com.lingdong.learning.attachment.domain.AttachmentRuleStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AttachmentRuleMapper {
    int insert(@Param("rule") AttachmentRuleRecord rule);
    AttachmentRuleRecord findById(@Param("id") Long id);
    AttachmentRuleRecord findByModuleAndCategory(@Param("moduleCode") String moduleCode, @Param("fileCategory") String fileCategory);
    List<AttachmentRuleRecord> findAll(
            @Param("ruleName") String ruleName,
            @Param("moduleCode") String moduleCode,
            @Param("fileCategory") String fileCategory,
            @Param("status") AttachmentRuleStatus status
    );
    int updateConfiguration(
            @Param("rule") AttachmentRuleRecord rule,
            @Param("expectedVersion") Long expectedVersion
    );
    int updateStatus(
            @Param("id") Long id,
            @Param("status") AttachmentRuleStatus status,
            @Param("expectedStatus") AttachmentRuleStatus expectedStatus,
            @Param("expectedVersion") Long expectedVersion
    );
}
