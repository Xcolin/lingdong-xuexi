package com.lingdong.learning.learningtask.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.learningtask.application.PreviousDayTaskCopyPreview;

import java.time.LocalDate;
import java.util.List;

/** 复制昨日任务预览响应。 */
public record PreviousDayTaskCopyPreviewResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long studentId,
        String studentName,
        LocalDate sourceDate,
        LocalDate targetDate,
        int candidateCount,
        List<String> duplicateTitles,
        boolean alreadyCopied,
        TaskCopyBatchResponse existingBatch
) {
    static PreviousDayTaskCopyPreviewResponse from(PreviousDayTaskCopyPreview preview) {
        return new PreviousDayTaskCopyPreviewResponse(
                preview.studentId(), preview.studentName(), preview.sourceDate(), preview.targetDate(),
                preview.candidateCount(), preview.duplicateTitles(), preview.alreadyCopied(),
                preview.existingBatch() == null ? null : TaskCopyBatchResponse.from(preview.existingBatch()));
    }
}
