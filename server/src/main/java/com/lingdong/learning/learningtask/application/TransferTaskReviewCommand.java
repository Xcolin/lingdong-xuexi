package com.lingdong.learning.learningtask.application;

/** 当前审核人转交审核责任命令。 */
public record TransferTaskReviewCommand(Long reviewerUserId, String transferReason) {
}
