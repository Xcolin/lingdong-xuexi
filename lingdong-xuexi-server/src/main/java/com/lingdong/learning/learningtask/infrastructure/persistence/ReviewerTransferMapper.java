package com.lingdong.learning.learningtask.infrastructure.persistence;

import com.lingdong.learning.learningtask.domain.ReviewerTransfer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 审核责任转交历史持久化边界。 */
@Mapper
public interface ReviewerTransferMapper {
    int insert(@Param("transfer") ReviewerTransfer transfer);
}
