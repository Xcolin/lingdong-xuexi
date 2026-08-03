package com.lingdong.learning.attachment.application;

/** Client-verified metadata that must still be checked again before an object-storage upload is authorized. */
public record AttachmentCandidate(String originalName, String contentType, long sizeBytes) { }
