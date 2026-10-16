package com.lingdong.learning.organization.onboarding.domain;

/** Batch and version must both match before any workflow mutation. */
public enum OnboardingState {
 PENDING, APPROVED, REJECTED, WITHDRAWN;
 public void requirePending(int version,int batch,int expectedVersion,int expectedBatch) {
  if(version!=expectedVersion||batch!=expectedBatch)throw new IllegalStateException("申请已更新，请刷新后重试");
  if(this!=PENDING)throw new IllegalStateException("仅待审核申请可执行此操作");
 }
 public void requireResubmittable(int version,int batch,int expectedVersion,int expectedBatch) {
  if(version!=expectedVersion||batch!=expectedBatch)throw new IllegalStateException("申请已更新，请刷新后重试");
  if(this!=REJECTED&&this!=WITHDRAWN)throw new IllegalStateException("仅驳回或撤回的申请可重新提交");
 }
}
