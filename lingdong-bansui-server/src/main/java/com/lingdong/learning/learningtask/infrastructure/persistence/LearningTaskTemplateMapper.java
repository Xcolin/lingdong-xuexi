package com.lingdong.learning.learningtask.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface LearningTaskTemplateMapper {
    List<LearningTaskTemplateRow> findVisible(@Param("ownerUserId") Long ownerUserId);

    List<LearningTaskTemplateTagRow> findTagsByTemplateIds(
            @Param("templateIds") List<Long> templateIds);

    LearningTaskTemplateRow findById(@Param("id") Long id);

    LearningTaskTemplateRow findByIdForUpdate(@Param("id") Long id);

    List<LearningTaskTemplateRow> findEnabledPersonalForUpdate(
            @Param("ownerUserId") Long ownerUserId);

    int countEnabledPersonal(@Param("ownerUserId") Long ownerUserId);

    boolean existsEnabledName(
            @Param("ownerScopeKey") String ownerScopeKey,
            @Param("templateName") String templateName);

    boolean existsOtherEnabledName(
            @Param("ownerScopeKey") String ownerScopeKey,
            @Param("templateName") String templateName,
            @Param("excludedId") Long excludedId);

    int findNextPersonalSortOrder(@Param("ownerUserId") Long ownerUserId);

    int insert(LearningTaskTemplateRow template);

    int insertTag(
            @Param("id") Long id,
            @Param("templateId") Long templateId,
            @Param("tagCode") String tagCode);

    int deleteTags(@Param("templateId") Long templateId);

    int update(
            @Param("template") LearningTaskTemplateRow template,
            @Param("expectedVersion") long expectedVersion);

    int markDeleted(
            @Param("id") Long id,
            @Param("ownerUserId") Long ownerUserId,
            @Param("expectedVersion") long expectedVersion);

    int updateSort(
            @Param("id") Long id,
            @Param("sortOrder") int sortOrder,
            @Param("ownerUserId") Long ownerUserId,
            @Param("expectedVersion") long expectedVersion);
}
