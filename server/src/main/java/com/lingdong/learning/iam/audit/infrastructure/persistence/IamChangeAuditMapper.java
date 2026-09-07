package com.lingdong.learning.iam.audit.infrastructure.persistence;

import com.lingdong.learning.iam.audit.application.IamChangeAudit;
import com.lingdong.learning.iam.audit.application.IamChangeAuditQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 身份权限审计只允许追加和查询，不暴露更新或删除能力。 */
@Mapper
public interface IamChangeAuditMapper {
    int insert(@Param("audit") IamChangeAudit audit);

    List<IamChangeAudit> findPage(@Param("query") IamChangeAuditQuery query);

    long count(@Param("query") IamChangeAuditQuery query);
}
