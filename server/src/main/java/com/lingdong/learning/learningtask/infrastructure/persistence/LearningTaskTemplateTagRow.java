package com.lingdong.learning.learningtask.infrastructure.persistence;

/** 用于批量装配模板标签的只读行。 */
public record LearningTaskTemplateTagRow(Long templateId, String tagCode) {
}
