package com.lingdong.learning.exportjob.application.adapter;

import com.lingdong.learning.exportjob.application.template.ExportColumnDefinition;
import com.lingdong.learning.exportjob.domain.ExportJobType;
import com.lingdong.learning.exportjob.infrastructure.persistence.AttachmentLedgerExportMapper;
import com.lingdong.learning.exportjob.infrastructure.persistence.AttachmentLedgerExportRow;
import org.springframework.stereotype.Component;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/** 仅输出 V54 授权的安全元数据，不读取存储路径、摘要或文件内容。 */
@Component
public class AttachmentLedgerExportAdapter implements ExportDatasetAdapter {
    private static final List<ExportColumnDefinition> COLUMNS = List.of(
            new ExportColumnDefinition("NAME", "名称", true),
            new ExportColumnDefinition("MODULE_CODE", "所属模块", true),
            new ExportColumnDefinition("UPLOADER_NAME", "上传人", true),
            new ExportColumnDefinition("CREATED_AT", "创建时间", true),
            new ExportColumnDefinition("FILE_CATEGORY", "文件分类", true),
            new ExportColumnDefinition("SIZE_BYTES", "大小（字节）", true),
            new ExportColumnDefinition("STATUS", "状态", true));
    private final AttachmentLedgerExportMapper mapper;
    public AttachmentLedgerExportAdapter(AttachmentLedgerExportMapper mapper) { this.mapper = mapper; }
    @Override public ExportJobType type() { return ExportJobType.ATTACHMENT_LEDGER; }
    @Override public boolean sensitive() { return false; }
    @Override public List<ExportColumnDefinition> columns() { return COLUMNS; }
    @Override public long captureUpperBound(ExportRequestDefinition request) {
        Long upper = mapper.findUpperBound(request); return upper == null ? 0 : upper;
    }
    @Override public long count(ExportRequestDefinition request, long upperBound) { return mapper.count(request, upperBound); }
    @Override public ExportDataPage fetchAfter(ExportRequestDefinition request, long upperBound, long cursor, int limit) {
        if (limit < 1 || limit == Integer.MAX_VALUE || cursor < 0 || upperBound < 0) throw new IllegalArgumentException("导出分页参数不合法");
        var fetched = mapper.findAfter(request, upperBound, cursor, limit + 1);
        boolean more = fetched.size() > limit;
        var selected = more ? fetched.subList(0, limit) : fetched;
        return new ExportDataPage(selected.stream().map(this::values).toList(),
                selected.isEmpty() ? null : selected.get(selected.size() - 1).id(), more);
    }
    private Map<String, Object> values(AttachmentLedgerExportRow row) {
        return Map.of("NAME", row.originalName(), "MODULE_CODE", row.moduleCode(),
                "UPLOADER_NAME", ExportMasking.familyName(row.uploaderName()),
                "CREATED_AT", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(row.createdAt()),
                "FILE_CATEGORY", row.fileCategory(), "SIZE_BYTES", row.sizeBytes(), "STATUS", row.status());
    }
}
