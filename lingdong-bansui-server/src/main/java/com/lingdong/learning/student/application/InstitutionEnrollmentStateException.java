package com.lingdong.learning.student.application;

/** Known enrollment state conflicts with safe user-facing explanations. */
public final class InstitutionEnrollmentStateException extends IllegalStateException {
    public InstitutionEnrollmentStateException(String message) { super(message); }
}
