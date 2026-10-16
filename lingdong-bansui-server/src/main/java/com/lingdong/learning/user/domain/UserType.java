package com.lingdong.learning.user.domain;

/**
 * Primary identity category of an account; permissions remain role-based.
 */
public enum UserType {
    PERSONAL,
    PLATFORM,
    ORGANIZATION,
    FAMILY,
    STUDENT
}
