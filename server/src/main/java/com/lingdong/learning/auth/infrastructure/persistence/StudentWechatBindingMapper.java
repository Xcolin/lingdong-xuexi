package com.lingdong.learning.auth.infrastructure.persistence;

import com.lingdong.learning.auth.application.StudentWechatBinding;
import com.lingdong.learning.auth.application.StudentWechatBindingAudit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 学生当前微信绑定和不可变变更审计的持久化边界。 */
@Mapper
public interface StudentWechatBindingMapper {
    StudentWechatBinding findByIdentity(@Param("appId") String appId, @Param("openId") String openId);

    StudentWechatBinding findByStudentId(@Param("studentId") Long studentId);

    StudentWechatBinding findByStudentIdForUpdate(@Param("studentId") Long studentId);

    List<com.lingdong.learning.auth.application.StudentWechatBindingRow> findPrimaryStudentsByParentUserId(
            @Param("parentUserId") Long parentUserId);

    int insert(@Param("binding") StudentWechatBinding binding);

    int touchLastLogin(@Param("id") Long id, @Param("lastLoginAt") LocalDateTime lastLoginAt);

    int deleteById(@Param("id") Long id);

    int insertAudit(@Param("audit") StudentWechatBindingAudit audit);
}
