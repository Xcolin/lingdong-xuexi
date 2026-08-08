package com.lingdong.learning.learningtask.infrastructure.persistence;

/** 服务端裁剪后的审核候选用户。 */
public record ReviewerOptionRow(Long userId, String displayName) {
}
