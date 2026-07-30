package com.lingdong.learning.organization.application;

/**
 * Raised when the immutable internal organization code is already in use.
 */
public class DuplicateOrganizationCodeException extends RuntimeException {
    public DuplicateOrganizationCodeException(String code) {
        super("组织编码已存在：" + code);
    }
}
