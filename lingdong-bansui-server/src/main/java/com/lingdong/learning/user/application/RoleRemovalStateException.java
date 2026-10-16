package com.lingdong.learning.user.application;
/** Safe, actionable messages for known role removal conflicts. */
public final class RoleRemovalStateException extends IllegalStateException {
 public RoleRemovalStateException(String message) { super(message); }
}
