package com.lingdong.learning.attachment.application;

/** Business module declaration of a file's visible owner and purpose. */
public record AttachFileToBusinessCommand(Long fileId, String moduleCode, Long businessId, String relationType,
                                          String visibleScope) { }
