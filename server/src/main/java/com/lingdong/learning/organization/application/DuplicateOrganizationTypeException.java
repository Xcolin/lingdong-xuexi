package com.lingdong.learning.organization.application;

/**
 * Raised when a configurable organization type code or name conflicts with an existing type.
 */
public class DuplicateOrganizationTypeException extends RuntimeException {
    public DuplicateOrganizationTypeException(String value) {
        super("组织类型编码或名称已存在：" + value);
    }
}
