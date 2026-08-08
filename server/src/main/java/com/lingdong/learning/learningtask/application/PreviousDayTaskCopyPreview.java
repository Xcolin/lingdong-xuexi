package com.lingdong.learning.learningtask.application;

import java.time.LocalDate;
import java.util.List;

/** 复制昨日任务前的服务端预览。 */
public record PreviousDayTaskCopyPreview(
        Long studentId,
        String studentName,
        LocalDate sourceDate,
        LocalDate targetDate,
        int candidateCount,
        List<String> duplicateTitles,
        boolean alreadyCopied,
        TaskCopyBatchResult existingBatch
) {
    public PreviousDayTaskCopyPreview {
        duplicateTitles = List.copyOf(duplicateTitles);
    }
}
