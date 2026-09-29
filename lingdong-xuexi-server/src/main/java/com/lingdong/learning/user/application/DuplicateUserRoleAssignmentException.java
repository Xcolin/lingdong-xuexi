package com.lingdong.learning.user.application;

/**
 * Raised when the same role is granted to a user for an identical scope more than once.
 */
public class DuplicateUserRoleAssignmentException extends RuntimeException {
    public DuplicateUserRoleAssignmentException() {
        super("该用户已拥有相同范围的角色授权");
    }
}
