package com.lingdong.learning.user.infrastructure.persistence;

/** 用户标识到展示姓名的只读投影。 */
public record UserNameRow(Long id, String displayName) {
}
