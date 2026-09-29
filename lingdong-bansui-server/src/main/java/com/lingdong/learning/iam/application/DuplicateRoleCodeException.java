package com.lingdong.learning.iam.application;

/**
 * Raised when a role code conflicts with either a baseline or existing custom role.
 */
public class DuplicateRoleCodeException extends RuntimeException {
    public DuplicateRoleCodeException(String code) {
        super("角色编码已存在：" + code);
    }
}
