package com.lingdong.learning.organization.application;

/**
 * Raised when another organization node under the same parent already has the requested name.
 */
public class DuplicateOrganizationNameException extends RuntimeException {
    public DuplicateOrganizationNameException(String name) {
        super("同级组织名称已存在：" + name);
    }
}
